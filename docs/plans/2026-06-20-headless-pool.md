# V2 性能 —— 无头浏览器实例池化

> 日期:2026-06-20
> 状态:**无头渲染从「每次新建 Playwright+Browser」改为「池化复用」,真实 Chromium 验证通过**。

## 做了什么

Playwright/Browser **非线程安全且要求与创建线程同线程使用**,这正是原实现每次新建的原因。
池化在不违反线程模型的前提下复用浏览器:

- 每个 `RenderWorker` 持有一条专属单线程 + 在该线程上惰性创建并<b>长期复用</b>的 Playwright+Browser;
  所有 Playwright 调用都 `submit` 到这条线程执行(严格同创建线程)。
- 每次渲染只在该线程上开一个全新 `BrowserContext`(隔离 cookie/状态)、用完即关 —— 省掉最贵的
  浏览器启动开销。
- 池容量 `extract.headless.pool-size`(默认 2):`render` 借出空闲 worker、阻塞等待、用完归还;
  全忙且超时则优雅返回空(上层回退 HTTP 抓取)。
- 自愈:Browser 断连时下次渲染自动重建;`@PreDestroy` 在各自线程上优雅关闭。
- 行为不变:`extract.headless.enabled=false`(默认)时不建池、直接返回空。

## 测试(真实 Chromium,/opt/pw-browsers)
- 后端 `./gradlew test`:**111 / 0 / 8 门控**(渲染相关 live 测试默认门控)。
- `HeadlessRendererLiveTest`(`HEADLESS_TEST=true` 真跑 Chromium,data: 页零网络隔离沙箱网络限制):
  - `rendersRealPage` 1.92s(含首次浏览器启动);
  - **`reusesPooledBrowserAcrossSequentialRenders` 3 次连续渲染共 0.64s** —— 远低于单次启动耗时,
    证明浏览器被复用而非每次新建;
  - **`handlesConcurrentRendersWithPool` 池容量 2、并发 4 请求全部成功** 0.72s,证明借出/归还/阻塞正确;
  - `disabledReturnsEmpty` 关闭即空。

复现:
```bash
PLAYWRIGHT_BROWSERS_PATH=/opt/pw-browsers HEADLESS_TEST=true \
  ./gradlew test --tests "com.cnotes.extract.HeadlessRendererLiveTest"
```

## 诚实边界
- 无前端 UI(抓取兜底路径),故无截图;验证以真实 Chromium 的池化 live 测试(复用 + 并发)为准。
- 沙箱网络限制下用 data: 页验证池化机制;真实站点渲染走同一路径。
