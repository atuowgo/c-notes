import { test, expect } from '@playwright/test';

/**
 * 深聊「联网发现 → 一键收藏」端到端(真实链路,无 mock):
 *   浏览器 → nginx(8088 同源) → 后端(8080) → H2 + 向量库 → DeepSeek 对话
 *   → DeepSeek 自行调用 WebSearchTool(源3 🌐,真打 DuckDuckGo)→ 后端结构化捕获发现
 *   → 前端渲染发现卡 → 点「＋ 收藏」→ POST /api/collect → 新卡片进收件箱。
 *
 * 对应产品设计 §2:「联网搜索发现的好内容可一键收进知识网」。
 *
 * 断言:
 *   - 显式要求联网检索后,AI 回复下方出现「🌐 联网发现」结构化结果(带可点链接);
 *   - 点首条「＋ 收藏」→ 按钮变「✓ 已收」(收藏成功的乐观反馈);
 *   - 回到收件箱,收件箱卡片数较收藏前 +1(真实落库 → 列表可见)。
 */
test('深聊:联网发现可一键收藏进收件箱(🌐 → /api/collect → 新卡片)', async ({ page, baseURL }) => {
  // 收藏前收件箱卡片数(经后端真实读取,排除前端首屏时序)。
  const before = await (await page.request.get(`${baseURL}/api/articles`)).json();
  const beforeCount = Array.isArray(before) ? before.length : 0;

  await page.goto('/');
  const card = page.locator('.card', { hasText: 'Attention Is All You Need:重读经典' });
  await expect(card).toBeVisible();
  await card.click();

  const fab = page.getByRole('button', { name: /深聊/ });
  await expect(fab).toBeVisible();
  await fab.click();

  const box = page.locator('.chat-input textarea');
  await expect(box).toBeVisible();

  // 是否调用联网工具由模型自行决定,偶尔会直接用已有知识作答。为稳健起见最多追问 3 次,
  // 每次都强措辞要求「必须实际联网检索并给链接」,直到出现「🌐 联网发现」结构化卡。
  const discoveries = page.locator('.msg.ai .discoveries').first();
  const prompts = [
    '请使用联网搜索工具,实际联网检索 Transformer 神经网络模型的权威资料,并给我几个可以收藏的网页链接(必须真正联网,不要只用你已有的知识)。',
    '请务必调用你的联网搜索能力,查 Transformer 模型的相关网页,并列出可收藏的链接。',
    '联网搜一下 large language model,把找到的网页链接列出来给我收藏。',
  ];
  let shown = false;
  for (const p of prompts) {
    await box.fill(p);
    await box.press('Enter');
    try {
      await expect(discoveries).toBeVisible({ timeout: 110_000 });
      shown = true;
      break;
    } catch {
      // 这一轮模型没联网,换个更强措辞再试。
    }
  }
  expect(shown, '深聊应至少一轮调用联网搜索并回出 🌐 发现卡').toBe(true);

  const firstCollect = discoveries.locator('.disc .disc-collect').first();
  await expect(firstCollect).toBeVisible();
  await expect(firstCollect).toContainText('收藏');
  await firstCollect.click();

  // 收藏成功 → 按钮变「✓ 已收」。
  await expect(firstCollect).toContainText('已收', { timeout: 30_000 });

  // 真实落库:收件箱卡片数 +1。
  await expect
    .poll(
      async () => {
        const after = await (await page.request.get(`${baseURL}/api/articles`)).json();
        return Array.isArray(after) ? after.length : 0;
      },
      { timeout: 30_000 },
    )
    .toBeGreaterThan(beforeCount);
});
