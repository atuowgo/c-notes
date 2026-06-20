import { test, expect } from '@playwright/test';
import path from 'node:path';

/**
 * 锚点重定位 / 刷新正文 取证:阅读页新增「🔄 刷新正文」入口 —— 重新抓取正文,正文变化时后端
 * 按 quote 把划线想法的锚点重定位到新偏移(产品设计 §8.4)。本截图展示带高亮的阅读页 + 刷新入口。
 */
test('刷新正文入口 + 划线高亮截图', async ({ page, baseURL }) => {
  // 取一篇 done 文章及其正文,按真实偏移建一条划线想法(让阅读页出现高亮)。
  const arts = await (await page.request.get(`${baseURL}/api/articles`)).json();
  const target = arts.find((a: { status: string; title?: string }) =>
    a.status === 'done' && (a.title || '').includes('Attention'));
  expect(target).toBeTruthy();
  const detail = await (await page.request.get(`${baseURL}/api/articles/${target.id}`)).json();
  const content: string = detail.content || '';
  const quote = '自注意力';
  const start = content.indexOf(quote);
  expect(start).toBeGreaterThanOrEqual(0);
  await page.request.post(`${baseURL}/api/notes`, {
    data: { articleId: target.id, quote, thought: '核心机制', anchor: { start, end: start + quote.length } },
  });

  await page.goto('/');
  await page.locator('.card', { hasText: 'Attention Is All You Need:重读经典' }).click();
  await expect(page.locator('.r-title')).toBeVisible();
  // 刷新入口可见 + 正文里出现高亮(锚点命中)。
  await expect(page.locator('.refresh-btn')).toBeVisible();
  await expect(page.locator('.r-body mark.hl').first()).toBeVisible();

  await page.screenshot({
    path: path.resolve(process.cwd(), '../../../docs/ops/e2e-screenshots/10-anchor-refresh.png'),
    fullPage: true,
  });
});
