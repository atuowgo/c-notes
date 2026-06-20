import type { ShareLevel } from '@cnotes/types';

/** 分享级别元数据(单调递增)。label 给设置项,short 给阅读页下拉,desc 给说明。 */
export interface ShareLevelMeta {
  value: ShareLevel;
  label: string;
  short: string;
  desc: string;
}

export const SHARE_LEVELS: ShareLevelMeta[] = [
  { value: 'PRIVATE', label: '私有(不进广场)', short: '私有', desc: '仅自己可见' },
  { value: 'READ_ONLY', label: '仅只读', short: '只读', desc: '他人可看全文与摘要' },
  { value: 'BOOKMARKABLE', label: '允许收藏', short: '可收藏', desc: '他人可加入阅读列表' },
  { value: 'COLLECTABLE', label: '允许收录', short: '可收录', desc: '他人可收录进自己的知识库' },
  { value: 'ANNOTATABLE', label: '允许公开批注', short: '可批注', desc: '他人可在原文发表公开批注' },
  { value: 'COMMENTABLE', label: '允许评论', short: '可评论', desc: '他人可在文末评论' },
];

const ORDER: ShareLevel[] = SHARE_LEVELS.map((l) => l.value);

/** 级别单调比较:a 是否满足(>=)所需 b。 */
export function levelAtLeast(a: ShareLevel | undefined, b: ShareLevel): boolean {
  if (!a) return false;
  return ORDER.indexOf(a) >= ORDER.indexOf(b);
}

export function shareLabel(level: ShareLevel | undefined | null): string {
  return SHARE_LEVELS.find((l) => l.value === level)?.short ?? '私有';
}
