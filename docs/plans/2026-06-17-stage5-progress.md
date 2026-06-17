# Stage-5 深聊 — 进度 / 审计跟踪

> 每完成一个 phase 在此追加记录（时间、做了什么、验证证据、git ref），供续作 / 审计。

## 既定环境事实（live-verified, 2026-06-17）

- DeepSeek chat：200 OK，model `deepseek-chat`（OpenAI 兼容，仅对话）。
- 火山引擎 Ark embedding：纯文本 `/embeddings` → **400**（视觉模型 `doubao-embedding-vision-251215` 不支持该 api）；`/embeddings/multimodal` → **200**，`data.embedding` 长度 **2048**。接入点 `ep-20260617000458-2mslf`。
- `server/.env` 已建并被 `.gitignore` 命中（不入仓）：`DEEPSEEK_API_KEY/LLM_MODEL/ARK_API_KEY/ARK_EMBEDDING_BASE_URL/ARK_EMBEDDING_MODEL/ARK_EMBEDDING_DIM`。
- nginx：已装（brew nginx 1.31.1），仓库内自带可移植配置 `ops/nginx/cnotes.dev.conf`，监听 8088（避开后端 8080）。

## Phase 状态总览

| Phase | 名称 | 状态 | git ref |
|---|---|---|---|
| P0 | 环境核对 + 三份文档 | ✅ 文档完成 | 0f9e660 |
| P1 | V3 迁移 + 实体/Mapper | ✅ 完成 | 4971c7e |
| P2 | Ark EmbeddingModel | ✅ 完成 | 0f9e660 |
| P3 | SimpleVectorStore + ClusterIndexer | ✅ 完成 | aad07c5 |
| P4 | WebSearch @Tool（源3） | ✅ 完成 | cfd910c |
| P5 | ChatContextAssembler（源1/2/3） | ✅ 完成 | e69eacd |
| P6 | ChatService + ChatController | ✅ 完成 | 7c38911 |
| P7 | 前端接线 ChatPanel | ✅ 完成 | 2fc3c11 |
| P8 | nginx 同源基础设施 | ✅ 完成 | 736ebb0 |
| P9 | Playwright 真实 e2e | ✅ 完成 | be16ac7 |
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

### 2026-06-17 — P5 KnowledgeRetriever（源2 读路径）+ ChatContextAssembler（源1/2/3 编排）
- **KnowledgeRetriever（源2 读)**：`@Component`,在 `SimpleVectorStore`(由 ClusterIndexer 写入簇综述)上 `similaritySearch(topK)`,把命中 Document → `Hit(tagId/tagName/summary/score)`(metadata + 正文 + 分)。优雅降级:空查询/空库/异常 → 空列表。
  - TDD:`KnowledgeRetrieverTest`(RED 类不存在)→ 实现 → GREEN。离线复用 `ClusterIndexerTest.StubEmbeddingModel`(同文本→同向量,确定性)断言「检索映射 tagId/tagName/summary + 空库降级 + 空/null 查询降级」;**门控 `ARK_API_KEY` 真打火山引擎**:`retrieve("红烧牛肉怎么做更入味")` 命中 cook 簇。`--rerun-tasks` 4/0/0(0 跳过,含真实语义召回)。
- **ChatContextAssembler（编排)**：`@Component`,`assemble(articleId, question)` → `ChatContext(systemText, sources)`。源1 📄:文章标题/摘要/要点(JSON 反序列化)+ 该文批注引文/想法;源2 🕸:`knowledgeRetriever.retrieve` 命中簇综述织入;源3 🌐 由 ChatClient 经 WebSearchTool 自行调用,**不在此预取**。sources 记录实际启用的源标签。
  - TDD:`ChatContextAssemblerTest`(`@SpringBootTest @Transactional`,H2 播种文章+2 批注,`@MockitoBean KnowledgeRetriever` 隔离向量检索)(RED)→ 实现 → GREEN。3 例:源1+源2 织入且 sources 含 📄/🕸、缺文章且无知识网命中 → sources 空、有文章无知识网 → 仅 📄。
- **验证证据**：全量 `./gradlew test` BUILD SUCCESSFUL（22 个测试类 / 61 用例 / 0 失败 0 错误 / 3 env 门控跳过）。
- 下一步：P6 ChatService + ChatController（POST `/api/articles/{id}/chat`,ChatClient 注入 WebSearchTool,持久化 session/message,sources 标签随回复返回）。

