# Stage-5 深聊（Deep Chat）Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans / subagent-driven-development to implement this plan task-by-task. TDD iron law applies: NO production code without a failing test first.

**Goal:** 在已有 收集→组织→沉淀→批注 之上落地「深聊」——围绕一篇锚定文章，跨 3 路上下文（源1 本文+批注、源2 知识网向量检索、源3 联网搜索）与 DeepSeek 对话，会话与消息持久化。

**Architecture:** 单模块 Spring Boot，模型层直接用 Spring AI `ChatClient`（不自建抽象）。源2 用「火山引擎 Ark 多模态 embedding（2048 维，`/embeddings/multimodal`）」实现一个 Spring AI `EmbeddingModel`，喂给 `SimpleVectorStore`（内存 + 本地 JSON 文件落盘）。源3 用 Spring AI `@Tool` 做联网搜索。会话/消息存 H2(dev)/MySQL(prod)，经 V3 迁移建表。

**Tech Stack:** Java 21、Spring Boot 4.1.0、Spring AI 2.0.0（`spring-ai-starter-model-openai` + vector-store）、MyBatis-Plus 3.5.16、Flyway、H2/MySQL、Jackson 3（`tools.jackson.*`）、JUnit 5 + MockMvc、Vue 3 + Vite、nginx、Playwright。

---

## 关键既定事实（实现时不可偏离）

- **源2 向量**：endpoint `${ARK_EMBEDDING_BASE_URL}/embeddings/multimodal`，model `ep-20260617000458-2mslf`，请求体 `input` 为带 `type` 的数组（纯文本时 `[{"type":"text","text":"..."}]`），响应 `data` 是**单个对象** `{embedding:[...2048...]}`（非 OpenAI 的数组）。纯文本 `/embeddings` 会 400，禁止使用。
- **对话**：DeepSeek OpenAI 兼容，仅 chat，model `deepseek-chat`，沿用 `spring-ai-starter-model-openai` 已配置的 `ChatClient.Builder`。
- **表规范**：id CHAR(32) 物理主键、业务唯一索引、`create_time`+`update_time`（MySQL DEFAULT/ON UPDATE CURRENT_TIMESTAMP；H2 省略 ON UPDATE 靠 `AutoFillHandler`）。JSON 数据列用 **TEXT** 不用 JSON（H2 二次编码教训）。
- **Spring Boot 4 / Jackson 3**：`tools.jackson.databind.ObjectMapper` / `tools.jackson.core.type.TypeReference`，禁止 `com.fasterxml.jackson.*`。
- **禁止提交密钥**：key 只从 env/`server/.env` 读，绝不入库、不入仓。

---

## Task 1: V3 迁移 + ChatSession/ChatMessage 实体与 Mapper

**Files:**
- Create: `server/src/main/resources/db/migration/h2/V3__chat.sql`
- Create: `server/src/main/resources/db/migration/mysql/V3__chat.sql`
- Create: `server/src/main/java/com/cnotes/chat/entity/ChatSession.java`
- Create: `server/src/main/java/com/cnotes/chat/entity/ChatMessage.java`
- Create: `server/src/main/java/com/cnotes/chat/mapper/ChatSessionMapper.java`
- Create: `server/src/main/java/com/cnotes/chat/mapper/ChatMessageMapper.java`
- Test: `server/src/test/java/com/cnotes/chat/ChatPersistenceTest.java`

**Step 1: 写失败测试** — `ChatPersistenceTest`：插入一个 `ChatSession`（articleId 可空）+ 两条 `ChatMessage`（role=user/assistant, content, sources JSON 文本），按 sessionId 查回，断言条数=2、role 正确、`create_time` 自动填充非空。

**Step 2: 运行验证失败** — `cd server && ./gradlew test --tests '*ChatPersistenceTest*'`，预期 FAIL（表/类不存在）。

**Step 3: 最小实现**
- 两份 V3 SQL：
  - `chat_session(id CHAR(32) PK, article_id CHAR(32) NULL, title VARCHAR(255), create_time, update_time, INDEX idx_article(article_id))`
  - `chat_message(id CHAR(32) PK, session_id CHAR(32) NOT NULL, role VARCHAR(16) NOT NULL, content TEXT, sources TEXT, create_time, update_time, INDEX idx_session(session_id))`
  - MySQL 用 `DEFAULT CURRENT_TIMESTAMP` / `ON UPDATE CURRENT_TIMESTAMP`；H2 省略 ON UPDATE（与 V1/V2 同风格）。
- 实体：`@TableName`、`@TableId(type=ASSIGN_UUID)`、`create_time`(INSERT fill)/`update_time`(INSERT_UPDATE fill)，沿用 `Note.java` 风格。
- Mapper 继承 `BaseMapper`。

**Step 4: 运行验证通过** — 同上测试 PASS，且既有测试全绿（`./gradlew test`）。

