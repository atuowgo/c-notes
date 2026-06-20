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
  CollectedCard,
  CollectRequest,
  CollectResponse,
  ComposeReply,
  CreateNoteRequest,
  AppNotification,
  Comment,
  Note,
  PlazaCard,
  PublicAnnotation,
  PublicArticle,
  PublicProfile,
  RelatedArticle,
  RelatedNote,
  ShareLevel,
  TagSuggestion,
  UpdateNoteRequest,
} from '@cnotes/types';

/** 分页列表结果:数据 + 总数(来自 X-Total-Count)。 */
export interface Paged<T> {
  items: T[];
  total: number;
}

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
  /** 多用户阶段 2:更新账号默认分享级别(分享设置) */
  updateShareSettings(defaultShareLevel: ShareLevel): Promise<CurrentUser>;
  /** 多用户阶段 2:逐篇覆盖分享级别(仅本人);传 null 清除覆盖回到账号默认 */
  setArticleShareLevel(id: string, shareLevel: ShareLevel | null): Promise<void>;
  /** 多用户阶段 2:公开文章只读视图(免登录);私有/不存在抛 404 */
  getPublicArticle(id: string): Promise<PublicArticle>;
  /** 多用户阶段 2:收藏 / 取消收藏(轻量阅读列表) */
  bookmark(id: string): Promise<void>;
  unbookmark(id: string): Promise<void>;
  /** 多用户阶段 2:收录到我的知识库 / 取消收录(链接引用 + 个人笔记) */
  collectArticle(id: string, personalNote?: string): Promise<void>;
  uncollectArticle(id: string): Promise<void>;
  /** 多用户阶段 2:我收录的卡片(渲染进收件箱) */
  listCollections(): Promise<CollectedCard[]>;
  /** 多用户阶段 3:广场发现流(sort: 'score' 质量分 | 'recent' 最新),分页 */
  plazaDiscover(params?: { sort?: 'score' | 'recent'; page?: number; size?: number }): Promise<Paged<PlazaCard>>;
  /** 多用户阶段 3:用户公开主页头部 */
  plazaProfile(userId: string): Promise<PublicProfile>;
  /** 多用户阶段 3:某用户的公开文章(主页「已分享文章」),分页 */
  plazaUserArticles(userId: string, params?: { page?: number; size?: number }): Promise<Paged<PlazaCard>>;
  /** 多用户阶段 3/4:关注流——我关注用户的最新公开内容,分页 */
  plazaFollowing(params?: { page?: number; size?: number }): Promise<Paged<PlazaCard>>;
  /** 多用户阶段 4:点赞 / 取消点赞 */
  like(id: string): Promise<void>;
  unlike(id: string): Promise<void>;
  /** 多用户阶段 4:评论(列表 / 发表 / 删除) */
  listComments(articleId: string): Promise<Comment[]>;
  addComment(articleId: string, body: string, parentId?: string): Promise<Comment>;
  deleteComment(id: string): Promise<void>;
  /** 多用户阶段 4:公开批注(列表 / 发表) */
  listAnnotations(articleId: string): Promise<PublicAnnotation[]>;
  addAnnotation(articleId: string, req: { quote: string; thought?: string; anchor?: { start: number; end: number } }): Promise<PublicAnnotation>;
  /** 多用户阶段 4:关注 / 取消关注 */
  follow(userId: string): Promise<void>;
  unfollow(userId: string): Promise<void>;
  /** 多用户阶段 4:通知(列表 / 未读数 / 全标已读) */
  listNotifications(): Promise<AppNotification[]>;
  unreadCount(): Promise<number>;
  markNotificationsRead(): Promise<void>;
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

  // 分页请求:数据来自 body,总数来自 X-Total-Count 头。
  async function requestPaged<T>(path: string): Promise<Paged<T>> {
    const res = await fetch(`${base}${path}`, { credentials: 'include' });
    if (!res.ok) {
      const data = (await res.json().catch(() => null)) as { message?: string } | null;
      throw new ApiError(res.status, data?.message ?? `HTTP ${res.status}`);
    }
    const total = Number(res.headers.get('X-Total-Count') ?? '0');
    const text = await res.text();
    const items = (text ? JSON.parse(text) : []) as T[];
    return { items, total: Number.isFinite(total) ? total : items.length };
  }

  const jsonBody = (method: string, body: unknown): RequestInit => ({
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });

  const qs = (params: Record<string, string | number | undefined>): string => {
    const u = new URLSearchParams();
    for (const [k, v] of Object.entries(params)) if (v !== undefined) u.set(k, String(v));
    const s = u.toString();
    return s ? `?${s}` : '';
  };

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

    updateShareSettings: (defaultShareLevel) =>
      request<CurrentUser>('/api/auth/share-settings', jsonBody('PUT', { defaultShareLevel })),

    setArticleShareLevel: (id, shareLevel) =>
      request<void>(`/api/articles/${encodeURIComponent(id)}/share-level`, jsonBody('PUT', { shareLevel })),

    getPublicArticle: (id) =>
      request<PublicArticle>(`/api/public/articles/${encodeURIComponent(id)}`),

    bookmark: (id) =>
      request<void>(`/api/articles/${encodeURIComponent(id)}/bookmark`, { method: 'POST' }),

    unbookmark: (id) =>
      request<void>(`/api/articles/${encodeURIComponent(id)}/bookmark`, { method: 'DELETE' }),

    collectArticle: (id, personalNote) =>
      request<void>(`/api/articles/${encodeURIComponent(id)}/collect`, jsonBody('POST', { personalNote })),

    uncollectArticle: (id) =>
      request<void>(`/api/articles/${encodeURIComponent(id)}/collect`, { method: 'DELETE' }),

    listCollections: () => request<CollectedCard[]>('/api/collections'),

    plazaDiscover: (params) =>
      requestPaged<PlazaCard>(
        `/api/plaza/discover${qs({ sort: params?.sort, page: params?.page, size: params?.size })}`,
      ),

    plazaProfile: (userId) =>
      request<PublicProfile>(`/api/plaza/users/${encodeURIComponent(userId)}`),

    plazaUserArticles: (userId, params) =>
      requestPaged<PlazaCard>(
        `/api/plaza/users/${encodeURIComponent(userId)}/articles${qs({ page: params?.page, size: params?.size })}`,
      ),

    plazaFollowing: (params) =>
      requestPaged<PlazaCard>(`/api/plaza/following${qs({ page: params?.page, size: params?.size })}`),

    like: (id) => request<void>(`/api/articles/${encodeURIComponent(id)}/like`, { method: 'POST' }),
    unlike: (id) => request<void>(`/api/articles/${encodeURIComponent(id)}/like`, { method: 'DELETE' }),

    listComments: (articleId) => request<Comment[]>(`/api/articles/${encodeURIComponent(articleId)}/comments`),
    addComment: (articleId, body, parentId) =>
      request<Comment>(`/api/articles/${encodeURIComponent(articleId)}/comments`, jsonBody('POST', { body, parentId })),
    deleteComment: (id) => request<void>(`/api/comments/${encodeURIComponent(id)}`, { method: 'DELETE' }),

    listAnnotations: (articleId) =>
      request<PublicAnnotation[]>(`/api/articles/${encodeURIComponent(articleId)}/annotations`),
    addAnnotation: (articleId, req) =>
      request<PublicAnnotation>(`/api/articles/${encodeURIComponent(articleId)}/annotations`, jsonBody('POST', req)),

    follow: (userId) => request<void>(`/api/users/${encodeURIComponent(userId)}/follow`, { method: 'POST' }),
    unfollow: (userId) => request<void>(`/api/users/${encodeURIComponent(userId)}/follow`, { method: 'DELETE' }),

    listNotifications: () => request<AppNotification[]>('/api/notifications'),
    unreadCount: () =>
      request<{ count: number }>('/api/notifications/unread-count').then((r) => r.count),
    markNotificationsRead: () => request<void>('/api/notifications/read', { method: 'POST' }),
  };
}
