<script setup lang="ts">
import { ref, onMounted } from 'vue';
import type { PlazaCard as PlazaCardModel } from '@cnotes/types';
import { api } from '../api';
import PlazaCard from '../components/PlazaCard.vue';

const emit = defineEmits<{ open: [id: string]; openProfile: [userId: string] }>();

type Flow = 'discover' | 'following' | 'topics';
type Sort = 'score' | 'recent';

const flow = ref<Flow>('discover');
const sort = ref<Sort>('score');
const items = ref<PlazaCardModel[]>([]);
const total = ref(0);
const page = ref(1);
const size = 20;
const loading = ref(false);
const error = ref('');

async function loadDiscover(reset = true) {
  if (reset) {
    page.value = 1;
    items.value = [];
  }
  loading.value = true;
  error.value = '';
  try {
    const r = await api.plazaDiscover({ sort: sort.value, page: page.value, size });
    items.value = reset ? r.items : [...items.value, ...r.items];
    total.value = r.total;
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    loading.value = false;
  }
}

function setSort(s: Sort) {
  if (sort.value === s) return;
  sort.value = s;
  loadDiscover();
}

function loadMore() {
  page.value += 1;
  loadDiscover(false);
}

onMounted(() => loadDiscover());
</script>

<template>
  <div class="plaza">
    <div class="plaza-tabs">
      <button class="sub-tab" :class="{ on: flow === 'discover' }" @click="flow = 'discover'">发现</button>
      <button class="sub-tab" :class="{ on: flow === 'following' }" @click="flow = 'following'">关注</button>
      <button class="sub-tab" :class="{ on: flow === 'topics' }" @click="flow = 'topics'">话题</button>
    </div>

    <!-- 发现流 -->
    <template v-if="flow === 'discover'">
      <div class="plaza-bar">
        <div class="plaza-bar-title">🔥 精品发现</div>
        <div class="sort-toggle">
          <button :class="{ on: sort === 'score' }" @click="setSort('score')">质量分</button>
          <button :class="{ on: sort === 'recent' }" @click="setSort('recent')">最新</button>
        </div>
      </div>

      <div v-if="loading && !items.length" class="empty">加载中…</div>
      <div v-else-if="error" class="empty">加载失败:{{ error }}<br />请确认后端已启动。</div>
      <div v-else-if="!items.length" class="empty">
        广场还没有公开内容。<br />把你的文章设为「只读 / 可收藏 / 可收录」即可出现在这里。
      </div>
      <template v-else>
        <PlazaCard
          v-for="c in items"
          :key="c.id"
          :card="c"
          @open="emit('open', $event)"
          @open-profile="emit('openProfile', $event)"
        />
        <div class="plaza-more">
          <button v-if="items.length < total" class="more-btn" :disabled="loading" @click="loadMore">
            {{ loading ? '加载中…' : '加载更多' }}
          </button>
          <p v-else class="hint">— 没有更多了 —</p>
        </div>
      </template>
    </template>

    <!-- 关注流(阶段 4) -->
    <div v-else-if="flow === 'following'" class="empty">
      关注功能即将上线(阶段 4 社交互动)。<br />
      先到「发现」逛逛,遇到喜欢的作者点头像进主页。
    </div>

    <!-- 话题流(待跨用户聚类) -->
    <div v-else class="empty">
      话题流正在炼制中。<br />
      按 AI 主题跨用户聚合需要广场级向量聚类,将随后续阶段上线。
    </div>
  </div>
</template>
