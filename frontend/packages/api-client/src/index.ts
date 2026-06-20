import type {
  ArticleCard,
  ArticleDetail,
  AuthProvider,
  ChatReply,
  ChatRequest,
  CurrentUser,
  ClusterCard,
  ClusterDetail,
  ClusterSuggestion,
  CollectRequest,
  CollectResponse,
  ComposeReply,
  CreateNoteRequest,
  Note,
  RelatedArticle,
  RelatedNote,
  TagSuggestion,
  UpdateNoteRequest,
} from '@cnotes/types';

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export interface CnotesClient {
  listInbox(): Promise<ArticleCard[]>;
  getArticle(id: string): Promise<ArticleDetail>;
  /** 刷新正文:重新抓取,正文变化则后端重定位划线锚点;返回最新详情 */
  refreshArticle(id: string): Promise<ArticleDetail>;
  collect(req: CollectRequest): Promise<CollectResponse>;
  /** 划线想法:不传参取全部;articleId 取本文;q 跨文章检索 quote/thought */
  listNotes(params?: { articleId?: string; q?: string }): Promise<Note[]>;
  createNote(req: CreateNoteRequest): Promise<Note>;
  updateNote(id: string, req: UpdateNoteRequest): Promise<Note>;
  deleteNote(id: string): Promise<void>;
  /** 文章关联:「顺着这篇继续探索」——后端 LLM/标签近邻给出「为什么相关」 */
  listRelated(articleId: string): Promise<RelatedArticle[]>;
  /** 知识网:主题簇列表 / 详情 / 重写综述 */
  listClusters(): Promise<ClusterCard[]>;
  getCluster(id: string): Promise<ClusterDetail>;
  regenerateCluster(id: string): Promise<ClusterDetail>;
  /** 知识网纠偏:移动文章到别的簇 / 合并簇 / 拆分出新簇 */
  moveArticleToCluster(clusterId: string, articleId: string, toClusterId: string): Promise<ClusterDetail>;
  mergeClusters(fromId: string, toId: string): Promise<ClusterDetail>;
  splitCluster(sourceId: string, name: string, articleIds: string[]): Promise<ClusterDetail>;
  /** 知识网纠偏:LLM 建议的新主题簇 / 一键接受 */
  listClusterSuggestions(): Promise<ClusterSuggestion[]>;
  /** 知识网:纯向量(DBSCAN)聚类发现的新主题簇 */
  listVectorClusterSuggestions(): Promise<ClusterSuggestion[]>;
  acceptClusterSuggestion(name: string, articleIds: string[]): Promise<ClusterDetail>;
  /** 想法关联:某条想法的「相关想法」 */
  listRelatedNotes(noteId: string): Promise<RelatedNote[]>;
  /** 创作:把若干想法拼装为草稿 */
  compose(noteIds: string[], topic?: string): Promise<ComposeReply>;
  /** 深聊:围绕锚定文章发起/续接一轮对话;sessionId 为空则后端新建会话;noteId 由「提问」带入 */
  chat(articleId: string, req: ChatRequest): Promise<ChatReply>;
  /** 标签建议:列出某文章 pending 的 LLM 标签建议(阅读页「待确认标签」) */
  listTagSuggestions(articleId: string): Promise<TagSuggestion[]>;
  /** 接受标签建议:新建受控标签 + 链接文章 */
  acceptTagSuggestion(id: string): Promise<TagSuggestion>;
  /** 拒绝标签建议 */
  rejectTagSuggestion(id: string): Promise<TagSuggestion>;
  /** 多用户:当前登录用户;未登录返回 null */
  me(): Promise<CurrentUser | null>;
  /** 多用户:取三方授权跳转 URL(前端 window.location 跳转) */
  oauthAuthorizeUrl(provider: AuthProvider): Promise<string>;
  /** 本地/测试登录:按 handle 建会话(生产关闭),便于端到端联调 */
  devLogin(handle: string, nickname?: string): Promise<CurrentUser>;
  /** 退出登录:清会话 cookie */
  logout(): Promise<void>;
}

/**
 * 创建后端 API 客户端。
 * @param baseUrl 后端基地址。web 同源/代理时留空 '';扩展、移动端传绝对地址。
 *
 * 鉴权、重试、错误规范化等都应收敛在这一层 —— 多端共用,改一处全端生效。
 */
