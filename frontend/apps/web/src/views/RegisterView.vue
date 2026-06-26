<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { api } from '../api';
import { ApiError, setToken } from '@cnotes/api-client';

const router = useRouter();
const username = ref('');
const password = ref('');
const confirm = ref('');
const error = ref('');
const loading = ref(false);

async function submit() {
  error.value = '';
  if (password.value !== confirm.value) {
    error.value = '两次输入的密码不一致';
    return;
  }
  loading.value = true;
  try {
    // 注册成功即签发 token,自动登录进首页
    const res = await api.register({ username: username.value, password: password.value });
    setToken(res.token);
    router.push('/');
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '注册失败,请重试';
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="auth-page">
    <form class="auth-card" @submit.prevent="submit">
      <div class="brand"><span class="mark">⚗</span> 知识炼金炉</div>
      <h1>注册</h1>
      <label class="field">
        <span>用户名(3-64)</span>
        <input v-model="username" autocomplete="username" required minlength="3" maxlength="64" />
      </label>
      <label class="field">
        <span>密码(≥6 位)</span>
        <input v-model="password" type="password" autocomplete="new-password" required minlength="6" />
      </label>
      <label class="field">
        <span>确认密码</span>
        <input v-model="confirm" type="password" autocomplete="new-password" required minlength="6" />
      </label>
      <button class="submit" type="submit" :disabled="loading || !username || !password || !confirm">
        {{ loading ? '注册中…' : '注册并登录' }}
      </button>
      <p v-if="error" class="auth-error">{{ error }}</p>
      <p class="auth-switch">已有账号?<router-link to="/login">去登录</router-link></p>
    </form>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg, #f5f5f7);
  padding: 24px;
}
.auth-card {
  width: 100%;
  max-width: 380px;
  background: #fff;
  border-radius: 16px;
  padding: 32px 28px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.brand { font-size: 14px; color: #888; text-align: center; }
.brand .mark { margin-right: 4px; }
h1 { margin: 0; text-align: center; font-size: 22px; }
.field { display: flex; flex-direction: column; gap: 6px; font-size: 13px; color: #555; }
.field input {
  padding: 10px 12px; border: 1px solid #ddd; border-radius: 8px; font-size: 14px;
}
.field input:focus { outline: none; border-color: #6a8cff; }
.submit {
  margin-top: 4px; padding: 11px; border: none; border-radius: 8px;
  background: #6a8cff; color: #fff; font-size: 15px; cursor: pointer;
}
.submit:disabled { opacity: 0.5; cursor: not-allowed; }
.auth-error { margin: 0; color: #e53935; font-size: 13px; text-align: center; }
.auth-switch { margin: 0; text-align: center; font-size: 13px; color: #666; }
.auth-switch a { color: #6a8cff; text-decoration: none; }
</style>
