import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { createClient, ApiError } from './index';
import type { ChatReply } from '@cnotes/types';

/**
 * P7:api-client 深聊方法。`chat(articleId, {message, sessionId?})` 应 POST 到
 * `/api/articles/{id}/chat`,JSON body 带 message(及可选 sessionId),解析后端 ChatReply。
 * fetch 被打桩,断言 URL / method / body / 解析结果与错误规范化(沿用 request 层)。
 */
describe('CnotesClient.chat', () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
    fetchMock.mockReset();
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function okReply(reply: ChatReply) {
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => reply,
    } as Response);
  }

  it('POSTs message to /api/articles/{id}/chat and parses ChatReply', async () => {
    okReply({ sessionId: 's1', reply: '小火慢炖更入味', sources: ['📄', '🕸'] });
    const client = createClient('');

    const res = await client.chat('a1', { message: '怎么做更入味' });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/articles/a1/chat');
    expect(init.method).toBe('POST');
    expect(init.headers['Content-Type']).toBe('application/json');
    expect(JSON.parse(init.body)).toEqual({ message: '怎么做更入味' });
    expect(res).toEqual({ sessionId: 's1', reply: '小火慢炖更入味', sources: ['📄', '🕸'] });
  });

  it('forwards sessionId for follow-up turns and encodes the article id', async () => {
    okReply({ sessionId: 's1', reply: '继续', sources: [] });
    const client = createClient('');

    await client.chat('a/b', { message: '第二问', sessionId: 's1' });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/articles/a%2Fb/chat');
    expect(JSON.parse(init.body)).toEqual({ message: '第二问', sessionId: 's1' });
  });

  it('throws ApiError on non-ok response', async () => {
    fetchMock.mockResolvedValue({
      ok: false,
      status: 500,
      json: async () => ({ message: 'boom' }),
    } as Response);
    const client = createClient('');

    await expect(client.chat('a1', { message: 'x' })).rejects.toBeInstanceOf(ApiError);
  });
});

/**
 * V3/V4:新增的关联 / 纠偏 / 想法关联 / 创作方法。仅断言 URL、method、body 构造,
 * 与后端解耦(fetch 打桩)。
 */
describe('CnotesClient V3/V4 endpoints', () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
    fetchMock.mockReset();
    fetchMock.mockResolvedValue({ ok: true, status: 200, json: async () => [] } as Response);
  });
  afterEach(() => vi.unstubAllGlobals());

  function lastCall() {
    return fetchMock.mock.calls[fetchMock.mock.calls.length - 1];
  }

  it('listRelated GETs /api/articles/{id}/related', async () => {
    await createClient('').listRelated('a/b');
    const [url, init] = lastCall();
    expect(url).toBe('/api/articles/a%2Fb/related');
    expect(init?.method ?? 'GET').toBe('GET');
  });

  it('moveArticleToCluster POSTs articleId + toClusterId', async () => {
    await createClient('').moveArticleToCluster('c1', 'art1', 'c2');
    const [url, init] = lastCall();
    expect(url).toBe('/api/clusters/c1/move-article');
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body)).toEqual({ articleId: 'art1', toClusterId: 'c2' });
  });

  it('mergeClusters POSTs fromId + toId', async () => {
    await createClient('').mergeClusters('from', 'to');
    const [url, init] = lastCall();
    expect(url).toBe('/api/clusters/merge');
    expect(JSON.parse(init.body)).toEqual({ fromId: 'from', toId: 'to' });
  });

  it('splitCluster POSTs sourceId + name + articleIds', async () => {
    await createClient('').splitCluster('src', '新主题', ['a1', 'a2']);
    const [url, init] = lastCall();
    expect(url).toBe('/api/clusters/split');
    expect(JSON.parse(init.body)).toEqual({ sourceId: 'src', name: '新主题', articleIds: ['a1', 'a2'] });
  });

  it('listClusterSuggestions GETs /api/clusters/suggestions', async () => {
    await createClient('').listClusterSuggestions();
    expect(lastCall()[0]).toBe('/api/clusters/suggestions');
  });

  it('listVectorClusterSuggestions GETs /api/clusters/vector-suggestions', async () => {
    await createClient('').listVectorClusterSuggestions();
    expect(lastCall()[0]).toBe('/api/clusters/vector-suggestions');
  });

  it('acceptClusterSuggestion POSTs name + articleIds', async () => {
    await createClient('').acceptClusterSuggestion('新簇', ['a1']);
    const [url, init] = lastCall();
    expect(url).toBe('/api/clusters/accept-suggestion');
    expect(JSON.parse(init.body)).toEqual({ name: '新簇', articleIds: ['a1'] });
  });

  it('listRelatedNotes GETs /api/notes/{id}/related', async () => {
    await createClient('').listRelatedNotes('n1');
    expect(lastCall()[0]).toBe('/api/notes/n1/related');
  });

  it('compose POSTs noteIds + topic', async () => {
    await createClient('').compose(['n1', 'n2'], '我的主题');
    const [url, init] = lastCall();
    expect(url).toBe('/api/compose');
    expect(JSON.parse(init.body)).toEqual({ noteIds: ['n1', 'n2'], topic: '我的主题' });
  });

  it('chat forwards optional noteId', async () => {
    fetchMock.mockResolvedValue({ ok: true, status: 200, json: async () => ({ sessionId: 's', reply: 'r', sources: [] }) } as Response);
    await createClient('').chat('a1', { message: '基于这条想法', noteId: 'note1' });
    const [, init] = lastCall();
    expect(JSON.parse(init.body)).toEqual({ message: '基于这条想法', noteId: 'note1' });
  });

  it('listTagSuggestions GETs /api/articles/{id}/tag-suggestions', async () => {
    await createClient('').listTagSuggestions('art1');
    expect(lastCall()[0]).toBe('/api/articles/art1/tag-suggestions');
  });

  it('acceptTagSuggestion POSTs to /api/tags/suggestions/{id}/accept', async () => {
    fetchMock.mockResolvedValue({ ok: true, status: 200, json: async () => ({ id: 's1', status: 'accepted' }) } as Response);
    await createClient('').acceptTagSuggestion('s1');
    const [url, init] = lastCall();
    expect(url).toBe('/api/tags/suggestions/s1/accept');
    expect(init.method).toBe('POST');
  });

  it('rejectTagSuggestion POSTs to /api/tags/suggestions/{id}/reject', async () => {
    fetchMock.mockResolvedValue({ ok: true, status: 200, json: async () => ({ id: 's2', status: 'rejected' }) } as Response);
    await createClient('').rejectTagSuggestion('s2');
    const [url, init] = lastCall();
    expect(url).toBe('/api/tags/suggestions/s2/reject');
    expect(init.method).toBe('POST');
  });
});
