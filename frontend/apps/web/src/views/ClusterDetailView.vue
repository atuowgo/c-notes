<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import type { ClusterDetail, ClusterCard } from '@cnotes/types';
import { api } from '../api';
import { toast } from '../toast';
import ArticleCard from '../components/ArticleCard.vue';

const props = defineProps<{ id: string }>();
const emit = defineEmits<{ back: []; open: [id: string] }>();

const cluster = ref<ClusterDetail | null>(null);
const allClusters = ref<ClusterCard[]>([]);
const regenerating = ref(false);
const mergeTarget = ref('');
const busy = ref(false);

// 可作为「移动/合并」目标的其它簇
const otherClusters = computed(() => allClusters.value.filter((c) => c.id !== props.id));

async function load(id: string) {
  cluster.value = null;
  try {
    [cluster.value, allClusters.value] = await Promise.all([api.getCluster(id), api.listClusters()]);
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

async function moveArticle(articleId: string, toClusterId: string) {
  if (!toClusterId || busy.value) return;
  busy.value = true;
  try {
    await api.moveArticleToCluster(props.id, articleId, toClusterId);
    toast('已移动到其他簇');
    await load(props.id);
  } catch (e) {
    toast(`移动失败:${(e as Error).message}`);
  } finally {
    busy.value = false;
  }
}

async function merge() {
  if (!mergeTarget.value || busy.value) return;
  busy.value = true;
  try {
    await api.mergeClusters(props.id, mergeTarget.value);
    toast('已合并,本簇归档');
    emit('back');
  } catch (e) {
    toast(`合并失败:${(e as Error).message}`);
  } finally {
    busy.value = false;
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

      <!-- 纠偏:合并整簇 -->
      <div v-if="otherClusters.length" class="correct-bar">
        <span class="cb-label">纠偏:</span>
        <select v-model="mergeTarget" :disabled="busy" data-testid="merge-target">
          <option value="">合并入其他簇…</option>
          <option v-for="c in otherClusters" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
        <button class="cb-btn" :disabled="busy || !mergeTarget" @click="merge">合并</button>
      </div>

      <div class="day-label" style="margin-top: 26px">本簇文章</div>
      <div v-for="a in cluster.articles" :key="a.id" class="cluster-article">
        <ArticleCard :article="a" @open="emit('open', $event)" />
        <div v-if="otherClusters.length" class="move-row">
          <select
            :disabled="busy"
            data-testid="move-target"
            @change="moveArticle(a.id, ($event.target as HTMLSelectElement).value)"
          >
            <option value="">移动到其他簇…</option>
            <option v-for="c in otherClusters" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
        </div>
      </div>
    </template>
  </div>
</template>
