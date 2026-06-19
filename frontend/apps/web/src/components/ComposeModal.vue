<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { notes } from '../notes';
import { api } from '../api';
import { toast } from '../toast';

// 创作:把若干想法拼装成一篇草稿(§6.4「想法是下一步产出的种子」)。
// 由想法抽屉的「✍ 创作」带入一条想法;可再补主题,后端 LLM 织成草稿。
const props = defineProps<{ open: boolean; noteIds: string[] }>();
const emit = defineEmits<{ close: [] }>();

const topic = ref('');
const draft = ref('');
const generating = ref(false);

const seeds = computed(() => notes.value.filter((n) => props.noteIds.includes(n.id)));

watch(
  () => props.open,
  (o) => {
    if (o) {
      topic.value = '';
      draft.value = '';
      generating.value = false;
    }
  },
);

async function generate() {
  if (generating.value || !props.noteIds.length) return;
  generating.value = true;
  draft.value = '';
  try {
    const res = await api.compose(props.noteIds, topic.value.trim() || undefined);
    draft.value = res.draft;
  } catch {
    toast('生成失败,请确认后端已启动');
  } finally {
    generating.value = false;
  }
}

async function copy() {
  try {
    await navigator.clipboard.writeText(draft.value);
    toast('草稿已复制');
  } catch {
    toast('复制失败');
  }
}
</script>

<template>
  <div v-if="open" class="modal" @click.self="emit('close')">
    <div class="modal-box compose-box">
      <h3>✍ 由想法创作</h3>
      <p class="compose-seeds-label">拼装这些想法({{ seeds.length }} 条):</p>
      <div class="compose-seeds">
        <div v-for="n in seeds" :key="n.id" class="compose-seed">
          <span class="cs-quote">"{{ n.quote }}"</span>
          <span v-if="n.thought" class="cs-thought">— {{ n.thought }}</span>
        </div>
      </div>

      <label>主题方向(可选)</label>
      <input v-model="topic" placeholder="想写成什么?如「一篇随笔」「观点综述」…" />

      <div v-if="draft" class="distill" style="margin-top: 14px">
        <h4>⚗ 草稿</h4>
        <p class="summary" style="white-space: pre-wrap">{{ draft }}</p>
      </div>

      <div class="modal-actions">
        <button @click="emit('close')">关闭</button>
        <button v-if="draft" @click="copy">复制草稿</button>
        <button class="save" :disabled="generating || !noteIds.length" @click="generate">
          {{ generating ? '拼装中…' : draft ? '重新生成' : '生成草稿' }}
        </button>
      </div>
    </div>
  </div>
</template>