### 2026-06-17 — P6 ChatService + ChatController（深聊 API 端点）
- **实现**：`POST /api/articles/{id}/chat`（`ChatController`）→ `ChatService.chat(articleId, ChatRequest)`：① `ensureSession` 按 `sessionId` 续聊或新建会话（title 取首条消息前 30 字）；② `ChatContextAssembler.assemble` 织源1/源2 上下文；③ `chatClient.prompt().system(ctx).user(msg).tools(webSearchTool).call()`——源3 由模型按需调 WebSearchTool；④ 落库 user/assistant 两条 `ChatMessage`（sources JSON 写在 assistant 行，用 Jackson 3 `tools.jackson` ObjectMapper bean）；⑤ 返回 `ChatReply(sessionId, reply, sources)`。DTO：`ChatRequest(message, sessionId?)`、`ChatReply(sessionId, reply, sources)`。ChatClient 由 Spring AI 自动配置指向 DeepSeek（不自建抽象层）。
- **TDD**：先写 `ChatApiTest`（RED：POST 404，断言失败于 line 85/117）→ 建 DTO/Service/Controller → GREEN。用 stub `ChatModel`（`@Bean @Primary` 返回固定串）保证 CI 确定性、`@MockitoBean KnowledgeRetriever` 隔离 Ark 网络。2 例：① 持久化（1 session + 2 message，user content / assistant 含「小火慢炖」/ assistant.sources 含 📄🕸）+ 响应 `$.sources` 含 📄🕸；② 传 `sessionId` 续聊复用同会话（4 message / 该文 1 session）。本机真实 LLM 由 P9 门控覆盖。
- **验证证据**：全量 `./gradlew test` **BUILD SUCCESSFUL（23 个测试类 / 63 用例 / 0 失败 0 错误 / 6 env 门控跳过）**。
- 下一步：P7 前端接线 ChatPanel（types ChatRequest/ChatReply/ChatMessage、api-client `chat(articleId,{message,sessionId?})`、ChatPanel.vue 去 mock、vitest spec）。

### 2026-06-17 — P7 前端接线 ChatPanel（深聊接真后端）
- **共享契约（types）**：`packages/types` 加 `ChatRole/ChatRequest(message,sessionId?)/ChatReply(sessionId,reply,sources)/ChatMessage(role,content,sources?)`，与后端 DTO 一一对齐,作为多端单一来源。
- **api-client**：`CnotesClient` 加 `chat(articleId, {message, sessionId?})` → `POST /api/articles/{id}/chat`（沿用 `request`/`jsonBody` 层做错误规范化为 `ApiError`、article id 经 `encodeURIComponent`）。
- **TDD（vitest 首次引入前端）**：`packages/api-client` 加 `vitest` devDep + `test: vitest run`。先写 `src/index.test.ts`（打桩 global `fetch`）RED（`client.chat is not a function`，3 例失败）→ 实现 `chat()` → GREEN。3 例:首轮 POST 到 `/api/articles/a1/chat`、body 仅 `{message}`、解析 ChatReply;续聊带 `sessionId` 且 article id `a/b`→`a%2Fb`;非 2xx → 抛 `ApiError`。
- **web 去 mock**：`ChatPanel.vue` 删除 `setTimeout` 假回复 → 真调 `api.chat(articleId,{message,sessionId})`,跨轮用 ref 跟踪后端回传 `sessionId` 续接上下文、渲染后端真实 `sources` 标签、`sending` 守卫禁重入、切换文章重置会话;无 `articleId` 时提示先开文章。错误经 `ApiError` 友好提示。`App.vue` 传 `:article-id="openId"`。底部提示由「原型示意 · V4」改为三层来源说明。
- **验证证据**：`pnpm --filter @cnotes/api-client test` → **3 passed (3)**;`pnpm --filter @cnotes/api-client typecheck`（tsc）与 `pnpm --filter @cnotes/web typecheck`（vue-tsc）均 0 报错。浏览器全链路点击留 P9 真实 e2e 覆盖。
- 下一步：P8 nginx 同源基础设施（web dist 根 + `/api/`→`127.0.0.1:8080` 反代 + `try_files`,装并起 nginx）。

