# V3/V4 端到端验证报告(已配置 ARK_API_KEY)

> 日期:2026-06-19
> 链路:真实浏览器(Playwright 自带 chromium)→ nginx(8088 同源)→ 后端 Spring Boot(8080)
> → H2 内存库 + SimpleVectorStore → **DeepSeek(对话)+ 火山引擎 Ark(2048 维 embedding)**。
> 密钥仅存于 gitignored 的 `server/.env`(`DEEPSEEK_API_KEY` / `ARK_API_KEY`),**不入仓**。

## 与上次的差别:本次配齐了 Ark embedding

配置 `ARK_API_KEY` 后,知识网向量源(🕸)被真实打通:
- 后台 `ClusterSummaryWorker` 对「深度学习」簇(≥2 篇)用 DeepSeek 织综述 → 用 Ark 向量化 →
  落 `SimpleVectorStore`(`server/.data/vectorstore.json`,**1 条目 / 维度 2048**,真实浮点)。
- 深聊检索 `KnowledgeRetriever` 命中该簇综述 → 回复来源出现 **🕸 知识网**。

## 测试结果(全绿)

| 层 | 命令 | 结果 |
|---|---|---|
| 后端 | `./gradlew test` | **83 通过 / 0 失败**(6 个 env 门控);本次 `ArkEmbeddingModelTest.realArkReturns2048Dim()` **真打 Ark、未跳过、返回 2048 维** |
| 前端单测 | `pnpm -r test` | api-client **12 通过** |
| 浏览器 e2e | `playwright test`(deep-chat + v3v4 + mobile)| **5/5 通过** |
| 截图取证 | `playwright test e2e/capture.e2e.ts` | **6/6 通过**,7 张截图落地 |

## 现场实证(curl,经后端)

- 深聊:`POST /api/articles/{id}/chat` → `"sources":["📄","🕸"]`(本文 + 知识网双源命中)。
- 向量库:`server/.data/vectorstore.json` → entries=1,**dim=2048**(离线 stub 为 8 维,2048 维只可能来自 Ark 真实返回)。
- 关联:`GET /api/articles/{id}/related` → LLM 判「互补」+ 理由。
- 创作:`POST /api/compose` → 成稿草稿;移动文章:簇成员数正确变更。

## 截图(`docs/ops/e2e-screenshots/`)

| 文件 | 内容 |
|---|---|
| `01-inbox.png` | 收件箱卡片流 |
| `02-reader-relations.png` | 阅读页 +「顺着这篇继续探索」(ResNet,关系=**互补**,带理由) |
| `03-deepchat-sources.png` | 深聊回复带 **📄 本文 + 🕸 知识网** 两源(Ark 向量库命中) |
| `04-clusters.png` | 知识网主题簇列表 |
| `05-cluster-correction.png` | 簇详情:演进式综述 + 纠偏(移动/合并控件) |
| `06-ideas-related.png` | 想法抽屉:相关想法(批注↔批注) |
| `07-compose-draft.png` | 由想法生成的创作草稿 |

## 复现

```bash
# server/.env 填好 DEEPSEEK_API_KEY 与 ARK_API_KEY
make server-dev                                            # H2 + 调度开(后台织综述+Ark向量化)
cd frontend && pnpm --filter @cnotes/web build             # 产 dist
nginx -p "$REPO_ROOT/" -c ops/nginx/cnotes.dev.conf        # 同源 8088
cd frontend/apps/web && pnpm exec playwright test          # 功能 e2e
pnpm exec playwright test e2e/capture.e2e.ts               # 截图取证
```