export function createClient(baseUrl = ''): CnotesClient {
  const base = baseUrl.replace(/\/$/, '');

  async function request<T>(path: string, init?: RequestInit): Promise<T> {
    // credentials:'include' 让会话 cookie(cnotes_token)随请求发送(同源/跨源皆可)。
    const res = await fetch(`${base}${path}`, { credentials: 'include', ...init });
    if (!res.ok) {
      const data = (await res.json().catch(() => null)) as { message?: string } | null;
      throw new ApiError(res.status, data?.message ?? `HTTP ${res.status}`);
    }
    if (res.status === 204) return undefined as T;
    const text = await res.text();
    return (text ? JSON.parse(text) : undefined) as T;   // /me 未登录返回 200 空体 → null
  }

  const jsonBody = (method: string, body: unknown): RequestInit => ({
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });

  return {
    listInbox: () => request<ArticleCard[]>('/api/articles'),

    getArticle: (id) =>
      request<ArticleDetail>(`/api/articles/${encodeURIComponent(id)}`),

    refreshArticle: (id) =>
      request<ArticleDetail>(`/api/articles/${encodeURIComponent(id)}/refresh`, { method: 'POST' }),

    collect: (req) => request<CollectResponse>('/api/collect', jsonBody('POST', req)),

    listNotes: (params) => {
      const qs = new URLSearchParams();
      if (params?.articleId) qs.set('articleId', params.articleId);
      if (params?.q) qs.set('q', params.q);
      const suffix = qs.toString() ? `?${qs}` : '';
      return request<Note[]>(`/api/notes${suffix}`);
    },

    createNote: (req) => request<Note>('/api/notes', jsonBody('POST', req)),

    updateNote: (id, req) =>
      request<Note>(`/api/notes/${encodeURIComponent(id)}`, jsonBody('PUT', req)),

    deleteNote: (id) =>
      request<void>(`/api/notes/${encodeURIComponent(id)}`, { method: 'DELETE' }),

    listRelated: (id) =>
      request<RelatedArticle[]>(`/api/articles/${encodeURIComponent(id)}/related`),

    listClusters: () => request<ClusterCard[]>('/api/clusters'),

    getCluster: (id) => request<ClusterDetail>(`/api/clusters/${encodeURIComponent(id)}`),

    regenerateCluster: (id) =>
      request<ClusterDetail>(`/api/clusters/${encodeURIComponent(id)}/regenerate`, { method: 'POST' }),

    moveArticleToCluster: (clusterId, articleId, toClusterId) =>
      request<ClusterDetail>(
        `/api/clusters/${encodeURIComponent(clusterId)}/move-article`,
        jsonBody('POST', { articleId, toClusterId }),
      ),

    mergeClusters: (fromId, toId) =>
      request<ClusterDetail>('/api/clusters/merge', jsonBody('POST', { fromId, toId })),

    splitCluster: (sourceId, name, articleIds) =>
      request<ClusterDetail>('/api/clusters/split', jsonBody('POST', { sourceId, name, articleIds })),

    listClusterSuggestions: () => request<ClusterSuggestion[]>('/api/clusters/suggestions'),

    listVectorClusterSuggestions: () =>
      request<ClusterSuggestion[]>('/api/clusters/vector-suggestions'),

    acceptClusterSuggestion: (name, articleIds) =>
      request<ClusterDetail>('/api/clusters/accept-suggestion', jsonBody('POST', { name, articleIds })),

    listRelatedNotes: (id) =>
      request<RelatedNote[]>(`/api/notes/${encodeURIComponent(id)}/related`),

    compose: (noteIds, topic) =>
      request<ComposeReply>('/api/compose', jsonBody('POST', { noteIds, topic })),

    chat: (id, req) =>
      request<ChatReply>(`/api/articles/${encodeURIComponent(id)}/chat`, jsonBody('POST', req)),

    listTagSuggestions: (articleId) =>
      request<TagSuggestion[]>(`/api/articles/${encodeURIComponent(articleId)}/tag-suggestions`),

    acceptTagSuggestion: (id) =>
      request<TagSuggestion>(`/api/tags/suggestions/${encodeURIComponent(id)}/accept`, { method: 'POST' }),

    rejectTagSuggestion: (id) =>
      request<TagSuggestion>(`/api/tags/suggestions/${encodeURIComponent(id)}/reject`, { method: 'POST' }),

    me: () => request<CurrentUser | null>('/api/auth/me').then((u) => u ?? null),

    oauthAuthorizeUrl: (provider) =>
      request<{ authorizeUrl: string }>(`/api/auth/login/${encodeURIComponent(provider)}`)
        .then((r) => r.authorizeUrl),

    devLogin: (handle, nickname) =>
      request<CurrentUser>('/api/auth/dev-login', jsonBody('POST', { handle, nickname: nickname ?? handle })),

    logout: () => request<void>('/api/auth/logout', { method: 'POST' }),
  };
}
