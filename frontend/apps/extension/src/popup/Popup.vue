<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { createClient } from '@cnotes/api-client';

// 扩展与后端必为跨域,baseURL 须为绝对地址(与 manifest host_permissions 对应)。
const api = createClient(import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080');

type Phase = 'idle' | 'working' | 'done' | 'error';
const phase = ref<Phase>('idle');
const message = ref('');
const pageTitle = ref('');

const busy = computed(() => phase.value === 'working');

async function getActiveTab(): Promise<chrome.tabs.Tab> {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  if (!tab?.id) throw new Error('找不到当前标签页');
  return tab;
}

onMounted(async () => {
  try {
    pageTitle.value = (await getActiveTab()).title ?? '';
  } catch {
    /* 忽略:仅用于展示 */
  }
});

async function collect() {
  phase.value = 'working';
  message.value = '正在提取正文…';
  try {
    const tab = await getActiveTab();
    let resp: ExtractResponse;
    try {
      resp = (await chrome.tabs.sendMessage(tab.id!, { type: 'EXTRACT' } satisfies ExtractRequest)) as ExtractResponse;
    } catch {
      throw new Error('无法读取此页面,请刷新后重试(浏览器内置页 / 应用商店等受限页面不支持)。');
    }
    if (!resp?.ok) throw new Error(resp?.error || '正文提取失败');

    message.value = '正在提交…';
    const { id } = await api.collect(resp.payload);
    phase.value = 'done';
    message.value = `已收藏,后台开始处理 #${id.slice(0, 8)}`;
  } catch (e) {
    phase.value = 'error';
    message.value = (e as Error).message;
  }
}
</script>

<template>
  <div class="wrap">
    <div class="brand"><span class="mark">⚗</span> 知识炼金炉</div>
    <p class="page-title" :title="pageTitle">{{ pageTitle || '当前页面' }}</p>

    <button class="collect" :disabled="busy" @click="collect">
      {{ busy ? '处理中…' : '＋ 收藏本页' }}
    </button>

    <p v-if="message" class="msg" :class="phase">{{ message }}</p>
  </div>
</template>

<style scoped>
.wrap {
  width: 300px;
  padding: 16px;
  font-family: var(--font-sans);
  background: var(--bg);
  color: var(--ink);
}
.brand { display: flex; align-items: center; gap: 8px; font-weight: 700; font-size: 15px; }
.brand .mark {
  width: 22px; height: 22px; border-radius: 7px;
  background: linear-gradient(150deg, var(--gold), #e0a050);
  display: grid; place-items: center; color: #fff; font-size: 13px;
}
.page-title {
  font-size: 12.5px; color: var(--ink-soft); margin: 10px 0 14px; line-height: 1.5;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.collect {
  width: 100%; border: 1px solid var(--gold); background: var(--gold); color: #fff;
  border-radius: 10px; height: 38px; font-size: 14px; font-weight: 600;
}
.collect:hover:not(:disabled) { filter: brightness(1.05); }
.collect:disabled { opacity: .6; cursor: default; }
.msg { font-size: 12.5px; margin: 12px 0 0; line-height: 1.5; }
.msg.done { color: var(--green); }
.msg.error { color: var(--red); }
.msg.working { color: var(--ink-soft); }
</style>
