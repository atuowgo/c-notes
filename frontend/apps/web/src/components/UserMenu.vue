<script setup lang="ts">
import { ref } from 'vue';
import type { CurrentUser } from '@cnotes/types';
import { api } from '../api';

const { user } = defineProps<{ user: CurrentUser }>();
const emit = defineEmits<{
  logout: [];
  'open-share-settings': [];
}>();

const open = ref(false);

function initials(u: CurrentUser) {
  return (u.nickname ?? u.email ?? '?').slice(0, 1).toUpperCase();
}

async function logout() {
  open.value = false;
  await api.logout();
  emit('logout');
}
</script>

<template>
  <div class="user-menu">
    <button
      class="avatar-btn"
      :title="user.nickname ?? user.email ?? '我'"
      @click="open = !open"
    >
      <img v-if="user.avatarUrl" :src="user.avatarUrl" class="avatar-img" alt="头像" />
      <span v-else class="avatar-initials">{{ initials(user) }}</span>
      <span class="avatar-chevron" :class="{ up: open }">▾</span>
    </button>

    <template v-if="open">
      <div class="um-mask" @click="open = false"></div>
      <div class="um-dropdown">
        <div class="um-header">
          <div class="um-name">{{ user.nickname ?? user.email ?? '匿名' }}</div>
          <div v-if="user.email && user.nickname" class="um-email">{{ user.email }}</div>
        </div>
        <div class="um-divider"></div>
        <button class="um-item um-item--soon" disabled>我的主页</button>
        <button class="um-item" @click="open = false; emit('open-share-settings')">分享设置</button>
        <button class="um-item um-item--soon" disabled>账号设置</button>
        <div class="um-divider"></div>
        <button class="um-item um-item--danger" @click="logout">退出登录</button>
      </div>
    </template>
  </div>
</template>
