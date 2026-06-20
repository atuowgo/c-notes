<script setup lang="ts">
import { ref, watch, nextTick } from 'vue';
import qrcode from 'qrcode-generator';
import { toast } from '../toast';

const props = defineProps<{
  open: boolean;
  articleId: string;
  title?: string;
  summary?: string;
  tags?: string[];
  authorNickname?: string;
}>();
const emit = defineEmits<{ close: [] }>();

const canvasEl = ref<HTMLCanvasElement>();

const W = 600, H = 370, DPR = 2;

// Brand tokens (hardcoded to match design-tokens/tokens.css)
const GOLD = '#b45309';
const GOLD2 = '#e0a050';
const BG = '#f6f4f0';
const INK = '#23201c';
const INK_SOFT = '#6b6359';
const INK_FAINT = '#9a9186';
const LINE = '#e7e2d9';

function buildQrCanvas(url: string): HTMLCanvasElement {
  const qr = qrcode(0, 'M');
  qr.addData(url);
  qr.make();
  const mc = qr.getModuleCount();
  const cell = 3;
  const sz = mc * cell;
  const c = document.createElement('canvas');
  c.width = sz; c.height = sz;
  const ctx = c.getContext('2d')!;
  ctx.fillStyle = '#ffffff';
  ctx.fillRect(0, 0, sz, sz);
  ctx.fillStyle = '#000000';
  for (let r = 0; r < mc; r++) {
    for (let col = 0; col < mc; col++) {
      if (qr.isDark(r, col)) ctx.fillRect(col * cell, r * cell, cell, cell);
    }
  }
  return c;
}

function wrapText(
  ctx: CanvasRenderingContext2D,
  text: string,
  x: number,
  y: number,
  maxW: number,
  lineH: number,
  maxLines: number,
): number {
  const chars = Array.from(text);
  let line = '';
  let drawn = 0;
  let cy = y;
  for (let i = 0; i < chars.length; i++) {
    const test = line + chars[i];
    if (ctx.measureText(test).width > maxW && line) {
      if (drawn === maxLines - 1) {
        // Last allowed line — truncate with ellipsis
        while (line.length > 0 && ctx.measureText(line + '…').width > maxW) line = line.slice(0, -1);
        ctx.fillText(line + '…', x, cy);
        return cy;
      }
      ctx.fillText(line, x, cy);
      cy += lineH; drawn++;
      line = chars[i];
    } else {
      line = test;
    }
  }
  ctx.fillText(line, x, cy);
  return cy;
}

