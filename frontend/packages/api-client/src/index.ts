import type {
  ArticleCard,
  ArticleDetail,
  ArticleLink,
  AuthCredentials,
  AuthToken,
  AutoClusterCard,
  AutoClusterDetail,
  ChatReply,
  ChatRequest,
  ClusterCard,
  ClusterDetail,
  CollectRequest,
  CollectResponse,
  CreateNoteRequest,
  MergeClustersRequest,
  MoveArticleRequest,
  Note,
  SplitClusterRequest,
  UpdateNoteRequest,
} from '@cnotes/types';

const TOKEN_KEY = 'cnotes_token';

/** 取 localStorage 中的 JWT;无(隐私模式/SSR/未登录)返回 null。 */
export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

/** 存/清 JWT;传 null 清除(登出、401 失效)。 */
export function setToken(token: string | null): void {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
  } catch {
    /* ignore */
  }
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
  register(req: AuthCredentials): Promise<AuthToken>;
  login(req: AuthCredentials): Promise<AuthToken>;
  listInbox(): Promise<ArticleCard[]>;
  getArticle(id: string): Promise<ArticleDetail>;
  collect(req: CollectRequest): Promise<CollectResponse>;
  /** 划线想法:不传参取全部;articleId 取本文;q 跨文章检索 quote/thought */
  listNotes(params?: { articleId?: string; q?: string }): Promise<Note[]>;
  createNote(req: CreateNoteRequest): Promise<Note>;
  updateNote(id: string, req: UpdateNoteRequest): Promise<Note>;
  deleteNote(id: string): Promise<void>;
  /** 知识网:主题簇列表 / 详情 / 重写综述 */
  listClusters(): Promise<ClusterCard[]>;
  getCluster(id: string): Promise<ClusterDetail>;
  regenerateCluster(id: string): Promise<ClusterDetail>;
  /** 簇纠偏:合并 / 拆分 / 单篇移动 */
  mergeClusters(req: MergeClustersRequest): Promise<ClusterDetail>;
  splitCluster(id: string, req: SplitClusterRequest): Promise<ClusterDetail>;
  moveArticleToCluster(id: string, req: MoveArticleRequest): Promise<ClusterDetail>;
  /** 语义簇(embedding 自动聚类):列表 / 详情(含成员文章) */
  listAutoClusters(): Promise<AutoClusterCard[]>;
  getAutoCluster(id: string): Promise<AutoClusterDetail>;
  /** 文章关联推荐:后端按共享标签 + embedding cosine 算 top-N,返回含理由 */
  listArticleLinks(articleId: string): Promise<ArticleLink[]>;
  /** 深聊:围绕锚定文章发起/续接一轮对话;sessionId 为空则后端新建会话 */
  chat(articleId: string, req: ChatRequest): Promise<ChatReply>;
}

/**
 * 401(未登录/令牌失效)时的默认处理:清 token 并跳登录页。
 * 非浏览器环境(扩展后台/SSR)无 window,仅由 request() 抛 ApiError,不导航。
 */
function defaultOnUnauthorized(): void {
  if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
    window.location.href = '/login';
  }
}

/**
 * 创建后端 API 客户端。
 * @param baseUrl 后端基地址。web 同源/代理时留空 '';扩展、移动端传绝对地址。
 * @param onUnauthorized 401 回调;默认清 token + 跳 /login。多端可注入各自导航。
 *
 * 鉴权(每请求带 Bearer)、401 跳登录、错误规范化都收敛在这一层 —— 多端共用,改一处全端生效。
 */
export function createClient(baseUrl = '', onUnauthorized: () => void = defaultOnUnauthorized): CnotesClient {
  const base = baseUrl.replace(/\/$/, '');

  async function request<T>(path: string, init?: RequestInit): Promise<T> {
    const headers: Record<string, string> = {
      ...(init?.headers as Record<string, string> | undefined),
    };
    const token = getToken();
    if (token) headers['Authorization'] = `Bearer ${token}`;
    const res = await fetch(`${base}${path}`, { ...init, headers });
    if (res.status === 401) {
      setToken(null);
      onUnauthorized();
      throw new ApiError(401, '未登录或令牌已失效');
    }
    if (!res.ok) {
      const data = (await res.json().catch(() => null)) as { message?: string } | null;
      throw new ApiError(res.status, data?.message ?? `HTTP ${res.status}`);
    }
    if (res.status === 204) return undefined as T;
    return res.json() as Promise<T>;
  }

  const jsonBody = (method: string, body: unknown): RequestInit => ({
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });

  return {
    register: (req) => request<AuthToken>('/api/auth/register', jsonBody('POST', req)),

    login: (req) => request<AuthToken>('/api/auth/login', jsonBody('POST', req)),

    listInbox: () => request<ArticleCard[]>('/api/articles'),

    getArticle: (id) =>
      request<ArticleDetail>(`/api/articles/${encodeURIComponent(id)}`),

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

    listClusters: () => request<ClusterCard[]>('/api/clusters'),

    getCluster: (id) => request<ClusterDetail>(`/api/clusters/${encodeURIComponent(id)}`),

    regenerateCluster: (id) =>
      request<ClusterDetail>(`/api/clusters/${encodeURIComponent(id)}/regenerate`, { method: 'POST' }),

    mergeClusters: (req) =>
      request<ClusterDetail>('/api/clusters/merge', jsonBody('POST', req)),

    splitCluster: (id, req) =>
      request<ClusterDetail>(`/api/clusters/${encodeURIComponent(id)}/split`, jsonBody('POST', req)),

    moveArticleToCluster: (id, req) =>
      request<ClusterDetail>(`/api/clusters/${encodeURIComponent(id)}/move`, jsonBody('POST', req)),

    listAutoClusters: () => request<AutoClusterCard[]>('/api/clusters/auto'),

    getAutoCluster: (id) =>
      request<AutoClusterDetail>(`/api/clusters/auto/${encodeURIComponent(id)}`),

    listArticleLinks: (id) =>
      request<ArticleLink[]>(`/api/articles/${encodeURIComponent(id)}/links`),

    chat: (id, req) =>
      request<ChatReply>(`/api/articles/${encodeURIComponent(id)}/chat`, jsonBody('POST', req)),
  };
}
