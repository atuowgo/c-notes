import { ref } from 'vue';
import type { Note } from '@cnotes/types';

// 「提问」意图:由想法抽屉发起,驱动深聊面板锚定该想法所属文章、带 noteId 开聊
// (后端把这条想法作为第四层来源 💭 我的想法)。ChatPanel 监听并消费后清空。
export interface AskIntent {
  articleId: string;
  noteId: string;
  question: string;
}

export const askIntent = ref<AskIntent | null>(null);

export function askFromNote(note: Note): void {
  const thought = note.thought?.trim();
  const question = thought
    ? `基于我的这条想法继续聊:「${thought}」(划线原文:"${note.quote}")`
    : `基于我划线的这句继续聊:"${note.quote}"`;
  askIntent.value = { articleId: note.articleId, noteId: note.id, question };
}
