<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue';
import type { PublicArticle, Comment, PublicAnnotation } from '@cnotes/types';
import { api } from '../api';
import { toast } from '../toast';
import { relTime } from '../format';
import { levelAtLeast } from '../share';
import ShareCardModal from '../components/ShareCardModal.vue';

const props = defineProps<{ id: string; loggedIn: boolean }>();
const emit = defineEmits<{ back: []; login: []; openProfile: [userId: string] }>();

const article = ref<PublicArticle | null>(null);
const annotations = ref<PublicAnnotation[]>([]);
const comments = ref<Comment[]>([]);
const loading = ref(true);
const notFound = ref(false);
const busy = ref(false);
const bodyEl = ref<HTMLElement>();

const eff = computed(() => article.value?.effectiveShareLevel);
const shareCardOpen = ref(false);
const canBookmark = computed(() => levelAtLeast(eff.value, 'BOOKMARKABLE'));
const canCollect = computed(() => levelAtLeast(eff.value, 'COLLECTABLE'));
const canAnnotate = computed(() => levelAtLeast(eff.value, 'ANNOTATABLE') && !article.value?.mine);
const canComment = computed(() => levelAtLeast(eff.value, 'COMMENTABLE'));

async function load(id: string) {
  loading.value = true;
  notFound.value = false;
  article.value = null;
  annotations.value = [];
  comments.value = [];
  try {
    article.value = await api.getPublicArticle(id);
    window.scrollTo(0, 0);
    annotations.value = await api.listAnnotations(id).catch(() => []);
    comments.value = await api.listComments(id).catch(() => []);
  } catch (e) {
    const err = e as { status?: number };
    notFound.value = err.status === 404;
    if (!notFound.value) toast('打开失败,请稍后重试');
  } finally {
    loading.value = false;
  }
}
watch(() => props.id, load, { immediate: true });

function requireLogin(): boolean {
  if (props.loggedIn) return true;
  emit('login');
  return false;
}

/* ---------- 正文按公开批注锚点切段高亮 ---------- */
interface Segment { text: string; annotated?: boolean; }
const segments = computed<Segment[]>(() => {
  const content = article.value?.content ?? '';
  if (!content) return [{ text: content }];
  const anchored = annotations.value
    .filter((a) => a.anchor && a.anchor.start >= 0 && a.anchor.end <= content.length && a.anchor.start < a.anchor.end)
    .map((a) => a.anchor!)
    .sort((x, y) => x.start - y.start);
  const segs: Segment[] = [];
  let cursor = 0;
  for (const an of anchored) {
    if (an.start < cursor) continue;
    if (an.start > cursor) segs.push({ text: content.slice(cursor, an.start) });
    segs.push({ text: content.slice(an.start, an.end), annotated: true });
    cursor = an.end;
  }
  if (cursor < content.length) segs.push({ text: content.slice(cursor) });
  return segs;
});

/* ---------- 点赞 ---------- */
async function toggleLike() {
  if (!article.value || busy.value || !requireLogin()) return;
  const a = article.value;
  busy.value = true;
  try {
    if (a.liked) { await api.unlike(a.id); a.liked = false; a.likeCount--; }
    else { await api.like(a.id); a.liked = true; a.likeCount++; }
  } catch (e) { toast(`操作失败:${(e as Error).message}`); }
  finally { busy.value = false; }
}

/* ---------- 收藏 / 收录 ---------- */
async function toggleBookmark() {
  if (!article.value || busy.value || !requireLogin()) return;
  const a = article.value;
  busy.value = true;
  try {
    if (a.bookmarked) { await api.unbookmark(a.id); a.bookmarked = false; toast('已取消收藏'); }
    else { await api.bookmark(a.id); a.bookmarked = true; toast('已加入收藏'); }
  } catch (e) { toast(`操作失败:${(e as Error).message}`); }
  finally { busy.value = false; }
}
async function toggleCollect() {
  if (!article.value || busy.value || !requireLogin()) return;
  const a = article.value;
  busy.value = true;
  try {
    if (a.collected) { await api.uncollectArticle(a.id); a.collected = false; toast('已取消收录'); }
    else {
      const note = window.prompt('收录到我的知识库 —— 可加一句我的笔记(可留空):') ?? undefined;
      await api.collectArticle(a.id, note); a.collected = true; toast('已收录,可在收件箱查看');
    }
  } catch (e) { toast(`操作失败:${(e as Error).message}`); }
  finally { busy.value = false; }
}

/* ---------- 公开批注:划选发表 ---------- */
const tip = ref<{ left: number; top: number } | null>(null);
const compose = ref<{ left: number; top: number } | null>(null);
const composeText = ref('');
let pending: { start: number; end: number; quote: string; rect: DOMRect } | null = null;

