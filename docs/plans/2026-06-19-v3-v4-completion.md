# V3 收尾 + V4 落地 — 完成记录

> 日期:2026-06-19
> 状态:**V3 知识网剩余项 + V4 让知识活起来,全部实现并端到端实跑验证通过**。

## 做了什么

### V3 知识网(补齐剩余)
- **文章关联(Link)** —— `com.cnotes.relation`。`GET /api/articles/{id}/related`:取共享标签候选 →
  DeepSeek 选「为什么相关」(同概念/互补/对立/延伸)+ 一句话理由 → 落 `article_relation`,稳定复现;
  LLM 不可用时按共享标签数兜底。阅读页「顺着这篇继续探索」由它驱动(无关联退回同标签近邻)。
- **纠偏** —— `cluster` 包扩展。`move-article`(移到别的簇)/`merge`(并簇 + 归档源 + `tag_merge` 重定向)
  /`split`(拆出新簇)/`suggestions`(LLM 建议新主题)/`accept-suggestion`(一键建簇)。
  `article_tag.source='user'` 记录用户钉选,`TagClassifier` 跟随合并重定向、不动用户钉选。
- **移动端** —— `apps/mobile`(Capacitor)包壳,复用 `apps/web/dist`,一处 web 多端复用。

### V4 让知识活起来
- **批注↔批注关联** —— `note_relation`,`GET /api/notes/{id}/related`(呼应/对立/延伸/同主题),LLM + 同篇兜底。
- **创作** —— `compose` 包,`POST /api/compose` 把若干想法拼装成草稿(DeepSeek)。
- **提问** —— `ChatRequest.noteId`:从想法发起深聊,后端把该想法作为**第四层来源 💭 我的想法**织入上下文。
- 前端:阅读页关联推荐(关系类型 + 理由)、簇详情纠偏 UI、知识网「💡 发现新主题」、
  想法抽屉 `💬 提问 / ✍ 创作(ComposeModal)/ 🔗 相关想法` 三钩子真正接通。

## 工程方式
受控标签集仍是知识网的种子;关联/建议/想法关联在本环境用 **DeepSeek**(无 embedding key 时的可用路径),
embedding 路径经 `EmbeddingModel` 抽象保留(配 `ARK_API_KEY` 即增强)。三个后端特性以**不相交的包**
并行实现(relation / cluster+tag / note+compose),合并后一次性集成构建。

## 实跑验证(真实链路,非 mock)
环境自备:本环境 `apt` 装 **nginx 1.24** + **chromium**;`server/.env` 有 `DEEPSEEK_API_KEY`(无 Ark key,
故 🕸 知识网向量源在此优雅缺省)。

- **后端**:`./gradlew test` → **BUILD SUCCESSFUL,83 测试 / 0 失败 / 6 env 门控跳过**。
- **前端**:`pnpm -r test` → api-client **12 passed**;`pnpm -r typecheck` 全绿;`web build` 通过。
- **浏览器 e2e(穿 nginx:8088 → 后端:8080 → H2 → DeepSeek,Playwright 自带 chromium)** → **5/5 passed**:
  - 深聊回复带 📄(🕸 依赖 Ark,缺省);
  - 关联「顺着这篇继续探索」展示后端关系类型 + 理由;
  - 知识网簇详情把文章移动到其他簇(toast 确认);
  - 想法的 相关 / 创作(真实草稿)/ 提问(真实回复)三钩子;
  - 移动视口(Pixel 7)下同份 dist 渲染/阅读/深聊 FAB 可用。
- **curl 现场复验**:`/related` 返回 LLM「互补」+ 理由;`/compose` 返回成稿草稿;`move-article` 簇成员数正确变更;
  `/notes/{id}/related` 返回 LLM 相关想法。

## 诚实边界
- **🕸 知识网向量源 / embedding 自动聚簇**:需 `ARK_API_KEY`(本环境未配),代码就绪、优雅缺省。
- **移动真机 / 模拟器**:需 Android SDK(本环境未装);已验证 `cap add android` + `cap sync` + 移动视口渲染。
- **建议簇**:文章较少且都被既有标签覆盖时返回空(预期的优雅行为)。
