# V3 收尾 —— 纯向量自动聚簇(DBSCAN)完成记录

> 日期:2026-06-20
> 状态:**「超越受控标签、按文章内容 embedding 自动聚簇」已实现并端到端实跑验证(含截图)**。

## 做了什么

此前知识网的「建议新主题」用 LLM(ClusterSuggester)。本次补上产品设计里的**纯向量路径**:
不依赖 LLM,直接对文章内容 embedding 做聚类。

### 后端(`com.cnotes.cluster`)
- `VectorClusterer`:纯算法 **DBSCAN**(余弦距离)。选 DBSCAN 而非 K-means —— 无需预设簇数 k,
  且天然把不相近的文章判为噪声(不强行归簇)。纯函数、无 IO,离线确定性可测。
- `ClusterService.vectorSuggestions()`:对 done 文章逐篇 Ark 向量化(标题+摘要)→ DBSCAN 聚簇
  → 跳过「已被同一受控标签覆盖」的组 → 启发式命名(簇内标题高频词:拉丁词 + 中文 2-gram,
  无高频词则回退首篇标题)→ 返回 `ClusterSuggestionDto`,复用既有「采纳为簇」落地路径。
  任何异常(无 key/网络/样本不足)优雅降级为空。
- `GET /api/clusters/vector-suggestions`。
- **eps 标定**:用真实 Ark 2048 维 embedding 实测——同主题文章余弦距离约 0.4~0.5,跨主题约 0.8;
  故默认 `cluster.vector.eps=0.45`(可配),`min-pts=2`。

### 前端
- `@cnotes/types` / api-client 加 `listVectorClusterSuggestions`;知识网页加「🧲 向量聚类」按钮,
  与「💡 发现新主题」并列,结果复用同一套建议卡 + 一键采纳。

## 测试与验证(真实链路,非 mock)
- 后端 `./gradlew test`:**101 / 0 / 6 门控**(+VectorClustererTest 4 例纯算法 + ClusterApiTest
  2 例 HTTP 集成,用标记法 embed 桩隔离共享库噪声)。
- 前端:api-client **16 passed**;`pnpm -r typecheck` 全绿;web build 通过。
- **真实 Ark 端到端**:收 3 篇语义相近、且受控标签未覆盖的文章(Espresso/Latte/Cappuccino)→
  worker 处理为 done(无共享标签)→ `GET /api/clusters/vector-suggestions` 用真实 Ark embedding +
  DBSCAN 把 3 篇聚成 1 簇(命名「Espresso」)。浏览器点「🧲 向量聚类」呈现该建议卡。
  截图:`docs/ops/e2e-screenshots/09-vector-cluster.png`。

## 实测距离(真实 Ark,佐证 eps 取值)
| | Espresso | Latte | Cappuccino | Attention | ResNet |
|---|---|---|---|---|---|
| 咖啡互相 | — | 0.50 | 0.41 | 0.79 | 0.83 |
| Cappuccino | 0.41 | 0.41 | — | 0.81 | 0.81 |
| Attention↔ResNet | | | | — | 0.47 |

咖啡三篇经 Cappuccino(0.41 桥接)密度连通成簇;跨主题 ~0.8 远在 eps 外。

## 诚实边界
- 命名是启发式(标题词频),非 LLM —— 保证离线可用;簇名偶尔取首篇标题,用户采纳时可改。
- eps 是全局阈值;不同语料的最优值可能不同,已做成可配项。
