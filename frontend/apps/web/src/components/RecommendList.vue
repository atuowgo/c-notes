<script setup lang="ts">
import { ref, watch } from 'vue';
import type { ArticleCard } from '@cnotes/types';
import { api } from '../api';

// 推荐文(§6.3)。V3 起改由后端「关联」驱动:GET /api/articles/{id}/related —— LLM 给出
// 「为什么相关」(同概念/互补/对立/延伸)。后端不可用或暂无关联时,退回客户端「同标签近邻」粗排。
const props = defineProps<{ articleId: string; tags: string[] }>();
const emit = defineEmits<{ open: [id: string] }>();

interface Rec {
  article: ArticleCard;
  relationType: string;
  reason: string;
}

const related = ref<Rec[]>([]);

async function load() {
  related.value = [];
  try {
    const rels = await api.listRelated(props.articleId);
    if (rels.length) {
      related.value = rels.map((r) => ({ article: r.article, relationType: r.relationType, reason: r.reason }));
      return;
    }
  } catch {
    /* 落到标签兜底 */
  }
  // 兜底:同标签近邻
  if (!props.tags.length) return;
  try {
    const all = await api.listInbox();
    related.value = all
      .filter(
        (a) =>
          a.id !== props.articleId &&
          a.status === 'done' &&
          (a.tags ?? []).some((t) => props.tags.includes(t)),
      )
      .slice(0, 4)
      .map((a) => ({ article: a, relationType: '相关', reason: '同主题标签近邻' }));
  } catch {
    /* 忽略推荐失败,不影响阅读 */
  }
}

watch(() => props.articleId, load, { immediate: true });
</script>

<template>
  <div v-if="related.length" class="recommend">
    <h4>⚗ 顺着这篇继续探索</h4>
    <div v-for="r in related" :key="r.article.id" class="rec-item" @click="emit('open', r.article.id)">
      <span class="rec-kind">{{ r.relationType }}</span>
      <div class="rec-body">
        <p class="t">{{ r.article.title || '(未命名)' }}</p>
        <p class="r">{{ r.reason || r.article.summary || '同主题文章' }}</p>
      </div>
    </div>
  </div>
</template>
