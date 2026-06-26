# 本地启动验证手册(全功能点)

> 目的:在一台开发机上编译、起全栈,把 cnotes **所有功能点**端到端验证一遍(V1 脊柱 / V2 微信 / V3 知识网 / Stage5 深聊 / A1 鉴权 / A2 存储 / B1 语义簇 / B2 关联推荐 / B3 簇整理 / B4 更深入 / Ops 同源)。
> 基础命令同 `docs/ops/local-build-run.md`;本文是**全功能验证清单**。profile:dev = H2 内存 + 真 DeepSeek/Ark(读 `server/.env`),无需 MySQL/nginx。

## 1. 前置

- JDK 21;Node 20+ / pnpm 10.33(`corepack prepare pnpm@10.33.0 --activate`);系统 Google Chrome
- `server/.env`:`DEEPSEEK_API_KEY`、`LLM_MODEL=deepseek-chat`、`ARK_API_KEY`、`ARK_EMBEDDING_BASE_URL`、`ARK_EMBEDDING_MODEL`、`ARK_EMBEDDING_DIM=2048`(A1 `JWT_SECRET` 可空 dev 自签;微信 `WECHAT_TOKEN` 验微信时填)
- 可选 nginx:仅复现同源 8088;否则 Vite 5173 代理即可

## 2. 编译打包

```bash
make build            # = server-build + frontend-build
# Windows / 无 make:
server/gradlew.bat build
pnpm -C frontend install && pnpm -C frontend -r build
```
预期:后端全量测试绿(含 UserApiTest/ArticleApiTest/NoteApiTest/ChatApiTest/ClusterApiTest/AutoClusterApiTest/LinkApiTest/WeChatApiTest/CollectApiTest 等);前端 typecheck+build 绿。

## 3. 启动全栈(两个终端)

```bash
# A 后端 dev(H2 + 真 LLM + 调度,seed demo/demo123)
make server-dev
# Windows: cd server && set -a && . .env && set +a && gradlew.bat bootRun --args="--spring.profiles.active=dev --worker.scheduling.enabled=true"
# 等 "Started CNotesApplication";H2 控制台 http://localhost:8080/h2-console (jdbc:h2:mem:cnotes-dev, sa/空)

# B 前端 Vite(5173,/api 代理 8080)
make web        # Windows: pnpm -C frontend dev:web
# 打开 http://localhost:5173
```
DevDataSeeder 幂等建 demo/demo123 + 样本文章(Attention/ResNet/Rust 等,均回填归属 demo)。ArticleWorker 5s 轮询整理;ClusterSummaryWorker 与 AutoClusterWorker 30s 周期重算。

## 4. 全功能验证矩阵

### 4.1 鉴权(A1)
| 操作 | 预期 |
|---|---|
| 访问 `/register` | 注册表单(未登录可达) |
| `/login` → demo/demo123 → 登录 | 跳 `/` 收件箱;localStorage 存 token |
| 清 token 访问 `/` | 守卫跳 `/login` |
| 未带 token 调 `/api/articles` | 401 |

### 4.2 收集(V1 手填 + V2 微信 + 插件)
| 操作 | 预期 |
|---|---|
| `＋收藏链接` → 填 URL(可选标题/正文)→ 收藏 | toast「已收藏,后台处理」;收件箱出现 pending 卡片 |
| ArticleWorker 抓取(jsoup,微信公众号 `#js_content` 等)+ 整理 | 卡片转 done,带 summary/keyPoints/tags |
| 浏览器插件加载 `apps/extension`,在任一页面点收藏 | 走 Readability+Turndown → `/api/collect` 入库 |
| 微信:GET `/wechat/callback?signature=...&timestamp=...&nonce=...&echostr=...` | 原样回 echostr(签名校验) |
| 微信:POST `/wechat/callback`(文本含 URL / link 消息) | 提 URL 入库 + 被动回复 XML;无 URL 回引导语 |

### 4.3 抓取与整理(V1)
| 操作 | 预期 |
|---|---|
| 收一个长正文静态页 URL | ContentFetcher 提取正文(>min-content-length);done |
| 收一个动态页(开 `HEADLESS_ENABLED=true`) | HeadlessRenderer(Playwright)兜底渲染后提取 |
| 看 done 文章详情 | LLM 生成的 summary + keyPoints + 受控标签 |

### 4.4 收件箱与阅读器(V1)
| 操作 | 预期 |
|---|---|
| 收件箱列表 | 分页 + `X-Total-Count`;标签过滤;done 卡片可点开 |
| 阅读器 | 标题/来源/原文链接/标签/正文;「看原文 ↗」跳源站 |
| 划线正文 → 「✍ 划线记想法」→ 写想法 → 保存 | 想法落库;正文 `<mark>` 高亮;重渲染存活 |
| 💡 想法抽屉 | 本文想法 + 全部想法;跨文 quote/thought 检索 |

### 4.5 标签(V1)
| 操作 | 预期 |
|---|---|
| 整理时 TagClassifier | 命中受控集入 `article_tag`;未命中入 `tag_suggestion` |
| 同标签多篇文章 | 自动聚成标签簇 |

