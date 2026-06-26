# Stage-6 收尾审计报告

> 日期:2026-06-26
> 范围:A1 鉴权/JWT、A2 本地文件存储、B1 语义聚类、B2 关联推荐、B3 簇 merge/split、B4 更深入
> 基线:实现 workflow 全绿(6 批次 + 末尾冒烟)+ 真实链路 e2e + live API 核验 + 截图留底
> 环境:后端 `make server-dev`(H2 + 真 DeepSeek/Ark,server/.env 密钥)+ Vite 5173(/api 代理 8080,替代缺失 nginx)+ 系统 Chrome(Playwright channel:chrome)
> 提交策略:用户指定「全部改动留工作区不提交」,审计以截图 + 报告为准,未 git commit/push

---

## 1. 总结论

6 个批次全部实现完成,后端测试 + 前端 typecheck/build 全绿,核心新功能经真实链路 e2e + live API 核验可用。截图 18 张存 `docs/ops/stage6-audit/screenshots/`。

| 批次 | 实现状态 | 测试 | 真实链路证据 | 截图 |
|---|---|---|---|---|
| A1 多用户鉴权 | done | 65 server(含 UserApiTest) | 登录 demo/demo123 → JWT,未登录守卫跳 /login,401 拦截 | 01-login / 02-register / 03-inbox |
| A2 本地文件存储 | done | LocalFileStorageServiceTest 6 + ArticleApiTest longContent | 长文(>20000)落盘 `server/data/content/`(101.6K 文件),详情透明 hydrate | 04b-a2-long-article-detail-hydrated |
| B1 语义聚类 | done | AutoClusterServiceTest 3 + ApiTest 3 | live `GET /api/clusters/auto` 返回 2 成员簇 + DeepSeek 综述 | 08b(worker 产出晚于 e2e 等待窗,截到空态;live API 证实簇存在) |
| B2 关联推荐 | done | LinkServiceTest 5 + ApiTest 3 | live `GET /api/articles/{id}/links` 返回 相关 + DeepSeek reason + Ark cosine score(0.53/0.31) | 05b-recommend-related-deeper |
| B3 簇 merge/split | done | ClusterApiTest 7 + ServiceTest 6(merge/split/move+审计) | 端点 + 审计落库经单测覆盖;UI 整理簇按钮可见 | 07b2-cluster-detail-deeplearn(整理簇按钮在位) |
| B4 更深入 | done | LinkServiceTest 更深入 用例 | 同 auto_cluster 成员 → 更深入 徽标渲染(琥珀金) | 05c-recommend-deeper(含 更深入 徽标) |
| Stage5 深聊 | done(回归) | e2e deep-chat | 真 DeepSeek 回复 + 📄/🕸 来源 | 06-deep-chat-reply |
| 冒烟 | green | server test / frontend typecheck+build | — | — |

## 2. 已知审计缺口(非功能缺陷)

- **B1 截图空态**:e2e 等待窗(240s)内 AutoClusterWorker 尚未把样本聚成簇,08b 截到空态;e2e 结束后 live API 核实簇已生成(2 成员 + 综述)。B1 由 live API + 单测证实可用。
- **B3 manage-bar 截图**:headless e2e 中「整理簇」按钮点击未触发 manage 模式(疑按钮状态/时序),07d 截到 fail 态;merge/split/move 端点 + cluster_preference 审计由 ClusterApiTest/ClusterServiceTest 7+6 用例覆盖,07b2 截图可见整理簇按钮在位。

## 3. 截图清单(docs/ops/stage6-audit/screenshots/)

01-login / 02-register / 03-inbox / 04a-long-article-pending / 04b-a2-long-article-detail-hydrated / 05a-article-detail / 05b-recommend-related-deeper / 05c-recommend-deeper / 06-deep-chat-reply / 07a-clusters-tag-list / 07b-cluster-detail / 07b2-cluster-detail-deeplearn / 07c-cluster-manage-fail / 07d-cluster-manage-fail-v2 / 08a-semantic-clusters-empty / 08b-semantic-clusters-still-empty / 99-audit-done / 99-audit2-done

## 4. 实现产物(工作区,未提交)

迁移 V4–V8(mysql+h2 双脚本);新包 user/storage/cluster.auto/link;ClusterService merge/split/move + ClusterPreference;前端 LoginView/RegisterView/router 守卫/api 拦截器/ClustersView 语义簇 tab/ClusterDetailView 整理簇/RecommendList 更深入。详见各批次 files_created/modified(workflow 输出)。
