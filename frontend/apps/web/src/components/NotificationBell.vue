<script setup lang="ts">
import { ref, onMounted } from 'vue';
import type { AppNotification, NotificationType } from '@cnotes/types';
import { api } from '../api';
import { relTime } from '../format';

const emit = defineEmits<{ open: [id: string] }>();

const open = ref(false);
const unread = ref(0);
const items = ref<AppNotification[]>([]);
const loaded = ref(false);

const VERB: Record<NotificationType, string> = {
  LIKE: '赞了你的文章',
  COMMENT: '评论了你的文章',
  REPLY: '回复了你的评论',
  FOLLOW: '关注了你',
  ANNOTATION: '在你的文章发表了公开批注',
};

async function refreshCount() {
  try { unread.value = await api.unreadCount(); } catch { /* ignore */ }
}

async function toggle() {
  open.value = !open.value;
  if (open.value) {
    items.value = await api.listNotifications().catch(() => []);
    loaded.value = true;
    if (unread.value > 0) {
      await api.markNotificationsRead().catch(() => {});
      unread.value = 0;
    }
  }
}

function go(n: AppNotification) {
  open.value = false;
  if (n.articleId) emit('open', n.articleId);
}

onMounted(refreshCount);
defineExpose({ refreshCount });
</script>

<template>
  <div class="notif">
    <button class="icon-btn notif-btn" title="通知" @click="toggle">
      🔔<span v-if="unread > 0" class="notif-badge">{{ unread > 99 ? '99+' : unread }}</span>
    </button>

    <template v-if="open">
      <div class="notif-mask" @click="open = false"></div>
      <div class="notif-panel">
        <div class="notif-head">通知</div>
        <div v-if="loaded && !items.length" class="notif-empty">还没有通知。</div>
        <button
          v-for="n in items"
          :key="n.id"
          class="notif-item"
          :class="{ unread: !n.read }"
          @click="go(n)"
        >
          <span class="notif-actor">{{ n.actorNickname || '某人' }}</span>
          <span class="notif-verb">{{ VERB[n.type] }}</span>
          <span v-if="n.articleTitle" class="notif-art">「{{ n.articleTitle }}」</span>
          <span class="notif-time">{{ relTime(n.createTime) }}</span>
        </button>
      </div>
    </template>
  </div>
</template>
