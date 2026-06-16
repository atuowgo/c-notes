<script setup lang="ts">
import { ref, useTemplateRef, onMounted, onUnmounted } from 'vue';
import type { ArticleDetail } from '@cnotes/types';
import InboxView from './views/InboxView.vue';
import ReaderView from './views/ReaderView.vue';
import CollectModal from './components/CollectModal.vue';
import ChatPanel from './components/ChatPanel.vue';
import IdeasDrawer from './components/IdeasDrawer.vue';
import Toast from './components/Toast.vue';

const openId = ref<string | null>(null);
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

function onKey(e: KeyboardEvent) {
  if (e.key !== 'Escape') return;
  if (drawer.value) drawer.value = null;
  else if (collectOpen.value) collectOpen.value = false;
  else if (openId.value) closeReader();
}
onMounted(() => document.addEventListener('keydown', onKey));
onUnmounted(() => document.removeEventListener('keydown', onKey));
</script>

<template>
  <div class="topbar">
    <div class="topbar-inner">
      <div class="brand"><span class="mark">⚗</span> 知识炼金炉 <small>收件箱</small></div>
      <div class="spacer"></div>
      <button class="add-btn" @click="collectOpen = true">＋ 收藏链接</button>
      <button class="icon-btn" title="全部想法" @click="drawer = { scope: 'all' }">💡</button>
      <button class="icon-btn" title="刷新" @click="inbox?.load()">⟳</button>
    </div>
  </div>

  <InboxView v-show="!openId" ref="inbox" @open="openReader" />
  <ReaderView
    v-if="openId"
    :id="openId"
    @back="closeReader"
    @open="openReader"
    @open-ideas="drawer = { scope: 'article' }"
    @loaded="currentArticle = $event"
  />

  <CollectModal :open="collectOpen" @close="collectOpen = false" @collected="inbox?.load()" />

  <ChatPanel :article-title="openId ? currentArticle?.title : null" />

  <IdeasDrawer
    v-if="drawer"
    :scope="drawer.scope"
    :article-id="openId ?? undefined"
    @close="drawer = null"
  />

  <Toast />
</template>
