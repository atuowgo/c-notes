<script setup lang="ts">
import { ref, useTemplateRef, onMounted, onUnmounted } from 'vue';
import InboxView from './views/InboxView.vue';
import ReaderView from './views/ReaderView.vue';
import CollectModal from './components/CollectModal.vue';
import Toast from './components/Toast.vue';

const openId = ref<string | null>(null);
const collectOpen = ref(false);
const inbox = useTemplateRef<InstanceType<typeof InboxView>>('inbox');

function openReader(id: string) {
  openId.value = id;
}
function closeReader() {
  openId.value = null;
  window.scrollTo(0, 0);
}

function onKey(e: KeyboardEvent) {
  if (e.key !== 'Escape') return;
  if (collectOpen.value) collectOpen.value = false;
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
      <button class="icon-btn" title="刷新" @click="inbox?.load()">⟳</button>
    </div>
  </div>

  <InboxView v-show="!openId" ref="inbox" @open="openReader" />
  <ReaderView v-if="openId" :id="openId" @back="closeReader" />

  <CollectModal :open="collectOpen" @close="collectOpen = false" @collected="inbox?.load()" />
  <Toast />
</template>
