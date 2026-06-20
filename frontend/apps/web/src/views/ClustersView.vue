<script setup lang="ts">
import { ref, onMounted } from 'vue';
import type { ClusterCard, ClusterSuggestion } from '@cnotes/types';
import { api } from '../api';
import { toast } from '../toast';

const emit = defineEmits<{ open: [id: string] }>();

const clusters = ref<ClusterCard[]>([]);
const loading = ref(true);
const error = ref('');

const suggestions = ref<ClusterSuggestion[]>([]);
const suggesting = ref(false);
const suggestingVec = ref(false);
const accepting = ref('');

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

async function loadSuggestions() {
  suggesting.value = true;
  try {
    suggestions.value = await api.listClusterSuggestions();
    if (!suggestions.value.length) toast('暂无新的主题建议(文章太少或都已成簇)');
  } catch (e) {
    toast(`建议失败:${(e as Error).message}`);
  } finally {
    suggesting.value = false;
  }
}

async function loadVectorSuggestions() {
  suggestingVec.value = true;
  try {
    suggestions.value = await api.listVectorClusterSuggestions();
    if (!suggestions.value.length) toast('向量聚类暂无新主题(需配置 embedding,或文章语义都已成簇)');
  } catch (e) {
    toast(`聚类失败:${(e as Error).message}`);
  } finally {
    suggestingVec.value = false;
  }
}

async function accept(s: ClusterSuggestion) {
  if (accepting.value) return;
  accepting.value = s.name;
  try {
    await api.acceptClusterSuggestion(s.name, s.articles.map((a) => a.id));
    toast(`已建簇:${s.name}`);
    suggestions.value = suggestions.value.filter((x) => x !== s);
    await load();
  } catch (e) {
    toast(`接受失败:${(e as Error).message}`);
  } finally {
    accepting.value = '';
  }
}

onMounted(load);
defineExpose({ load });
</script>

<template>
  <div class="feed">
    <div class="day-label">
      知识网 · 主题簇
      <button class="suggest-btn" :disabled="suggesting" @click="loadSuggestions">
        {{ suggesting ? '思考中…' : '💡 发现新主题' }}
      </button>
      <button class="suggest-btn vec" :disabled="suggestingVec" @click="loadVectorSuggestions">
        {{ suggestingVec ? '聚类中…' : '🧲 向量聚类' }}
      </button>
    </div>

    <!-- LLM 建议的新主题簇 -->
    <div v-for="s in suggestions" :key="s.name" class="card suggestion">
      <div class="c-head">
        <h3 class="c-title">💡 {{ s.name }}</h3>
        <button class="accept-btn" :disabled="accepting === s.name" @click="accept(s)">
          {{ accepting === s.name ? '建簇中…' : '采纳为簇' }}
        </button>
      </div>
      <p class="c-summary">{{ s.reason }}</p>
      <div class="c-meta"><span>{{ s.articles.length }} 篇候选</span></div>
    </div>

    <div v-if="loading" class="empty">加载中…</div>
    <div v-else-if="error" class="empty">加载失败:{{ error }}</div>
    <div v-else-if="!clusters.length && !suggestions.length" class="empty">
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