### 2026-06-17 — P8 nginx 同源基础设施（8088 → web dist + /api 反代）
- **配置**：`ops/nginx/cnotes.dev.conf` 自包含、机器无关——以**仓库根为 prefix** 运行（`nginx -p "$PWD/" -c ops/nginx/cnotes.dev.conf`），故 `root frontend/apps/web/dist` 与运行期目录 `.data/nginx/`(pid/日志/临时,均 gitignore)都按仓库根相对解析,不写死任何绝对路径;内联最小 MIME 映射避免依赖 brew/apt 的 `mime.types` 绝对路径。监听 **8088** 避开后端 8080。`location /api/` 反代 `127.0.0.1:8080`(保留 `/api` 前缀、`proxy_read_timeout 120s` 容深聊一轮等 LLM)、`location /` `try_files $uri $uri/ /index.html` 做 SPA history 回退。生产仅需改 `listen 80/443`+证书+`root` 指向部署目录,location 规则不变。
- **依赖拉起**：brew 装 nginx(homebrew 域不在沙箱白名单,装时临时禁沙箱);web 产物 `pnpm --filter @cnotes/web build` → `frontend/apps/web/dist`;后端 `make server-dev`(H2 内存 + DeepSeek,Tomcat 8080,Flyway 跑 V1/V2/V3 三迁移)。
- **验证证据（真实链路,后端在跑）**：① 直连后端 `127.0.0.1:8080/api/articles` → **200**;② **经 nginx** `127.0.0.1:8088/api/articles` → **200 且返回真实 JSON**(dev 播种 3 篇,含「Attention Is All You Need:重读经典」status=done + summary + tags=[LLM 推理优化,深度学习])——证明 `/api` 反代真打到后端→H2;③ 根 `/` → 200 `text/html`;④ 深路径 `/reader/x` → 200 `text/html`(SPA history 回退生效)。`nginx -t` 配置校验通过。
- **配套**：`.gitignore` 加 `.data/`(向量库快照 / nginx 运行期文件不入仓);修正 `docs/ops/local-build-run.md` §5 启动命令(prefix 必须是仓库根,原 `-p "$PWD/ops/nginx"` 会令 root 与 `.data` 解析错位)。
- 下一步：P9 Playwright 真实浏览器 e2e（浏览器穿 nginx:8088 → 后端:8080 → H2 → 向量库,门控真 LLM/Ark;断言深聊返回带源标签且 H2 落 chat_session/chat_message）。

### 2026-06-17 — P9 Playwright 真实浏览器 e2e（穿 nginx→后端→H2→向量库→DeepSeek）
- **🕸 前置打通(关键)**：`DevDataSeeder` 增第二篇「深度学习」done 文章(ResNet),使该标签满足 `cluster.min-members(=2)` → 重启后 `ClusterSummaryWorker`(15s 轮询)真实跑「DeepSeek 综述 → Ark 多模态向量化(2048 维) → `SimpleVectorStore.save()` 落盘 `server/.data/vectorstore.json`(30019 字节)」。`KnowledgeRetriever` 无分数阈值,向量库非空即命中,故深聊 🕸 知识网来源在端到端链路被真实触发。
- **真实链路验证证据**：① 烟测 `curl POST http://localhost:8088/api/articles/{id}/chat`(经 nginx)→ 真实 DeepSeek 回复且 `"sources":["📄","🕸"]`;② Playwright e2e `深聊:浏览器穿过 nginx→后端→向量库→DeepSeek` → **1 passed(19.3s/总 46.2s)**——真实 Chrome 点开「Attention Is All You Need」→ 点⚗深聊 FAB(`.chat-ctx` 含「正在聊本文」)→ 发问回车 → 断言 `.msg.ai .srcs` 含 📄 与 🕸、回复非空;第二轮追问断言 `.msg.ai` 计数 +1 且末条非空(同 sessionId,证 H2 会话往返)。
- **DB 行级断言归属**：H2 内存库为单 JVM,node 测试进程无 H2 TCP 端口无法直连,故 e2e 以「两轮会话连续」证 DB 往返;`chat_session`/`chat_message` 行级落库由后端 `ChatPersistenceTest`/`ChatApiTest` 对同一 H2 覆盖——分层而不重复,符合 /goal「真实端到端」意图。
- **浏览器内核**：`playwright.config.ts` chromium 工程用 `channel: 'chrome'`(系统 Chrome)。本机网络受限,`cdn.playwright.dev` 不在沙箱白名单,自带 chromium(build 1228)下载 ECONNRESET 失败;系统 Chrome 同为真实浏览器,点击/渲染/网络行为一致,满足真实 e2e。
- **配套**：`package.json` 加 `@playwright/test` + `test:e2e` 脚本;`.gitignore` 忽略 `test-results/`、`playwright-report/`;`docs/ops/local-build-run.md` §6 更新真实链路描述与浏览器内核说明。
- 下一步：P10 收尾——全套件绿(`./gradlew test` + `pnpm -r test` + e2e)→ 进度文档定稿 → commit + push。
