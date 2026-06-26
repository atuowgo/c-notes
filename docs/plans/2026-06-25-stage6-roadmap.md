# Stage 6 — 收尾审计与产品化路线图

> 日期:2026-06-25
> 状态:**V1/V2/V3/Stage5 四阶段代码全部落地,核心闭环端到端贯通且 e2e 覆盖**。本文为收尾审计结论 + 后续执行路线图。剩余项全为 medium/low,**无 high 级阻塞**。
> 审计依据:5 域并行核对(98 项逐项判定,代码级实读),基线 git ref `06b4bfb`(P10 收尾)。
> 前序计划:产品设计 `2026-06-15-knowledge-network-product-design.md`、MVP 脊柱 `2026-06-15-mvp-backend-spine-implementation.md`、前端 `2026-06-16-frontend-monorepo-restructure.md`、V2 微信 `2026-06-16-wechat-collect-v2.md`、V3 知识网 `2026-06-16-knowledge-network-v3.md`、Stage5 深聊三件套 `2026-06-17-stage5-*.md`。

---

## 1. 项目速览

知识炼金炉(cnotes)是 Spring Boot 后端 + Vue pnpm monorepo(web 阅读端 + 浏览器插件)+ nginx 同源的个人知识沉淀系统。核心闭环:

```
收集(插件/微信/手填)→ 抓取(三级)→ 整理(LLM 摘要/要点/标签)→ 文章
   → 笔记(划线+想法+锚定)→ 标签(受控集)→ 深聊(三源上下文)→ 知识网(主题簇+演进式综述)
```

技术栈:Boot 4.1 + Spring AI 2.0 + MyBatis-Plus + Flyway(MySQL/H2 双脚本)+ Java 21;真实 Provider 为 DeepSeek(chat,env `LLM_MODEL`)+ Ark embedding(2048 维,Stage5 向量检索)。前端 pnpm monorepo 三包(types/api-client/design-tokens)+ 两端(web/extension)。

---

## 2. 已实现主线(精炼,按阶段)

**V1 MVP 脊柱** — 全 done,多处优于 plan:
- Gradle 工程 + Flyway 五表(mysql/h2 双脚本,索引齐全)+ Article 实体(ASSIGN_UUID + AutoFillHandler)
- `POST /api/collect` + `url_hash` 幂等(唯一索引 `DuplicateKeyException` 兜底并发)→ `commit` 链 P1–P2
- `ArticleOrganizer`(`ChatClient.entity()` 一次拿 summary+keyPoints+tags)+ `TagClassifier`(批量查询归类,命中 `article_tag`/未命中 `tag_suggestion`,幂等)
- `ArticleWorker` `@Scheduled` 轮询 + 乐观认领(CAS)+ 指数退避;`SchedulingConfig` 开关化解决测试隔离
- `GET /api/articles` 列表分页(带 tags + `X-Total-Count`)+ `/{id}` 详情(404 兜底);`DevDataSeeder` 保障无 LLM/MySQL 可演示
- Note CRUD + 锚定(`NoteController`/`NoteService`,anchor 落 start/end 字符偏移,selector 预留)
- 正文抓取:`ContentFetcher`(jsoup + `#js_content` 等容器 + 启发式 + 三级编排)+ `HeadlessRenderer`(Playwright,默认关 + `--no-sandbox` + LOAD)

**V2 微信收集** — 全 done(原 V2 deferred 项已提前实现):
- `GET/POST /wechat/callback`(`WeChatController`/`WeChatService`):签名校验 `sha1Hex`、文本正则提 URL / link 取 `Url+Title`、无链接回引导语、复用 `CollectService`(`source_type=wechat`)、立即被动回复 XML、XML 防 XXE、`wechat.token` 仅 env;`WeChatApiTest` 6 用例

**V3 知识网** — 7 大块 done:
- Flyway V2 `tag` 加 `living_summary`/`summary_member_count`/`summary_updated_at`(mysql+h2,全链路贯通 Tag → `ClusterDetailDto` → `@cnotes/types` → `ClusterDetailView`)
- `ClusterService`(listClusters/detail/regenerate/staleClusterTagIds,min-members 默认 2)+ `ClusterSummarizer`(ChatClient 织 300–500 字演进式综述)+ `ClusterSummaryWorker` `@Scheduled` 周期重写(共用 `worker.scheduling.enabled` 门控)
- `GET /api/clusters` + `/{id}` + `POST /api/clusters/{id}/regenerate`;前端 `ClustersView` + `ClusterDetailView`(综述卡 + 本簇文章 + 手动重写)

