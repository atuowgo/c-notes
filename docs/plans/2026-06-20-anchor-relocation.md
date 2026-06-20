# V? 收尾 —— note 锚点重定位(产品设计 §8.4)

> 日期:2026-06-20
> 状态:**正文更新后划线想法锚点重定位已实现并验证(含截图)**。

## 问题
划线想法的锚点是正文字符偏移 `[start,end)`。正文一旦更新(重新抓取/刷新),偏移会失效,
高亮会错位甚至越界。

## 做了什么

### 后端
- `AnchorRelocator`(纯算法):以划线时保存的 **quote 原文** 为锚,在新正文里重新定位 ——
  能找到则取与旧 start <b>最接近</b>的那次出现(应对 quote 多次出现),返回新偏移;找不到返回空。
- `NoteService.relocateAnchors(articleId, newContent)`:遍历本文带锚点想法,命中→更新偏移,
  未命中→把锚点置 NULL(孤立想法,只存不高亮;用 `lambdaUpdate().set` 显式写 NULL)。
- `ArticleRefreshService` + `POST /api/articles/{id}/refresh`:重新抓取正文,正文变化时先重定位
  锚点再落新正文,并尽力重织摘要/要点/标签(LLM 不可用不阻断正文与锚点更新)。404/抓取失败 422。

### 前端
- api-client `refreshArticle(id)`;阅读页新增「🔄 刷新正文」入口,成功后重载想法缓存让高亮跟随新正文。

## 测试与验证(真实链路,非 mock 之处即真)
- 后端 `./gradlew test`:**120 / 0 / 8 门控**。新增:
  - `AnchorRelocatorTest`:漂移重定位、多次出现取最近、quote 消失返回空、空输入降级。
  - `NoteRelocationTest`(H2):命中重定位 + 未命中孤立(DB 中 anchor 置 NULL);正文未变不写库。
  - `ArticleRefreshApiTest`(HTTP,mock 抓取返回变化正文 + organize 抛错):正文更新、命中想法锚点
    漂移到新偏移、删去段落的想法被孤立;未知文章 404;抓取失败 422。
- 前端:typecheck 全绿;api-client 16 passed;web build 通过。
- 截图:`docs/ops/e2e-screenshots/10-anchor-refresh.png` —— 阅读页「🔄 刷新正文」入口 + 正文「自注意力」高亮。

## 诚实边界
- 重定位以 quote 文本为准;若新正文里 quote 被改写,则判为孤立(锚点置空),这是预期的安全降级。
- 真实站点刷新时正文常不变(contentChanged=false);相对内容变化的重定位由确定性测试覆盖。
