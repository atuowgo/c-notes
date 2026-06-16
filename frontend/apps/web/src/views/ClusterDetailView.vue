<script setup lang="ts">
import { ref, watch } from 'vue';
import type { ClusterDetail } from '@cnotes/types';
import { api } from '../api';
import { toast } from '../toast';
import ArticleCard from '../components/ArticleCard.vue';

const props = defineProps<{ id: string }>();
const emit = defineEmits<{ back: []; open: [id: string] }>();

const cluster = ref<ClusterDetail | null>(null);
const regenerating = ref(false);

async function load(id: string) {
  cluster.value = null;
  try {
    cluster.value = await api.getCluster(id);
    window.scrollTo(0, 0);
  } catch (e) {
    toast(`打开失败:${(e as Error).message}`);
    emit('back');
  }
}
watch(() => props.id, load, { immediate: true });

async function regenerate() {
  if (!cluster.value || regenerating.value) return;
  regenerating.value = true;
  try {
    cluster.value = await api.regenerateCluster(cluster.value.id);
    toast('综述已重写');
  } catch (e) {
    toast(`重写失败:${(e as Error).message}`);
  } finally {
    regenerating.value = false;
  }
}
</script>

<template>
  <div class="reader">
    <div class="r-top">
      <button class="back" @click="emit('back')">← 返回知识网</button>
      <button v-if="cluster" class="ideas-entry" :disabled="regenerating" @click="regenerate">
        ⚗ {{ regenerating ? '重写中…' : '重写综述' }}
      </button>
    </div>

    <template v-if="cluster">
      <h1 class="r-title">{{ cluster.name }}</h1>
      <div class="r-meta"><span class="src">主题簇</span><span>·</span><span>{{ cluster.articleCount }} 篇</span></div>

      <div v-if="cluster.livingSummary" class="distill">
        <h4>⚗ 演进式综述</h4>
        <p class="summary" style="white-space: pre-wrap">{{ cluster.livingSummary }}</p>
      </div>
      <div v-else class="distill waiting">
        <h4>⚗ 演进式综述</h4>
        <p class="wait-text">综述尚未生成(需至少 2 篇)。后台会随新内容自动织综述,也可点右上角手动重写。</p>
      </div>

      <div class="day-label" style="margin-top: 26px">本簇文章</div>
      <ArticleCard v-for="a in cluster.articles" :key="a.id" :article="a" @open="emit('open', $event)" />
    </template>
  </div>
</template>