**Stage5 深聊** — 全 done:
- `ChatContextAssembler` 三源组装(源1 本文+批注 / 源2 向量检索标 🕸 / 源3 `@Tool` 不预取)
- `ArkEmbeddingModel`(`@Primary`,POST `/embeddings/multimodal`,2048 维)+ `SimpleVectorStore` + `ClusterIndexer`(delete-then-add + metadata,落盘)+ `KnowledgeRetriever`(`similaritySearch`)
- `WebSearchTool`(DuckDuckGo + jsoup + 真实链接解码 + 优雅降级空串)
- `POST /api/articles/{id}/chat`(reply+sessionId+sources)+ 会话持久化(V3 建表 `chat_session`/`chat_message` + entity/mapper + `ensureSession`)
- 前端 `ChatPanel` 接真实端点(去 mock + sessionId 续接 + sources 渲染 + loading/错误态);e2e `deep-chat.e2e.ts`(Playwright 穿 nginx 8088 → 后端 8080 → H2 → 向量库 → DeepSeek,断言 📄+🕸 来源 + 续聊)→ `commit be16ac7`(P9)

**前端 monorepo** — 三包 + 两端 done:`packages/{types,api-client,design-tokens}` + `apps/web`(InboxView/ReaderView/ClustersView/ClusterDetailView + ArticleCard/DistillCard/CollectModal/Toast/TagFilter/RecommendList/IdeasDrawer/ChatPanel)+ `apps/extension`(MV3 popup + content Readability+Turndown → `/api/collect`);Vite `/api` 代理 + nginx 同源(后端 `static/index.html` 已删)。

**Ops**:`nginx/cnotes.dev.conf`(listen 8088 + `/api/` proxy_pass 127.0.0.1:8080 + SPA `try_files`)+ `Makefile` 汇总命令。

git 提交链与 progress 记录 ref 逐一对齐:P3 `aad07c5` / P4 `cfd910c` / P5 `e69eacd` / P6 `7c38911` / P7 `2fc3c11` / P8 `736ebb0` / P9 `be16ac7` / P10 `06b4bfb`。

---

## 3. 审计方法与局限

5 域(V1 脊柱 / V3 知识网 / Stage5 深聊 / 前端 / V2+Ops+移动)并行核对,98 项逐项判定 done/partial/missing/external-blocked/not-started,证据精确到文件/类/行。**均为静态代码级(Read/grep/ls 实读)**,未重跑:

- `./gradlew test` / `pnpm -r test` / Playwright —— 需 JDK21 + pnpm10 + nginx + 系统 Chrome + 真 DeepSeek/Ark key + 运行中全栈
- 测试数(22 类/63 用例等)、`BUILD SUCCESSFUL`、e2e `3 passed` 未独立复现,以测试源码断言 + stage-5 验收报告(`be16ac7`)为据证实可达成
- L2 门控测试源码真实存在(`@EnabledIfEnvironmentVariable ARK_API_KEY`/`WEBSEARCH_LIVE`),断言文本与报告吻合但未带 key 实跑
- `vectorstore.json` 为运行期产物且 `.gitignore`,仓内无快照,无法静态证实 2048 维落盘(不构成代码缺口)
- `pnpm install`/`build` 因网络未在本会话实跑,以前端代码结构 + 提交链为据

非缺口(有意微调/已 deferred,不计入剩余):`NoteAnchor` 仅落字符偏移、selector 预留(§8 已将正文重定位稳定性列为待定);`key_points`/anchor 列用 TEXT 非 JSON(MySQL 脚本注释标明应用层(反)序列化,行为等价);`IdeasDrawer` 提问/创作为 toast 占位,完整入网能力留 V4(§6.4 deferred)。

---

## 4. 剩余工作总览

