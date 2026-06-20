import { test, expect } from '@playwright/test';

/**
 * V3/V4 端到端(真实链路,无 mock):浏览器 → nginx(8088) → 后端(8080) → H2 → DeepSeek。
 * 覆盖:文章关联推荐、知识网纠偏(移动文章)、想法的相关/创作/提问。
 * 前置同 deep-chat:后端 dev 已起 + dist 已构建 + nginx 已起。
 */

test('关联:阅读页「顺着这篇继续探索」展示后端关联(为什么相关)', async ({ page }) => {
  await page.goto('/');
  const card = page.locator('.card', { hasText: 'Attention Is All You Need:重读经典' });
  await expect(card).toBeVisible();
  await card.click();

  await expect(page.locator('.r-title')).toBeVisible();
  // 关联由后端 /related 驱动(LLM 选「为什么相关」,或同标签近邻兜底)。
  const rec = page.locator('.recommend');
  await expect(rec).toBeVisible();
  await expect(rec.locator('.rec-item').first()).toBeVisible();
  // 关系类型徽标(同概念/互补/对立/延伸/相关)非空。
  const kind = (await rec.locator('.rec-kind').first().innerText()).trim();
  expect(kind.length).toBeGreaterThan(0);
});

test('纠偏:知识网簇详情可把文章移动到其他簇', async ({ page }) => {
  await page.goto('/');
  await page.locator('.nav-tab', { hasText: '知识网' }).click();

  const clusterCard = page.locator('.card[data-clickable="1"]').first();
  await expect(clusterCard).toBeVisible();
  await clusterCard.click();

  // 簇详情:本簇文章 + 每篇的「移动到其他簇」下拉。
  await expect(page.locator('.r-title')).toBeVisible();
  const moveSelect = page.locator('[data-testid="move-target"]').first();
  await expect(moveSelect).toBeVisible();

  // 选第一个真实目标簇(index 1,0 是占位)。
  const optionValues = await moveSelect.locator('option').evaluateAll(
    (opts) => (opts as HTMLOptionElement[]).map((o) => o.value).filter((v) => v),
  );
  expect(optionValues.length).toBeGreaterThan(0);
  await moveSelect.selectOption(optionValues[0]);

  // 移动成功 toast。
  await expect(page.locator('.toast.show')).toContainText('移动');
});

test('想法:相关/创作/提问 三个钩子真实可用', async ({ page, baseURL }) => {
  // 预置两条互相呼应的想法(经 nginx 同源 /api 落库)。
  const articles = await (await page.request.get(`${baseURL}/api/articles`)).json();
  const done = articles.filter((a: { status: string }) => a.status === 'done');
  expect(done.length).toBeGreaterThanOrEqual(2);
  await page.request.post(`${baseURL}/api/notes`, {
    data: { articleId: done[0].id, quote: '自注意力机制', thought: '并行度是关键优势,和残差都是结构创新' },
  });
  await page.request.post(`${baseURL}/api/notes`, {
    data: { articleId: done[1].id, quote: '残差连接', thought: '让深层网络可训练,也是结构创新' },
  });

  await page.goto('/');
  // 顶栏「全部想法」入口。
  await page.locator('.icon-btn[title="全部想法"]').click();
  const drawer = page.locator('.drawer');
  await expect(drawer).toBeVisible();
  const firstNote = drawer.locator('.note-item').first();
  await expect(firstNote).toBeVisible();

  // 🔗 相关:展开相关想法(有命中或「暂无」,但区块必现)。
  await firstNote.getByRole('button', { name: /相关/ }).click();
  await expect(firstNote.locator('.note-rel')).toBeVisible();

  // ✍ 创作:打开创作弹窗 → 生成草稿(真实 DeepSeek)。
  await firstNote.getByRole('button', { name: /创作/ }).click();
  const compose = page.locator('.compose-box');
  await expect(compose).toBeVisible();
  await compose.getByRole('button', { name: /生成草稿/ }).click();
  await expect(compose.locator('.distill .summary')).not.toHaveText('', { timeout: 90_000 });
  // 关闭创作弹窗,回到仍打开的想法抽屉。
  await compose.getByRole('button', { name: '关闭' }).click();
  await expect(compose).toBeHidden();

  // 💬 提问:从想法发起深聊 → 抽屉关闭、聊天面板出现真实回复。
  await firstNote.getByRole('button', { name: /提问/ }).click();
  await expect(page.locator('.chat')).toBeVisible();
  const ai = page.locator('.msg.ai').last();
  await expect(ai).not.toHaveText('', { timeout: 90_000 });
});
