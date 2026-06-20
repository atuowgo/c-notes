<script setup lang="ts">
import { ref, watch } from 'vue';
import type { CurrentUser, ShareLevel } from '@cnotes/types';
import { api } from '../api';
import { toast } from '../toast';
import { SHARE_LEVELS } from '../share';

const props = defineProps<{ open: boolean; user: CurrentUser }>();
const emit = defineEmits<{ close: []; saved: [user: CurrentUser] }>();

const selected = ref<ShareLevel>('PRIVATE');
const saving = ref(false);

// 每次打开时回填当前账号默认。
watch(
  () => props.open,
  (open) => {
    if (open) selected.value = (props.user.defaultShareLevel as ShareLevel) ?? 'PRIVATE';
  },
);

async function save() {
  saving.value = true;
  try {
    const u = await api.updateShareSettings(selected.value);
    emit('saved', u);
    toast('分享设置已保存');
    emit('close');
  } catch (e) {
    toast(`保存失败:${(e as Error).message}`);
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <div v-if="open" class="modal" @click.self="emit('close')">
    <div class="modal-box share-box">
      <h3>分享设置</h3>
      <p>我的内容默认分享级别。级别向下兼容——选高级别即解锁其下全部能力。单篇可在阅读页单独覆盖。</p>

      <label v-for="lv in SHARE_LEVELS" :key="lv.value" class="share-opt" :class="{ on: selected === lv.value }">
        <input v-model="selected" type="radio" :value="lv.value" />
        <span class="share-opt-main">
          <span class="share-opt-label">{{ lv.label }}</span>
          <span class="share-opt-desc">{{ lv.desc }}</span>
        </span>
      </label>

      <div class="modal-actions">
        <button class="cancel" @click="emit('close')">取消</button>
        <button class="save" :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存' }}</button>
      </div>
    </div>
  </div>
</template>
