# 分享卡片 + 二维码 端到端验证截图

> 日期:2026-06-20 · 分支 `claude/vibrant-dijkstra-5wxegb`
> 真实链路取证:Spring Boot(dev profile / H2)后端 + Vite(`/api` 代理)前端 +
> Playwright(Chromium)驱动真实浏览器。脚本见 `frontend/apps/web/e2e/share-card.e2e.ts`。

每张图都是浏览器实际渲染(2× 高清),分享卡由 HTML Canvas 绘制、二维码由 `qrcode-generator` 实时生成。

| 截图 | 验证点 |
|---|---|
| `01-reader-share-card.png` | 阅读页点「📤 分享」弹出分享卡:琥珀金渐变 header(⚗ 标识 + 知识炼金炉 + 副标语)、大标题、摘要折行、底部标签徽章(深度学习 / LLM 推理优化)、右下角二维码 + 「扫码查看」、分享链接 `/?a=<id>`、关闭 / 复制链接 / 下载图片三按钮 |
| `02-share-card-copy-link.png` | ResNet 文章分享卡(单标签)+ 点「🔗 复制链接」后弹出「链接已复制到剪贴板」toast |

## 断言(非截图)

`share-card.e2e.ts` 两条用例均通过:

1. **Canvas 已绘制内容 + 二维码**:读取 `getImageData` 统计偏离背景色的像素 > 1000,
   证明卡面与二维码已真实绘制(非空白)。
2. **复制链接落剪贴板**:`navigator.clipboard.readText()` 等于卡面展示的分享链接,
   且页面弹出「已复制」toast。

```
2 passed (2.9s)
```

## 复现方式

```bash
# 后端(dev profile,H2 内存库,DevDataSeeder 灌样本文章,worker 关闭免真实 LLM)
cd server && SPRING_DATASOURCE_URL="jdbc:h2:mem:cnotes;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1" \
  SPRING_DATASOURCE_USERNAME=sa SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver \
  WORKER_SCHEDULING_ENABLED=false AUTH_DEV_LOGIN=true DEEPSEEK_API_KEY=dummy ARK_API_KEY=dummy \
  ./gradlew bootRun --args='--spring.profiles.active=dev --worker.scheduling.enabled=false'
# 前端
cd frontend/apps/web && pnpm dev
# 取证
E2E_BASE_URL=http://localhost:5173 pnpm exec playwright test share-card.e2e.ts
```