| # | 项 | 类别 | 状态 | 优先级 | 批次 |
|---|---|---|---|---|---|
| 0 | `LLM_MODEL` 默认值 `deepseek-v4-flash` 非真实 id | 配置缺陷 | not-started | medium | B0 |
| 1 | 鉴权/JWT + 对象存储 | 安全/产品化 | not-started | medium | A |
| 2 | V3 embedding 自动聚类 | V3 增强 | not-started | low | B |
| 3 | V3 关联 article↔article Link | V3 增强 | not-started | low | B |
| 4 | V3 簇合并/拆分纠偏 | V3 增强 | not-started | low | B |
| 5 | RecommendList「更深入」分类 | 前端推荐 | partial | low | B |
| 6 | 微信安全模式 AES 加解密 | 微信硬化 | not-started | low | C |
| 7 | 微信关注/事件消息自动回复 | 微信硬化 | not-started | low | C |
| 8 | `ChatPanel.spec.ts` vitest 组件单测 | 前端测试 | partial | low | E |
| 9 | 移动端 Capacitor 包壳 | 移动端 | not-started | low | D |
| F1 | 公众号认证(执照+年费) | 外部前置 | external-blocked | medium | 外部轨道 |
| F2 | 公网域名 + HTTPS 证书 | 外部前置 | external-blocked | medium | 外部轨道 |
| F3 | 无头浏览器 CA(部署环境) | 外部前置 | external-blocked | low | 外部轨道 |

**核心闭环无 high 级阻塞**;剩余全为 medium/low。

---

## 5. 执行路线图(批次化)

> 原则:每批次独立可验收、可提交;沿用 Pn + git ref 记录法(见 §6)。批次间标注依赖,可并行处注明。

### 批次 B0 — 立即修正(配置缺陷)

- **任务**:改 `server/src/main/resources/application.yml`,`spring.ai.openai.chat.options.model` 默认值 `deepseek-v4-flash` → `deepseek-chat`(DeepSeek 官方真实 id;`deepseek-reasoner` 为推理模型,按需另配)
- **原因**:无 env `LLM_MODEL` 时 dev 指向不存在模型而失败;live 链路(有 env)正常
- **验收**:无 env 启动 dev profile,`ArticleOrganizer` 调用 DeepSeek 返回正常摘要;现有测试不受影响
- **依赖**:无;先行,5 分钟

### 批次 A — 产品化地基(鉴权 + 对象存储)

- **A1 鉴权/JWT**:加 `SecurityConfig` + JWT 签发/校验 + `AuthFilter`;`article`/`note`/`chat_session` 等表加 `owner_id` 隔离;`/wechat/callback` 与健康检查放行
- **A2 对象存储**:正文过长者(或图片)落 OSS/S3,`article.content` 存引用而非 LONGTEXT 全量;`application.yml` 加 OSS 配置(env 注入 key)
- **验收**:多用户数据互不可见;长正文不再全量入库;`make server-test` 绿
- **依赖**:无;**与批次 B 可并行**(不同包/关注点)
- **备注**:plan 明列产品化阶段 deferred,多机预留仅在结构上留口(收/发分包、配置化数据源)

### 批次 B — V3 知识网深化(有依赖序)

- **B1 embedding 自动聚类**:复用 Stage5 已有的 `ArkEmbeddingModel` 为 `article`/簇综述向量化,新增 `AutoClusterService`(超越受控标签按语义聚簇);与现有 tag 聚簇并存,不破坏 V3
- **B2 关联 article↔article Link**:新增 `article_link` 表 + `LinkService`(共同概念/观点对立/互补 → 「为什么相关」);阅读端 `RecommendList` 接入
- **B3 簇合并/拆分纠偏**:`POST /api/clusters/merge`、`/split`、拖文章到别簇;记录用户偏好,影响后续聚类
- **B4 RecommendList「更深入」分类**:升级为簇/关联驱动,补 `rec-kind=更深入`(现仅「相关」)
- **验收**:`GET /api/clusters` 含语义簇;阅读端推荐出现「更深入」;用户可合并/拆分簇且偏好被记住
- **依赖**:B2/B4 依赖 B1 的 embedding 能力已就位(已有);B3 依赖簇模型稳定;**B2 可与 B1 并行**(共同标签维度不依赖聚类)

### 批次 C — 微信硬化(代码侧)

- **C1 安全模式 AES 加解密**:补 `EncodingAESKey` 的消息加解密,与明文模式并存(按公众号后台加解密方式自动切换);`WeChatService` 类注释已标留作硬化
- **C2 关注/事件消息自动回复**:`handle()` 区分 `MsgType=event`(关注/取关等)做运营自动回复,非统一引导语
- **验收**:`WeChatApiTest` 增 AES 加解密用例;关注事件回欢迎语;`make server-test` 绿
- **依赖**:无;与 A/B 可并行
- **外部前置**:真实接入还需 F1/F2(见 §6),代码侧硬化不阻塞

