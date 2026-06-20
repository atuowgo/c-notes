<script setup lang="ts">
import { ref, watch } from 'vue';
import type { PublicArticle } from '@cnotes/types';
import { api } from '../api';
import { toast } from '../toast';
import { levelAtLeast } from '../share';

const props = defineProps<{ id: string; loggedIn: boolean }>();
const emit = defineEmits<{ back: []; login: [] }>();

const article = ref<PublicArticle | null>(null);
const loading = ref(true);
const notFound = ref(false);
const busy = ref(false);

async function load(id: string) {
  loading.value = true;
  notFound.value = false;
  article.value = null;
  try {
    article.value = await api.getPublicArticle(id);
    window.scrollTo(0, 0);
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

async function toggleBookmark() {
  if (!article.value || busy.value) return;
  if (!requireLogin()) return;
  busy.value = true;
  const a = article.value;
  try {
    if (a.bookmarked) {
      await api.unbookmark(a.id);
      a.bookmarked = false;
      toast('已取消收藏');
    } else {
      await api.bookmark(a.id);
      a.bookmarked = true;
      toast('已加入收藏');
    }
  } catch (e) {
    toast(`操作失败:${(e as Error).message}`);
  } finally {
    busy.value = false;
  }
}

async function toggleCollect() {
  if (!article.value || busy.value) return;
  if (!requireLogin()) return;
  const a = article.value;
  busy.value = true;
  try {
    if (a.collected) {
      await api.uncollectArticle(a.id);
      a.collected = false;
      toast('已取消收录');
    } else {
      const note = window.prompt('收录到我的知识库 —— 可加一句我的笔记(可留空):') ?? undefined;
      await api.collectArticle(a.id, note);
      a.collected = true;
      toast('已收录,可在收件箱查看');
    }
  } catch (e) {
    toast(`操作失败:${(e as Error).message}`);
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <div class="reader">
    <div class="r-top">
      <button class="back" @click="emit('back')">← 返回</button>
    </div>

    <div v-if="loading" class="empty">加载中…</div>
    <div v-else-if="notFound" class="empty">这篇内容不存在或未公开。</div>

    <template v-else-if="article">
      <h1 class="r-title">{{ article.title || '(未命名)' }}</h1>
      <div class="r-meta">
        <span class="src">😊 来自 {{ article.ownerNickname || '匿名' }}</span>
        <template v-if="article.author"><span>·</span><span>{{ article.author }}</span></template>
        <a v-if="article.url" class="origin" :href="article.url" target="_blank" rel="noopener">看原文 ↗</a>
      </div>

      <div v-if="article.tags && article.tags.length" class="r-tags">
        <span v-for="t in article.tags" :key="t" class="tag">{{ t }}</span>
      </div>

      <div v-if="article.summary || article.keyPoints.length" class="distill">
        <h4>⚗ 自动沉淀</h4>
        <p v-if="article.summary" class="summary">{{ article.summary }}</p>
        <ul v-if="article.keyPoints.length">
          <li v-for="(p, i) in article.keyPoints" :key="i">{{ p }}</li>
        </ul>
      </div>

      <div class="r-body">{{ article.content || '(无正文)' }}</div>

      <!-- 操作条:按生效分享级别渐进显示(本人文章不显示) -->
      <div v-if="!article.mine" class="public-actions">
        <button
          v-if="levelAtLeast(article.effectiveShareLevel, 'BOOKMARKABLE')"
          class="pa-btn"
          :class="{ on: article.bookmarked }"
          :disabled="busy"
          @click="toggleBookmark"
        >
          🔖 {{ article.bookmarked ? '已收藏' : '收藏' }}
        </button>
        <button
          v-if="levelAtLeast(article.effectiveShareLevel, 'COLLECTABLE')"
          class="pa-btn"
          :class="{ on: article.collected }"
          :disabled="busy"
          @click="toggleCollect"
        >
          📥 {{ article.collected ? '已收录' : '收录到我的知识库' }}
        </button>
      </div>
    </template>
  </div>
</template>
