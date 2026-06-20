import { test, expect } from '@playwright/test';
import path from 'node:path';

/**
 * 二级抓取(模型清洗 DOM 快照)取证:一篇插件首屏正文过薄、真实正文埋在导航/广告/页脚里的页面,
 * 经后端第 2 级「模型清洗快照」后,阅读端呈现的是干净正文(无导航/广告噪声)。
 */
test('二级抓取:模型清洗后的干净正文在阅读端呈现', async ({ page }) => {
  await page.goto('/');
  await page.locator('.card', { hasText: '光合作用的暗反应' }).first().click();
  await expect(page.locator('.r-title')).toBeVisible();

  const body = page.locator('.r-body');
  await expect(body).toContainText('卡尔文循环');     // 真实正文被保留
  await expect(body).not.toContainText('广告');        // 导航/广告噪声被清洗掉
  await expect(body).not.toContainText('版权所有');

  await page.screenshot({
    path: path.resolve(process.cwd(), '../../../docs/ops/e2e-screenshots/11-level2-clean-extract.png'),
    fullPage: true,
  });
});
