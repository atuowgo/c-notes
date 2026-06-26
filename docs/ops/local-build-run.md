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

e2e 覆盖：浏览器打开 nginx 同源页 → 点开已就绪文章 →点「深聊」FAB → 发问 → 断言 AI 回复带 📄/🕸 来源标签;第二轮追问验证会话连续(同 sessionId,证明 H2 往返)。chat_session/chat_message 的行级落库断言由后端 `ChatPersistenceTest`/`ChatApiTest` 对同一 H2 覆盖(内存库为单 JVM,node 测试进程无法直连)。详见 `docs/plans/2026-06-17-stage5-progress.md` 的 P9 记录。

> **浏览器内核**:`playwright.config.ts` 的 chromium 工程用 `channel: 'chrome'`(系统已装的 Google Chrome),不依赖 Playwright 自带内核。本机网络受限无法从 `cdn.playwright.dev` 下载自带 chromium 时,这是默认且足够的真实浏览器路径;若机器无系统 Chrome 且可联网,可改回自带内核并 `pnpm exec playwright install chromium`。

## 7. 上线切换（MySQL）

`make server`（默认档）走 `application.yml` 的 MySQL：`jdbc:mysql://localhost:3306/cnotes`，Flyway 跑 `db/migration/mysql/*`。向量库生产可换 pgvector/外部库，但本地与 MVP 用 `SimpleVectorStore` 即可。建库见 `docs/plans` 各设计文档。

## 8. 常见问题

- `./gradlew` 权限：`chmod +x server/gradlew`。
- Ark 返回 400 且报 model 不支持 → 用了纯文本 `/embeddings`，改 `/embeddings/multimodal`。
- 向量维度不符 → 该模型固定 2048 维；`SimpleVectorStore` 不强校维度，混入旧维度向量会污染检索，换模型时删 `server/.data/vectorstore.json` 重建。
- nginx 404 刷新 → 缺 `try_files ... /index.html` SPA 回退。

## 9. Windows 环境（实测）

> 本节为 Windows 11 + Git Bash 实跑记录，命令均可在 Git Bash 直接复制。与 §0-§8 的 macOS/Linux 视角互补，不替代之。

### 9.1 前置补齐（Windows 坑）

- **pnpm 版本锁**：项目 `packageManager` 字段锁 `pnpm@10.33.0`，corepack 默认拉的高版本不会自动降级，直接 `pnpm install` 会因版本不符报错。先固定版本：
  ```bash
  corepack prepare pnpm@10.33.0 --activate
  ```
- **make 不可用**：Windows Git Bash 不带 `make`，§3-§6 的 `make xxx` 全部用底层命令替代——后端用 `server/gradlew.bat`，前端用 `pnpm -C frontend ...`。
- **gradlew.bat**：Windows 用 `server/gradlew.bat`（不是 `./gradlew`）；§8 那条 `chmod +x server/gradlew` 在 Windows 无意义，跳过。

### 9.2 nginx 安装（本地解压，不改 PATH）

仓库根：`/e/workspace/ai/road/c-notes`，目标使 `nginx.exe` 落在 `/e/workspace/ai/road/c-notes/.nginx/nginx.exe`。

```bash
# 1. 确认当前无 nginx
nginx -v
# -> command not found (EXIT 127)

# 2. 下载 Windows stable zip
curl -L -sS -o /tmp/nginx.zip -w "HTTP:%{http_code} SIZE:%{size_download}\n" https://nginx.org/download/nginx-1.27.5.zip
# -> HTTP:200 SIZE:2110044
# 备用：若 404，WebFetch https://nginx.org/en/download.html 找当前 stable zip 真实 URL

# 3. 解压并拍平嵌套目录（zip 内含 nginx-1.27.5/ 一层）
TARGET=/e/workspace/ai/road/c-notes/.nginx
rm -rf "$TARGET"
mkdir -p "$TARGET"
unzip -q /tmp/nginx.zip -d "$TARGET"
NESTED="$TARGET"/nginx-1.27.5
if [ -d "$NESTED" ]; then
  shopt -s dotglob
  mv "$NESTED"/* "$TARGET"/
  rmdir "$NESTED"
fi
# 结果：.nginx/ 下直接为 nginx.exe, conf/, html/, logs/, temp/, docs/, contrib/

# 4. 验证
/e/workspace/ai/road/c-notes/.nginx/nginx.exe -v
# -> nginx version: nginx/1.27.5  (EXIT 0)
```

备注：
- 未改系统 PATH；调用统一用绝对路径 `/e/workspace/ai/road/c-notes/.nginx/nginx.exe`。
- `conf/nginx.conf` 在 `.nginx/conf/` 下，用于同源反代 8088 配置。
- `logs/` 与 `temp/` 已由 zip 提供，nginx 可直接写。

### 9.3 完整 e2e 步骤（Windows 等效命令，据 §6 改写）

```bash
# 0. 放密钥（§2）：根 .env 的 key_deepseek / key_embedding 已就位

# 1. 锁 pnpm 版本
corepack prepare pnpm@10.33.0 --activate

# 2. 装前端依赖
pnpm -C frontend install

# 3. 构建 web dist
pnpm --filter @cnotes/web build

# 4. 起后端（先载入 .env，再 bootRun；终端 A 保持运行，等 vectorstore seed 完成）
cd /e/workspace/ai/road/c-notes
set -a; . ./.env; set +a
server/gradlew.bat bootRun --args='--spring.profiles.active=dev --worker.scheduling.enabled=true'

# 5. 起 nginx（另开终端 B）
/e/workspace/ai/road/c-notes/.nginx/nginx.exe -p /e/workspace/ai/road/c-notes -c ops/nginx/cnotes.dev.conf

# 6. 跑 e2e
pnpm -C frontend/apps/web exec playwright test e2e/deep-chat.e2e.ts
```

### 9.4 本次实跑结果（2026-06-25，诚实记录）

- `env_keys=true`、`backend_ready=true`、`seed_ready=true`（`server/.data/vectorstore.json`=30110 bytes；clusters 中 `hasSummary=true` 共 1 个簇）。
- `nginx_ready=true`、`e2e_passed=true`，summary=`1 passed (19.0s)`。
- 测试「深聊：浏览器穿过 nginx→后端→向量库→DeepSeek，回复带 📄/🕸 来源」通过（单测 12.6s）；📄/🕸 来源断言均通过；`exit_code=0`。
- 唯一故障：nginx 首启失败（exit 1，缺 `logs/` 与 `.data/nginx/client_body_temp` 目录）；已创建 `logs/` 及 `.data/nginx/{client_body,proxy,fastcgi,uwsgi,scgi}_temp` 后重启成功，8088 就绪。e2e 无失败。
- **结论：Windows 实测通过。**
