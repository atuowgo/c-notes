# Stage-5 深聊 — 进度 / 审计跟踪

> 每完成一个 phase 在此追加记录（时间、做了什么、验证证据、git ref），供续作 / 审计。

## 既定环境事实（live-verified, 2026-06-17）

- DeepSeek chat：200 OK，model `deepseek-chat`（OpenAI 兼容，仅对话）。
- 火山引擎 Ark embedding：纯文本 `/embeddings` → **400**（视觉模型 `doubao-embedding-vision-251215` 不支持该 api）；`/embeddings/multimodal` → **200**，`data.embedding` 长度 **2048**。接入点 `ep-20260617000458-2mslf`。
- `server/.env` 已建并被 `.gitignore` 命中（不入仓）：`DEEPSEEK_API_KEY/LLM_MODEL/ARK_API_KEY/ARK_EMBEDDING_BASE_URL/ARK_EMBEDDING_MODEL/ARK_EMBEDDING_DIM`。
- nginx：尚未安装（Task 8 处理）。

## Phase 状态总览

| Phase | 名称 | 状态 | git ref |
|---|---|---|---|
| P0 | 环境核对 + 三份文档 | ✅ 文档完成 | 0f9e660 |
| P1 | V3 迁移 + 实体/Mapper | ✅ 完成 | 4971c7e |
| P2 | Ark EmbeddingModel | ✅ 完成 | 0f9e660 |
| P3 | SimpleVectorStore + ClusterIndexer | ✅ 完成 | aad07c5 |
| P4 | WebSearch @Tool（源3） | ✅ 完成 | (见下方记录) |
| P5 | ChatContextAssembler（源1/2/3） | ⬜ | |
| P6 | ChatService + ChatController | ⬜ | |
| P7 | 前端接线 ChatPanel | ⬜ | |
| P8 | nginx 同源基础设施 | ⬜ | |
| P9 | Playwright 真实 e2e | ⬜ | |
| P10 | 收尾 commit + push | ⬜ | |

## 记录

### 2026-06-17 — P0 环境核对 + 文档
- 核对既有 API 契约（api-client/types/Note/ArticleOrganizer/build.gradle/ArticleController/TagClassifier/ArticleApiTest）。
- live 验证 DeepSeek/Ark 连通性与维度（见上）。
- 建 `server/.env`（gitignored）。
- 产出三份文档：`docs/ops/local-build-run.md`、`docs/plans/2026-06-17-stage5-deep-chat-implementation.md`、本文件。
- 下一步：按计划逐 Task TDD 实施，先 P1（V3 迁移）。

### 2026-06-17 — P1 V3 迁移 + ChatSession/ChatMessage 实体与 Mapper
- TDD：先写 `ChatPersistenceTest`（插 1 session + 2 message，断言条数=2、role 正确、sources JSON、create_time 自动填充），编译失败（类不存在）→ 实现 → PASS。
- 新建 H2/MySQL `V3__chat.sql`（chat_session: id PK + article_id 可空 + idx_article；chat_message: id PK + session_id NOT NULL + role + content/sources TEXT + idx_session；MySQL ON UPDATE CURRENT_TIMESTAMP，H2 省略）。
- 实体沿用 `Note.java` 风格（ASSIGN_UUID + create_time INSERT / update_time INSERT_UPDATE fill），Mapper 继承 BaseMapper（已被 `@MapperScan("com.cnotes.**.mapper")` 覆盖）。
- 验证：`./gradlew test --tests '*ChatPersistenceTest*'` PASS；全量 `./gradlew test` BUILD SUCCESSFUL（17 个测试类全绿，含 SchemaMigrationTest 不受影响）。

### 2026-06-17 — P2 ArkEmbeddingModel（火山引擎多模态向量）
- **续作背景**：原 workflow 在 P2 因「agent 180s 无进度看门狗」连续 6 次判死被杀（实为首次冷启动 gradle 下依赖/冷 daemon 耗时 >180s，非代码问题）。改在主循环直跑——gradle 不受看门狗约束。保留 workflow 失败 agent 留下的 P2 半成品并补全 yml 接线后验证。
- **实现**：`ArkEmbeddingModel implements org.springframework.ai.embedding.EmbeddingModel`（`@Component @Primary`），POST `{base-url}/embeddings/multimodal`，请求 `input:[{"type":"text","text":..}]`，解析响应 `data.embedding` 单对象 → 2048 维 `float[]`；用 Jackson 3 `tools.jackson.*`。配置类 `ArkEmbeddingProperties(@ConfigurationProperties("ark.embedding"))`，已在 `CNotesApplication` 用 `@EnableConfigurationProperties` 注册。
- **接线**：`application.yml` 加 `spring.ai.model.embedding: none`（关 openai embedding 自动配置，DeepSeek 不提供 embedding）+ 顶层 `ark.embedding.{base-url,api-key,model,dim}`（密钥仅来自 env）。dev profile 继承之。
- **测试**：`ArkEmbeddingModelTest` —— 离线用 Spring `MockRestServiceServer` 绑 `RestClient.Builder`，断言命中 `/embeddings/multimodal`、请求体 type=text、解析 `data.embedding` 单对象为 2048 维；`call()` 多输入各回一条；**`@EnabledIfEnvironmentVariable(ARK_API_KEY)` 的 `realArkReturns2048Dim()` 真打火山引擎活端点**。
- **验证证据**：带 `server/.env` 跑 `./gradlew test --tests '*ArkEmbeddingModelTest'` → 3 用例 0 跳过 0 失败，`realArkReturns2048Dim()` 耗时 1.704s（真实网络往返 200 + 2048 维）；全量 `./gradlew test` BUILD SUCCESSFUL（18 个测试类 / 46 用例 / 0 失败 0 错误 / 2 env 门控跳过），证明 `@Primary` 组件未破坏任何上下文加载。

