<script setup lang="ts">
import { ref, watch } from 'vue';
import type { ClusterCard, ClusterDetail } from '@cnotes/types';
import { api } from '../api';
import { toast } from '../toast';
import ArticleCard from '../components/ArticleCard.vue';

const props = defineProps<{ id: string }>();
const emit = defineEmits<{ back: []; open: [id: string] }>();

const cluster = ref<ClusterDetail | null>(null);
const regenerating = ref(false);

// 纠偏(merge/split/move)模式:多选本簇文章 + 选目标簇,执行拆分/移动/并入。
const managing = ref(false);
const selected = ref<Set<string>>(new Set());
const targets = ref<ClusterCard[]>([]);
const targetId = ref('');
const acting = ref(false);

async function load(id: string) {
  cluster.value = null;
  managing.value = false;
  selected.value = new Set();
  try {
    cluster.value = await api.getCluster(id);
    window.scrollTo(0, 0);
  } catch (e) {
    toast(`打开失败:${(e as Error).message}`);
    emit('back');
  }
}
watch(() => props.id, load, { immediate: true });

async function regenerate() {
  if (!cluster.value || regenerating.value) return;
  regenerating.value = true;
  try {
    cluster.value = await api.regenerateCluster(cluster.value.id);
    toast('综述已重写');
  } catch (e) {
    toast(`重写失败:${(e as Error).message}`);
  } finally {
    regenerating.value = false;
  }
}

async function enterManage() {
  managing.value = true;
  selected.value = new Set();
  targetId.value = '';
  if (!targets.value.length) {
    try {
      targets.value = (await api.listClusters()).filter((c) => c.id !== props.id);
    } catch (e) {
      toast(`加载簇列表失败:${(e as Error).message}`);
    }
  }
}

function exitManage() {
  managing.value = false;
  selected.value = new Set();
  targetId.value = '';
}

function toggle(id: string) {
  const s = new Set(selected.value);
  if (s.has(id)) s.delete(id);
  else s.add(id);
  selected.value = s;
}

function targetName(id: string): string {
  return targets.value.find((c) => c.id === id)?.name ?? '';
}

async function doSplit() {
  if (!cluster.value || acting.value) return;
  if (selected.value.size === 0) {
    toast('请先选择要拆出的文章');
    return;
  }
  const name = window.prompt('新簇名称', '');
  if (!name || !name.trim()) return;
  acting.value = true;
  try {
    await api.splitCluster(cluster.value.id, {
      articleIds: [...selected.value],
      newTag: name.trim(),
    });
    toast(`已拆出 ${selected.value.size} 篇到新簇「${name.trim()}」`);
    cluster.value = await api.getCluster(cluster.value.id);
    exitManage();
  } catch (e) {
    toast(`拆分失败:${(e as Error).message}`);
  } finally {
    acting.value = false;
  }
}

async function doMove() {
  if (!cluster.value || acting.value) return;
  if (selected.value.size === 0) {
    toast('请先选择要移动的文章');
    return;
  }
  if (!targetId.value) {
    toast('请选择目标簇');
    return;
  }
  acting.value = true;
  try {
    for (const aid of selected.value) {
      await api.moveArticleToCluster(cluster.value.id, {
        articleId: aid,
        targetTagId: targetId.value,
      });
    }
    toast(`已移动 ${selected.value.size} 篇到「${targetName(targetId.value)}」`);
    cluster.value = await api.getCluster(cluster.value.id);
    exitManage();
  } catch (e) {
    toast(`移动失败:${(e as Error).message}`);
  } finally {
    acting.value = false;
  }
}

async function doMerge() {
  if (!cluster.value || acting.value) return;
  if (!targetId.value) {
    toast('请选择目标簇');
    return;
  }
  const tgt = targetName(targetId.value);
  if (!window.confirm(`确认把「${cluster.value.name}」全部并入「${tgt}」?此操作会删除当前簇。`)) return;
  acting.value = true;
  try {
    await api.mergeClusters({ sourceId: cluster.value.id, targetId: targetId.value });
    toast(`已并入「${tgt}」,当前簇已删除`);
    emit('back');
  } catch (e) {
    toast(`合并失败:${(e as Error).message}`);
  } finally {
    acting.value = false;
  }
}
</script>

