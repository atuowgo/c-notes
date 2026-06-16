import type { ArticleStatus, SourceType } from '@cnotes/types';

export const SRC_LABEL: Record<string, string> = { browser: '浏览器', wechat: '公众号' };

export interface StatusMeta {
  cls: 'ok' | 'proc' | 'fail';
  label: string;
}
export const STATUS_META: Record<ArticleStatus, StatusMeta> = {
  done: { cls: 'ok', label: '已就绪' },
  pending: { cls: 'proc', label: '处理中' },
  processing: { cls: 'proc', label: '处理中' },
  failed: { cls: 'fail', label: '失败' },
};

export function srcLabel(sourceType?: SourceType): string {
  return (sourceType && SRC_LABEL[sourceType]) || sourceType || '浏览器';
}

export function relTime(iso?: string): string {
  if (!iso) return '';
  const t = new Date(iso);
  if (isNaN(t.getTime())) return '';
  const diff = (Date.now() - t.getTime()) / 1000;
  if (diff < 60) return '刚刚';
  if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`;
  if (diff < 86400) return `${Math.floor(diff / 3600)} 小时前`;
  if (diff < 86400 * 7) return `${Math.floor(diff / 86400)} 天前`;
  return t.toLocaleDateString('zh-CN');
}