function offsetTo(node: Node, nodeOffset: number): number {
  const r = document.createRange();
  r.setStart(bodyEl.value!, 0);
  r.setEnd(node, nodeOffset);
  return r.toString().length;
}
function onMouseUp() {
  if (!canAnnotate.value || compose.value) return;
  const sel = window.getSelection();
  const body = bodyEl.value;
  if (!sel || sel.rangeCount === 0 || !body || !sel.toString().trim() || !body.contains(sel.anchorNode)) {
    tip.value = null;
    return;
  }
  const range = sel.getRangeAt(0);
  const a = offsetTo(range.startContainer, range.startOffset);
  const b = offsetTo(range.endContainer, range.endOffset);
  const start = Math.min(a, b), end = Math.max(a, b);
  const content = article.value?.content ?? '';
  pending = { start, end, quote: content.slice(start, end), rect: range.getBoundingClientRect() };
  tip.value = { left: pending.rect.left + pending.rect.width / 2, top: pending.rect.top };
}
function startAnnotate() {
  if (!pending) return;
  if (!requireLogin()) { tip.value = null; return; }
  const r = pending.rect;
  compose.value = { left: Math.max(12, Math.min(r.left, window.innerWidth - 276)), top: r.bottom + 8 };
  composeText.value = '';
  tip.value = null;
  window.getSelection()?.removeAllRanges();
}
async function saveAnnotation() {
  if (!pending || !article.value) { compose.value = null; return; }
  try {
    const created = await api.addAnnotation(article.value.id, {
      quote: pending.quote, thought: composeText.value.trim(),
      anchor: { start: pending.start, end: pending.end },
    });
    annotations.value = [...annotations.value, created];
    toast('已发表公开批注');
  } catch (e) { toast(`发表失败:${(e as Error).message}`); }
  finally { compose.value = null; pending = null; }
}

/* ---------- 评论 ---------- */
const newComment = ref('');
const replyTo = ref<Comment | null>(null);
const replyText = ref('');

const topComments = computed(() => comments.value.filter((c) => !c.parentId));
function repliesOf(id: string) { return comments.value.filter((c) => c.parentId === id); }

async function submitComment() {
  if (!newComment.value.trim() || !article.value || !requireLogin()) return;
  try {
    const c = await api.addComment(article.value.id, newComment.value.trim());
    comments.value = [...comments.value, c];
    newComment.value = '';
  } catch (e) { toast(`评论失败:${(e as Error).message}`); }
}
async function submitReply() {
  if (!replyText.value.trim() || !article.value || !replyTo.value || !requireLogin()) return;
  try {
    const c = await api.addComment(article.value.id, replyText.value.trim(), replyTo.value.id);
    comments.value = [...comments.value, c];
    replyText.value = '';
    replyTo.value = null;
  } catch (e) { toast(`回复失败:${(e as Error).message}`); }
}
function startReply(c: Comment) {
  if (!requireLogin()) return;
  replyTo.value = c;
  replyText.value = '';
  nextTick(() => document.querySelector<HTMLTextAreaElement>('.reply-box textarea')?.focus());
}
async function removeComment(c: Comment) {
  try { await api.deleteComment(c.id); comments.value = comments.value.filter((x) => x.id !== c.id && x.parentId !== c.id); }
  catch (e) { toast(`删除失败:${(e as Error).message}`); }
}
</script>

