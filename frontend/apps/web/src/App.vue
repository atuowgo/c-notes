<script setup lang="ts">
import { ref, useTemplateRef, onMounted, onUnmounted } from 'vue';
import type { ArticleDetail, CurrentUser, ShareLevel } from '@cnotes/types';
import InboxView from './views/InboxView.vue';
import ReaderView from './views/ReaderView.vue';
import ClustersView from './views/ClustersView.vue';
import ClusterDetailView from './views/ClusterDetailView.vue';
import PublicArticleView from './views/PublicArticleView.vue';
import PlazaView from './views/PlazaView.vue';
import ProfileView from './views/ProfileView.vue';
import CollectModal from './components/CollectModal.vue';
import ChatPanel from './components/ChatPanel.vue';
import IdeasDrawer from './components/IdeasDrawer.vue';
import ComposeModal from './components/ComposeModal.vue';
import Toast from './components/Toast.vue';
import LoginModal from './components/LoginModal.vue';
import UserMenu from './components/UserMenu.vue';
import NotificationBell from './components/NotificationBell.vue';
import ShareSettingsModal from './components/ShareSettingsModal.vue';
import { loadNotes } from './notes';
import { api } from './api';

type Tab = 'inbox' | 'clusters' | 'plaza';

const tab = ref<Tab>('inbox');
const openClusterId = ref<string | null>(null);
const openId = ref<string | null>(null); // 文章阅读器,优先级最高
const openPublicId = ref<string | null>(null); // 公开文章只读视图(收录卡片 / 公开链接进入)
const openProfileId = ref<string | null>(null); // 用户公开主页
const currentArticle = ref<ArticleDetail | null>(null);
const collectOpen = ref(false);
const drawer = ref<{ scope: 'article' | 'all' } | null>(null);
const composeNoteIds = ref<string[] | null>(null);
const inbox = useTemplateRef<InstanceType<typeof InboxView>>('inbox');

const user = ref<CurrentUser | null>(null);
const loginOpen = ref(false);
const shareSettingsOpen = ref(false);
const bell = useTemplateRef<{ refreshCount: () => void }>('bell');

function openCompose(noteId: string) {
  composeNoteIds.value = [noteId];
}

function openReader(id: string) {
  openId.value = id;
}
function closeReader() {
  openId.value = null;
  currentArticle.value = null;
  window.scrollTo(0, 0);
}
function openPublic(id: string) {
  openPublicId.value = id;
}
function closePublic() {
  openPublicId.value = null;
  window.scrollTo(0, 0);
}
function openProfile(userId: string) {
  openProfileId.value = userId;
}
function closeProfile() {
  openProfileId.value = null;
  window.scrollTo(0, 0);
}
function go(t: Tab) {
  tab.value = t;
  openClusterId.value = null;
  openId.value = null;
  openPublicId.value = null;
  openProfileId.value = null;
  currentArticle.value = null;
}

function onKey(e: KeyboardEvent) {
  if (e.key !== 'Escape') return;
  if (loginOpen.value) loginOpen.value = false;
  else if (shareSettingsOpen.value) shareSettingsOpen.value = false;
  else if (composeNoteIds.value) composeNoteIds.value = null;
  else if (drawer.value) drawer.value = null;
  else if (collectOpen.value) collectOpen.value = false;
  else if (openPublicId.value) closePublic();
  else if (openProfileId.value) closeProfile();
  else if (openId.value) closeReader();
  else if (openClusterId.value) openClusterId.value = null;
}
onMounted(async () => {
  document.addEventListener('keydown', onKey);
  loadNotes();
  user.value = await api.me();
  // 公开链接深链:?a=<id> 直接打开公开文章只读视图。
  const a = new URLSearchParams(window.location.search).get('a');
  if (a) openPublic(a);
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
        <span class="nav-sep"></span>
        <button class="nav-tab" :class="{ active: tab === 'plaza' }" @click="go('plaza')">广场</button>
      </nav>
      <div class="spacer"></div>
      <button class="add-btn" @click="collectOpen = true">＋ 收藏链接</button>
      <button class="icon-btn" title="全部想法" @click="drawer = { scope: 'all' }">💡</button>
      <NotificationBell v-if="user" ref="bell" @open="openPublic" />
      <UserMenu
        v-if="user"
        :user="user"
        @logout="user = null"
        @open-share-settings="shareSettingsOpen = true"
      />
      <button v-else class="login-entry-btn" @click="loginOpen = true">登录</button>
    </div>
  </div>

  <!-- 公开文章只读叠加层(最高优先级) -->
  <PublicArticleView
    v-if="openPublicId"
    :id="openPublicId"
    :logged-in="!!user"
    @back="closePublic"
    @login="loginOpen = true"
  />
  <!-- 用户公开主页叠加层 -->
  <ProfileView
    v-else-if="openProfileId"
    :user-id="openProfileId"
    :logged-in="!!user"
    @back="closeProfile"
    @open="openPublic"
    @open-profile="openProfile"
    @login="loginOpen = true"
  />
  <!-- 阅读器叠加层 -->
  <ReaderView
    v-else-if="openId"
    :id="openId"
    :account-default="(user?.defaultShareLevel as ShareLevel | undefined)"
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
    <InboxView v-if="tab === 'inbox'" ref="inbox" @open="openReader" @open-public="openPublic" />
    <ClustersView v-if="tab === 'clusters'" @open="openClusterId = $event" />
    <PlazaView v-if="tab === 'plaza'" :logged-in="!!user" @open="openPublic" @open-profile="openProfile" />
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
    @compose="openCompose"
  />
  <ComposeModal
    :open="composeNoteIds !== null"
    :note-ids="composeNoteIds ?? []"
    @close="composeNoteIds = null"
  />
  <Toast />
  <LoginModal
    :open="loginOpen"
    @close="loginOpen = false"
    @logged-in="user = $event; loginOpen = false"
  />
  <ShareSettingsModal
    v-if="user"
    :open="shareSettingsOpen"
    :user="user"
    @close="shareSettingsOpen = false"
    @saved="user = $event"
  />
</template>
