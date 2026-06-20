<script setup lang="ts">
import { ref, watch } from 'vue';
import type { PublicProfile, PlazaCard as PlazaCardModel } from '@cnotes/types';
import { api } from '../api';
import PlazaCard from '../components/PlazaCard.vue';

const props = defineProps<{ userId: string }>();
const emit = defineEmits<{ back: []; open: [id: string]; openProfile: [userId: string] }>();

const profile = ref<PublicProfile | null>(null);
const articles = ref<PlazaCardModel[]>([]);
const loading = ref(true);
const notFound = ref(false);

function initials(p: PublicProfile) {
  return (p.nickname ?? '?').slice(0, 1).toUpperCase();
}

async function load(userId: string) {
  loading.value = true;
  notFound.value = false;
  profile.value = null;
  articles.value = [];
  try {
    const [p, a] = await Promise.all([
      api.plazaProfile(userId),
      api.plazaUserArticles(userId).then((r) => r.items).catch(() => []),
    ]);
    profile.value = p;
    articles.value = a;
    window.scrollTo(0, 0);
  } catch (e) {
    notFound.value = (e as { status?: number }).status === 404;
  } finally {
    loading.value = false;
  }
}
watch(() => props.userId, load, { immediate: true });
</script>

<template>
  <div class="reader">
    <div class="r-top">
      <button class="back" @click="emit('back')">← 返回</button>
    </div>

    <div v-if="loading" class="empty">加载中…</div>
    <div v-else-if="notFound || !profile" class="empty">用户不存在。</div>

    <template v-else>
      <div class="profile-head">
        <img v-if="profile.avatarUrl" :src="profile.avatarUrl" class="profile-avatar" alt="头像" />
        <span v-else class="profile-avatar initials">{{ initials(profile) }}</span>
        <div class="profile-meta">
          <div class="profile-name">{{ profile.nickname || '匿名' }}</div>
          <div class="profile-stats">
            <span><b>{{ profile.publicCount }}</b> 公开</span>
            <span><b>{{ profile.collectedTotal }}</b> 被收录</span>
            <span><b>{{ profile.bookmarkedTotal }}</b> 被收藏</span>
            <span><b>{{ profile.followers }}</b> 粉丝</span>
          </div>
        </div>
      </div>

      <div class="profile-section-title">已分享文章</div>
      <div v-if="!articles.length" class="empty">还没有公开内容。</div>
      <template v-else>
        <PlazaCard
          v-for="c in articles"
          :key="c.id"
          :card="c"
          @open="emit('open', $event)"
          @open-profile="emit('openProfile', $event)"
        />
      </template>
    </template>
  </div>
</template>
