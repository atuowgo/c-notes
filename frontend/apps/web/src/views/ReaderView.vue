<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue';
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
  const content = article.value?.content ?? '';
  pending = { start, end, quote: content.slice(start, end), rect: range.getBoundingClientRect() };
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

      <div ref="bodyEl" class="r-body" @mouseup="onMouseUp">
        <template v-for="(seg, i) in segments" :key="i">
          <mark v-if="seg.noteId" class="hl" @click="emit('openIdeas')">{{ seg.text }}</mark>
          <template v-else>{{ seg.text }}</template>
        </template>
      </div>

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
