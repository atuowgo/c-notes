<script setup lang="ts">
import { ref, watch } from 'vue';
import type { ArticleCard } from '@cnotes/types';
import { api } from '../api';

// 推荐文(§6.3)。MVP 先用"同标签近邻"粗排(客户端从收件箱算);
// V3 知识网成型后改由簇 / 关联驱动,并补"更深入"一类。
const props = defineProps<{ articleId: string; tags: string[] }>();
const emit = defineEmits<{ open: [id: string] }>();

const related = ref<ArticleCard[]>([]);

async function load() {
  related.value = [];
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
      .slice(0, 4);
  } catch {
    /* 忽略推荐失败,不影响阅读 */
  }
}

watch(() => props.articleId, load, { immediate: true });
</script>

<template>
  <div v-if="related.length" class="recommend">
    <h4>⚗ 顺着这篇继续探索</h4>
    <div v-for="a in related" :key="a.id" class="rec-item" @click="emit('open', a.id)">
      <span class="rec-kind">相关</span>
      <div class="rec-body">
        <p class="t">{{ a.title || '(未命名)' }}</p>
        <p class="r">{{ a.summary || '同主题文章' }}</p>
      </div>
    </div>
  </div>
</template>