### 批次 D — 移动端(Capacitor 包壳)

- **任务**:新增 `apps/mobile`,`@capacitor/core` 包壳 `apps/web` 构建产物,复用全部 `packages/*`
- **验收**:`pnpm build:mobile` 产出 Android/iOS 包;web 端功能在移动端可用
- **依赖**:web 稳定;**建议 A(鉴权)先行**(多设备需登录态);放最后

### 批次 E — 测试补齐

- **任务**:补 `frontend/apps/web/src/components/__tests__/ChatPanel.spec.ts`(vitest,mock api-client,断言发问后渲染 reply 与来源标签);`apps/web` 配 vitest 依赖/脚本/`vitest.config`
- **现状**:已有 `api-client` 层 3 例契约测试 + Playwright e2e 全链路兜底,组件渲染断言无单测覆盖;progress P7 明确以 P9 e2e 替代,非遗漏
- **验收**:`pnpm -r test` 含 ChatPanel 组件用例且绿
- **依赖**:无;低优先,随时可插入

---

## 6. 外部前置(运维轨道,与代码并行)

非代码项,不阻塞批次 B/C/E 的开发,但解锁真实微信接入与无头浏览器渲染:

- **F1 公众号认证**:认证服务号能力最完整,需营业执照 + 年费(product-design §8.2)。代码侧 `/wechat/callback` 已就绪
- **F2 公网域名 + HTTPS**:公众号后台填的 URL 须公网可达 80/443,token 与服务端 `WECHAT_TOKEN` 一致;`nginx cnotes.dev.conf` 已预留 listen 80/443 + 真实证书注释,location 规则不变
- **F3 无头浏览器 CA**:部署环境装 Chromium + 系统库且出网 CA 受信任(沙箱遇 `ERR_CERT_AUTHORITY_INVALID`)。生产代码未加 `--ignore-certificate-errors`(刻意不绕过)。`HeadlessRenderer` 默认关,不阻塞主链路

---

## 7. 进度记录约定

沿用既有 Pn + git ref 记录法(git log 可见 `docs(plan): record Pn git ref xxxx`):

- 每批次完成 → 单独 commit,信息按全局规范:`[#AI commit#][Claude Code]type(scope): 描述`
- 每批次收尾 → 在本文件对应批次下追加 `> git ref: <sha>` 与一句验收结论(参照 stage5-progress.md 风格)
- 建议执行序:**B0 → (A ‖ B) → C → D → E**,外部轨道 F 全程并行

---

## 附:批次验收清单(快速勾选)

- [ ] B0:`LLM_MODEL` 默认值改 `deepseek-chat`,无 env 启动正常
- [x] A1:JWT 鉴权 + 多用户 `owner_id` 隔离 — git ref `3d86ea9`
- [x] A2:本地文件对象存储,长正文落盘 + hydrate — git ref `eb97c17`
- [x] B1:embedding 自动聚类(复用 `ArkEmbeddingModel`) — git ref `6a800c9`
- [x] B2:`article_link` + 关联推荐 — git ref `6e6fdd7`
- [x] B3:簇 merge/split/move 纠偏 + 审计 — git ref `92531a5`
- [x] B4:RecommendList「更深入」分类 — git ref `8c8f1b7`
- [ ] C1:微信安全模式 AES 加解密
- [ ] C2:关注/事件消息自动回复
- [ ] D:`apps/mobile` Capacitor 包壳
- [ ] E:`ChatPanel.spec.ts` vitest 组件单测
- [ ] F1:公众号认证(外部)
- [ ] F2:公网域名 + HTTPS(外部)
- [ ] F3:无头浏览器 CA(外部)

> Stage-6 A1/A2/B1-B4 收尾(2026-06-26):实现全绿(6 批次 + 冒烟),e2e + live API + 18 张截图留底,审计报告见 `docs/ops/stage6-audit/STAGE6-AUDIT.md`。本地验证/生产部署步骤见 `docs/ops/local-verify-stage6.md` 与 `docs/ops/prod-deploy.md`。B0/C1/C2/D/E/F1-F3 待办。
