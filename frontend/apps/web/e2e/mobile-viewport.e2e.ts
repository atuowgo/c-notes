import { test, expect, devices } from '@playwright/test';

/**
 * 移动端包壳前置验证:Capacitor(apps/mobile)直接包同一份 web dist。这里在手机视口下
 * 验证该 dist 的关键流在小屏可用(顶栏、卡片、打开阅读、深聊 FAB)。真机/模拟器构建需
 * Android SDK(见 docs/ops/mobile-capacitor.md),不在本环境内运行。
 */
test.use({ ...devices['Pixel 7'] });

test('移动视口:Web 阅读端在手机屏可正常渲染与阅读', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('.topbar')).toBeVisible();

  const card = page.locator('.card[data-clickable="1"]').first();
  await expect(card).toBeVisible();
  await card.click();

  await expect(page.locator('.r-title')).toBeVisible();
  // 深聊 FAB 在小屏仍可见可点。
  await expect(page.getByRole('button', { name: /深聊/ })).toBeVisible();
});
