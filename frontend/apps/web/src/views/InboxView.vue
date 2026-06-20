<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import type { ArticleCard as ArticleCardModel, CollectedCard } from '@cnotes/types';
import { api } from '../api';
import { relTime } from '../format';
import ArticleCard from '../components/ArticleCard.vue';
import TagFilter from '../components/TagFilter.vue';

const emit = defineEmits<{ open: [id: string]; openPublic: [id: string] }>();

const items = ref<ArticleCardModel[]>([]);
const collected = ref<CollectedCard[]>([]);
const loading = ref(true);
const error = ref('');
const readIds = ref<Set<string>>(new Set());
const activeTag = ref('all');

const allTags = computed(() => {
  const set = new Set<string>();
  for (const a of items.value) for (const t of a.tags ?? []) set.add(t);
  return [...set];
});

const filtered = computed(() =>
  activeTag.value === 'all'
    ? items.value
    : items.value.filter((a) => (a.tags ?? []).includes(activeTag.value)),
);

async function load() {
  loading.value = true;
  error.value = '';
  try {
    // 收件箱 = 我收藏的文章 + 我收录的他人文章(后者带「收录自」角标)。
    const [inbox, cols] = await Promise.all([api.listInbox(), api.listCollections().catch(() => [])]);
    items.value = inbox;
    collected.value = cols;
    if (activeTag.value !== 'all' && !allTags.value.includes(activeTag.value)) {
      activeTag.value = 'all';
    }
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    loading.value = false;
  }
}

function onOpen(id: string) {
  readIds.value.add(id);
  emit('open', id);
}

// 收录卡片只在「全部」视图展示(私有标签筛选属于自己的文章体系)。
const showCollected = computed(() => activeTag.value === 'all' && collected.value.length > 0);

function openCollected(c: CollectedCard) {
  if (c.sourceWithdrawn || !c.articleId) return;
  emit('openPublic', c.articleId);
}

onMounted(load);
defineExpose({ load });
</script>

<template>
  <TagFilter :tags="allTags" :active="activeTag" @select="activeTag = $event" />

  <div class="feed">
    <div class="day-label">收件箱<template v-if="activeTag !== 'all'"> · {{ activeTag }}</template></div>

    <div v-if="loading" class="empty">加载中…</div>
    <div v-else-if="error" class="empty">加载失败:{{ error }}<br />请确认后端已启动。</div>
    <div v-else-if="!filtered.length && !showCollected" class="empty">
      <template v-if="activeTag !== 'all'">该标签下还没有文章。</template>
      <template v-else>收件箱还是空的。<br />点右上角「＋ 收藏链接」加一篇试试。</template>
    </div>
    <template v-else>
      <!-- 收录的他人文章:带「收录自」角标 -->
      <div
        v-for="c in (showCollected ? collected : [])"
        :key="c.id"
        class="card collected-card"
        :data-clickable="c.sourceWithdrawn ? null : '1'"
        @click="openCollected(c)"
      >
        <div class="collected-badge">📥 收录自 {{ c.collectedFrom || '某人' }}</div>
        <template v-if="c.sourceWithdrawn">
          <h3 class="c-title withdrawn">(原文已撤回)</h3>
          <p v-if="c.personalNote" class="c-summary">我的笔记:{{ c.personalNote }}</p>
        </template>
        <template v-else>
          <h3 class="c-title">{{ c.title || '(未命名)' }}</h3>
          <div class="c-meta">
            <template v-if="c.author"><span>{{ c.author }}</span><span>·</span></template>
            <span>{{ relTime(c.createTime) }}</span>
          </div>
          <p v-if="c.personalNote" class="c-summary note">我的笔记:{{ c.personalNote }}</p>
          <p v-else-if="c.summary" class="c-summary">{{ c.summary }}</p>
          <div v-if="c.tags && c.tags.length" class="c-tags">
            <span v-for="t in c.tags" :key="t" class="tag">{{ t }}</span>
          </div>
        </template>
      </div>

      <ArticleCard
        v-for="a in filtered"
        :key="a.id"
        :article="a"
        :read="readIds.has(a.id)"
        @open="onOpen"
      />
      <p class="hint">— 没有更多了。收藏的内容会自动出现在这里 —</p>
    </template>
  </div>
</template>
