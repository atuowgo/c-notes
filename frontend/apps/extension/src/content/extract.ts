import { Readability } from '@mozilla/readability';
import TurndownService from 'turndown';
import type { CollectRequest } from '@cnotes/types';

// 一级抓取(计划 §5.4):读浏览器里渲染后的 DOM,本地 Readability 提取正文,
// 转 Markdown;同时带出 Readability 干净 HTML 供沉浸式渲染。
function extract(): CollectRequest {
  const docClone = document.cloneNode(true) as Document;
  const parsed = new Readability(docClone).parse();

  const turndown = new TurndownService({ headingStyle: 'atx', codeBlockStyle: 'fenced' });
  const content = parsed?.content ? turndown.turndown(parsed.content) : '';

  return {
    url: location.href,
    title: parsed?.title || document.title || null,
    author: parsed?.byline || null,
    content: content || null,
    contentHtml: parsed?.content || null,
    sourceType: 'browser',
  };
}

chrome.runtime.onMessage.addListener(
  (msg: ExtractRequest, _sender, sendResponse: (r: ExtractResponse) => void) => {
    if (msg?.type !== 'EXTRACT') return;
    try {
      sendResponse({ ok: true, payload: extract() });
    } catch (e) {
      sendResponse({ ok: false, error: (e as Error).message });
    }
    return true; // 保持消息通道开放以异步响应
  },
);
