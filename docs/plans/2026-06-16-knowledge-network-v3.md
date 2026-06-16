# V3 — 知识网(第一刀:主题簇 + 演进式综述)

> 日期:2026-06-16
> 状态:**主题簇 + 演进式综述已实现并实跑验证**。关联(article↔article)、embedding 聚类、簇的合并/拆分纠偏留作后续。

## 思路

按产品设计 §3 / §5.5「受控标签集是知识网的种子,tag 长成簇:加 parent_id / living_summary」:
**把已有标签升级为主题簇**,每簇维护一篇 LLM 织成的「演进式综述」(Karpathy LLM-Wiki 思路:不是罗列,而是把多篇织成一篇有脉络、有共识与分歧的概览),随成员变化异步重写。

> 为何不先用 embedding 聚类:当前模型 Provider(DeepSeek)只提供 chat、无 embedding 接口;而受控标签归类(MVP 已做)天然给出了高质量的主题分组,正是 V3 的种子。embedding 自动聚类作为后续增强。

## 已实现

- **schema**(Flyway V2,mysql+h2):`tag` 加 `living_summary` / `summary_member_count` / `summary_updated_at`。
- **`ClusterService`**:簇 = 标签 + 其下 `done` 文章;`listClusters`、`detail`、`regenerate`、`staleClusterTagIds`(成员数 ≥ `cluster.min-members`(默认 2)且综述缺失或成员数变化即为"待重写")。
- **`ClusterSummarizer`**(Spring AI `ChatClient`):把成员文章的标题/摘要/要点织成综述正文。
- **`ClusterSummaryWorker`**(`@Scheduled`,与文章 Worker 共用 `worker.scheduling.enabled` 门控):周期性重写成员变化的簇——"你睡觉时系统在工作"。
- **API**:`GET /api/clusters`(主题列表 + 篇数 + 是否已织)、`GET /api/clusters/{id}`(综述 + 成员文章)、`POST /api/clusters/{id}/regenerate`(手动重写/纠偏)。
- **前端**:顶栏「收件箱 / 知识网」切换;知识网列出主题簇 → 簇详情页展示**演进式综述卡** + 本簇文章(点开进阅读器)+ 手动重写按钮。
- **测试**:`ClusterSummarizer`(stub ChatModel)、`ClusterService`(列表/详情/staleness/重写,H2)、`ClusterApiTest`(列表/详情/重写/404);均 `@Transactional` 防共享库串扰。后端全量通过。

## 实跑验证(真实 DeepSeek)

收两篇 LLM 主题文章(《Attention Is All You Need》+《Retrieval-augmented generation》)→ 均归入「LLM 推理优化」簇(2 篇)→ 后台 worker 自动织出综述:把 Transformer 与 RAG 串成"模型内在效率提升"与"外部知识注入"两条并行互补路径的连贯概览。`GET /api/clusters/{id}` 正确返回综述 + 成员。

## 待后续

- **关联(Link)**:article↔article「为什么相关」(引用同概念/观点对立/互补);现仅有阅读端"同标签近邻"推荐。
- **embedding 聚类**:超越受控标签、按语义自动聚簇(需 embedding 模型)。
- **纠偏**:用户合并/拆分簇、把文章拖到别的簇,系统记住偏好(§3 的"自动为主 + 可轻量纠偏")。
- **移动端**:V3 的另一半(Capacitor 包壳 web),未开始。
