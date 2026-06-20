# 多用户(阶段 1–3)端到端验证截图

> 日期:2026-06-20 · 分支 `claude/vibrant-dijkstra-5wxegb`
> 真实链路取证:Spring Boot(H2)后端 + Vite 前端 + Playwright(Chromium)驱动真实浏览器截图。
> 数据由 dev-login + REST 收藏/收录/设分享级别播种;两位用户「阿陈(alice)」「小李(bob)」。

每张图都是浏览器实际渲染(2× 高清),非 mockup。

| 截图 | 阶段 | 验证点 |
|---|---|---|
| `01-login-modal.png` | 1 | 登录叠加层:GitHub / Google / 微信扫码三方入口 + 邮箱登录置灰预留 + 仅本地 dev 登录入口;顶栏「广场」登录前可见 |
| `02-inbox-owner.png` | 1/2 | 阿陈登录后收件箱:本人三篇文章(数据隔离,仅见自己的) |
| `03-reader-share-control.png` | 2 | 阅读页逐篇分享控件:下拉 6 级(私有→评论),当前「允许收录」打勾,顶部「分享:可收录」+ 复制公开链接图标 |
| `04-share-settings.png` | 2 | 账号默认分享设置弹窗:6 级单选 + 说明,默认「私有(不进广场)」 |
| `05-plaza-discover.png` | 3 | 精品广场发现流:按质量分排序(LLM ⭐6 > RAG ⭐3 > Agent ⭐1)+ 行为计数(🔖收藏/📥收录/👍赞/💬评论)+ 质量分/最新切换 |
| `06-public-profile.png` | 3 | 用户公开主页:阿陈 统计(3 公开 · 1 被收录 · 2 被收藏 · 0 粉丝)+ 已分享文章列表 |
| `07-inbox-collected.png` | 2 | 小李收件箱:收录卡片「📥 收录自 阿陈」+ 个人笔记,与本人原创卡片(处理中)区分 |
| `08-public-article-actions.png` | 2 | 公开文章只读视图:「来自 阿陈」+ 自动沉淀(摘要 + 要点)+ 按生效级别渐进显示的 🔖已收藏 / 📥已收录 操作条 |

## 配套自动化验证(非截图)

- 后端单元 / HTTP 端到端:`./gradlew test` → **142 tests / 0 失败 / 8 skipped**
  (含 `AuthIsolationApiTest` 跨用户隔离、`SharingApiTest` 分享/收藏/收录门槛、`PlazaApiTest` 质量分排序与公开主页)。
- 真实 H2 服务 + curl 链路 + Vite `/api` 代理(浏览器路径)复验:见各阶段提交说明。

## 复现方式

```bash
# 后端(H2 内存库,dev-login 开)
cd server && SPRING_DATASOURCE_URL="jdbc:h2:mem:cnotes;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1" \
  SPRING_DATASOURCE_USERNAME=sa SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver \
  WORKER_SCHEDULING_ENABLED=false AUTH_DEV_LOGIN=true DEEPSEEK_API_KEY=dummy ./gradlew bootRun
# 前端
cd frontend/apps/web && pnpm dev
# 浏览器打开 http://localhost:5173 → 登录用 dev 入口输入 handle(如 alice)即可
```