<template>
  <div class="reader">
    <div class="r-top"><button class="back" @click="emit('back')">← 返回</button></div>

    <div v-if="loading" class="empty">加载中…</div>
    <div v-else-if="notFound" class="empty">这篇内容不存在或未公开。</div>

    <template v-else-if="article">
      <h1 class="r-title">{{ article.title || '(未命名)' }}</h1>
      <div class="r-meta">
        <button class="author-link" @click="emit('openProfile', article.ownerId)">😊 来自 {{ article.ownerNickname || '匿名' }}</button>
        <a v-if="article.url" class="origin" :href="article.url" target="_blank" rel="noopener">看原文 ↗</a>
      </div>

      <div v-if="article.tags && article.tags.length" class="r-tags">
        <span v-for="t in article.tags" :key="t" class="tag">{{ t }}</span>
      </div>

      <div v-if="article.summary || article.keyPoints.length" class="distill">
        <h4>⚗ 自动沉淀</h4>
        <p v-if="article.summary" class="summary">{{ article.summary }}</p>
        <ul v-if="article.keyPoints.length"><li v-for="(p, i) in article.keyPoints" :key="i">{{ p }}</li></ul>
      </div>

      <div ref="bodyEl" class="r-body" @mouseup="onMouseUp">
        <template v-for="(seg, i) in segments" :key="i">
          <mark v-if="seg.annotated" class="hl pub">{{ seg.text }}</mark>
          <template v-else>{{ seg.text }}</template>
        </template>
      </div>

      <!-- 公开批注列表 -->
      <div v-if="annotations.length" class="annot-list">
        <h4>📝 公开批注 <span class="cnt">{{ annotations.length }}</span></h4>
        <div v-for="an in annotations" :key="an.id" class="annot-item">
          <div class="annot-quote">{{ an.quote }}</div>
          <div v-if="an.thought" class="annot-thought">{{ an.thought }}</div>
          <div class="annot-meta">{{ an.authorNickname || '匿名' }} · {{ relTime(an.createTime) }}</div>
        </div>
      </div>
      <p v-if="canAnnotate" class="annot-hint">划选正文即可发表公开批注 ✍</p>

      <!-- 操作条 -->
      <div class="public-actions">
        <button v-if="!article.mine" class="pa-btn" :class="{ on: article.liked }" :disabled="busy" @click="toggleLike">
          👍 {{ article.liked ? '已赞' : '赞' }} ({{ article.likeCount }})
        </button>
        <button v-if="!article.mine && canBookmark" class="pa-btn" :class="{ on: article.bookmarked }" :disabled="busy" @click="toggleBookmark">
          🔖 {{ article.bookmarked ? '已收藏' : '收藏' }}
        </button>
        <button v-if="!article.mine && canCollect" class="pa-btn" :class="{ on: article.collected }" :disabled="busy" @click="toggleCollect">
          📥 {{ article.collected ? '已收录' : '收录到我的知识库' }}
        </button>
        <button class="pa-btn" @click="shareCardOpen = true">📤 分享</button>
      </div>

      <!-- 评论区 -->
      <div v-if="canComment" class="comments">
        <h4>💬 评论 <span class="cnt">{{ comments.length }}</span></h4>

        <div class="comment-composer">
          <textarea v-model="newComment" placeholder="写评论…" rows="2"></textarea>
          <button class="send-btn" :disabled="!newComment.trim()" @click="submitComment">发送</button>
        </div>

        <div v-if="!topComments.length" class="comment-empty">还没有评论,来抢沙发。</div>
        <div v-for="c in topComments" :key="c.id" class="comment">
          <div class="comment-head">
            <span class="comment-author">😊 {{ c.authorNickname || '匿名' }}</span>
            <span v-if="c.byArticleAuthor" class="author-tag">作者</span>
            <span class="comment-time">{{ relTime(c.createTime) }}</span>
          </div>
          <div class="comment-body">{{ c.body }}</div>
          <div class="comment-ops">
            <button @click="startReply(c)">回复</button>
            <button v-if="c.mine" class="del" @click="removeComment(c)">删除</button>
          </div>

          <!-- 楼中楼 -->
          <div v-for="r in repliesOf(c.id)" :key="r.id" class="comment reply">
            <div class="comment-head">
              <span class="comment-author">↳ 😊 {{ r.authorNickname || '匿名' }}</span>
              <span v-if="r.byArticleAuthor" class="author-tag">作者</span>
              <span class="comment-time">{{ relTime(r.createTime) }}</span>
            </div>
            <div class="comment-body">{{ r.body }}</div>
            <div class="comment-ops">
              <button @click="startReply(c)">回复</button>
              <button v-if="r.mine" class="del" @click="removeComment(r)">删除</button>
            </div>
          </div>

          <div v-if="replyTo && replyTo.id === c.id" class="reply-box">
            <textarea v-model="replyText" :placeholder="`回复 ${c.authorNickname || '匿名'}…`" rows="2"></textarea>
            <div class="reply-ops">
              <button class="cancel" @click="replyTo = null">取消</button>
              <button class="send-btn" :disabled="!replyText.trim()" @click="submitReply">回复</button>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- 划选浮条 / 批注气泡 -->
    <div v-if="tip" class="sel-tip" :style="{ left: tip.left + 'px', top: tip.top + 'px' }">
      <button @mousedown.prevent @click="startAnnotate">✍ 发表公开批注</button>
    </div>
    <div v-if="compose" class="note-compose" :style="{ left: compose.left + 'px', top: compose.top + 'px' }">
      <textarea v-model="composeText" placeholder="写下你的公开批注…(可留空,仅划线)"></textarea>
      <div class="nc-actions">
        <button class="cancel" @click="compose = null">取消</button>
        <button class="save" @click="saveAnnotation">发表</button>
      </div>
    </div>
  </div>

  <ShareCardModal
    v-if="article"
    :open="shareCardOpen"
    :article-id="article.id"
    :title="article.title"
    :summary="article.summary"
    :tags="article.tags"
    :author-nickname="article.ownerNickname ?? undefined"
    @close="shareCardOpen = false"
  />
</template>
