<script setup lang="ts">
import { ref, onMounted } from 'vue';
import type { PlazaCard as PlazaCardModel } from '@cnotes/types';
import { api } from '../api';
import PlazaCard from '../components/PlazaCard.vue';

defineProps<{ loggedIn: boolean }>();
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

async function loadFlow(reset = true) {
  if (reset) {
    page.value = 1;
    items.value = [];
  }
  loading.value = true;
  error.value = '';
  try {
    const r = flow.value === 'following'
      ? await api.plazaFollowing({ page: page.value, size })
      : await api.plazaDiscover({ sort: sort.value, page: page.value, size });
    items.value = reset ? r.items : [...items.value, ...r.items];
    total.value = r.total;
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    loading.value = false;
  }
}

function setFlow(f: Flow) {
  if (flow.value === f) return;
  flow.value = f;
  if (f === 'discover' || f === 'following') loadFlow();
}

function setSort(s: Sort) {
  if (sort.value === s) return;
  sort.value = s;
  loadFlow();
}

function loadMore() {
  page.value += 1;
  loadFlow(false);
}

onMounted(() => loadFlow());
</script>

<template>
  <div class="plaza">
    <div class="plaza-tabs">
      <button class="sub-tab" :class="{ on: flow === 'discover' }" @click="setFlow('discover')">发现</button>
      <button class="sub-tab" :class="{ on: flow === 'following' }" @click="setFlow('following')">关注</button>
      <button class="sub-tab" :class="{ on: flow === 'topics' }" @click="setFlow('topics')">话题</button>
    </div>

    <!-- 发现 / 关注流 -->
    <template v-if="flow === 'discover' || flow === 'following'">
      <div class="plaza-bar">
        <div class="plaza-bar-title">{{ flow === 'discover' ? '🔥 精品发现' : '👥 关注动态' }}</div>
        <div v-if="flow === 'discover'" class="sort-toggle">
          <button :class="{ on: sort === 'score' }" @click="setSort('score')">质量分</button>
          <button :class="{ on: sort === 'recent' }" @click="setSort('recent')">最新</button>
        </div>
      </div>

      <div v-if="loading && !items.length" class="empty">加载中…</div>
      <div v-else-if="error" class="empty">加载失败:{{ error }}<br />请确认后端已启动。</div>
      <div v-else-if="!items.length && flow === 'following'" class="empty">
        <template v-if="!loggedIn">登录后关注作者,这里汇总他们的最新公开内容。</template>
        <template v-else>还没有关注任何人。<br />去「发现」逛逛,点作者头像进主页关注。</template>
      </div>
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

    <!-- 话题流(待跨用户聚类) -->
    <div v-else class="empty">
      话题流正在炼制中。<br />
      按 AI 主题跨用户聚合需要广场级向量聚类,将随后续阶段上线。
    </div>
  </div>
</template>
