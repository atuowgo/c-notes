<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue';
import DOMPurify from 'dompurify';
import type { ArticleDetail } from '@cnotes/types';
import { api } from '../api';
import { srcLabel } from '../format';
import { toast } from '../toast';
import { addNote, notesForArticle } from '../notes';
import DistillCard from '../components/DistillCard.vue';
import RecommendList from '../components/RecommendList.vue';

const props = defineProps<{ id: string }>();
const emit = defineEmits<{
  back: [];
  open: [id: string];
  openIdeas: [];
  loaded: [article: ArticleDetail | null];
}>();

const article = ref<ArticleDetail | null>(null);
const bodyEl = ref<HTMLElement>();

interface Segment {
  text: string;
  noteId?: string;
}

// 正文按想法锚点(字符偏移)切段,命中段包 <mark>(由响应式 notes 驱动,survive 重渲染)。
const segments = computed<Segment[]>(() => {
  const content = article.value?.content ?? '';
  const id = article.value?.id;
  if (!content || !id) return [{ text: content }];
  const ns = notesForArticle(id)
    .filter(
      (n) => n.anchor && n.anchor.start >= 0 && n.anchor.end <= content.length && n.anchor.start < n.anchor.end,
    )
    .sort((a, b) => a.anchor!.start - b.anchor!.start);
  const segs: Segment[] = [];
  let cursor = 0;
  for (const n of ns) {
    const { start, end } = n.anchor!;
    if (start < cursor) continue; // 重叠则跳过
    if (start > cursor) segs.push({ text: content.slice(cursor, start) });
    segs.push({ text: content.slice(start, end), noteId: n.id });
    cursor = end;
  }
  if (cursor < content.length) segs.push({ text: content.slice(cursor) });
  return segs;
});

const noteCount = computed(() => (article.value ? notesForArticle(article.value.id).length : 0));

// 富 HTML 正文(Task 6 服务端已净化,这里 DOMPurify 二次净化防 XSS)。
const safeHtml = computed(() =>
  article.value?.contentHtml ? DOMPurify.sanitize(article.value.contentHtml) : '',
);
const hasHtml = computed(() => !!safeHtml.value);

async function load(id: string) {
  article.value = null;
  emit('loaded', null);
  try {
    article.value = await api.getArticle(id);
    emit('loaded', article.value);
    window.scrollTo(0, 0);
  } catch (e) {
    const err = e as { status?: number; message: string };
    toast(err.status === 404 ? '文章不存在' : `打开失败:${err.message}`);
    emit('back');
  }
}
watch(() => props.id, load, { immediate: true });

/* ---------- 划线记想法 ---------- */
const tip = ref<{ left: number; top: number } | null>(null);
const compose = ref<{ left: number; top: number } | null>(null);
const composeText = ref('');
const composeEl = ref<HTMLTextAreaElement>();
let pending: { start: number; end: number; quote: string; rect: DOMRect } | null = null;

function offsetTo(node: Node, nodeOffset: number): number {
  const r = document.createRange();
  r.setStart(bodyEl.value!, 0);
  r.setEnd(node, nodeOffset);
  return r.toString().length;
}

function onMouseUp() {
  if (compose.value) return;
  const sel = window.getSelection();
  const body = bodyEl.value;
  if (!sel || sel.rangeCount === 0 || !body || !sel.toString().trim() || !body.contains(sel.anchorNode)) {
    tip.value = null;
    return;
  }
  const range = sel.getRangeAt(0);
  const a = offsetTo(range.startContainer, range.startOffset);
  const b = offsetTo(range.endContainer, range.endOffset);
  const start = Math.min(a, b);
  const end = Math.max(a, b);
  pending = { start, end, quote: range.toString(), rect: range.getBoundingClientRect() };
  tip.value = { left: pending.rect.left + pending.rect.width / 2, top: pending.rect.top };
}

// 点"划线记想法":先弹想法气泡(此时尚未落库),保存才持久化。
function startMark() {
  if (!pending) return;
  const r = pending.rect;
  compose.value = {
    left: Math.max(12, Math.min(r.left, window.innerWidth - 276)),
    top: r.bottom + 8,
  };
  composeText.value = '';
  tip.value = null;
  window.getSelection()?.removeAllRanges();
  nextTick(() => composeEl.value?.focus());
}

async function saveNote() {
  if (!pending || !article.value) {
    compose.value = null;
    return;
  }
  const note = await addNote({
    articleId: article.value.id,
    quote: pending.quote,
    thought: composeText.value.trim(),
    anchor: { start: pending.start, end: pending.end },
  });
  compose.value = null;
  pending = null;
  if (!note) toast('保存失败,请确认后端已启动');
}

function cancelNote() {
  compose.value = null;
  pending = null;
}

// 富 HTML 高亮 click 事件委托:落在 mark.hl 内即打开想法列表(替代每个 mark 逐一绑定)。
function onBodyClick(e: MouseEvent) {
  if ((e.target as HTMLElement).closest?.('mark.hl')) emit('openIdeas');
}

/* ---------- 富 HTML 上色(纯文本模式靠 segments 的 <mark>,这里只处理 v-html 场景) ---------- */

