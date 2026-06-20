# V2 硬化 —— 微信安全模式 AES + 关注/事件消息

> 日期:2026-06-20
> 状态:**安全模式(AES 消息加解密)与关注/事件消息自动回复已实现并集成测试通过**。

## 做了什么

### 安全模式 AES(`WeChatCrypto`)
官方 WXBizMsgCrypt 算法精简实现:
- EncodingAESKey(43 位)→ `base64decode(key+"=")` 得 32 字节 AESKey,IV 取前 16 字节;AES-256-CBC,
  **块大小 32 的 PKCS#7** 补位。
- 明文报文结构 `16 随机 + 4 长度(网络序) + 消息 + AppID`;解密后校验 AppID 防串号。
- `WeChatService`:配 `wechat.aes-key` 即开启安全模式;POST 体的 `<Encrypt>` 经 msg_signature
  验签(`sha1(sort(token,timestamp,nonce,encrypt))`)→ 解密 → 走明文处理 → 回复同样加密回包
  (`<Encrypt>+<MsgSignature>+<TimeStamp>+<Nonce>`)。控制器按 `encrypt_type=aes`(或已配 key 且报文带
  `<Encrypt>`)路由到安全路径,验签失败 403。明文模式行为不变(向后兼容)。

### 关注/事件消息
- `MsgType=event`:`subscribe`(关注)回欢迎语 `wechat.subscribe-reply`;其余事件(取关、菜单点击等)
  回 `success` 静默 ack。安全模式下 ack 不加密(微信允许)。

## 测试(集成,非 mock)
- 后端 `./gradlew test`:**109 / 0 / 6 门控**。新增:
  - `WeChatCryptoTest`:加密→解密往返还原;AppID 不匹配拒绝;空 AppID 跳过校验;非 43 位 key 拒绝。
  - `WeChatApiTest`:关注事件回欢迎语;取关事件回 `success`。
  - `WeChatSecureModeApiTest`:构造加密 link 消息 POST → 验签/解密/入库/加密回包;
    解密回包含欢迎语、文章真实入库;错误 msg_signature → 403。

## 诚实边界
- 无前端 UI(公众号 webhook),故无截图;验证以加解密往返 + 安全模式 HTTP 端到端集成测试为准。
- 生产启用安全模式需在公众号后台配置同一 Token / EncodingAESKey,并把服务器配置切到「安全模式」。
