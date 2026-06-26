<script setup lang="ts">
import { ref, useTemplateRef, onMounted, onUnmounted } from 'vue';
import type { ArticleDetail } from '@cnotes/types';
import InboxView from './InboxView.vue';
import ReaderView from './ReaderView.vue';
import ClustersView from './ClustersView.vue';
import ClusterDetailView from './ClusterDetailView.vue';
import CollectModal from '../components/CollectModal.vue';
import ChatPanel from '../components/ChatPanel.vue';
import IdeasDrawer from '../components/IdeasDrawer.vue';
import Toast from '../components/Toast.vue';
import { loadNotes } from '../notes';

type Tab = 'inbox' | 'clusters';

const tab = ref<Tab>('inbox');
const openClusterId = ref<string | null>(null);
const openId = ref<string | null>(null); // 文章阅读器,优先级最高
const currentArticle = ref<ArticleDetail | null>(null);
const collectOpen = ref(false);
const drawer = ref<{ scope: 'article' | 'all' } | null>(null);
const inbox = useTemplateRef<InstanceType<typeof InboxView>>('inbox');

function openReader(id: string) {
  openId.value = id;
}
function closeReader() {
  openId.value = null;
  currentArticle.value = null;
  window.scrollTo(0, 0);
}
function go(t: Tab) {
  tab.value = t;
  openClusterId.value = null;
  openId.value = null;
  currentArticle.value = null;
}

function onKey(e: KeyboardEvent) {
  if (e.key !== 'Escape') return;
  if (drawer.value) drawer.value = null;
  else if (collectOpen.value) collectOpen.value = false;
  else if (openId.value) closeReader();
  else if (openClusterId.value) openClusterId.value = null;
}
onMounted(() => {
  document.addEventListener('keydown', onKey);
  loadNotes();
});
onUnmounted(() => document.removeEventListener('keydown', onKey));
</script>

<template>
  <div class="topbar">
    <div class="topbar-inner">
      <div class="brand"><span class="mark">⚗</span> 知识炼金炉</div>
      <nav class="tabs">
        <button class="nav-tab" :class="{ active: tab === 'inbox' && !openClusterId }" @click="go('inbox')">收件箱</button>
        <button class="nav-tab" :class="{ active: tab === 'clusters' || openClusterId }" @click="go('clusters')">知识网</button>
      </nav>
      <div class="spacer"></div>
      <button class="add-btn" @click="collectOpen = true">＋ 收藏链接</button>
      <button class="icon-btn" title="全部想法" @click="drawer = { scope: 'all' }">💡</button>
    </div>
  </div>

  <!-- 阅读器叠加层(最高优先级) -->
  <ReaderView
    v-if="openId"
    :id="openId"
    @back="closeReader"
    @open="openReader"
    @open-ideas="drawer = { scope: 'article' }"
    @loaded="currentArticle = $event"
  />
  <!-- 簇详情叠加层 -->
  <ClusterDetailView
    v-else-if="openClusterId"
    :id="openClusterId"
    @back="openClusterId = null"
    @open="openReader"
  />
  <!-- 主视图 -->
  <template v-else>
    <InboxView v-show="tab === 'inbox'" ref="inbox" @open="openReader" />
    <ClustersView v-if="tab === 'clusters'" @open="openClusterId = $event" />
  </template>

  <CollectModal :open="collectOpen" @close="collectOpen = false" @collected="inbox?.load()" />
  <ChatPanel
    :article-id="openId"
    :article-title="openId ? currentArticle?.title : null"
  />
  <IdeasDrawer
    v-if="drawer"
    :scope="drawer.scope"
    :article-id="openId ?? undefined"
    @close="drawer = null"
  />
  <Toast />
</template>
