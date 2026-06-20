<script setup lang="ts">
import type { PlazaCard } from '@cnotes/types';
import { srcLabel, relTime } from '../format';

defineProps<{ card: PlazaCard }>();
const emit = defineEmits<{ open: [id: string]; openProfile: [userId: string] }>();
</script>

<template>
  <div class="card plaza-card" data-clickable="1" @click="emit('open', card.id)">
    <div class="c-head">
      <h3 class="c-title">{{ card.title || '(未命名)' }}</h3>
      <span class="quality" title="质量分 = 行为分(收录/收藏) + AI 深度分(摘要 + 知识网连通度)">
        ⭐ {{ card.qualityScore }}
      </span>
    </div>

    <div class="c-meta">
      <button class="author-link" @click.stop="emit('openProfile', card.ownerId)">
        😊 {{ card.ownerNickname || '匿名' }}
      </button>
      <span>·</span><span class="src">{{ srcLabel(card.sourceType) }}</span>
      <template v-if="card.createTime"><span>·</span><span>{{ relTime(card.createTime) }}</span></template>
    </div>

    <p class="c-summary">{{ card.summary || '(无摘要)' }}</p>

    <div v-if="card.tags && card.tags.length" class="c-tags">
      <span v-for="t in card.tags" :key="t" class="tag">{{ t }}</span>
    </div>

    <div class="plaza-stats">
      <span title="被收藏">🔖 {{ card.bookmarkCount }}</span>
      <span title="被收录">📥 {{ card.collectCount }}</span>
      <span title="赞(即将上线)">👍 {{ card.likeCount }}</span>
      <span title="评论(即将上线)">💬 {{ card.commentCount }}</span>
    </div>
  </div>
</template>
