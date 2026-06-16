<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue';

// AI 深聊入口外壳(§6.5):仅交互示意,双引擎检索(私域知识网 + 联网)为 V4。
const props = defineProps<{ articleTitle?: string | null }>();

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
  messages.value.push({ role: 'me', text: v });
  input.value = '';
  scrollDown();
  setTimeout(() => {
    messages.value.push({
      role: 'ai',
      text: `(原型示意)我会综合${ctxIsArticle.value ? '本文、' : ''}你的知识网和联网内容来回答。`,
      srcs: ['📄 本文', '🕸 知识网', '🌐 联网'],
    });
    scrollDown();
  }, 350);
}

watch(
  () => props.articleTitle,
  () => {
    if (!props.articleTitle) onArticleCtx.value = false;
  },
);
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
        placeholder="问点什么…(基于本文 + 知识网 + 联网)"
        @keydown.enter.exact.prevent="send"
      ></textarea>
      <button class="send" @click="send">↑</button>
    </div>
    <div class="chat-hint">原型示意 · 深聊为 V4 能力 · 三层来源:本文 + 知识网 + 联网</div>
  </div>
</template>
