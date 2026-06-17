# Stage-5 深聊 — 验证报告(流程 / 中间件 / 过程 / 结果)

> 目的:把「我用什么组件、按什么流程、跑了哪些验证、得到什么结果」一次写清,供审计 / 续作 / 复现。
> 诚实原则:区分「离线确定性测试」与「真打远端的门控测试」——不是每次 `gradlew test` 都烧 key,真打远端的证据单独列。
> 关联文档:实施计划 `2026-06-17-stage5-deep-chat-implementation.md`、逐 phase 进度 `2026-06-17-stage5-progress.md`、本地编译运行手册 `../ops/local-build-run.md`。

## 1. 验证哲学:三层金字塔(分层不重复)

| 层 | 测什么 | 形态 | 是否触网 |
|---|---|---|---|
| L1 单元 / 切片 | 单个类的行为(解析、降级、落库、向量化逻辑) | 离线,Mock/Stub/MockRestServiceServer/H2 | 否(确定性,CI 可复现) |
| L2 真打远端(门控) | 与火山引擎 Ark / DeepSeek / DuckDuckGo 的**真实契约**(端点、维度、语义召回) | 真网,`@EnabledIfEnvironmentVariable` 门控 | 是(仅当 env 注入对应 key 时运行,否则跳过) |
| L3 浏览器端到端 | 生产形态全链路:真实浏览器点击穿过反代→后端→DB→向量库→大模型 | 真实 Chrome + 运行中的全栈 | 是 |

为什么分层:H2 内存库是单 JVM,node 测试进程没有 H2 的 TCP 端口、无法直连断言行级数据;所以 `chat_session/chat_message` 的**行级落库**由后端 L1 的 `ChatPersistenceTest`/`ChatApiTest` 对同一 H2 覆盖,而 L3 浏览器用**两轮会话连续(同 sessionId)**间接证 DB 往返。两者互补,合起来才是「真实端到端」。

## 2. 涉及的中间件 / 组件(全本地或可本地,符合 /goal「内存型 / 本地文件型优先」)

| 层 | 组件 | 版本 / 接入点 | 形态 | 在验证里的角色 |
|---|---|---|---|---|
| 反向代理 | nginx | 本地进程,监听 **8088** | 本地进程 | 同源生产形态;`/api/`→`127.0.0.1:8080`,`try_files`→`/index.html` SPA 回退 |
| 应用容器 | Tomcat(Spring Boot 内嵌) | Spring Boot **4.1.0**,监听 **8080** | 本地进程 | 承载 REST + 深聊编排 |
| 关系库 | H2 | 2.x,`jdbc:h2:mem:cnotes-dev` MODE=MySQL | **内存型** | dev/test 库;`chat_session`/`chat_message` 落此 |
| 库迁移 | Flyway | core + mysql | — | 启动跑 `V1/V2/V3`(h2 与 mysql 双套 SQL),V3 建深聊两表 |
| ORM | MyBatis-Plus | 3.5.16(spring-boot4-starter) | — | BaseMapper CRUD |
| 向量库 | Spring AI `SimpleVectorStore` | spring-ai-bom 管版本 | **本地文件型** | 簇综述向量落 `server/.data/vectorstore.json`;`similaritySearch` 命中即 🕸 |
| 模型层-对话 | DeepSeek(OpenAI 兼容) | `deepseek-chat`,`api.deepseek.com` | 远程 API | 源1/源2 上下文 + WebSearchTool 交 ChatClient 生成回复 |
| 模型层-向量 | 火山引擎 Ark 多模态 embedding | 接入点 `ep-20260617000458-2mslf`,2048 维,`/embeddings/multimodal` | 远程 API | 簇综述向量化 + 检索时把用户问题向量化 |
| 联网搜索 | DuckDuckGo HTML 版 + jsoup 1.18.3 | `html.duckduckgo.com/html/`,无需 key | 远程 HTML | 源3 `@Tool`,优雅降级 |
| 构建 | Gradle + Wrapper | **8.14**(`server/gradlew`,不用 Maven) | 本地 | 跑 L1/L2 |
| 前端 | Vite 产物 `dist` + pnpm 10 monorepo | — | 本地静态 | nginx 托管;vue-tsc 类型检查 |
| 浏览器 | Playwright + **系统 Google Chrome**(`channel:'chrome'`) | @playwright/test ^1.61 | 本地真实浏览器 | L3 真实点击;本机网络受限拉不到自带 chromium,系统 Chrome 同为真实浏览器 |

