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
