import { test, expect } from '@playwright/test';
import path from 'node:path';

/**
 * 端到端「取证」:分享卡片 + 二维码(ShareCardModal)。
 * 真实链路:浏览器 → vite(/api 代理)→ Spring Boot(dev profile, H2)。
 * 匿名态当前用户回退系统用户,可见 DevDataSeeder 灌入的 done 文章,
 * 在阅读页点「📤 分享」弹出 Canvas 渲染的分享卡(含 qrcode-generator 真实生成的二维码)。
 */

const SHOT_DIR = path.resolve(process.cwd(), '../../../docs/evidence/share-card');
const shot = (name: string) => path.join(SHOT_DIR, name);

test('截图:阅读页分享卡片(Canvas 渲染 + 二维码)', async ({ page }) => {
  await page.goto('/');
  // 打开已就绪的样本文章(有摘要 + 标签,卡面信息完整)。
  await page.locator('.card', { hasText: 'Attention Is All You Need:重读经典' }).click();
  await expect(page.locator('.r-title')).toBeVisible();

  // 顶栏「📤 分享」→ 弹出分享卡模态。
  await page.getByRole('button', { name: '📤 分享' }).click();
  const modal = page.locator('.share-card-box');
  await expect(modal).toBeVisible();

  // Canvas 已绘制(宽度 600,二维码已嵌入);等一帧确保绘制完成。
  const canvas = modal.locator('canvas.share-canvas');
  await expect(canvas).toBeVisible();
  await page.waitForTimeout(300);

  // 断言 Canvas 非空白:取像素数据,存在非背景色像素即说明已绘制内容/二维码。
  const hasContent = await canvas.evaluate((el) => {
    const c = el as HTMLCanvasElement;
    const ctx = c.getContext('2d')!;
    const { data } = ctx.getImageData(0, 0, c.width, c.height);
    let nonBg = 0;
    for (let i = 0; i < data.length; i += 4) {
      // 背景 #f6f4f0 ≈ (246,244,240);统计明显偏离背景的像素。
      if (Math.abs(data[i] - 246) > 20 || Math.abs(data[i + 1] - 244) > 20 || Math.abs(data[i + 2] - 240) > 20) nonBg++;
    }
    return nonBg;
  });
  expect(hasContent).toBeGreaterThan(1000);

  await page.screenshot({ path: shot('01-reader-share-card.png'), fullPage: true });
});

test('截图:分享卡片复制链接(剪贴板写入 → toast)', async ({ page, context }) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  await page.goto('/');
  await page.locator('.card', { hasText: '深度残差网络 ResNet' }).click();
  await expect(page.locator('.r-title')).toBeVisible();

  await page.getByRole('button', { name: '📤 分享' }).click();
  const modal = page.locator('.share-card-box');
  await expect(modal).toBeVisible();

  // 分享链接形如 origin/?a=<id>,且复制后落入剪贴板。
  const urlText = (await modal.locator('.share-url').innerText()).trim();
  expect(urlText).toContain('/?a=');

  await modal.getByRole('button', { name: /复制链接/ }).click();
  await expect(page.locator('.toast.show')).toContainText('已复制');

  const clip = await page.evaluate(() => navigator.clipboard.readText());
  expect(clip).toBe(urlText);

  await page.screenshot({ path: shot('02-share-card-copy-link.png') });
});
