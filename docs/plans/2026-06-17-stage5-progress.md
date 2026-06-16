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
| P0 | 环境核对 + 三份文档 | ✅ 文档完成 | (待提交) |
| P1 | V3 迁移 + 实体/Mapper | ✅ 完成 | (见下 commit) |
| P2 | Ark EmbeddingModel | ⬜ | |
| P3 | SimpleVectorStore + ClusterIndexer | ⬜ | |
| P4 | WebSearch @Tool（源3） | ⬜ | |
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