**Step 5: Commit** — `feat(chat): V3 migration + chat_session/chat_message entities`。

---

## Task 2: 火山引擎 Ark EmbeddingModel（Spring AI 接口）

**Files:**
- Modify: `server/build.gradle`（加 `spring-ai-starter-vector-store` 或 `spring-ai-vector-store` 提供 `SimpleVectorStore`）
- Create: `server/src/main/java/com/cnotes/chat/embedding/ArkEmbeddingModel.java`（implements `org.springframework.ai.embedding.EmbeddingModel`）
- Create: `server/src/main/java/com/cnotes/chat/embedding/ArkEmbeddingProperties.java`（`@ConfigurationProperties("ark.embedding")`）
- Modify: `server/src/main/resources/application*.yml`（`ark.embedding.{base-url,api-key,model,dim}` 绑定 env）
- Test: `server/src/test/java/com/cnotes/chat/embedding/ArkEmbeddingModelTest.java`

**Step 1: 写失败测试** — 单元测试用一个 stub HTTP（或 `@EnabledIfEnvironmentVariable(named="ARK_API_KEY")` 的真实调用门控测试）：`embed("天很蓝，海很深")` 返回长度 2048 的 float[]。离线 CI 用 stub `RestClient`/`MockWebServer` 喂 `{"data":{"embedding":[...]}}`，断言解析正确且 `dimensions()`==2048。

**Step 2: 运行验证失败** — `./gradlew test --tests '*ArkEmbeddingModelTest*'`，预期 FAIL（类不存在）。

**Step 3: 最小实现** — `ArkEmbeddingModel` 用注入的 `RestClient.Builder` POST `/embeddings/multimodal`，body `{"model":..,"input":[{"type":"text","text":text}]}`，解析 `data.embedding`（用 `tools.jackson`）。实现 `call(EmbeddingRequest)`、`embed(...)`、`dimensions()`。`@Component` + `@Primary`（避免与 OpenAI 自动配置的 EmbeddingModel 歧义；并在 yml 关掉 openai 的 embedding 自动配置：`spring.ai.openai.embedding.enabled=false` 若可用）。

**Step 4: 运行验证通过** — stub 测试 PASS；本机带 env 时跑门控真实测试确认 200 + 2048 维。

**Step 5: Commit** — `feat(chat): Ark multimodal EmbeddingModel (2048-dim)`。

---

## Task 3: SimpleVectorStore Bean（本地文件型）+ ClusterIndexer

**Files:**
- Create: `server/src/main/java/com/cnotes/chat/vector/VectorStoreConfig.java`（`@Bean SimpleVectorStore` 用 ArkEmbeddingModel，启动时若本地文件存在则 `load(file)`）
- Create: `server/src/main/java/com/cnotes/chat/vector/ClusterIndexer.java`（把每个 tag 的 `living_summary` 作为一个 `Document`，metadata 带 tagId/tagName，`add` 进库并 `save(file)`）
- Modify: 簇 living_summary 生成处（沉淀/`regenerateCluster`）调用 `ClusterIndexer.index(tagId)`
- Test: `server/src/test/java/com/cnotes/chat/vector/ClusterIndexerTest.java`

**Step 1: 写失败测试** — 给两个带 living_summary 的 tag 建索引，`vectorStore.similaritySearch("查询词")` 返回的 top 文档 metadata 含期望 tagId。embedding 用门控真实 Ark；无 key 时该测试 `@EnabledIfEnvironmentVariable` 跳过（同时保留一个用 stub embedding 的离线断言「Document 数量与 metadata 落库」）。

**Step 2-4:** 失败 → 实现 → 通过。落盘文件路径 `${cnotes.vectorstore.path:./.data/vectorstore.json}`。

**Step 5: Commit** — `feat(chat): SimpleVectorStore (file-backed) + cluster indexer`。

---

## Task 4: 联网搜索 @Tool（源3）

**Files:**
- Create: `server/src/main/java/com/cnotes/chat/tool/WebSearchTool.java`（`@Tool` 方法 `search(String query)`，DuckDuckGo HTML/IA，jsoup 解析，返回 top N 标题+摘要+链接）
- Test: `server/src/test/java/com/cnotes/chat/tool/WebSearchToolTest.java`（门控真实网络；离线断言「空结果优雅降级返回空串不抛」）

**Step 1-5:** TDD。工具失败必须降级（源3 不可用不应使 chat 失败）。Commit `feat(chat): web search @Tool (源3)`。

---

## Task 5: ChatContextAssembler（源1+源2+源3 编排）

**Files:**
- Create: `server/src/main/java/com/cnotes/chat/ChatContextAssembler.java`
- Test: `server/src/test/java/com/cnotes/chat/ChatContextAssemblerTest.java`