密钥来源:仅 `server/.env`(被 `.gitignore` 命中,不入仓),变量 `DEEPSEEK_API_KEY / LLM_MODEL / ARK_API_KEY / ARK_EMBEDDING_BASE_URL / ARK_EMBEDDING_MODEL / ARK_EMBEDDING_DIM`。`embedding-demo.txt` 仅作 Ark API 调法的**规格参考**(据它定下「必须走 `/embeddings/multimodal`、`input` 为带 type 的数组、返回 `data.embedding` 2048 维」),不被任何代码 import / 执行,内含 `$ARK_API_KEY` 占位符无真 key,故不提交。

## 3. 🕸(知识网/源2)的触发前置——验证能成立的关键

源2 不是凭空有的,要先把向量库喂出来:某标签需 ≥ `cluster.min-members`(=2) 篇 done 文章 → `ClusterSummaryWorker`(15s 轮询)→ `ClusterService.regenerate()` → `ClusterSummarizer.summarize()`(**DeepSeek 综述**)→ `ClusterIndexer.index()`(**Ark 向量化**)→ `SimpleVectorStore.save()` 落盘。`DevDataSeeder` 故意播两篇「深度学习」done 文章(Attention / ResNet)凑齐 min-members,使这条真实链路在 dev 启动后自动跑通。`KnowledgeRetriever.retrieve()` 无分数阈值,库非空即命中,故深聊必触发 🕸。

## 4. 验证过程(实际跑的命令与判定)

### L1 离线单元 / 切片(后端 22 个测试类)
```bash
cd server && ./gradlew test          # H2 内存 + stub ChatModel + MockitoBean 隔离远端
```
判定:`BUILD SUCCESSFUL`,全绿;门控的 L2 用例在未注入 env 时记为 skipped(确定性,不烧 key)。深聊相关:`ChatPersistenceTest`(1 session+2 message、role、sources JSON、create_time 自动填充)、`ChatApiTest`(POST 持久化 + `sessionId` 续聊复用同会话)、`ChatContextAssemblerTest`(源1/源2 织入与缺省降级)、`ArkEmbeddingModelTest`(MockRestServiceServer 断言命中 `/embeddings/multimodal`、解析 2048 维)、`ClusterIndexerTest`/`KnowledgeRetrieverTest`(8 维 Stub 向量断言落库/检索映射/降级)、`WebSearchToolTest`(uddg 解码、topN 截断、异常降级空串)。

### L1 前端契约(api-client vitest)
```bash
pnpm -r test    # 或 pnpm --filter @cnotes/api-client test
```
判定:`3 passed`——首轮 POST `/api/articles/{id}/chat` body 仅 `{message}`、续聊带 `sessionId`、article id 经 `encodeURIComponent`、非 2xx 抛 `ApiError`。另 `vue-tsc`/`tsc` typecheck 0 报错。

### L2 真打远端(门控,注入 `server/.env` 才跑)
```bash
# Ark 真实契约:真打火山引擎返回 200 + 2048 维
ARK_API_KEY=… ./gradlew test --tests '*ArkEmbeddingModelTest'      # realArkReturns2048Dim()
# 向量库真实语义召回:索引「烹饪/航天」两簇,检索「红烧牛肉…」命中烹饪簇
ARK_API_KEY=… ./gradlew test --tests '*ClusterIndexerTest' --rerun-tasks
ARK_API_KEY=… ./gradlew test --tests '*KnowledgeRetrieverTest' --rerun-tasks
# 源3 真打 DuckDuckGo
WEBSEARCH_LIVE=1 ./gradlew test --tests '*WebSearchToolTest' --rerun-tasks
```
判定:对应门控用例 0 跳过 0 失败,含真实网络往返。

