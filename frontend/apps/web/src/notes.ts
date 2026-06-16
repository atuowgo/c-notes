import { ref, watch } from 'vue';

// 划线记想法(批注基础版)—— MVP 先本地存(localStorage),待后端 note 接口就绪再迁移。
// anchor 用正文字符偏移 start/end(配合详情返回的 content 字符串重定位高亮)。
export interface LocalNote {
  id: string;
  articleId: string;
  articleTitle: string;
  quote: string;
  thought: string;
  start: number;
  end: number;
  createdAt: string;
}

const KEY = 'cnotes.notes.v1';

function load(): LocalNote[] {
  try {
    const raw = localStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as LocalNote[]) : [];
  } catch {
    return [];
  }
}

export const notes = ref<LocalNote[]>(load());

watch(
  notes,
  (v) => {
    try {
      localStorage.setItem(KEY, JSON.stringify(v));
    } catch {
      /* 忽略写入配额等异常 */
    }
  },
  { deep: true },
);

export function notesForArticle(articleId: string): LocalNote[] {
  return notes.value.filter((n) => n.articleId === articleId);
}

export function addNote(n: Omit<LocalNote, 'id' | 'createdAt'>): string {
  const id = crypto.randomUUID();
  notes.value.unshift({ ...n, id, createdAt: new Date().toISOString() });
  return id;
}

export function updateNote(id: string, patch: Partial<Pick<LocalNote, 'thought'>>): void {
  const n = notes.value.find((x) => x.id === id);
  if (n) Object.assign(n, patch);
}

export function removeNote(id: string): void {
  notes.value = notes.value.filter((n) => n.id !== id);
}
