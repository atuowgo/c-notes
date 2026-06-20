<script setup lang="ts">
import { ref, computed } from 'vue';
import type { ShareLevel } from '@cnotes/types';
import { api } from '../api';
import { toast } from '../toast';
import { SHARE_LEVELS, shareLabel, levelAtLeast } from '../share';

const props = defineProps<{
  articleId: string;
  /** 逐篇覆盖(null=继承账号默认) */
  shareLevel: string | null | undefined;
  /** 生效级别 */
  effectiveLevel: ShareLevel | undefined;
  /** 账号默认(用于「继承」时回显生效级别) */
  accountDefault: ShareLevel | undefined;
}>();
const emit = defineEmits<{ updated: [payload: { shareLevel: string | null; effectiveLevel: ShareLevel }] }>();

const open = ref(false);
const saving = ref(false);

const effective = computed<ShareLevel>(() => props.effectiveLevel ?? 'PRIVATE');
const isPublic = computed(() => levelAtLeast(effective.value, 'READ_ONLY'));

async function choose(level: ShareLevel | null) {
  if (saving.value) return;
  saving.value = true;
  try {
    await api.setArticleShareLevel(props.articleId, level);
    const eff = level ?? props.accountDefault ?? 'PRIVATE';
    emit('updated', { shareLevel: level, effectiveLevel: eff });
    open.value = false;
    toast(level === null ? '已恢复为账号默认' : levelAtLeast(eff, 'READ_ONLY') ? '已公开,可在广场查看' : '已设为私有');
  } catch (e) {
    toast(`保存失败:${(e as Error).message}`);
  } finally {
    saving.value = false;
  }
}

async function copyLink() {
  const link = `${window.location.origin}/?a=${encodeURIComponent(props.articleId)}`;
  try {
    await navigator.clipboard.writeText(link);
    toast('公开链接已复制');
  } catch {
    window.prompt('复制此公开链接:', link);
  }
}
</script>

<template>
  <div class="share-ctl">
    <button class="share-btn" @click="open = !open">
      分享:{{ shareLabel(effective) }} <span class="caret">▾</span>
    </button>

    <template v-if="open">
      <div class="share-mask" @click="open = false"></div>
      <div class="share-menu">
        <button class="share-mi" :class="{ on: shareLevel == null }" @click="choose(null)">
          继承账号默认<span class="share-mi-sub">{{ shareLabel(accountDefault) }}</span>
        </button>
        <div class="share-menu-div"></div>
        <button
          v-for="lv in SHARE_LEVELS"
          :key="lv.value"
          class="share-mi"
          :class="{ on: shareLevel === lv.value }"
          @click="choose(lv.value)"
        >
          {{ lv.label }}
        </button>
      </div>
    </template>

    <button v-if="isPublic" class="copy-link-btn" title="复制公开链接" @click="copyLink">🔗</button>
  </div>
</template>