### 2026-06-17 — P3 SimpleVectorStore（本地文件型）+ ClusterIndexer + 接线
- **依赖**：`build.gradle` 加 `org.springframework.ai:spring-ai-vector-store`（BOM 管版本）——经全 spring-ai 缓存 jar grep 确认 `SimpleVectorStore`/`VectorStore` 不在原 classpath（只有 commons 的 observation 约定类），故须显式引入。
- **TDD（向量库）**：先写 `ClusterIndexerTest`（编译失败 RED：`找不到符号 ClusterIndexer`）→ 实现 `VectorStoreConfig`（`@Bean SimpleVectorStore`，注入 `@Primary` ArkEmbeddingModel，启动时若磁盘有快照则 `load`，构造期不触网）+ `ClusterIndexer`（以 tagId 为 Document id「先删后加」覆盖、metadata 带 tagId/tagName、`save` 落盘 JSON）→ GREEN。离线 3 例用 8 维 hash `StubEmbeddingModel` + `@TempDir` 断言「落库 + metadata + 落盘 + 去重不重复」；**第 4 例 `@EnabledIfEnvironmentVariable(ARK_API_KEY)` 真打火山引擎**：索引「烹饪/航天」两簇，`similaritySearch("红烧牛肉怎么做更入味").topK(1)` 命中烹饪簇 → 真实语义召回打通（4 用例 0 跳过 0 失败）。
- **TDD（接线）**：`ClusterServiceTest` 加 `@MockitoBean ClusterIndexer` + `verify(clusterIndexer).index(tagId)`（RED：Wanted but not invoked）→ 在 `ClusterService.regenerate()` 综述落库后 `if (summarized) clusterIndexer.index(tagId)`（构造器注入）→ GREEN；`ClusterApiTest` 同加 `@MockitoBean ClusterIndexer` 保持离线（真实走 `/regenerate` 端点不触 Ark）。
- **验证证据**：`./gradlew test --tests '*ClusterIndexerTest*'` 4/0/0；接线 `*ClusterServiceTest*`+`*ClusterApiTest*` BUILD SUCCESSFUL；全量 `./gradlew test` **BUILD SUCCESSFUL（19 个测试类 / 50 用例 / 0 失败 0 错误 / 2 env 门控跳过）**。向量库落盘路径 `cnotes.vectorstore.path`(默认 `./.data/vectorstore.json`)。
- 下一步：P4 WebSearchTool `@Tool`（源3，DuckDuckGo+jsoup，优雅降级）。

### 2026-06-17 — P4 WebSearchTool @Tool（源3 联网搜索，优雅降级）
- **实现**：`WebSearchTool`（`@Component`）以 Spring AI `org.springframework.ai.tool.annotation.@Tool` 方法 `search(@ToolParam query)` 暴露给 ChatClient——本文(源1)+知识网(源2)不足时模型自行决定调用。走 DuckDuckGo HTML 版端点 `chat.websearch.base-url`(默认 `https://html.duckduckgo.com/html/`，无需 API key)，jsoup 解析 top N(`chat.websearch.top-n` 默认 3)条「标题/摘要/真实链接」；DuckDuckGo 的 `//duckduckgo.com/l/?uddg=` 跳转包装被解码回真实 URL。**优雅降级**：网络/超时/解析/空结果一律返回空串、绝不抛——源3 不可用不致整轮 chat 失败。
- **TDD**：先写 `WebSearchToolTest`（编译失败 RED：`找不到符号 WebSearchTool`）→ 实现 → GREEN。离线 3 例:`parseResults` 固定样本断言「标题/摘要 + uddg 解码为真实链接 + topN=2 截断 + 不泄露 uddg 包装」、空/异常 HTML 降级空串、不可达 host 时 `search()` 返回空串不抛;**第 4 例 `@EnabledIfEnvironmentVariable(WEBSEARCH_LIVE=1)` 真打 DuckDuckGo**。
- **验证证据**：`WEBSEARCH_LIVE=1 ./gradlew test --tests '*WebSearchToolTest*' --rerun-tasks` → `tests="4" skipped="0" failures="0" errors="0"`（**含真实网络往返**，源3 真打 DuckDuckGo 返回非空）；全量 `./gradlew test` BUILD SUCCESSFUL（20 个测试类 / 54 用例 / 0 失败 0 错误 / 3 env 门控跳过）。
- 下一步：P5 ChatContextAssembler（源1 本文+批注 / 源2 知识网向量检索 / 源3 作为 tool 交给 ChatClient）。
