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

/** 深聊角色:与后端 chat_message.role 对齐 */
export type ChatRole = 'user' | 'assistant';

/** 深聊一轮请求:用户消息 + 可选续聊会话 id(为空则后端新建会话) + 可选锚定想法(由「提问」发起,作为第四层来源 💭 我的想法) */
export interface ChatRequest {
  message: string;
  sessionId?: string;
  noteId?: string;
}

/** 文章关联:一条「为什么相关」的连线(同概念 / 互补 / 对立 / 延伸) */
export interface RelatedArticle {
  article: ArticleCard;
  relationType: string;
  reason: string;
}

/** 知识网纠偏:LLM 建议的新主题簇(尚未落库,用户可一键接受) */
export interface ClusterSuggestion {
  name: string;
  reason: string;
  articles: ArticleCard[];
}

/** 想法关联:一条想法↔想法的连线(呼应 / 对立 / 延伸 / 同主题) */
export interface RelatedNote {
  note: Note;
  relationType: string;
  reason: string;
}

/** 创作:由若干想法拼装出的草稿 */
export interface ComposeReply {
  draft: string;
}

/** 联网搜索发现的一条网页结果(深聊源 🌐),可一键收藏进知识网 */
export interface ChatDiscovery {
  title: string;
  url: string;
  snippet: string;
}

/** 深聊一轮回复:会话 id + 助手回复 + 本轮实际启用的源标签(📄 本文 / 🕸 知识网 / 🌐 联网)+ 联网发现(可一键收藏) */
export interface ChatReply {
  sessionId: string;
  reply: string;
  sources: string[];
  discoveries?: ChatDiscovery[];
}

/** 深聊消息(对齐后端 chat_message:role + 正文 + 源标签) */
export interface ChatMessage {
  role: ChatRole;
  content: string;
  sources?: string[];
}

/** 标签建议(LLM 提议的受控集外标签,用户可接受→新建标签 or 拒绝) */
export interface TagSuggestion {
  id: string;
  articleId: string;
  name: string;
  confidence?: number;
  status: 'pending' | 'accepted' | 'rejected';
  createTime: string;
}

/** 当前登录用户(多用户阶段 1);未登录时 me() 返回 null */
export interface CurrentUser {
  id: string;
  email: string | null;
  nickname: string | null;
  avatarUrl: string | null;
  defaultShareLevel: string;
}

/** 三方登录渠道 */
export type AuthProvider = 'github' | 'google' | 'wechat';
