# 本地启动验证手册(Stage-6 收尾:A1/A2/B1-B4)

> 目的:在一台开发机上把当前代码编译、起全栈、逐项验证 A1 鉴权 / A2 长文落盘 / B1 语义簇 / B2 关联推荐 / B3 簇整理 / B4 更深入 / Stage5 深聊。
> 基础编译/运行命令同 `docs/ops/local-build-run.md`;本文只补 Stage-6 验证清单与要点。
> profile:dev = H2 内存库 + 真 DeepSeek/Ark(读 `server/.env`),无需 MySQL/nginx。

## 1. 前置

- JDK 21、Node 20+ / pnpm 10.33(`corepack prepare pnpm@10.33.0 --activate`)
- 系统 Google Chrome(Playwright `channel:'chrome'`,不下载自带内核)
- `server/.env` 含:`DEEPSEEK_API_KEY`、`LLM_MODEL=deepseek-chat`、`ARK_API_KEY`、`ARK_EMBEDDING_BASE_URL`、`ARK_EMBEDDING_MODEL`、`ARK_EMBEDDING_DIM=2048`(A1 新增 `JWT_SECRET` 可空,dev 自签)
- 可选 nginx:仅当要复现同源 8088 形态;否则用 Vite 5173 代理即可。

## 2. 编译打包

```bash
# 根目录(macOS/Linux 用 make;Windows Git Bash 无 make,用下方底层命令)
make build            # = server-build + frontend-build

# Windows / 无 make:
server/gradlew.bat build              # 后端:编译 + 全量测试(BUILD SUCCESSFUL)
pnpm -C frontend install
pnpm -C frontend -r build             # 前端:types/api-client/web/extension 全构建
```

预期:后端测试全绿(含新 UserApiTest/AutoClusterApiTest/LinkApiTest/ClusterApiTest merge-split);前端 typecheck + build 全绿。

## 3. 启动全栈(两个终端)

```bash
# 终端 A — 后端 dev(H2 + 真 LLM + 调度开启,seed demo/demo123)
make server-dev
# Windows: cd server && set -a && . .env && set +a && gradlew.bat bootRun --args="--spring.profiles.active=dev --worker.scheduling.enabled=true"
# 等 "Started CNotesApplication";H2 控制台 http://localhost:8080/h2-console

# 终端 B — 前端 Vite(5173,/api 代理 8080)
make web
# Windows: pnpm -C frontend dev:web
# 打开 http://localhost:5173
```

DevDataSeeder(dev profile)幂等创建 `demo/demo123` 并回填样本文章归属。AutoClusterWorker 每 30s 重算语义簇;ArticleWorker 每 5s 处理 pending。

## 4. 逐项验证清单(浏览器 http://localhost:5173)

| 项 | 操作 | 预期 |
|---|---|---|
| A1 注册 | 访问 `/register` | 注册表单渲染(未登录可达) |
| A1 登录 | `/login` → demo/demo123 → 登录 | 跳转 `/` 收件箱;localStorage 存 token |
| A1 守卫 | 退出清 token → 访问 `/` | 自动跳 `/login` |
| A2 落盘 | `＋收藏链接` → 粘贴 >20000 字正文 + URL → 收藏 | 文章 done;`server/data/content/` 出现落盘文件;详情正文透明读回 |
| B1 语义簇 | 知识网 → 语义簇 tab(等 ~60s worker) | 出现语义簇卡片(≥2 篇同主题);点击展开成员 + 综述 |
| B2 关联 | 打开任一 done 文章 → 底部 RecommendList | 出现「相关」卡片 + reason(DeepSeek 生成) |
| B3 整理 | 知识网 → 标签簇 → 任一簇 → 「整理簇」 | 出现多选 + 目标簇下拉 + 拆分/移动/并入按钮 |
| B4 更深 | 打开某语义簇成员文章 → RecommendList | 出现「更深入」徽标(琥珀金,同语义簇成员) |
| Stage5 深聊 | 文章详情 → 深聊 FAB → 发问 | 真 DeepSeek 回复 + 📄/🕸 来源;第二轮续接同 session |

## 5. 真实链路 e2e(Playwright,系统 Chrome)

```bash
cd frontend/apps/web
E2E_BASE_URL=http://localhost:5173 pnpm exec playwright test stage6-audit
# 可选补强(含收 2 篇同内容长文驱动作 B1 簇 + B4 更深入):
E2E_BASE_URL=http://localhost:5173 pnpm exec playwright test stage6-audit-2
# Stage5 回归:
E2E_BASE_URL=http://localhost:5173 pnpm exec playwright test deep-chat
```

截图产物:`docs/ops/stage6-audit/screenshots/`。审计结论见 `docs/ops/stage6-audit/STAGE6-AUDIT.md`。

## 6. 直查后端 API(免浏览器)

```bash
T=$(curl -s -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"demo123"}' | grep -o '"token":"[^"]*"' | sed 's/"token":"//;s/"//')
curl -s http://localhost:8080/api/clusters/auto -H "Authorization: Bearer $T"        # B1
curl -s http://localhost:8080/api/articles -H "Authorization: Bearer $T"             # 列表
curl -s "http://localhost:8080/api/articles/<id>/links" -H "Authorization: Bearer $T" # B2/B4
```

## 7. 停止

终端 A/B 各 `Ctrl+C`。或 `make clean` 清构建产物。`server/data/`、`server/.data/` 为运行期产物(已 .gitignore),删之即重置存储与向量库。
