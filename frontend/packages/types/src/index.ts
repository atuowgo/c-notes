// 与后端 DTO 对齐的数据契约,作为所有端共享的"单一来源"。
// 对应:server 的 ArticleCardDto / ArticleDetailDto / CollectRequest。

export type ArticleStatus = 'pending' | 'processing' | 'done' | 'failed';

export type SourceType = 'browser' | 'wechat';

/** 鉴权:注册/登录载荷(对齐后端 AuthRequest) */
export interface AuthCredentials {
  username: string;
  password: string;
}

/** 鉴权:注册/登录成功响应(对齐后端 AuthDto:JWT + 用户名) */
export interface AuthToken {
  token: string;
  username: string;
}

/** 用户(当前登录者) */
export interface User {
  id: string;
  username: string;
}

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

/** 正文定位锚点:目前用正文字符偏移 [start, end) */
export interface NoteAnchor {
  start: number;
  end: number;
}

/** 划线 / 想法(批注基础版) */
export interface Note {
  id: string;
  articleId: string;
  /** 关联文章标题(后端 join 带出,用于"全部想法"跨文章展示) */
  articleTitle?: string;
  quote: string;
  thought?: string;
  anchor?: NoteAnchor;
  createTime: string;
}

export interface CreateNoteRequest {
  articleId: string;
  quote: string;
  thought?: string;
  anchor?: NoteAnchor;
}

export interface UpdateNoteRequest {
  thought?: string;
}

/** 知识网:主题簇(由标签长成) */
export interface ClusterCard {
  id: string;
  name: string;
  description?: string;
  articleCount: number;
  hasSummary: boolean;
  summaryUpdatedAt?: string;
}

export interface ClusterDetail {
  id: string;
  name: string;
  description?: string;
  /** 演进式综述(AI 维护) */
  livingSummary?: string;
  summaryUpdatedAt?: string;
  articleCount: number;
  articles: ArticleCard[];
}

/** 簇纠偏:合并(source 簇全部并入 target,删 source) */
export interface MergeClustersRequest {
  sourceId: string;
  targetId: string;
}

/** 簇纠偏:从当前簇拆出指定文章到新建簇 */
export interface SplitClusterRequest {
  articleIds: string[];
  newTag: string;
}

/** 簇纠偏:单篇跨簇移动 */
export interface MoveArticleRequest {
  articleId: string;
  targetTagId: string;
}

/** 语义簇卡片:由 embedding 自动聚类产出(非标签簇,无人工命名) */
export interface AutoClusterCard {
  id: string;
  title?: string;
  memberCount: number;
  summary?: string;
  hasSummary: boolean;
  createTime: string;
}

/** 语义簇详情:簇元信息 + 成员文章卡片 */
export interface AutoClusterDetail {
  id: string;
  title?: string;
  memberCount: number;
  summary?: string;
  createTime: string;
  articles: ArticleCard[];
}

/** 关联推荐关系类型(对齐后端 article_link.link_type) */
export type LinkType = '相关' | '更深入' | '对立' | '互补';

/** 文章关联推荐项(对齐后端 ArticleLinkDto):目标文章卡片 + 关系 + 理由 + 相似度 */
export interface ArticleLink {
  targetArticle: ArticleCard;
  linkType: LinkType;
  reason: string;
  score: number;
}

/** 深聊角色:与后端 chat_message.role 对齐 */
export type ChatRole = 'user' | 'assistant';

/** 深聊一轮请求:用户消息 + 可选续聊会话 id(为空则后端新建会话) */
export interface ChatRequest {
  message: string;
  sessionId?: string;
}

/** 深聊一轮回复:会话 id + 助手回复 + 本轮实际启用的源标签(📄 本文 / 🕸 知识网 / 🌐 联网) */
export interface ChatReply {
  sessionId: string;
  reply: string;
  sources: string[];
}

/** 深聊消息(对齐后端 chat_message:role + 正文 + 源标签) */
export interface ChatMessage {
  role: ChatRole;
  content: string;
  sources?: string[];
}
