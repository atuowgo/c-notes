<script setup lang="ts">
import { ref } from 'vue';
import { api } from '../api';
import type { AuthProvider, CurrentUser } from '@cnotes/types';

defineProps<{ open: boolean }>();
const emit = defineEmits<{ close: []; 'logged-in': [user: CurrentUser] }>();

const loading = ref<AuthProvider | null>(null);
const error = ref<string | null>(null);
const isDev = import.meta.env.DEV;

// dev-login 仅开发态显示:import.meta.env.DEV 在 vite build 里恒为 false,生产构建下整个入口
// 的 v-if 永不渲染(模板字符串仍在包内,但不暴露任何可用入口)。
const devHandle = ref('alice');
const devBusy = ref(false);

async function devLogin() {
  if (!devHandle.value.trim()) return;
  devBusy.value = true;
  error.value = null;
  try {
    const u = await api.devLogin(devHandle.value.trim());
    emit('logged-in', u);
  } catch {
    error.value = '开发登录失败(后端未开 dev-login?)';
  } finally {
    devBusy.value = false;
  }
}

async function loginWith(provider: AuthProvider) {
  loading.value = provider;
  error.value = null;
  try {
    const url = await api.oauthAuthorizeUrl(provider);
    window.location.href = url;
  } catch (e: unknown) {
    if (e && typeof e === 'object' && 'status' in e && (e as { status: number }).status === 503) {
      const names: Record<AuthProvider, string> = { github: 'GitHub', google: 'Google', wechat: '微信' };
      error.value = `${names[provider]} 登录暂未开放`;
    } else {
      error.value = '出错了,请稍后重试';
    }
    loading.value = null;
  }
}
</script>

<template>
  <div v-if="open" class="modal" @click.self="emit('close')">
    <div class="modal-box login-box">
      <button class="login-close" @click="emit('close')">✕</button>

      <div class="login-brand">
        <span class="brand-mark">⚗</span>
        <div>
          <div class="login-title">知识炼金炉</div>
          <div class="login-sub">登录后开始炼金 / 收藏</div>
        </div>
      </div>

      <p v-if="error" class="login-error">{{ error }}</p>

      <div class="login-btns">
        <button
          class="login-btn"
          :disabled="loading !== null"
          @click="loginWith('github')"
        >
          <svg class="login-icon" viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 2C6.477 2 2 6.477 2 12c0 4.42 2.865 8.166 6.839 9.489.5.092.682-.217.682-.482 0-.237-.008-.866-.013-1.7-2.782.603-3.369-1.342-3.369-1.342-.454-1.155-1.11-1.462-1.11-1.462-.908-.62.069-.608.069-.608 1.003.07 1.531 1.03 1.531 1.03.892 1.529 2.341 1.087 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.11-4.555-4.943 0-1.091.39-1.984 1.029-2.683-.103-.253-.446-1.27.098-2.647 0 0 .84-.269 2.75 1.025A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.294 2.747-1.025 2.747-1.025.546 1.377.202 2.394.1 2.647.64.699 1.028 1.592 1.028 2.683 0 3.842-2.339 4.687-4.566 4.935.359.309.678.919.678 1.852 0 1.336-.012 2.415-.012 2.741 0 .267.18.578.688.48C19.138 20.163 22 16.418 22 12c0-5.523-4.477-10-10-10z" />
          </svg>
          使用 GitHub 登录
          <span v-if="loading === 'github'" class="login-spinner"></span>
        </button>

        <button
          class="login-btn"
          :disabled="loading !== null"
          @click="loginWith('google')"
        >
          <svg class="login-icon" viewBox="0 0 24 24">
            <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4" />
            <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853" />
            <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05" />
            <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335" />
          </svg>
          使用 Google 登录
          <span v-if="loading === 'google'" class="login-spinner"></span>
        </button>

        <button
          class="login-btn"
          :disabled="loading !== null"
          @click="loginWith('wechat')"
        >
          <svg class="login-icon" viewBox="0 0 24 24" fill="#07c160">
            <path d="M9.5 2C5.91 2 3 4.69 3 8c0 1.92.93 3.63 2.38 4.77L4.5 15.5l3.16-1.58c.6.17 1.21.26 1.84.26 3.59 0 6.5-2.69 6.5-6S13.09 2 9.5 2zm0 10c-2.76 0-5-2.24-5-4.5S6.74 3 9.5 3 14.5 5.24 14.5 7.5 12.26 12 9.5 12zm10 2c0-2.27-1.64-4.18-3.88-4.77.25.73.38 1.52.38 2.27 0 3.31-2.69 6-6 6-.46 0-.9-.05-1.33-.14C9.71 18.8 11.26 20 13 20c.55 0 1.07-.1 1.55-.25L17 21l-.87-2.09C17.28 17.93 19.5 16.1 19.5 14z" />
          </svg>
          微信扫码登录
          <span v-if="loading === 'wechat'" class="login-spinner"></span>
        </button>

        <button class="login-btn login-btn--dim" disabled>
          <span class="login-icon-text">✉</span>
          邮箱登录
          <span class="login-soon">即将上线</span>
        </button>
      </div>

      <div v-if="isDev" class="login-dev">
        <div class="login-dev-label">开发登录(仅本地)</div>
        <div class="login-dev-row">
          <input
            v-model="devHandle"
            class="login-dev-input"
            placeholder="handle, 如 alice"
            @keydown.enter="devLogin"
          />
          <button class="login-dev-btn" :disabled="devBusy" @click="devLogin">进入</button>
        </div>
      </div>
    </div>
  </div>
</template>
