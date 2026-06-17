# 本地编译 / 运行手册（c-notes 个人知识炼金炉）

> 面向「零上下文的工程师」：照此即可在一台 macOS/Linux 上把前后端依赖组件**全部用内存型 / 本地文件型**拉起、编译、跑通端到端。
> 生产环境另切 MySQL（见末节），本手册只覆盖本地 dev。

## 0. 组件总览（全部本地，无外部托管依赖）

| 层 | 组件 | 形态 | 说明 |
|---|---|---|---|
| 数据库 | H2 2.x | **内存型** `jdbc:h2:mem` MODE=MySQL | dev/test 用；上线切 MySQL 8 |
| 向量库 | Spring AI `SimpleVectorStore` | **本地文件型** JSON 落盘 | `server/.data/vectorstore.json`，进程内检索 |
| 模型层-对话 | DeepSeek（OpenAI 兼容） | 远程 API | `https://api.deepseek.com`，仅对话，无 embedding |
| 模型层-向量 | 火山引擎 Ark 多模态 embedding | 远程 API | `ep-20260617000458-2mslf`，2048 维，走 `/embeddings/multimodal` |
| 反向代理 | nginx | 本地进程 | 同源：静态 `dist` + `/api` 反代到 8080 |
| 前端 | Vite 构建产物 `dist` | 本地静态文件 | nginx 托管；开发期也可 `pnpm dev` 用 Vite 代理 |
| 浏览器 e2e | Playwright（Chromium） | 本地 | 真实点击穿过 nginx→后端→H2→向量库 |

## 1. 前置工具

- JDK 21（`java -version` 含 21）。Gradle 用仓库内 **Wrapper**：`server/gradlew`（不装 Maven）。
- Node ≥ 20、pnpm 10.x（`corepack enable` 或 `npm i -g pnpm@10`）。
- nginx：`brew install nginx`（macOS）/ `apt install nginx`（Linux）。
- 密钥：仓库根 `.env`（`key_deepseek` / `key_embedding`）。应用读取的是 `server/.env`（已 .gitignore），变量改名见 §2。

## 2. 密钥与环境变量（绝不提交）

`server/.env`（由根 `.env` 派生，`.gitignore` 第 21 行 `.env` 命中）：

```
DEEPSEEK_API_KEY=<root .env 的 key_deepseek>
LLM_MODEL=deepseek-chat
ARK_API_KEY=<root .env 的 key_embedding>
ARK_EMBEDDING_BASE_URL=https://ark.cn-beijing.volces.com/api/v3
ARK_EMBEDDING_MODEL=ep-20260617000458-2mslf
ARK_EMBEDDING_DIM=2048
```

> 火山引擎该接入点是**视觉/多模态** embedding 模型，纯文本 `/embeddings` 会 400；必须走 `/embeddings/multimodal`，`input` 为带 `type` 的数组，返回 `data.embedding`（2048 维）。
> `Makefile` 已通过 `LOAD_ENV` 自动 `source server/.env`。

## 3. 编译

```bash
# 后端（Gradle Wrapper）
cd server && ./gradlew clean build            # 编译 + 跑全部单测/集成测试
# 前端（pnpm monorepo）
cd frontend && pnpm install && pnpm -r build  # 产出各 app 的 dist
```

或用根 `Makefile`：`make server-build`、`make frontend-build`、`make build`。

## 4. 本地运行（dev：H2 内存 + 本地文件向量库）

```bash
# 后端：dev 档 = H2 内存库 + 调度开启
make server-dev
#   实际等价：cd server && ./gradlew bootRun --args='--spring.profiles.active=dev --worker.scheduling.enabled=true'
#   H2 控制台 http://localhost:8080/h2-console  (JDBC: jdbc:h2:mem:cnotes-dev, user sa, 空密码)

# 前端开发服务器（Vite 代理 /api → 8080）
make web        # http://localhost:5173
```

向量库落盘文件：`server/.data/vectorstore.json`（首次启动若不存在则建空库；簇综述变更时增量写入）。删除该文件即重置向量库。

## 5. 同源运行（nginx 反代，贴近生产形态）

```bash
cd frontend && pnpm --filter @cnotes/web build   # 产出 frontend/apps/web/dist
# 用仓库内模板启动 nginx（监听 http://localhost:8088）
# prefix 必须是仓库根：配置内 root(frontend/apps/web/dist) 与运行期目录(.data/nginx)都按仓库根相对解析。
cd "$REPO_ROOT" && nginx -p "$PWD/" -c ops/nginx/cnotes.dev.conf
# 校验：nginx -p "$PWD/" -c ops/nginx/cnotes.dev.conf -t
# 停止：nginx -p "$PWD/" -c ops/nginx/cnotes.dev.conf -s stop
```

`ops/nginx/cnotes.dev.conf` 要点：`root` 指向 web 的 `dist`，`location /api/ { proxy_pass http://127.0.0.1:8080; }`，`try_files $uri /index.html`（SPA 回退）。前端打包时 `VITE_API_BASE_URL` 留空 → 同源请求 `/api/...`。

## 6. 端到端验证（真实链路）

```bash
make server-dev          # 终端 A：H2 后端
# 终端 B：构建 dist 并起 nginx（§5）
cd frontend/apps/web && pnpm exec playwright test e2e/deep-chat.e2e.ts
```

e2e 覆盖：浏览器打开 nginx 同源页 → 收集一篇文章(POST /api/collect) → 等待 worker 归类/沉淀 → 簇综述生成并写入向量库 → 打开阅读页点「深聊」→ 发问 → 断言返回带 源1/源2 来源标签且 H2 落了 chat_session/chat_message。详见 `docs/plans/2026-06-17-stage5-progress.md` 的 e2e 记录。

## 7. 上线切换（MySQL）

`make server`（默认档）走 `application.yml` 的 MySQL：`jdbc:mysql://localhost:3306/cnotes`，Flyway 跑 `db/migration/mysql/*`。向量库生产可换 pgvector/外部库，但本地与 MVP 用 `SimpleVectorStore` 即可。建库见 `docs/plans` 各设计文档。

## 8. 常见问题

- `./gradlew` 权限：`chmod +x server/gradlew`。
- Ark 返回 400 且报 model 不支持 → 用了纯文本 `/embeddings`，改 `/embeddings/multimodal`。
- 向量维度不符 → 该模型固定 2048 维；`SimpleVectorStore` 不强校维度，混入旧维度向量会污染检索，换模型时删 `server/.data/vectorstore.json` 重建。
- nginx 404 刷新 → 缺 `try_files ... /index.html` SPA 回退。
