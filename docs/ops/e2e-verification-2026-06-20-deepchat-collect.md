# V4 端到端验证报告 —— 深聊「联网发现 → 一键收藏」

> 日期:2026-06-20
> 链路:真实浏览器(Playwright chromium)→ nginx(8088 同源)→ 后端 Spring Boot(8080)
> → H2 内存库 + SimpleVectorStore → **DeepSeek(对话 + 工具调用)+ 火山引擎 Ark(2048 维 embedding)
> + 联网搜索(三通道,无 key)**。密钥仅存于 gitignored 的 `server/.env`,**不入仓**。

## 这次补了什么(产品设计 §2 的最后一块功能)

产品设计 §2 明确「联网搜索发现的好内容可一键收进知识网」。此前联网结果只融进 LLM 回复正文,
无法入库。本次打通**结构化捕获 → 一键收藏**全链路:

- **后端**:`WebSearchTool` 在 LLM 工具调用期用 `ThreadLocal` **结构化捕获**每条联网发现
  (标题 / 链接 / 摘要);`ChatService` 在 `.call()` 前后 `beginCapture()` / `drainCaptured()`,
  把发现随 `ChatReply.discoveries` 返回,并在确有联网结果时追加 **🌐** 源标签。
- **联网搜索健壮性(三通道逐级降级,均无需 API key)**:
  1. DuckDuckGo HTML(通用网页,覆盖面最广);
  2. 被反爬拦截/零结果时 → DuckDuckGo Instant Answer JSON API(实体答案);
  3. 仍为空 → **维基百科全文检索 `list=search`**(按问题语言自动选 zh/en,对任意中英文长句
     都能返回真实、可收藏的条目)。单轮累计去重后截到 6 条,避免刷屏。
- **前端**:`ChatPanel` 在 AI 回复下渲染「🌐 联网发现 · 可一键收藏」卡片组,每条「＋ 收藏」
  调用既有 `POST /api/collect` 走正常抓取整理管线,按 url 跟踪状态(收藏中 → ✓ 已收)。

> 说明:本沙箱里 DuckDuckGo 的 HTML / Lite / IA、SearXNG、Bing、Mojeek 等通用搜索端点均被
> 反爬拦截(202 挑战页 / 403 / 429 / JS 门控)。新增的**维基百科全文检索通道**正是为此类受限
> 网络兜底——它对任意查询稳定返回真实链接,使「联网发现 → 一键收藏」在受限环境也能真跑验证。
> 在网络通畅的生产环境,通道1(DuckDuckGo HTML)会优先给出更广的通用网页结果。

## 测试结果(全绿)

| 层 | 命令 | 结果 |
|---|---|---|
| 后端 | `./gradlew test` | **95 通过 / 0 失败 / 6 env 门控**(新增 4 个 WebSearchTool 解析测试:DDG 结构化 / IA JSON / 维基 JSON / 全通道失败优雅降级) |
| 前端单测 | `pnpm -r test` | api-client **15 通过**(含 chat discoveries 透传) |
| 前端类型 | `pnpm -r typecheck` | 全绿 |
| 浏览器 e2e | `playwright test`(deep-chat + v3v4 + mobile + **deep-chat-collect**)| **6/6 通过** |
| 截图取证 | `playwright test e2e/capture.e2e.ts` | **7/7 通过**,8 张截图落地(新增 `08-deepchat-collect.png`) |

## 现场实证(curl,经后端真实链路)

- 深聊带联网:`POST /api/articles/{id}/chat`(「请联网搜索 Transformer…给我可收藏的链接」)→
  `"sources":["📄","🕸","🌐"]`,`discoveries` 返回 **6 条**带真实 `url` 的网页发现。
- 一键收藏落库:e2e 点「＋ 收藏」→ `POST /api/collect` → 收件箱卡片数 **+1**;
  收藏的「BERT」(维基,`sourceType=browser`)随后被 worker 抓取处理为 **done**,
  证明发现的内容真正进入了「收集 → 整理」管线。

## 截图(`docs/ops/e2e-screenshots/08-deepchat-collect.png`)

深聊面板内「🌐 联网发现 · 可一键收藏」卡片组:首条已变「✓ 已收」(绿色),其余为「＋ 收藏」,
底部 toast「已收进知识网,正在抓取整理…」。

## 复现

```bash
# server/.env 填好 DEEPSEEK_API_KEY 与 ARK_API_KEY
make server-dev                                            # H2 + 调度开(后台织综述 + Ark 向量化)
cd frontend && pnpm --filter @cnotes/web build             # 产 dist
nginx -p "$REPO_ROOT/" -c ops/nginx/cnotes.dev.conf        # 同源 8088
cd frontend/apps/web
PLAYWRIGHT_BROWSERS_PATH=/opt/pw-browsers pnpm exec playwright test \
  e2e/deep-chat.e2e.ts e2e/v3v4.e2e.ts e2e/mobile-viewport.e2e.ts e2e/deep-chat-collect.e2e.ts
PLAYWRIGHT_BROWSERS_PATH=/opt/pw-browsers pnpm exec playwright test e2e/capture.e2e.ts
```

## 诚实边界

- **是否联网由模型自行决定**:DeepSeek 偶尔会直接用已有知识作答而不调用工具;e2e 用强措辞 +
  最多 3 次追问保证稳定触发,这是工具调用的固有非确定性,非链路缺陷。
- **通用网页搜索在本沙箱受限**:见上文说明;维基通道保证可验证性,生产环境通道1 给更广结果。