// 已知限制:富 HTML 锚点基于渲染后 DOM 文本坐标,与纯文本降级模式(基于 article.content)坐标系不同。
// 同一篇文章模式固定故自洽;但若某篇 contentHtml 被重新抓取/净化改变,旧锚点可能错位甚至越界,导致该高亮静默消失。
// 遍历 bodyEl 文本节点,把落在各 note [start,end) 区间的文本包 <mark>。
// 升序 + cursor 跳过、start 小者胜,与纯文本 segments 同语义(插入 mark 不改变文本总长度,升序不会导致偏移漂移)。
function paintHighlights() {
  const body = bodyEl.value;
  const art = article.value;
  if (!body || !art || !hasHtml.value) return;
  // 先解包旧 mark(避免重复上色)
  body.querySelectorAll('mark.hl').forEach((m) => {
    const parent = m.parentNode as Node;
    while (m.firstChild) parent.insertBefore(m.firstChild, m);
    parent.removeChild(m);
  });
  body.normalize();
  const ns = notesForArticle(art.id)
    .filter((n) => n.anchor && n.anchor.start < n.anchor.end)
    .sort((a, b) => a.anchor!.start - b.anchor!.start); // 升序,与纯文本 segments 同序
  let cursor = 0;
  for (const n of ns) {
    if (n.anchor!.start < cursor) continue;         // 重叠:start 小者(更靠前)胜,与 segments 一致
    if (wrapRange(body, n.anchor!.start, n.anchor!.end, n.id)) cursor = n.anchor!.end;
  }
}

// 用全局文本偏移([start,end) 基于 body 内文本节点拼接)定位并包裹 <mark>;成功上色返回 true,跨块/失败返回 false。
function wrapRange(body: HTMLElement, start: number, end: number, noteId: string): boolean {
  const walker = document.createTreeWalker(body, NodeFilter.SHOW_TEXT);
  let pos = 0;
  let sNode: Text | null = null, sOff = 0, eNode: Text | null = null, eOff = 0;
  let t = walker.nextNode() as Text | null;
  while (t) {
    const len = t.data.length;
    if (!sNode && start < pos + len) { sNode = t; sOff = start - pos; }
    if (!eNode && end <= pos + len) { eNode = t; eOff = end - pos; break; }
    pos += len;
    t = walker.nextNode() as Text | null;
  }
  if (!sNode || !eNode) return false;
  try {
    const range = document.createRange();
    range.setStart(sNode, sOff);
    range.setEnd(eNode, eOff);
    const mark = document.createElement('mark');
    mark.className = 'hl';
    mark.dataset.noteId = noteId;   // 供事件委托识别
    range.surroundContents(mark);   // 跨块边界会抛,视为未上色
    return true;
  } catch { return false; }
}

// 依赖只看笔记数量;现有改笔记的操作(updateNoteThought)不动 anchor,故数量不变即无需重绘。
// 若将来支持"编辑划线范围",需把此依赖改为对 anchor 敏感(如序列化 hash),否则会漏重绘。
watch(
  [safeHtml, () => (article.value ? notesForArticle(article.value.id).length : 0)],
  () => nextTick(paintHighlights),
);
</script>

<template>
  <div class="reader">
    <div class="r-top">
      <button class="back" @click="emit('back')">← 返回收件箱</button>
      <button v-if="article" class="ideas-entry" @click="emit('openIdeas')">
        💡 本文想法 <span class="cnt">{{ noteCount }}</span>
      </button>
    </div>

    <template v-if="article">
      <h1 class="r-title">{{ article.title || '(未命名)' }}</h1>
      <div class="r-meta">
        <span class="src">{{ srcLabel(article.sourceType) }}</span>
        <template v-if="article.author"><span>·</span><span>{{ article.author }}</span></template>
        <a v-if="article.url" class="origin" :href="article.url" target="_blank" rel="noopener">看原文 ↗</a>
      </div>

      <div v-if="article.tags && article.tags.length" class="r-tags">
        <span v-for="t in article.tags" :key="t" class="tag">{{ t }}</span>
      </div>

      <DistillCard :article="article" />

      <!-- 富 HTML 主路径;划线捕获(@mouseup)+ 高亮(paintHighlights)均用渲染后 bodyEl 的 DOM 文本坐标,与 article.content 无关 -->
      <div v-if="hasHtml" ref="bodyEl" class="r-body reader-content" v-html="safeHtml" @mouseup="onMouseUp" @click="onBodyClick"></div>
      <!-- 纯文本降级(保留 segments 渲染 + 提示;tip 移到 bodyEl 外,避免污染 offsetTo 偏移) -->
      <template v-else-if="article?.content">
        <p class="degrade-tip">未能提取版式,以下为纯文本;<a :href="article.url" target="_blank" rel="noopener">看原文 ↗</a></p>
        <div ref="bodyEl" class="r-body reader-plain" @mouseup="onMouseUp">
          <template v-for="(seg, i) in segments" :key="i">
            <mark v-if="seg.noteId" class="hl" @click="emit('openIdeas')">{{ seg.text }}</mark>
            <template v-else>{{ seg.text }}</template>
          </template>
        </div>
      </template>
      <!-- 仅元信息降级 -->
      <div v-else class="r-body"><p>暂无可读正文,<a :href="article?.url" target="_blank" rel="noopener">看原文 ↗</a></p></div>

      <RecommendList :article-id="article.id" :tags="article.tags ?? []" @open="emit('open', $event)" />
    </template>

    <!-- 选区浮条 -->
    <div v-if="tip" class="sel-tip" :style="{ left: tip.left + 'px', top: tip.top + 'px' }">
      <button @mousedown.prevent @click="startMark">✍ 划线记想法</button>
    </div>

    <!-- 想法编辑气泡 -->
    <div
      v-if="compose"
      class="note-compose"
      :style="{ left: compose.left + 'px', top: compose.top + 'px' }"
    >
      <textarea
        ref="composeEl"
        v-model="composeText"
        placeholder="写下你的想法…(可留空,仅划线)"
      ></textarea>
      <div class="nc-actions">
        <button class="cancel" @click="cancelNote">取消</button>
        <button class="save" @click="saveNote">保存</button>
      </div>
    </div>
  </div>
</template>
