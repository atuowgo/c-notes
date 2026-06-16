import { ref } from 'vue';
import type { Note, NoteAnchor } from '@cnotes/types';
import { api } from './api';

// 划线记想法(批注基础版)—— 已接后端 /api/notes,成为跨端可检索的真数据。
// 这里维护一份响应式缓存:进入应用时拉全量,增删改本地乐观更新 + 服务端持久化。
export const notes = ref<Note[]>([]);
export const notesLoaded = ref(false);

export async function loadNotes(): Promise<void> {
  try {
    notes.value = await api.listNotes();
    notesLoaded.value = true;
  } catch {
    /* 后端不可用时保持空,不阻塞阅读 */
  }
}

export function notesForArticle(articleId: string): Note[] {
  return notes.value.filter((n) => n.articleId === articleId);
}

export async function addNote(input: {
  articleId: string;
  quote: string;
  thought: string;
  anchor: NoteAnchor;
}): Promise<Note | null> {
  try {
    const created = await api.createNote(input);
    notes.value.unshift(created);
    return created;
  } catch {
    return null;
  }
}

export async function updateNoteThought(id: string, thought: string): Promise<void> {
  try {
    const updated = await api.updateNote(id, { thought });
    const i = notes.value.findIndex((n) => n.id === id);
    if (i >= 0) notes.value[i] = updated;
  } catch {
    /* 忽略 */
  }
}

export async function removeNote(id: string): Promise<void> {
  try {
    await api.deleteNote(id);
    notes.value = notes.value.filter((n) => n.id !== id);
  } catch {
    /* 忽略 */
  }
}
