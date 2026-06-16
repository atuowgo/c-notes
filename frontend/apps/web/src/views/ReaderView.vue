<script setup lang="ts">
import { ref, watch } from 'vue';
import type { ArticleDetail } from '@cnotes/types';
import { api } from '../api';
import { srcLabel } from '../format';
import { toast } from '../toast';
import DistillCard from '../components/DistillCard.vue';

const props = defineProps<{ id: string }>();
const emit = defineEmits<{ back: [] }>();

const article = ref<ArticleDetail | null>(null);

async function load(id: string) {
  article.value = null;
  try {
    article.value = await api.getArticle(id);
    window.scrollTo(0, 0);
  } catch (e) {
    const err = e as { status?: number; message: string };
    toast(err.status === 404 ? '文章不存在' : `打开失败:${err.message}`);
    emit('back');
  }
}

watch(() => props.id, load, { immediate: true });
</script>

<template>
  <div class="reader">
    <button class="back" @click="emit('back')">← 返回收件箱</button>
    <template v-if="article">
      <h1 class="r-title">{{ article.title || '(未命名)' }}</h1>
      <div class="r-meta">
        <span class="src">{{ srcLabel(article.sourceType) }}</span>
        <template v-if="article.author"><span>·</span><span>{{ article.author }}</span></template>
      </div>
      <DistillCard :article="article" />
      <div class="r-body">{{ article.content || '(无正文)' }}</div>
    </template>
  </div>
</template>