### 4.6 知识网-标签簇(V3)
| 操作 | 预期 |
|---|---|
| 知识网 → 标签簇 | 簇卡片(篇数 + 综述状态) |
| 进簇详情 | 演进式综述(≥2 篇时 ClusterSummaryWorker 织;可「重写综述」手动触发) |
| 收新文入簇 | 综述 `summary_updated_at` 随周期更新 |

### 4.7 深聊(Stage5 三源)
| 操作 | 预期 |
|---|---|
| 文章详情 → 深聊 FAB → 「正在聊本文」(源1 锚定) | 发问 → 真 DeepSeek 回复 |
| 回复来源标签 | 📄 本文(源1)+ 🕸 知识网(源2 向量检索,需 ≥2 同标签 done 文章触发簇综述入库) |
| 源3 WebSearch | 问时效性问题时 `@Tool` 实时搜索(DuckDuckGo)+ 真实链接 |
| 第二轮追问 | 同 sessionId 续接(chat_session/chat_message 经 H2 往返) |

### 4.8 对象存储(A2)
| 操作 | 预期 |
|---|---|
| 收 >20000 字正文 | `server/data/content/` 出现落盘文件;DB `content` 置空、`content_object_key` 记引用 |
| 打开该文章详情 | 正文透明 hydrate 读回(对调用方无感) |

### 4.9 语义簇(B1)
| 操作 | 预期 |
|---|---|
| 知识网 → 语义簇 tab(等 ~60s AutoClusterWorker) | 出现语义簇卡片(≥2 篇 embedding cosine > 0.75) |
| 点击展开 | 成员文章列表 + 语义综述(AutoClusterSummarizer) |

### 4.10 关联推荐(B2)
| 操作 | 预期 |
|---|---|
| 打开任一 done 文章 → 底部 RecommendList | 「相关」卡片 + reason(DeepSeek 生成)+ score(embedding cosine) |

### 4.11 簇整理(B3)
| 操作 | 预期 |
|---|---|
| 标签簇详情 → 「整理簇」 | 多选 + 目标簇下拉 + 拆为新簇/移动到目标/并入目标 |
| 执行拆分/移动/并入 | 文章 retag;`cluster_preference` 记审计;簇刷新 |

### 4.12 更深入(B4)
| 操作 | 预期 |
|---|---|
| 打开某语义簇成员文章 → RecommendList | 出现「更深入」徽标(琥珀金,同语义簇深一层成员) |

### 4.13 Ops 同源(可选,贴近生产)
```bash
pnpm -C frontend --filter @cnotes/web build                       # 产 dist
nginx -p "$PWD/" -c ops/nginx/cnotes.dev.conf                     # 监听 8088,/api 反代 8080,SPA 回退
# 浏览器 http://localhost:8088 → 全功能同上
```

## 5. 真实链路 e2e(Playwright,系统 Chrome)

```bash
cd frontend/apps/web
E2E_BASE_URL=http://localhost:5173 pnpm exec playwright test deep-chat        # Stage5 深聊
E2E_BASE_URL=http://localhost:5173 pnpm exec playwright test stage6-audit     # A1/A2/B1-B4 截图审计
E2E_BASE_URL=http://localhost:5173 pnpm exec playwright test stage6-audit-2   # 补强(含 B1/B4 触发)
```
截图:`docs/ops/stage6-audit/screenshots/`;审计报告:`docs/ops/stage6-audit/STAGE6-AUDIT.md`。

## 6. 直查后端 API(免浏览器)

```bash
T=$(curl -s -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"demo123"}' | grep -o '"token":"[^"]*"' | sed 's/"token":"//;s/"//')
curl -s http://localhost:8080/api/articles -H "Authorization: Bearer $T"            # 收件箱
curl -s "http://localhost:8080/api/articles/<id>" -H "Authorization: Bearer $T"     # 详情(A2 hydrate)
curl -s "http://localhost:8080/api/articles/<id>/chat" ...                           # Stage5 深聊
curl -s http://localhost:8080/api/clusters -H "Authorization: Bearer $T"            # V3 标签簇
curl -s http://localhost:8080/api/clusters/auto -H "Authorization: Bearer $T"       # B1 语义簇
curl -s "http://localhost:8080/api/articles/<id>/links" -H "Authorization: Bearer $T" # B2/B4
curl -s "http://localhost:8080/api/notes?articleId=<id>" -H "Authorization: Bearer $T" # 笔记
```

## 7. 停止

终端 A/B 各 `Ctrl+C`;`make clean` 清构建产物。`server/data/`、`server/.data/` 为运行期产物(已 .gitignore),删之重置存储与向量库。

## 8. 微信本地模拟(无公众号时)

```bash
# 签名校验:getecho
TS=12345 NONCE=abc ECHO=test
SIG=$(printf '%s\n' "$WECHAT_TOKEN" "$TS" "$NONCE" | sort | tr '\n' '' | sha1sum | awk '{print $1}')
curl "http://localhost:8080/wechat/callback?signature=$SIG&timestamp=$TS&nonce=$NONCE&echostr=$ECHO"
# 回 $ECHO 即签名通过

# 文本收录:POST XML(含 URL)
curl -X POST http://localhost:8080/wechat/callback -H 'Content-Type: application/xml' \
  --data '<xml><MsgType>text</MsgType><Content>https://example.com/x</Content></xml>'
```
