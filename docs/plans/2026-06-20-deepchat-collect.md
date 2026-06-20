# V4 收尾 —— 深聊「联网发现 → 一键收藏」完成记录

> 日期:2026-06-20
> 状态:**产品设计 §2「联网搜索发现的好内容可一键收进知识网」已实现并端到端实跑验证(含截图)**。

## 做了什么

### 后端(`com.cnotes.chat`)
- 新增 `dto/ChatDiscovery`(标题/链接/摘要),`ChatReply` 增 `discoveries` 字段。
- `WebSearchTool` 重构为**结构化解析 + 三通道逐级降级**(均无 key):
  DuckDuckGo HTML → DuckDuckGo Instant Answer JSON → 维基百科全文检索(按语言选 zh/en)。
  用 `ThreadLocal` 在 `beginCapture()`/`drainCaptured()` 间捕获本轮 LLM 联网发现,按 url 去重、
  截到 6 条。
- `ChatService` 在 ChatClient `.call()` 前后捕获发现,确有联网结果时追加 **🌐** 源标签,
  随 `ChatReply` 返回。

### 前端
- `@cnotes/types` 加 `ChatDiscovery`,`ChatReply.discoveries?`;api-client 透传(无需新方法)。
- `ChatPanel` 在 AI 回复下渲染「🌐 联网发现 · 可一键收藏」卡片组,「＋ 收藏」调既有
  `POST /api/collect` 走正常抓取整理管线,按 url 跟踪「收藏中 → ✓ 已收」。

## 测试与验证(真实链路,非 mock)
- 后端 `./gradlew test`:**95 / 0 / 6 门控**(+4 个 WebSearchTool 解析测试)。
- 前端 `pnpm -r test` **15 passed**;`pnpm -r typecheck` 全绿。
- 浏览器 e2e(穿 nginx→后端→H2→DeepSeek+Ark+联网):**deep-chat / v3v4 / mobile /
  deep-chat-collect 6/6**;截图取证 `capture.e2e.ts` **7/7**,新增 `08-deepchat-collect.png`。
- curl 复验:深聊 `sources:["📄","🕸","🌐"]` + 6 条 `discoveries`;一键收藏后收件箱 +1,
  收藏的「BERT」被 worker 处理为 done。

详见 `docs/ops/e2e-verification-2026-06-20-deepchat-collect.md`。

## 工程方式 / 边界
- 联网搜索通用网页端点在本沙箱被反爬拦截,**维基通道**保证受限环境可验证;生产网络通畅时
  通道1(DuckDuckGo HTML)优先给更广结果。
- 是否联网由模型自行决定;e2e 用强措辞 + 最多 3 次追问稳定触发(工具调用固有非确定性)。

## 产品设计剩余项(更新)
- ✅ 深聊发现 → 一键收藏(本次完成)。
- ✅ 标签建议审批 UI(2026-06-20 前序完成)。
- 待后续:纯向量自动聚簇(K-means/DBSCAN);微信安全模式 AES / 事件消息;无头浏览器实例池化;
  生产 MySQL 全量迁移验证;note 锚点重定位;移动真机构建(需 Android SDK)。
