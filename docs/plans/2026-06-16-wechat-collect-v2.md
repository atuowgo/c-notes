# V2 — 微信收集 落地说明

> 日期:2026-06-16
> 状态:**回调端点已实现并测试/实跑验证**(明文模式)。真正接入需公众号 + 公网域名等运维前置(见下),属外部步骤。

## 已实现(代码 + 测试)

- 端点 `GET/POST /wechat/callback`(直连本服务器,无云函数中转,呼应架构 §5.2)。
  - **GET**:服务器配置校验,`sha1(sort(token,timestamp,nonce))==signature` 通过则回显 `echostr`。
  - **POST**:校验签名 → 解析消息 → 提取链接 → 复用 `CollectService` 写 `article(source_type=wechat, status=pending)` → **立即被动回复**("已收下,正在自动整理")。满足微信 **5 秒响应**限制,契合"先入库后处理"。
- 支持两类消息:**文本**(正则提取首个 URL)、**链接分享**(`MsgType=link`,取 `Url` + `Title`)。无链接时回引导语,不入库。
- 去重/并发:复用 `CollectService` 的 `url_hash` 幂等 + 唯一索引兜底。
- 安全:XML 解析禁用 DOCTYPE/外部实体(防 XXE);签名不通过返回 403。
- 配置:`wechat.token` 仅来自环境变量 `WECHAT_TOKEN`(严禁入库);`wechat.welcome-reply` 可配回复语。
- 测试:`WeChatApiTest`(校验回显/坏签名、文本带链接入库、link 消息带标题、无链接引导、POST 坏签名 403);本机 live 实跑通过(GET 回显 / POST 回复 XML / 文章落 `wechat/pending`)。

## 接入真实公众号(运维前置,外部步骤)

1. **账号**:个人订阅号即可收消息;但「服务器配置(开发模式)」与被动回复在**认证服务号**上能力最完整。认证需营业执照 + 年费(§8 已记)。
2. **公网可达 + HTTPS**:公众号后台填的 URL 必须公网可访问(80/443),`token` 与服务端 `WECHAT_TOKEN` 一致;**消息加解密方式选「明文模式」**(本实现暂不支持安全模式 AES)。
3. 配置保存时微信发 GET 校验 → 本端回显 `echostr` 即通过。
4. 之后用户给公众号**发文章链接 / 转发链接**,即落入收件箱(`source_type=wechat`)。

## 正文抓取(已补,2026-06-16)

- 新增 `ContentFetcher`(`com.cnotes.extract`):Worker 处理时若 `content` 为空(微信/裸 URL),用 **jsoup 一次 HTTP 抓取 + 启发式提取**正文兜底,并回填标题、置 `extract_method=server-fetch`;抓不到则抛错走重试/退避。
- 提取策略:优先命中常见正文容器(`#js_content` 微信、`.mw-parser-output` 维基、`[itemprop=articleBody]`、`.post-content/.entry-content` 等),否则按"段落文本量最大容器"启发式,最后回退 `body`;抓取前剥离 `script/style/nav/aside` 等页面 chrome。
- 微信公众号文章页是服务端渲染静态 HTML(正文在 `#js_content`),普通抓取即可拿全。**实跑验证**:提交一个仅含 URL 的链接,Worker 自动抓取正文(回填标题)→ DeepSeek 出摘要/标签 → done。

## 无头浏览器(三级抓取最重一级,已补,2026-06-16)

- 新增 `HeadlessRenderer`(Playwright + Chromium):HTTP 抓取结果过薄(`< extract.min-content-length`,默认 200)时,渲染 JS 后再用同一套提取逻辑取正文,取更丰富者。处理强动态/SPA 页。
- **默认关闭**(`extract.headless.enabled=false`):未启用或浏览器缺失时静默降级到 HTTP 结果,绝不影响启动。容器/root 下启动加了 `--no-sandbox --disable-dev-shm-usage`;等待策略用 `LOAD`(比 `NETWORKIDLE` 稳)。
- **启用**:设 `HEADLESS_ENABLED=true`,并在部署环境安装浏览器与系统库:
  `npx playwright install --with-deps chromium`(或 Playwright CLI)。
- **验证**:编排逻辑单测覆盖(HTTP 过薄→走无头、够厚→跳过);`HeadlessRendererLiveTest`(默认跳过,`HEADLESS_TEST=true` 开启)。本沙箱已装 Chromium + 系统库,无头浏览器能启动并导航成功,仅因**沙箱出网为 TLS 拦截代理、其 CA 不被 Chromium 信任**(`ERR_CERT_AUTHORITY_INVALID`)而无法在此完成真实页面渲染——纯环境限制,干净网络/正确 CA 的部署环境可正常工作(故未在生产代码加 `--ignore-certificate-errors`)。

## 已知缺口 / 后续

- **需登录态 / 强反爬页**:无头浏览器看不到用户登录态,可能被反爬拦截(诚实盲区)。
- **无头性能**:当前每次调用独立创建 Playwright/浏览器(兜底低频,正确性优先);高频时可改为浏览器实例池化。
- **安全模式(AES)**:当前仅明文模式;若公众号强制安全模式,需补 `EncodingAESKey` 的消息加解密。
- **关注/事件消息**:目前统一回引导语,未做关注自动回复等运营能力。
