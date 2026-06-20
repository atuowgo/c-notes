import { test, expect } from '@playwright/test';
import path from 'node:path';

/**
 * 端到端「取证」脚本:驱动真实浏览器走遍 V3/V4 关键页,逐页落地截图到
 * docs/ops/e2e-screenshots/。本次带 ARK_API_KEY,深聊应出现 📄 + 🕸 两源。
 * 与功能断言互补:既证明可用,又留下可归档的页面证据。
 */

const SHOT_DIR = path.resolve(process.cwd(), '../../../docs/ops/e2e-screenshots');
const shot = (name: string) => path.join(SHOT_DIR, name);

test('截图:收件箱', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('.card').first()).toBeVisible();
  await page.screenshot({ path: shot('01-inbox.png'), fullPage: true });
});

test('截图:阅读页 + 关联推荐(为什么相关)', async ({ page }) => {
  await page.goto('/');
  await page.locator('.card', { hasText: 'Attention Is All You Need:重读经典' }).click();
  await expect(page.locator('.r-title')).toBeVisible();
  await expect(page.locator('.recommend .rec-item').first()).toBeVisible();
  await page.locator('.recommend').scrollIntoViewIfNeeded();
  await page.screenshot({ path: shot('02-reader-relations.png'), fullPage: true });
});

test('截图:深聊三源(📄 本文 + 🕸 知识网,Ark 向量库命中)', async ({ page }) => {
  await page.goto('/');
  // 锚定一篇「深度学习」簇里的文章,确保 🕸 命中。
  await page.locator('.card', { hasText: 'Attention Is All You Need:重读经典' }).click();
  await page.getByRole('button', { name: /深聊/ }).click();
  const box = page.locator('.chat-input textarea');
  await box.fill('这篇和深度学习的整体脉络有什么关系?');
  await box.press('Enter');
  const srcs = page.locator('.msg.ai .srcs').first();
  await expect(srcs).toBeVisible({ timeout: 90_000 });
  // 等到知识网源 🕸 出现(Ark 向量检索命中)。
  await expect(srcs).toContainText('🕸', { timeout: 90_000 });
  expect((await srcs.innerText())).toContain('📄');
  await page.screenshot({ path: shot('03-deepchat-sources.png'), fullPage: true });
});

test('截图:深聊联网发现 → 一键收藏(🌐 发现卡 + 收藏按钮)', async ({ page }) => {
  await page.goto('/');
  await page.locator('.card', { hasText: 'Attention Is All You Need:重读经典' }).click();
  await page.getByRole('button', { name: /深聊/ }).click();
  const box = page.locator('.chat-input textarea');
  const discoveries = page.locator('.msg.ai .discoveries').first();
  // 是否联网由模型决定,最多追问 3 次直到出现 🌐 发现卡。
  const prompts = [
    '请使用联网搜索工具,实际联网检索 Transformer 神经网络模型的权威资料,并给我几个可以收藏的网页链接(必须真正联网,不要只用你已有的知识)。',
    '请务必调用你的联网搜索能力,查 Transformer 模型的相关网页,并列出可收藏的链接。',
    '联网搜一下 large language model,把找到的网页链接列出来给我收藏。',
  ];
  for (const p of prompts) {
    await box.fill(p);
    await box.press('Enter');
    try {
      await expect(discoveries).toBeVisible({ timeout: 110_000 });
      break;
    } catch { /* 换更强措辞重试 */ }
  }
  await expect(discoveries).toBeVisible();
  // 收藏首条,留下「✓ 已收」证据后再截图。
  const firstCollect = discoveries.locator('.disc .disc-collect').first();
  await firstCollect.click();
  await expect(firstCollect).toContainText('已收', { timeout: 30_000 });
  await page.screenshot({ path: shot('08-deepchat-collect.png'), fullPage: true });
});

test('截图:知识网主题簇列表', async ({ page }) => {
  await page.goto('/');
  await page.locator('.nav-tab', { hasText: '知识网' }).click();
  await expect(page.locator('.card[data-clickable="1"]').first()).toBeVisible();
  await page.screenshot({ path: shot('04-clusters.png'), fullPage: true });
});

test('截图:簇详情 + 纠偏(综述 / 移动 / 合并)', async ({ page }) => {
  await page.goto('/');
  await page.locator('.nav-tab', { hasText: '知识网' }).click();
  // 打开 >=2 篇、已织综述的簇,展示演进式综述 + 纠偏控件。
  await page.locator('.card[data-clickable="1"]', { hasText: '深度学习' }).click();
  await expect(page.locator('.r-title')).toBeVisible();
  await expect(page.locator('[data-testid="move-target"]').first()).toBeVisible();
  await page.screenshot({ path: shot('05-cluster-correction.png'), fullPage: true });
});

test('截图:想法抽屉(相关想法)+ 创作草稿', async ({ page, baseURL }) => {
  // 预置两条互相呼应的想法。
  const arts = await (await page.request.get(`${baseURL}/api/articles`)).json();
  const done = arts.filter((a: { status: string }) => a.status === 'done');
  await page.request.post(`${baseURL}/api/notes`, {
    data: { articleId: done[0].id, quote: '自注意力机制', thought: '并行度是关键优势,和残差都是结构创新' },
  });
  await page.request.post(`${baseURL}/api/notes`, {
    data: { articleId: done[1].id, quote: '残差连接', thought: '让深层网络可训练,也是结构创新' },
  });

  await page.goto('/');
  await page.locator('.icon-btn[title="全部想法"]').click();
  const firstNote = page.locator('.drawer .note-item').first();
  await firstNote.getByRole('button', { name: /相关/ }).click();
  await expect(firstNote.locator('.note-rel')).toBeVisible();
  await page.screenshot({ path: shot('06-ideas-related.png') });

  await firstNote.getByRole('button', { name: /创作/ }).click();
  const compose = page.locator('.compose-box');
  await expect(compose).toBeVisible();
  await compose.getByRole('button', { name: /生成草稿/ }).click();
  await expect(compose.locator('.distill .summary')).not.toHaveText('', { timeout: 90_000 });
  await page.screenshot({ path: shot('07-compose-draft.png'), fullPage: true });
});
