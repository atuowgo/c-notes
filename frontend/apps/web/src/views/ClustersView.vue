<script setup lang="ts">
import { ref, onMounted } from 'vue';
import type { ClusterCard, AutoClusterCard, AutoClusterDetail } from '@cnotes/types';
import { api } from '../api';

const emit = defineEmits<{ open: [id: string] }>();

type SubTab = 'tag' | 'auto';
const subTab = ref<SubTab>('tag');

const clusters = ref<ClusterCard[]>([]);
const loading = ref(true);
const error = ref('');

// 语义簇(embedding 自动聚类):懒加载 + 卡片内联展开成员。
const autoClusters = ref<AutoClusterCard[]>([]);
const autoLoading = ref(false);
const autoError = ref('');
const autoLoaded = ref(false);
const expandedId = ref<string | null>(null);
const expandedDetail = ref<AutoClusterDetail | null>(null);
const expanding = ref(false);

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

async function loadAuto() {
  if (autoLoaded.value) return;
  autoLoading.value = true;
  autoError.value = '';
  try {
    autoClusters.value = await api.listAutoClusters();
    autoLoaded.value = true;
  } catch (e) {
    autoError.value = (e as Error).message;
  } finally {
    autoLoading.value = false;
  }
}

async function toggleAuto(id: string) {
  if (expandedId.value === id) {
    expandedId.value = null;
    expandedDetail.value = null;
    return;
  }
  expandedId.value = id;
  expandedDetail.value = null;
  expanding.value = true;
  try {
    expandedDetail.value = await api.getAutoCluster(id);
  } catch (e) {
    autoError.value = (e as Error).message;
    expandedId.value = null;
  } finally {
    expanding.value = false;
  }
}

function switchTab(t: SubTab) {
  subTab.value = t;
  if (t === 'auto') loadAuto();
}

onMounted(load);
defineExpose({ load });
</script>

<template>
  <div class="feed">
    <div class="day-label">知识网 · 主题簇</div>

    <div class="cluster-subtabs">
      <button class="subtab" :class="{ active: subTab === 'tag' }" @click="switchTab('tag')">
        标签簇
      </button>
      <button class="subtab" :class="{ active: subTab === 'auto' }" @click="switchTab('auto')">
        语义簇
      </button>
    </div>

    <!-- 标签簇(原行为) -->
    <template v-if="subTab === 'tag'">
      <div v-if="loading" class="empty">加载中…</div>
      <div v-else-if="error" class="empty">加载失败:{{ error }}</div>
      <div v-else-if="!clusters.length" class="empty">
        还没有主题簇。<br />多收几篇文章,系统会自动按主题聚成簇并织出综述。
      </div>
      <template v-else>
        <div
          v-for="c in clusters"
          :key="c.id"
          class="card"
          data-clickable="1"
          @click="emit('open', c.id)"
        >
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
    </template>

    <!-- 语义簇(embedding 自动聚类) -->
    <template v-else>
      <div v-if="autoLoading" class="empty">加载中…</div>
      <div v-else-if="autoError" class="empty">加载失败:{{ autoError }}</div>
      <div v-else-if="!autoClusters.length" class="empty">
        还没有语义簇。<br />后台会周期性按文章语义相似度自动聚簇;多收几篇同主题文章后即会出现。
      </div>
      <template v-else>
        <div
          v-for="c in autoClusters"
          :key="c.id"
          class="card auto-cluster"
          :class="{ open: expandedId === c.id }"
          data-clickable="1"
          @click="toggleAuto(c.id)"
        >
          <div class="c-head">
            <h3 class="c-title">{{ c.title || '未命名语义簇' }}</h3>
            <span class="badge" :class="c.hasSummary ? 'ok' : 'proc'">
              <span class="dot"></span>{{ c.hasSummary ? '已织综述' : '待织综述' }}
            </span>
          </div>
          <div class="c-meta"><span>{{ c.memberCount }} 篇 · 语义聚类</span></div>
          <p v-if="c.summary" class="c-summary">{{ c.summary }}</p>

          <div v-if="expandedId === c.id" class="auto-members" @click.stop>
            <div v-if="expanding" class="empty">载入成员…</div>
            <template v-else-if="expandedDetail">
              <div v-if="expandedDetail.summary" class="distill">
                <h4>⚗ 语义综述</h4>
                <p class="summary" style="white-space: pre-wrap">{{ expandedDetail.summary }}</p>
              </div>
              <div class="day-label" style="margin-top: 14px">本簇文章</div>
              <ul class="member-list">
                <li v-for="a in expandedDetail.articles" :key="a.id">
                  <span class="m-title">{{ a.title || '(无标题)' }}</span>
                  <span v-if="a.author" class="m-author">{{ a.author }}</span>
                </li>
              </ul>
            </template>
          </div>
        </div>
      </template>
    </template>
  </div>
</template>

<style scoped>
.cluster-subtabs {
  display: flex;
  gap: 4px;
  margin-bottom: 14px;
}
.subtab {
  border: none;
  background: transparent;
  color: var(--muted, #888);
  font-size: 13px;
  padding: 6px 12px;
  border-radius: 8px;
  cursor: pointer;
}
.subtab.active {
  background: var(--surface-2, #eee);
  color: var(--text, #222);
  font-weight: 600;
}
.auto-cluster.open {
  cursor: default;
}
.auto-members {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed var(--border, #ddd);
}
.member-list {
  list-style: none;
  padding: 0;
  margin: 8px 0 0;
}
.member-list li {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 0;
  font-size: 14px;
}
.member-list .m-title {
  color: var(--text, #222);
}
.member-list .m-author {
  color: var(--muted, #888);
  font-size: 12px;
  white-space: nowrap;
}
</style>
