<script setup lang="ts">
import { ref, onMounted } from 'vue';
import type { ArticleCard as ArticleCardModel } from '@cnotes/types';
import { api } from '../api';
import ArticleCard from '../components/ArticleCard.vue';

const emit = defineEmits<{ open: [id: string] }>();

const items = ref<ArticleCardModel[]>([]);
const loading = ref(true);
const error = ref('');
const readIds = ref<Set<string>>(new Set());

async function load() {
  loading.value = true;
  error.value = '';
  try {
    items.value = await api.listInbox();
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

onMounted(load);
defineExpose({ load });
</script>

<template>
  <div class="feed">
    <div class="day-label">收件箱</div>

    <div v-if="loading" class="empty">加载中…</div>
    <div v-else-if="error" class="empty">加载失败:{{ error }}<br />请确认后端已启动。</div>
    <div v-else-if="!items.length" class="empty">
      收件箱还是空的。<br />点右上角「＋ 收藏链接」加一篇试试。
    </div>
    <template v-else>
      <ArticleCard
        v-for="a in items"
        :key="a.id"
        :article="a"
        :read="readIds.has(a.id)"
        @open="onOpen"
      />
      <p class="hint">— 没有更多了。收藏的内容会自动出现在这里 —</p>
    </template>
  </div>
</template>
