# 二级抓取 —— 模型清洗 DOM 快照(补全产品设计 §5.4 三级兜底)

> 日期:2026-06-20
> 状态:**三级抓取的中间一级(模型清洗渲染后 DOM 快照)已实现并端到端实跑验证(含截图)**。

## 背景
产品设计 §5.4 三级正文抓取此前只落了第 1 级(插件 Readability)与第 3 级(服务器无头浏览器),
缺第 2 级:插件本地提取不干净时,用模型清洗插件提交的<b>渲染后 DOM 快照</b>抽正文。
`domSnapshot` 字段插件早已采集、`CollectRequest` 早已接收,但既不入库也不进处理链。

## 做了什么

- **存快照**:Flyway V7(mysql+h2)给 `article` 加 `dom_snapshot`(LONGTEXT);`Article` 加字段;
  `CollectService` 落库 `req.domSnapshot`。
- **DomSnapshotCleaner**(`extract` 包):Spring AI ChatClient,把 HTML 快照清洗成正文主体
  (去导航/广告/页脚/脚本),输入按 `extract.dom-clean.max-chars`(默认 6 万)截断控 token;
  空快照/失败优雅返回 null。
- **三级兜底编排**:`ArticleProcessor.resolveContent()` 改为按正文长度阈值逐级升级、取最丰富者:
  1. 第 1 级:插件正文(够厚直接用);
  2. 第 2 级:正文 < `min-content-length` 且有快照 → 模型清洗(不发网络),`extract_method=model-cleaned`;
  3. 第 3 级:仍过薄 → 服务器抓取(HTTP→无头),`extract_method=server-fetch`,title 兜底。
  三级都拿不到正文则抛错走退避重试。

> 前端无需改动:浏览器插件 `extract.ts` 早已提交 `domSnapshot: document.documentElement.outerHTML`。

## 测试与验证(真实链路)
- 后端 `./gradlew test`:**124 / 0 / 8 门控**。新增:
  - `DomSnapshotCleanerTest`(stub ChatModel):非空快照被清洗、空快照返回 null。
  - `ArticleProcessorTest.thinContentWithSnapshotUsesModelCleanedLevel2NotServerFetch`:
    正文过薄 + 有快照 → 用清洗结果、`extract_method=model-cleaned`、**不触发第 3 级**(`verify never fetch`)。
  - `CollectApiTest.collectStoresDomSnapshotForLevel2Fetch`:快照入库。
  - `SchemaMigrationTest` 通过(V7 在 h2/mysql 均合法)。
- **真实 DeepSeek 端到端**:`POST /api/collect`,正文给占位「加载中…」(5 字),`domSnapshot` 为一段
  把真实正文(光合作用暗反应)埋在 `<nav>登录/会员` + `<aside>广告` + `<footer>版权所有` 之间的 HTML →
  worker 第 2 级清洗 → `GET /api/articles/{id}` 正文 213 字,**含「卡尔文循环/ATP/暗反应」、不含
  「登录/广告/版权/会员/备案」**。该 URL 非真实可抓页,正文只可能来自快照清洗 → 证明走的是第 2 级。
  截图 `docs/ops/e2e-screenshots/11-level2-clean-extract.png`:杂乱页面在阅读端呈现为干净正文 +
  自动沉淀 + 标签建议。

## 诚实边界
- 触发阈值复用 `extract.min-content-length`(默认 200);正文确实短的干净文章可能被尝试升级,
  但"取最丰富者"保证不会用更差的结果覆盖,代价仅多一次模型调用(仅在有快照时)。
- 清洗质量取决于模型;快照超长按 max-chars 截断(对正文提取基本无损)。
