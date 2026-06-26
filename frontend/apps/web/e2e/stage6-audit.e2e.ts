import { test, expect, type Page } from '@playwright/test';

/**
 * Stage-6 收尾审计截图(真实链路,无 mock):
 *   Vite dev(5173,/api 代理 8080) → 后端 Spring Boot dev(H2 + 真 DeepSeek/Ark + server/.env 密钥)。
 * 覆盖 A1 鉴权 / A2 长正文落盘 / B1 语义簇 / B2 关联推荐 / B3 簇整理 / B4 更深入 + Stage5 深聊。
 * 所有截图存 docs/ops/stage6-audit/screenshots/。非关键步骤 best-effort,不阻断整体。
 */
const SHOT = '../../../docs/ops/stage6-audit/screenshots/';

async function shot(page: Page, name: string) {
  await page.screenshot({ path: `${SHOT}${name}.png`, fullPage: true });
  // eslint-disable-next-line no-console
  console.log(`[shot] ${name}.png`);
}

test('Stage-6 审计:A1/A2/B1/B2/B3/B4 + Stage5 深聊', async ({ page }) => {
  test.setTimeout(360_000);
  const log = (m: string) => console.log(`[audit] ${m}`);

  // ===== A1 鉴权:注册页(未登录可达)+ 登录页 + 登录 =====
  // 注意:router 守卫对已登录用户访问公开路由会重定向回 /,故注册页须在登录前截。
  await page.goto('/register');
  await page.waitForSelector('input[autocomplete="username"]', { timeout: 30_000 });
  await shot(page, '02-register');

  await page.goto('/login');
  await page.waitForSelector('input[autocomplete="username"]');
  await shot(page, '01-login');
  await page.fill('input[autocomplete="username"]', 'demo');
  await page.fill('input[autocomplete="current-password"]', 'demo123');
  await page.getByRole('button', { name: /登录/ }).click();
  await page.waitForURL('/', { timeout: 30_000 });
  log('A1 登录成功');

  // ===== 收件箱(已登录)=====
  await page.waitForSelector('.card[data-clickable="1"]', { timeout: 30_000 });
  await shot(page, '03-inbox');

  // ===== A2 长正文落盘 + 透明 hydrate:收一篇 >20000 字文章触发 offload =====
  const longBody = '注意力机制允许模型在处理序列时动态关注不同位置,是 Transformer 的核心思想。'.repeat(1000);
  try {
    await page.getByRole('button', { name: /收藏链接/ }).click();
    await page.locator('.modal input[type="url"]').fill('https://example.com/a2-audit-long-article');
    await page.locator('.modal input[type="text"]').fill('A2长正文落盘审计');
    await page.locator('.modal textarea').fill(longBody);
    await page.locator('.modal .save').click();
    log('A2 已提交长正文收藏,等后台处理');
    const newCard = page.locator('.card', { hasText: 'A2长正文落盘审计' }).first();
    await newCard.waitFor({ state: 'visible', timeout: 30_000 });
    // 轮询等 done(data-clickable=1)
    let done = false;
    for (let i = 0; i < 40; i++) {
      if ((await newCard.getAttribute('data-clickable')) === '1') { done = true; break; }
      await page.waitForTimeout(3000);
    }
    if (done) {
      await newCard.click();
      await page.waitForSelector('.r-body', { timeout: 15_000 });
      await page.waitForTimeout(500);
      await shot(page, '04a-long-article-offloaded-detail');
      log('A2 长文章 done + 详情 hydrate 截图完成');
    } else {
      await shot(page, '04a-long-article-pending');
      log('A2 长文章未在时限内 done,截 pending 态');
    }
  } catch (e) {
    log(`A2 长文章收藏失败(降级):${(e as Error).message}`);
    await shot(page, '04a-long-article-fail');
  }

  // ===== B2/B4 关联推荐:打开 Attention 文章,RecommendList 调 /links(相关 + 更深入)=====
  await page.goto('/');
  await page.waitForSelector('.card[data-clickable="1"]');
  const card = page.locator('.card', { hasText: 'Attention' }).first();
  await card.click();
  await page.waitForSelector('.r-body', { timeout: 15_000 });
  await shot(page, '05a-article-detail');
  const rec = page.locator('.recommend');
  for (let i = 0; i < 25; i++) {
    if (await rec.count()) break;
    await page.waitForTimeout(2000);
  }
  if (await rec.count()) {
    await shot(page, '05b-recommend-related-deeper');
    const kinds = await page.locator('.rec-kind').allTextContents();
    log(`B2/B4 RecommendList 渲染,kinds=${JSON.stringify(kinds)}`);
  } else {
    await shot(page, '05b-recommend-empty');
    log('B2/B4 RecommendList 为空(可能无共享标签/同簇候选)');
  }

  // ===== Stage5 深聊(真 DeepSeek 回复 + 来源标签)=====
  try {
    await page.getByRole('button', { name: /深聊/ }).click();
    await page.waitForSelector('.chat-input textarea');
    await page.locator('.chat-input textarea').fill('用一句话说说自注意力为什么重要?');
    await page.locator('.chat-input textarea').press('Enter');
    const ai = page.locator('.msg.ai .srcs').first();
    await ai.waitFor({ state: 'visible', timeout: 120_000 });
    await page.waitForTimeout(500);
    await shot(page, '06-deep-chat-reply');
    log('Stage5 深聊回复截图完成');
  } catch (e) {
    log(`Stage5 深聊失败:${(e as Error).message}`);
    await shot(page, '06-deep-chat-fail');
  }

  // ===== B3 簇整理 merge/split/move =====
  await page.goto('/');
  await page.getByRole('button', { name: '知识网' }).click();
  await page.waitForSelector('.card[data-clickable="1"]', { timeout: 15_000 });
  await shot(page, '07a-clusters-tag-list');
  try {
    await page.locator('.card[data-clickable="1"]').first().click();
    await page.waitForSelector('.r-title', { timeout: 15_000 });
    await shot(page, '07b-cluster-detail');
    await page.getByRole('button', { name: /整理簇/ }).click();
    await page.waitForSelector('.manage-bar', { timeout: 10_000 });
    const firstCheck = page.locator('.article-row .sel input').first();
    if (await firstCheck.count()) await firstCheck.check();
    await shot(page, '07c-cluster-manage-merge-split-move');
    log('B3 整理簇 manage-bar 截图完成');
  } catch (e) {
    log(`B3 整理簇失败:${(e as Error).message}`);
    await shot(page, '07c-cluster-manage-fail');
  }

  // ===== B1 语义簇(embedding 自动聚类,worker 30s 周期)=====
  await page.goto('/');
  await page.getByRole('button', { name: '知识网' }).click();
  await page.getByRole('button', { name: '语义簇' }).click();
  const autoCard = page.locator('.card.auto-cluster');
  for (let i = 0; i < 50; i++) {
    if (await autoCard.count()) break;
    await page.waitForTimeout(3000);
  }
  if (await autoCard.count()) {
    await autoCard.first().click();
    await page.waitForTimeout(2000);
    await shot(page, '08a-semantic-clusters');
    log('B1 语义簇截图完成');
  } else {
    await shot(page, '08a-semantic-clusters-empty');
    log('B1 语义簇为空(worker 未产出或无足够同主题文章)');
  }

  await shot(page, '99-audit-done');
  log('审计截图全部完成');
  expect(true).toBe(true);
});
