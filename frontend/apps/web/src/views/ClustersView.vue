<script setup lang="ts">
import { ref, onMounted } from 'vue';
import type { ClusterCard } from '@cnotes/types';
import { api } from '../api';

const emit = defineEmits<{ open: [id: string] }>();

const clusters = ref<ClusterCard[]>([]);
const loading = ref(true);
const error = ref('');

async function load() {
  loading.value = true;
  error.value = '';
  try {
    clusters.value = await api.listClusters();
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    loading.value = false;
  }
}
onMounted(load);
defineExpose({ load });
</script>

<template>
  <div class="feed">
    <div class="day-label">知识网 · 主题簇</div>
    <div v-if="loading" class="empty">加载中…</div>
    <div v-else-if="error" class="empty">加载失败:{{ error }}</div>
    <div v-else-if="!clusters.length" class="empty">
      还没有主题簇。<br />多收几篇文章,系统会自动按主题聚成簇并织出综述。
    </div>
    <template v-else>
      <div v-for="c in clusters" :key="c.id" class="card" data-clickable="1" @click="emit('open', c.id)">
        <div class="c-head">
          <h3 class="c-title">{{ c.name }}</h3>
          <span class="badge" :class="c.hasSummary ? 'ok' : 'proc'">
            <span class="dot"></span>{{ c.hasSummary ? '已织综述' : '待织综述' }}
          </span>
        </div>
        <div class="c-meta"><span>{{ c.articleCount }} 篇</span></div>
        <p v-if="c.description" class="c-summary">{{ c.description }}</p>
      </div>
    </template>
  </div>
</template>
