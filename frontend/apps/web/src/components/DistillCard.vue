<script setup lang="ts">
import { computed } from 'vue';
import type { ArticleDetail } from '@cnotes/types';

const props = defineProps<{ article: ArticleDetail }>();

const hasDistill = computed(
  () => props.article.status === 'done' && (!!props.article.summary || props.article.keyPoints.length > 0),
);
</script>

<template>
  <div v-if="hasDistill" class="distill">
    <h4>⚗ 自动沉淀</h4>
    <p v-if="article.summary" class="summary">{{ article.summary }}</p>
    <ul v-if="article.keyPoints.length">
      <li v-for="(p, i) in article.keyPoints" :key="i">{{ p }}</li>
    </ul>
  </div>
  <div v-else class="distill waiting">
    <h4>⚗ 自动沉淀</h4>
    <p class="wait-text">沉淀尚未生成(当前状态:{{ article.status }})。后台处理完成后会出现摘要与要点。</p>
  </div>
</template>
