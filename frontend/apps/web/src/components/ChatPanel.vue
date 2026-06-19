<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue';
import { api } from '../api';
import { ApiError } from '@cnotes/api-client';
import { askIntent } from '../chat';

// AI 深聊入口(§6.5):围绕锚定文章发起一轮对话,后端三源(本文/知识网/联网)综合回答。
const props = defineProps<{ articleId?: string | null; articleTitle?: string | null }>();

interface Msg {
  role: 'ai' | 'me';
  text: string;
  srcs?: string[];
}

const open = ref(false);
const onArticleCtx = ref(false);
const input = ref('');
const messages = ref<Msg[]>([]);
const bodyEl = ref<HTMLElement>();
const sending = ref(false);
// 后端会话 id:首轮为空由后端新建,后续轮次回传以续接上下文。
const sessionId = ref<string | undefined>(undefined);

const ctxIsArticle = computed(() => onArticleCtx.value && !!props.articleTitle);

function scrollDown() {
  nextTick(() => {
    if (bodyEl.value) bodyEl.value.scrollTop = bodyEl.value.scrollHeight;
  });
}

function openChat() {
  onArticleCtx.value = !!props.articleTitle;
  if (!messages.value.length) {
    messages.value.push({
      role: 'ai',
      text: '你好,我可以基于这篇文章和你的知识网跟你深聊,也能联网找最新的相关内容。想从哪儿聊起?',
    });
  }
  open.value = true;
  scrollDown();
}

function send() {
  const v = input.value.trim();
  if (!v) return;
  input.value = '';
  void sendMessage(v, props.articleId ?? undefined);
}

async function sendMessage(text: string, articleId?: string, noteId?: string) {
  if (!text || sending.value) return;
  if (!articleId) {
    messages.value.push({ role: 'ai', text: '请先打开一篇文章,再开始深聊。' });
    scrollDown();
    return;
  }
  messages.value.push({ role: 'me', text });
  sending.value = true;
  scrollDown();
  try {
    const reply = await api.chat(articleId, { message: text, sessionId: sessionId.value, noteId });
    sessionId.value = reply.sessionId;
    messages.value.push({ role: 'ai', text: reply.reply, srcs: reply.sources });
  } catch (e) {
    const msg = e instanceof ApiError ? `出错了(${e.status})` : '网络异常,请稍后重试。';
    messages.value.push({ role: 'ai', text: msg });
  } finally {
    sending.value = false;
    scrollDown();
  }
}

// 切换文章时重置会话,避免把上一篇的上下文带过来。
watch(
  () => props.articleId,
  () => {
    sessionId.value = undefined;
    if (!props.articleTitle) onArticleCtx.value = false;
  },
);

// 「提问」意图:从想法抽屉发起 —— 打开面板、锚定该想法的文章、带 noteId 直接发问。
watch(askIntent, (intent) => {
  if (!intent) return;
  openChat();
  onArticleCtx.value = true;
  sessionId.value = undefined;
  void sendMessage(intent.question, intent.articleId, intent.noteId);
  askIntent.value = null;
});
</script>

<template>
  <button v-if="!open" class="fab" @click="openChat"><span class="ic">⚗</span> 深聊</button>

  <div v-else class="chat">
    <div class="chat-head">
      <span class="mark">⚗</span>
      <span class="ttl">深聊</span>
      <button class="x" @click="open = false">×</button>
    </div>

    <div class="chat-ctx" :class="{ general: !ctxIsArticle }">
      <template v-if="ctxIsArticle">
        📄 正在聊本文:《{{ (articleTitle || '').slice(0, 12) }}…》
        <button class="sw" @click="onArticleCtx = false">切换通用</button>
      </template>
      <template v-else>
        💬 通用对话
        <button v-if="articleTitle" class="sw" @click="onArticleCtx = true">聊本文</button>
      </template>
    </div>

    <div ref="bodyEl" class="chat-body">
      <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
        {{ m.text }}
        <div v-if="m.srcs" class="srcs"><span v-for="s in m.srcs" :key="s">{{ s }}</span></div>
      </div>
    </div>

    <div class="chat-input">
      <textarea
        v-model="input"
        rows="1"
        :disabled="sending"
        placeholder="问点什么…(基于本文 + 知识网 + 联网)"
        @keydown.enter.exact.prevent="send"
      ></textarea>
      <button class="send" :disabled="sending" @click="send">{{ sending ? '…' : '↑' }}</button>
    </div>
    <div class="chat-hint">三层来源:📄 本文 + 🕸 知识网 + 🌐 联网</div>
  </div>
</template>