function drawCard(url: string) {
  const canvas = canvasEl.value;
  if (!canvas) return;
  canvas.width = W * DPR;
  canvas.height = H * DPR;
  const ctx = canvas.getContext('2d')!;
  ctx.scale(DPR, DPR);

  // Background
  ctx.fillStyle = BG;
  ctx.fillRect(0, 0, W, H);

  // Gold header strip (60px)
  const grad = ctx.createLinearGradient(0, 0, W, 0);
  grad.addColorStop(0, GOLD);
  grad.addColorStop(1, GOLD2);
  ctx.fillStyle = grad;
  ctx.fillRect(0, 0, W, 60);

  // Header: ⚗ logo mark
  ctx.fillStyle = 'rgba(255,255,255,0.25)';
  ctx.beginPath();
  ctx.roundRect(18, 14, 32, 32, 9);
  ctx.fill();
  ctx.font = 'bold 18px -apple-system, system-ui, sans-serif';
  ctx.fillStyle = '#ffffff';
  ctx.textBaseline = 'middle';
  ctx.fillText('⚗', 24, 30);

  // Header: app name
  ctx.font = 'bold 15px -apple-system, system-ui, sans-serif';
  ctx.fillStyle = '#ffffff';
  ctx.textBaseline = 'middle';
  ctx.fillText('知识炼金炉', 60, 30);

  // Header: right-side tagline
  ctx.font = '12px -apple-system, system-ui, sans-serif';
  ctx.fillStyle = 'rgba(255,255,255,0.75)';
  ctx.textAlign = 'right';
  ctx.fillText('知识管理 · 深度阅读 · 知识网络', W - 20, 30);
  ctx.textAlign = 'left';

  // QR code area (bottom-right)
  const qrCanvas = buildQrCanvas(url);
  const qrSz = 100;
  const qrX = W - qrSz - 20;
  const qrY = H - qrSz - 20;
  // QR background white frame
  ctx.fillStyle = '#ffffff';
  ctx.beginPath();
  ctx.roundRect(qrX - 6, qrY - 6, qrSz + 12, qrSz + 12, 6);
  ctx.fill();
  ctx.drawImage(qrCanvas, qrX, qrY, qrSz, qrSz);
  // QR label
  ctx.font = '10px -apple-system, system-ui, sans-serif';
  ctx.fillStyle = INK_FAINT;
  ctx.textAlign = 'center';
  ctx.fillText('扫码查看', qrX + qrSz / 2, qrY + qrSz + 18);
  ctx.textAlign = 'left';

  // Content area - title
  const contentX = 28, contentW = qrX - 20 - contentX;
  ctx.font = 'bold 22px -apple-system, system-ui, sans-serif';
  ctx.fillStyle = INK;
  ctx.textBaseline = 'top';
  const titleEndY = wrapText(ctx, props.title || '(未命名)', contentX, 80, contentW, 30, 2);

  // Summary
  if (props.summary) {
    const summaryY = titleEndY + 38;
    ctx.font = '14px -apple-system, system-ui, sans-serif';
    ctx.fillStyle = INK_SOFT;
    wrapText(ctx, props.summary, contentX, summaryY, contentW, 21, 3);
  }

  // Divider above footer
  ctx.strokeStyle = LINE;
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.moveTo(20, H - 50);
  ctx.lineTo(W - 20, H - 50);
  ctx.stroke();

  // Footer: author
  ctx.font = '12px -apple-system, system-ui, sans-serif';
  ctx.fillStyle = INK_FAINT;
  ctx.textBaseline = 'middle';
  const author = props.authorNickname ? `来自 ${props.authorNickname}` : '';
  ctx.fillText(author, contentX, H - 26);

  // Footer: tags
  if (props.tags && props.tags.length) {
    let tx = contentX + (author ? ctx.measureText(author).width + 14 : 0);
    ctx.font = '11px -apple-system, system-ui, sans-serif';
    for (const tag of props.tags.slice(0, 4)) {
      const tw = ctx.measureText(tag).width;
      if (tx + tw + 16 > qrX - 10) break;
      ctx.fillStyle = '#f2e6d2';
      ctx.beginPath();
      ctx.roundRect(tx, H - 36, tw + 12, 20, 5);
      ctx.fill();
      ctx.fillStyle = GOLD;
      ctx.fillText(tag, tx + 6, H - 26);
      tx += tw + 18;
    }
  }
}

function getShareUrl(): string {
  return `${window.location.origin}/?a=${props.articleId}`;
}

watch(
  () => props.open,
  (v) => {
    if (v) nextTick(() => drawCard(getShareUrl()));
  },
);

async function download() {
  const canvas = canvasEl.value;
  if (!canvas) return;
  const link = document.createElement('a');
  const name = (props.title || 'share').slice(0, 30).replace(/\s+/g, '-');
  link.download = `cnotes-${name}.png`;
  link.href = canvas.toDataURL('image/png');
  link.click();
}

async function copyLink() {
  const url = getShareUrl();
  try {
    await navigator.clipboard.writeText(url);
    toast('链接已复制到剪贴板');
  } catch {
    toast('复制失败,请手动复制: ' + url);
  }
}
</script>

<template>
  <div v-if="open" class="modal" @click.self="emit('close')">
    <div class="modal-box share-card-box">
      <h3>分享这篇文章</h3>
      <div class="canvas-wrap">
        <canvas ref="canvasEl" class="share-canvas" :style="{ width: W + 'px', height: H + 'px' }" />
      </div>
      <p class="share-url">{{ getShareUrl() }}</p>
      <div class="modal-actions">
        <button class="cancel" @click="emit('close')">关闭</button>
        <button class="pa-btn" @click="copyLink">🔗 复制链接</button>
        <button class="save" @click="download">⬇ 下载图片</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.share-card-box { max-width: 660px; width: 100%; }
.canvas-wrap { display: flex; justify-content: center; margin: 16px 0 8px; }
.share-canvas { border-radius: 10px; box-shadow: 0 2px 16px rgba(40,33,22,.12); display: block; }
.share-url { font-size: 12px; color: var(--ink-faint); text-align: center; margin: 4px 0 12px; word-break: break-all; }
</style>
