<script setup lang="ts">
import { computed } from 'vue';
import type { ArticleCard } from '@cnotes/types';
import { STATUS_META, srcLabel, relTime } from '../format';

const props = defineProps<{ article: ArticleCard; read?: boolean }>();
const emit = defineEmits<{ open: [id: string] }>();

const meta = computed(() => STATUS_META[props.article.status] ?? { cls: 'proc', label: props.article.status });
const ready = computed(() => props.article.status === 'done');
const pending = computed(() => props.article.status === 'pending' || props.article.status === 'processing');

function onClick() {
  if (ready.value) emit('open', props.article.id);
}
</script>

<template>
  <div
    class="card"
    :class="{ read }"
    :data-clickable="ready ? '1' : null"
    @click="onClick"
  >
    <div class="c-head">
      <h3 class="c-title">{{ article.title || '(未命名)' }}</h3>
      <span class="badge" :class="meta.cls"><span class="dot"></span>{{ meta.label }}</span>
    </div>
    <div class="c-meta">
      <span class="src">{{ srcLabel(article.sourceType) }}</span>
      <template v-if="article.author"><span>·</span><span>{{ article.author }}</span></template>
      <template v-if="article.createTime"><span>·</span><span>{{ relTime(article.createTime) }}</span></template>
    </div>

    <template v-if="pending">
      <div class="sk sk-line" style="width: 92%"></div>
      <div class="sk sk-line" style="width: 78%"></div>
    </template>
    <p v-else-if="article.status === 'failed'" class="c-summary fail">
      {{ article.summary || '正文提取失败,稍后可重试。' }}
    </p>
    <p v-else class="c-summary">{{ article.summary || '(无摘要)' }}</p>

    <div v-if="article.tags && article.tags.length" class="c-tags">
      <span v-for="t in article.tags" :key="t" class="tag">{{ t }}</span>
    </div>
  </div>
</template>
