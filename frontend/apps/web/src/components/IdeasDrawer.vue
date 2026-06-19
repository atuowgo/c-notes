<script setup lang="ts">
import { ref, computed } from 'vue';
import { notes, removeNote } from '../notes';
import type { Note, RelatedNote } from '@cnotes/types';
import { relTime } from '../format';
import { askFromNote } from '../chat';
import { api } from '../api';

const props = defineProps<{ scope: 'article' | 'all'; articleId?: string }>();
const emit = defineEmits<{ close: []; compose: [noteId: string] }>();

const q = ref('');

const list = computed<Note[]>(() => {
  const base =
    props.scope === 'article'
      ? notes.value.filter((n) => n.articleId === props.articleId)
      : notes.value;
  const kw = q.value.trim().toLowerCase();
  if (!kw) return base;
  return base.filter(
    (n) =>
      n.quote.toLowerCase().includes(kw) || (n.thought ?? '').toLowerCase().includes(kw),
  );
});

function ask(n: Note) {
  askFromNote(n);
  emit('close');
}

/* ---------- 相关想法(批注↔批注) ---------- */
const openRel = ref<string | null>(null);
const relCache = ref<Record<string, RelatedNote[]>>({});
const relLoading = ref<string | null>(null);

async function toggleRelated(n: Note) {
  if (openRel.value === n.id) {
    openRel.value = null;
    return;
  }
  openRel.value = n.id;
  if (relCache.value[n.id]) return;
  relLoading.value = n.id;
  try {
    relCache.value[n.id] = await api.listRelatedNotes(n.id);
  } catch {
    relCache.value[n.id] = [];
  } finally {
    relLoading.value = null;
  }
}
</script>

<template>
  <div class="drawer-mask" @click="emit('close')"></div>
  <div class="drawer">
    <div class="drawer-head">
      <span class="dt">{{ scope === 'article' ? '本文想法' : '全部想法' }}</span>
      <button class="x" @click="emit('close')">×</button>
    </div>

    <div v-if="scope === 'all'" class="drawer-search">
      <input v-model="q" type="search" placeholder="搜索划线 / 想法…" />
    </div>

    <div class="drawer-body">
      <div v-if="!list.length" class="note-empty">
        <template v-if="q.trim()">没有匹配"{{ q }}"的想法。</template>
        <template v-else>还没有想法。<br />在正文里选中一段文字,点「划线记想法」试试。</template>
      </div>
      <div v-for="n in list" :key="n.id" class="note-item">
        <div class="note-quote">"{{ n.quote }}"</div>
        <div v-if="n.thought" class="note-thought">{{ n.thought }}</div>
        <div v-else class="note-thought" style="color: var(--ink-faint)">(仅划线,未写想法)</div>
        <div v-if="scope === 'all'" class="note-art">↳ {{ n.articleTitle || '(未命名)' }}</div>
        <div class="note-time">{{ relTime(n.createTime) }}</div>
        <div class="note-actions">
          <button @click="ask(n)">💬 提问</button>
          <button @click="emit('compose', n.id)">✍ 创作</button>
          <button :class="{ on: openRel === n.id }" @click="toggleRelated(n)">🔗 相关</button>
          <button class="del" @click="removeNote(n.id)">🗑 删除</button>
        </div>

        <div v-if="openRel === n.id" class="note-rel">
          <div v-if="relLoading === n.id" class="note-rel-empty">寻找相关想法…</div>
          <template v-else-if="relCache[n.id] && relCache[n.id].length">
            <div v-for="r in relCache[n.id]" :key="r.note.id" class="rel-item">
              <span class="rel-kind">{{ r.relationType }}</span>
              <div class="rel-body">
                <p class="rq">"{{ r.note.quote }}"</p>
                <p v-if="r.note.thought" class="rt">{{ r.note.thought }}</p>
                <p class="rr">{{ r.reason }}</p>
              </div>
            </div>
          </template>
          <div v-else class="note-rel-empty">暂无相关想法。</div>
        </div>
      </div>
    </div>
    <div class="drawer-foot">由想法可发起 <b>提问</b>(进深聊)或 <b>创作</b>(拼装草稿),并查看 <b>相关想法</b></div>
  </div>
</template>
