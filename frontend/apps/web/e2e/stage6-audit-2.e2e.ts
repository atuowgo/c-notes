import { test, type Page } from '@playwright/test';

const SHOT = '../../../docs/ops/stage6-audit/screenshots/';
async function shot(page: Page, name: string) {
  await page.screenshot({ path: `${SHOT}${name}.png`, fullPage: true });
  // eslint-disable-next-line no-console
  console.log(`[shot2] ${name}.png`);
}

// 与 A2 同样的长正文(>20000 字)也触发 offload;A1/A2 用同一正文 → 摘要近乎一致 → B1 语义聚类 + B4 更深入
const LONG_BODY = 'Transformer 模型以自注意力机制为核心,通过查询键值三点积注意力并行捕捉序列长程依赖,摆脱了循环网络的顺序约束,奠定了现代大语言模型的架构基础。'.repeat(600);

async function collect(page: Page, url: string, title: string, content: string) {
  await page.getByRole('button', { name: /收藏链接/ }).click();
  await page.locator('.modal input[type="url"]').fill(url);
  await page.locator('.modal input[type="text"]').fill(title);
  await page.locator('.modal textarea').fill(content);
  await page.locator('.modal .save').click();
  await page.waitForTimeout(1500);
}

test('Stage-6 审计补强:A2详情 / B1语义簇 / B3整理簇 / B4更深入', async ({ page }) => {
  test.setTimeout(480_000);
  const log = (m: string) => console.log(`[audit2] ${m}`);

  await page.goto('/login');
  await page.waitForSelector('input[autocomplete="username"]', { timeout: 30_000 });
  await page.fill('input[autocomplete="username"]', 'demo');
  await page.fill('input[autocomplete="current-password"]', 'demo123');
  await page.getByRole('button', { name: /登录/ }).click();
  await page.waitForURL('/', { timeout: 30_000 });
  await page.waitForSelector('.card[data-clickable="1"]');

  // 收 2 篇同内容长文(语义簇样本A1/A2)— 驱动 B1 聚类 + B4 更深入
  await collect(page, 'https://example.com/cluster-a-1', '语义簇样本A1', LONG_BODY);
  await collect(page, 'https://example.com/cluster-a-2', '语义簇样本A2', LONG_BODY);
  log('已收集 A1/A2 同内容长文,等后台 organize + 聚类');

  // ===== A2 长正文落盘 + 透明 hydrate(之前收的 A2 文章已 done,长正文已 offload)=====
  const a2 = page.locator('.card', { hasText: 'A2长正文落盘审计' }).first();
  if (await a2.count()) {
    await a2.click();
    await page.waitForSelector('.r-body', { timeout: 15_000 });
    await page.waitForTimeout(800);
    await shot(page, '04b-a2-long-article-detail-hydrated');
    log('A2 长文章详情(hydrate)截图完成');
  }

  // ===== B1 语义簇:等 AutoClusterWorker 把 A1/A2 聚成一簇 =====
  await page.goto('/');
  await page.getByRole('button', { name: '知识网' }).click();
  await page.getByRole('button', { name: '语义簇' }).click();
  const autoCard = page.locator('.card.auto-cluster');
  let got = false;
  for (let i = 0; i < 80; i++) {
    if (await autoCard.count()) { got = true; break; }
    await page.waitForTimeout(3000);
  }
  if (got) {
    await autoCard.first().click();
    await page.waitForTimeout(2000);
    await shot(page, '08b-semantic-cluster-detail');
    log('B1 语义簇详情截图完成');
  } else {
    await shot(page, '08b-semantic-clusters-still-empty');
    log('B1 语义簇仍为空');
  }

  // ===== B4 更深入:打开 A1(与 A2 同簇),RecommendList 应含 更深入 =====
  await page.goto('/');
  await page.waitForSelector('.card[data-clickable="1"]');
  const a1 = page.locator('.card', { hasText: '语义簇样本A1' }).first();
  let a1Done = false;
  for (let i = 0; i < 50; i++) {
    if (await a1.count() && (await a1.getAttribute('data-clickable')) === '1') { a1Done = true; break; }
    await page.waitForTimeout(3000);
  }
  if (a1Done) {
    await a1.click();
    await page.waitForSelector('.r-body', { timeout: 15_000 });
    const rec = page.locator('.recommend');
    for (let i = 0; i < 25; i++) {
      if (await rec.count()) break;
      await page.waitForTimeout(2000);
    }
    if (await rec.count()) {
      await shot(page, '05c-recommend-deeper');
      const kinds = await page.locator('.rec-kind').allTextContents();
      log(`B4 recommend kinds=${JSON.stringify(kinds)}`);
    } else {
      await shot(page, '05c-recommend-a1-empty');
      log('B4 A1 RecommendList 为空');
    }
  } else {
    log('B4 A1 未就绪,跳过更深入截图');
  }

  // ===== B3 整理簇(稳健版):深度学习簇 → 整理簇 → manage-bar =====
  await page.goto('/');
  await page.getByRole('button', { name: '知识网' }).click();
  await page.waitForSelector('.card[data-clickable="1"]');
  await page.locator('.card', { hasText: '深度学习' }).first().click();
  await page.waitForSelector('.r-title', { timeout: 15_000 });
  await shot(page, '07b2-cluster-detail-deeplearn');
  try {
    const manageBtn = page.locator('button.ideas-entry', { hasText: '整理簇' });
    await manageBtn.scrollIntoViewIfNeeded();
    await manageBtn.click({ timeout: 10_000 });
    await page.waitForSelector('.manage-bar', { timeout: 20_000 });
    const firstCheck = page.locator('.article-row .sel input').first();
    if (await firstCheck.count()) await firstCheck.check();
    await shot(page, '07d-cluster-manage-merge-split');
    log('B3 整理簇 manage-bar 截图完成');
  } catch (e) {
    log(`B3 整理簇失败:${(e as Error).message}`);
    await shot(page, '07d-cluster-manage-fail-v2');
  }

  await shot(page, '99-audit2-done');
  log('补强审计完成');
});
