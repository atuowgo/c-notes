import type {
  ArticleCard,
  ArticleDetail,
  CollectRequest,
  CollectResponse,
  CreateNoteRequest,
  Note,
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
  collect(req: CollectRequest): Promise<CollectResponse>;
  /** 划线想法:不传参取全部;articleId 取本文;q 跨文章检索 quote/thought */
  listNotes(params?: { articleId?: string; q?: string }): Promise<Note[]>;
  createNote(req: CreateNoteRequest): Promise<Note>;
  updateNote(id: string, req: UpdateNoteRequest): Promise<Note>;
  deleteNote(id: string): Promise<void>;
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
    const res = await fetch(`${base}${path}`, init);
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
  };
}