**Step 1: 写失败测试** — 给定 articleId（带 content/summary/keyPoints + 2 条 note）与问题，assembler 产出的 system 上下文包含：本文摘要、要点、批注引文；并经向量检索把命中簇的 living_summary 纳入；返回结构体记录「实际启用了哪些源」（用于消息 sources 标签 📄/🕸/🌐）。

**Step 2-4:** 失败 → 实现（源1 查 Article+Note；源2 调 KnowledgeRetriever；源3 作为 tool 交给 ChatClient，不在此预取）→ 通过。

**Step 5: Commit** — `feat(chat): context assembler for 源1/源2/源3`。

---

## Task 6: ChatService + ChatController（API）

**Files:**
- Create: `server/src/main/java/com/cnotes/chat/ChatService.java`
- Create: `server/src/main/java/com/cnotes/chat/ChatController.java`
- Create: `server/src/main/java/com/cnotes/chat/dto/*.java`（ChatRequest{message,sessionId?}, ChatReply{sessionId,reply,sources[]}）
- Test: `server/src/test/java/com/cnotes/chat/ChatApiTest.java`（MockMvc）

**Step 1: 写失败测试** — `POST /api/articles/{id}/chat {message}`：200，响应含 `reply` 非空、`sessionId`，且 H2 落了 1 个 session + 2 条 message（user+assistant）。ChatClient 用测试替身/或门控真实 DeepSeek（CI 用 stub ChatModel bean 返回固定串，断言持久化与 sources 装配；本机门控真实 LLM）。

**Step 2-4:** 失败 → 实现（ensure/创建 session → assemble → `chatClient.prompt().system(ctx).user(msg).tools(webSearchTool).call().content()` → 持久化 user+assistant，sources 写 assistant 行）→ 通过。

**Step 5: Commit** — `feat(chat): ChatService + ChatController endpoint`。

---

## Task 7: 前端接线（types + api-client + ChatPanel.vue）

**Files:**
- Modify: `frontend/packages/types/src/index.ts`（加 ChatRequest/ChatReply/ChatMessage 类型）
- Modify: `frontend/packages/api-client/src/index.ts`（加 `chat(articleId, {message, sessionId?})`）
- Modify: `frontend/apps/web/src/components/ChatPanel.vue`（去 mock，调真实 `chat()`，渲染 reply 与 sources 标签，保留 loading/错误态）
- Test: `frontend/apps/web/src/components/__tests__/ChatPanel.spec.ts`（vitest，mock api-client，断言发问后渲染 reply 与来源标签）

**Step 1-5:** TDD（vitest）。Commit `feat(web): wire ChatPanel to real /chat endpoint`。

---

## Task 8: 基础设施（H2 dev 运行 + 前端 dist + nginx 同源）

**Files:**
- Create: `ops/nginx/cnotes.dev.conf`（root→web dist；`location /api/`→`proxy_pass http://127.0.0.1:8080`；`try_files $uri /index.html`；listen 8088）
- Modify: `docs/ops/local-build-run.md`（如端口/路径有出入则校正）

**步骤：** `brew install nginx`（若缺）→ 构建 web dist → `nginx -p ops/nginx -c ops/nginx/cnotes.dev.conf -t` 校验配置 → 启动 → `curl localhost:8088/`(静态) 与 `curl localhost:8088/api/articles`(反代) 均 200。Commit `chore(ops): nginx same-origin dev config`。

---

## Task 9: 真实端到端（Playwright，穿 nginx→后端→H2→向量库）

**Files:**
- Create: `frontend/apps/web/e2e/deep-chat.e2e.ts`
- Modify: `frontend/apps/web/playwright.config.ts`（baseURL http://localhost:8088）

**流程：** 起 H2 后端(dev) + nginx → 浏览器开同源页 → 收集一篇文章 → 等 worker done + 簇综述入向量库 → 阅读页点「深聊」→ 发问 → 断言界面出现 AI 回复且带 📄/🕸 来源标签；DB 侧断言 chat_message 落库。记录到 progress 文档。Commit `test(e2e): deep-chat browser flow through nginx`。

---

## Task 10: 收尾

- 全量 `./gradlew test` + `pnpm -r test` + e2e 全绿。
- 更新 `docs/plans/2026-06-17-stage5-progress.md` 终态。
- `git commit`（含 `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>` trailer）+ `git push`。

---

## 风险与缓解

- **Ark 限流/套餐**：达到限制则按 /goal 指示等待恢复后自行继续（向量索引与门控测试可暂跳，离线 stub 路径保证 CI 绿）。
- **OpenAI 自动配置 EmbeddingModel 歧义**：Ark 模型 `@Primary` + 关 openai embedding 自动配置。
- **nginx 未安装**：Task 8 先 `brew install nginx`；若装不上，e2e 退而用 Vite preview 同源兜底并在 progress 文档标注偏差。
- **维度污染**：换模型务必删 `.data/vectorstore.json` 重建。
