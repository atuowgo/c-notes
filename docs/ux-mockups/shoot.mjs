import { chromium } from '@playwright/test';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const dir = path.dirname(fileURLToPath(import.meta.url));
const outDir = path.join(dir, 'screens');
const fs = await import('node:fs');
fs.mkdirSync(outDir, { recursive: true });

const browser = await chromium.launch({ args: ['--no-sandbox'] });
const page = await browser.newPage({ viewport: { width: 960, height: 1000 }, deviceScaleFactor: 2 });
await page.goto('file://' + path.join(dir, 'multi-user-plaza.html'));
await page.waitForTimeout(300);

// 整页长图
await page.screenshot({ path: path.join(outDir, '00-all.png'), fullPage: true });

// 逐屏:每个 .frame 单独出图(含其上方 caption)
const captions = await page.locator('.caption').all();
const frames = await page.locator('.frame').all();
for (let i = 0; i < frames.length; i++) {
  const capBox = await captions[i].boundingBox();
  const frBox = await frames[i].boundingBox();
  const clip = {
    x: Math.min(capBox.x, frBox.x) - 8,
    y: capBox.y - 8,
    width: Math.max(capBox.width, frBox.width) + 16,
    height: (frBox.y + frBox.height) - capBox.y + 16,
  };
  await page.screenshot({ path: path.join(outDir, `${String(i + 1).padStart(2, '0')}.png`), clip });
}

await browser.close();
console.log('done -> ' + outDir);
