<script setup lang="ts">
import { computed } from 'vue';
import { notes, removeNote, type LocalNote } from '../notes';
import { relTime } from '../format';
import { toast } from '../toast';

const props = defineProps<{ scope: 'article' | 'all'; articleId?: string }>();
const emit = defineEmits<{ close: [] }>();

const list = computed<LocalNote[]>(() =>
  props.scope === 'article'
    ? notes.value.filter((n) => n.articleId === props.articleId)
    : notes.value,
);
</script>

<template>
  <div class="drawer-mask" @click="emit('close')"></div>
  <div class="drawer">
    <div class="drawer-head">
      <span class="dt">{{ scope === 'article' ? '本文想法' : '全部想法' }}</span>
      <button class="x" @click="emit('close')">×</button>
    </div>
    <div class="drawer-body">
      <div v-if="!list.length" class="note-empty">
        还没有想法。<br />在正文里选中一段文字,点「划线记想法」试试。
      </div>
      <div v-for="n in list" :key="n.id" class="note-item">
        <div class="note-quote">"{{ n.quote }}"</div>
        <div v-if="n.thought" class="note-thought">{{ n.thought }}</div>
        <div v-else class="note-thought" style="color: var(--ink-faint)">(仅划线,未写想法)</div>
        <div v-if="scope === 'all'" class="note-art">↳ {{ n.articleTitle }}</div>
        <div class="note-time">{{ relTime(n.createdAt) }}</div>
        <div class="note-actions">
          <button @click="toast('规划中:把这条想法作为提问起点,和 AI 深聊')">💬 提问</button>
          <button @click="toast('规划中:基于这条想法生成创作草稿')">✍ 创作</button>
          <button class="del" @click="removeNote(n.id)">🗑 删除</button>
        </div>
      </div>
    </div>
    <div class="drawer-foot">后续:可基于任意一条想法发起 <b>提问</b> 或 <b>创作</b>(规划中)</div>
  </div>
</template>
