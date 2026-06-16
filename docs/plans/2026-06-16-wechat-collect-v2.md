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

## 已知缺口 / 后续

- **强动态页 / 需登录页**:纯 HTTP 抓取拿不到(JS 渲染、付费墙、反爬)。这是三级抓取里最重的"无头浏览器渲染",留作后续硬化。
- **安全模式(AES)**:当前仅明文模式;若公众号强制安全模式,需补 `EncodingAESKey` 的消息加解密。
- **关注/事件消息**:目前统一回引导语,未做关注自动回复等运营能力。
