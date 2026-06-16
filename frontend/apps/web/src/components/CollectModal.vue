<script setup lang="ts">
import { ref, watch, nextTick, computed } from 'vue';
import { api } from '../api';
import { toast } from '../toast';

const props = defineProps<{ open: boolean }>();
const emit = defineEmits<{ close: []; collected: [] }>();

const url = ref('');
const title = ref('');
const content = ref('');
const saving = ref(false);
const urlInput = ref<HTMLInputElement>();

const canSave = computed(() => url.value.trim().length > 0 && !saving.value);

watch(
  () => props.open,
  (open) => {
    if (open) nextTick(() => urlInput.value?.focus());
    else reset();
  },
);

function reset() {
  url.value = '';
  title.value = '';
  content.value = '';
}

async function submit() {
  const u = url.value.trim();
  if (!u) return;
  saving.value = true;
  try {
    await api.collect({
      url: u,
      title: title.value.trim() || null,
      content: content.value.trim() || null,
    });
    emit('close');
    emit('collected');
    toast('已收藏,后台开始处理');
  } catch (e) {
    toast(`收藏失败:${(e as Error).message}`);
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <div v-if="open" class="modal" @click.self="emit('close')">
    <div class="modal-box">
      <h3>收藏一个链接</h3>
      <p>先入库,后台会自动抓取并沉淀。只有 URL 是必填的。</p>
      <label>链接 URL *</label>
      <input ref="urlInput" v-model="url" type="url" placeholder="https://…" @keyup.enter="submit" />
      <label>标题(可选)</label>
      <input v-model="title" type="text" placeholder="留空则由后台解析" />
      <label>正文 / 备注(可选)</label>
      <textarea v-model="content" placeholder="可粘贴正文,便于离线演示沉淀"></textarea>
      <div class="modal-actions">
        <button class="cancel" @click="emit('close')">取消</button>
        <button class="save" :disabled="!canSave" @click="submit">{{ saving ? '收藏中…' : '收藏' }}</button>
      </div>
    </div>
  </div>
</template>