### L3 浏览器端到端(真实全栈在跑)
```bash
make server-dev                                            # 终端A:H2 后端(8080),Flyway 跑 V1/V2/V3
cd frontend && pnpm --filter @cnotes/web build             # 产 dist
nginx -p "$REPO_ROOT/" -c ops/nginx/cnotes.dev.conf        # 终端B:nginx(8088)
cd frontend/apps/web && pnpm exec playwright test e2e/deep-chat.e2e.ts
```
e2e 断言:真实 Chrome 打开 nginx 同源页 → 点开「Attention Is All You Need」→ 点 ⚗深聊 FAB(`.chat-ctx` 含「正在聊本文」)→ 发问回车 → `.msg.ai .srcs` 含 📄 与 🕸 且回复非空 → 第二轮追问 `.msg.ai` 计数 +1 且末条非空(同 sessionId 证 H2 往返)。

## 5. 结果

### 5.1 测试套件(三层全绿)
- L1 后端 `./gradlew test`:**BUILD SUCCESSFUL**(22 个测试类;Gradle 8.14 / JDK 21;最近一次约 24s)。
- L1 前端 `pnpm -r test`:**3 passed**。
- L3 浏览器 e2e:**1 passed**(真实 Chrome 穿 nginx→后端→H2→向量库→DeepSeek)。

### 5.2 真打远端的实物证据(不是 mock)
- **向量库快照** `server/.data/vectorstore.json`:存在,1 条目,`tagName=深度学习`,**embedding 维度 = 2048**,值为真实浮点(`0.0128…, -0.0084…`)。离线 stub 是 8 维 —— 2048 维只可能来自火山引擎真实返回,证 Ark key 被真实使用、综述被真实向量化落盘。
- **Ark 门控用例** `realArkReturns2048Dim()`:带 `.env` 跑时真实网络往返 200 + 2048 维。
- **DeepSeek**:深聊回复为真实大模型输出(见 5.3 现场复验)。

### 5.3 本次会话现场复验(经 nginx 真发,非引用历史)
被测文章:`c58e370509116a96298348e353ddddc7`(李沐《Attention Is All You Need:重读经典》,status=done)。
- 第一轮 `POST http://localhost:8088/api/articles/{id}/chat` `{"message":"用一句话说这篇讲了什么?"}` → `sources: ["📄","🕸"]`,真实 DeepSeek 回复 127 字(开头「这篇论文提出的 Transformer 架构,彻底抛弃了传统的循环神经网络(RNN)和卷积神经网络(CNN)…」)。
- 第二轮带同 `sessionId`(`baadca584590b2dde1b381b85e35c938`)追问「它的核心创新点叫什么?」→ **返回相同 sessionId**(证 H2 会话往返成功)、`sources: ["📄","🕸"]`、回复延续上下文(「…核心创新点叫做…」)。

这一轮同时打通:nginx 反代(8088→8080)、H2 会话读写、SimpleVectorStore 检索(🕸,且检索时用户问题经 Ark 向量化)、DeepSeek 生成(回复正文)。源1 📄 来自锚定文章 + 批注,源3 🌐 由 ChatClient 按需调 WebSearchTool(本轮问题未触发联网,属预期降级行为)。

## 6. 诚实的边界说明(避免过度声称)
- **不是每次 `gradlew test` 都烧 key**:P10 收尾那次 `cd server && ./gradlew test` 未 `source server/.env`,故 L2 门控用例被跳过;那是刻意的 CI 确定性设计。真打 Ark/DeepSeek/DuckDuckGo 发生在带 `.env` 的 L2 门控运行与 L3 端到端链路里。
- **行级 DB 断言归属**:L3 不直连 H2(单 JVM 内存库无对外端口),靠两轮同 sessionId 证往返;行级断言在 L1 后端测试里。
- **浏览器内核**:用系统 Chrome(`channel:'chrome'`)而非 Playwright 自带 chromium——本机网络受限拉不到 `cdn.playwright.dev` 的自带内核,系统 Chrome 同为真实浏览器,真实点击/渲染/网络行为一致,满足「真实端到端」。
- **embedding-demo.txt**:仅作 Ark API 调法的规格参考,未被代码引用/执行;不入仓。

## 7. 复现门槛(零上下文工程师照此即可重跑)
JDK 21 + Node≥20/pnpm 10 + nginx + 系统 Chrome;在 `server/.env` 填好 DeepSeek 与 Ark key;按 §4 三段命令依次跑。完整环境与排错见 `../ops/local-build-run.md`。
