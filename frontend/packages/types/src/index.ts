// 与后端 DTO 对齐的数据契约,作为所有端共享的"单一来源"。
// 对应:server 的 ArticleCardDto / ArticleDetailDto / CollectRequest。

export type ArticleStatus = 'pending' | 'processing' | 'done' | 'failed';

export type SourceType = 'browser' | 'wechat';

/** 收件箱卡片(不含正文) */
export interface ArticleCard {
  id: string;
  title?: string;
  author?: string;
  sourceType: SourceType;
  summary?: string;
  status: ArticleStatus;
  createTime: string;
  /** 已归类的标签名;后端读 article_tag 带出,可能为空数组 */
  tags?: string[];
}

/** 文章详情(卡片 + 正文 + 要点) */
export interface ArticleDetail extends ArticleCard {
  /** 原文链接(用于"看原文") */
  url?: string;
  content?: string;
  keyPoints: string[];
}

/** 收集提交载荷(浏览器插件 / 手动收藏均用此) */
export interface CollectRequest {
  url: string;
  title?: string | null;
  author?: string | null;
  /** 端内本地提取的正文(插件 Readability) */
  content?: string | null;
  /** 渲染后 DOM 快照,正文提取不佳时的服务端兜底 */
  domSnapshot?: string | null;
  sourceType?: SourceType;
}

export interface CollectResponse {
  id: string;
}
