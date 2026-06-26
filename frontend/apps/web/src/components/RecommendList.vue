<script setup lang="ts">
import { ref, watch } from 'vue';
import type { ArticleLink } from '@cnotes/types';
import { api } from '../api';

// 推荐文(§6.3):后端 LinkService 算关联——相关(共享标签)+ 更深入(同 auto_cluster),
// Ark embedding cosine 排序,top-N 入库;reason 由 DeepSeek 生成。复用 listArticleLinks,
// 按 linkType 区分徽标样式:相关=绿,更深入=金(同语义簇,深一层)。tags 仅兼容 prop。
const props = defineProps<{ articleId: string; tags?: string[] }>();
const emit = defineEmits<{ open: [id: string] }>();

const related = ref<ArticleLink[]>([]);

async function load() {
  related.value = [];
  if (!props.articleId) return;
  try {
    related.value = await api.listArticleLinks(props.articleId);
  } catch {
    /* 忽略推荐失败,不影响阅读 */
  }
}

watch(() => props.articleId, load, { immediate: true });
</script>

<template>
  <div v-if="related.length" class="recommend">
    <h4>⚗ 顺着这篇继续探索</h4>
    <div
      v-for="r in related"
      :key="r.targetArticle.id"
      class="rec-item"
      @click="emit('open', r.targetArticle.id)"
    >
      <span class="rec-kind" :class="{ 'rec-kind--deeper': r.linkType === '更深入' }">{{ r.linkType }}</span>
      <div class="rec-body">
        <p class="t">{{ r.targetArticle.title || '(未命名)' }}</p>
        <p class="r">{{ r.reason || r.targetArticle.summary || '同主题文章' }}</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 更深入(同语义簇,深一层)徽标:琥珀金,区别于"相关"的绿。 */
.rec-kind--deeper {
  color: var(--gold);
  background: var(--gold-soft);
}
</style>
