import { test, expect } from '@playwright/test';
import path from 'node:path';

/**
 * 纯向量自动聚簇(DBSCAN)端到端取证:知识网页点「🧲 向量聚类」→ 后端对 done 文章内容
 * 跑真实 Ark embedding + DBSCAN → 返回现有标签未覆盖的语义簇 → 前端以建议卡呈现、可一键采纳。
 * 需先收好 3 篇语义相近、且无受控标签覆盖的文章(本验证用 Espresso/Latte/Cappuccino)。
 */
const SHOT_DIR = path.resolve(process.cwd(), '../../../docs/ops/e2e-screenshots');

test('向量聚类:发现现有标签未覆盖的语义簇并截图', async ({ page }) => {
  await page.goto('/');
  await page.locator('.nav-tab', { hasText: '知识网' }).click();
  await page.locator('.suggest-btn.vec').click();

  // 等到出现向量聚类建议卡(后端真实 Ark + DBSCAN 命中)。
  const suggestion = page.locator('.card.suggestion').first();
  await expect(suggestion).toBeVisible({ timeout: 60_000 });
  await expect(suggestion).toContainText('向量聚类');
  // 采纳按钮可见(一键建簇入口)。
  await expect(suggestion.locator('.accept-btn')).toBeVisible();

  await page.screenshot({ path: path.join(SHOT_DIR, '09-vector-cluster.png'), fullPage: true });
});