<template>
  <div class="reader">
    <div class="r-top">
      <button class="back" @click="emit('back')">← 返回知识网</button>
      <template v-if="cluster">
        <button
          v-if="!managing"
          class="ideas-entry"
          :disabled="regenerating"
          @click="regenerate"
        >
          ⚗ {{ regenerating ? '重写中…' : '重写综述' }}
        </button>
        <button
          v-if="!managing"
          class="ideas-entry"
          :disabled="regenerating"
          @click="enterManage"
        >
          ✂ 整理簇
        </button>
        <button v-else class="ideas-entry" :disabled="acting" @click="exitManage">取消</button>
      </template>
    </div>

    <template v-if="cluster">
      <h1 class="r-title">{{ cluster.name }}</h1>
      <div class="r-meta"><span class="src">主题簇</span><span>·</span><span>{{ cluster.articleCount }} 篇</span></div>

      <div v-if="cluster.livingSummary" class="distill">
        <h4>⚗ 演进式综述</h4>
        <p class="summary" style="white-space: pre-wrap">{{ cluster.livingSummary }}</p>
      </div>
      <div v-else class="distill waiting">
        <h4>⚗ 演进式综述</h4>
        <p class="wait-text">综述尚未生成(需至少 2 篇)。后台会随新内容自动织综述,也可点右上角手动重写。</p>
      </div>

      <div class="day-label" style="margin-top: 26px">本簇文章</div>

      <!-- 纠偏工具条:目标簇 + 拆分/移动/并入 -->
      <div v-if="managing" class="manage-bar">
        <select v-model="targetId" class="target-select">
          <option value="" disabled>选择目标簇(移动/并入)</option>
          <option v-for="t in targets" :key="t.id" :value="t.id">{{ t.name }}（{{ t.articleCount }} 篇）</option>
        </select>
        <span class="sel-count" v-if="selected.size">已选 {{ selected.size }} 篇</span>
        <button class="m-btn" :disabled="acting" @click="doSplit">拆为新簇</button>
        <button class="m-btn" :disabled="acting || !targetId" @click="doMove">移动到目标</button>
        <button class="m-btn danger" :disabled="acting || !targetId" @click="doMerge">并入目标</button>
        <span v-if="!targets.length" class="no-target">无其他簇可选,先到列表新建/收文。</span>
      </div>

      <div v-for="a in cluster.articles" :key="a.id" class="article-row" :class="{ managing }">
        <label v-if="managing" class="sel" @click.stop="toggle(a.id)">
          <input type="checkbox" :checked="selected.has(a.id)" @click.stop="toggle(a.id)" />
        </label>
        <ArticleCard :article="a" @open="emit('open', $event)" />
      </div>
    </template>
  </div>
</template>

<style scoped>
.manage-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin: 10px 0 16px;
  padding: 10px 12px;
  background: var(--surface-2, #f3f3f3);
  border: 1px dashed var(--border, #ddd);
  border-radius: 10px;
  font-size: 13px;
}
.target-select {
  padding: 6px 8px;
  border: 1px solid var(--border, #ddd);
  border-radius: 8px;
  background: var(--surface, #fff);
  color: var(--text, #222);
  font-size: 13px;
  max-width: 220px;
}
.sel-count {
  color: var(--muted, #888);
}
.m-btn {
  border: 1px solid var(--border, #ddd);
  background: var(--surface, #fff);
  color: var(--text, #222);
  padding: 6px 12px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
}
.m-btn:hover:not(:disabled) {
  background: var(--surface-2, #eee);
}
.m-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.m-btn.danger {
  color: #c33;
  border-color: #e3b3b3;
}
.no-target {
  color: var(--muted, #888);
  width: 100%;
}
.article-row {
  position: relative;
}
.article-row.managing {
  padding-left: 34px;
}
.article-row .sel {
  position: absolute;
  left: 8px;
  top: 50%;
  transform: translateY(-50%);
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.article-row .sel input {
  width: 16px;
  height: 16px;
  cursor: pointer;
}
</style>
