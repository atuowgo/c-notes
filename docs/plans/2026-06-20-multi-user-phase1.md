# 多用户阶段 1 —— 身份 + 数据隔离(地基)

> 日期:2026-06-20
> 状态:**已实现并端到端验证(后端 132 测试 / 0 失败,含跨用户隔离的真实 HTTP 链路验证)**。
> 设计见 `2026-06-20-multi-user-plaza.md`、交互见 `2026-06-20-multi-user-plaza-ux.md`。

## 范围
阶段 1 只做地基:**用户身份 + 会话 + 数据归属隔离**。广场、收录、社交是后续阶段。

## 做了什么

### 数据层(Flyway,mysql + h2 双方言)
- **V8**:`app_user`(表名规避保留字 `user`)、`auth_identity`(三方登录身份,`uk(provider,provider_uid)`)。
  种入**系统初始用户** `00000000000000000000000000000001`,承接多用户化之前的全部存量数据。
- **V9**:`article`/`note`/`tag` 加 `owner_id`;存量行回填到系统用户;文章 URL 幂等唯一键由全局
  `uk_url_hash` 改为**按所有者** `uk_owner_url(owner_id, url_hash)`——同一 URL 不同用户各存副本,互不可见。

### 身份与会话(零额外依赖)
- `JwtService`:手写 HS256 JWT(header.payload.signature),密钥来自 `auth.jwt.secret`(env-only)。
- `JwtAuthFilter`(OncePerRequestFilter):从 HttpOnly cookie `cnotes_token` 解析 JWT → 写入
  `UserContext`(ThreadLocal),请求结束清理。**无 token / 失效 → 回退系统用户**(保持单租户存量行为不破)。
- `AuthService.loginWith(ProviderUser)`:按 `(provider, providerUid)` 找身份;无则按 email 绑定已有用户
  (三方互通),仍无则建新用户。
- `AuthController`:
  - `GET /api/auth/me`(当前用户,未登录返回空体)、`POST /api/auth/logout`(过期 cookie)。
  - `GET /api/auth/login/{provider}`(取授权 URL)、`GET /api/auth/callback/{provider}`(换身份+下发 cookie+跳回)。
  - `POST /api/auth/dev-login`(本地/测试,`auth.dev-login.enabled`,**生产置 false**)——使端到端验证无需真实三方凭据。
- 三方 Provider(SPI `OAuthProvider`,未配置则 `configured()=false`、登录入口返回 503):
  `GithubOAuthProvider` / `GoogleOAuthProvider` / `WeChatOAuthProvider`(网站应用扫码 qrconnect)。
  凭据全部 env-only(`application.yml` 仅占位)。

### 隔离编排
- `CollectService`:落库 `owner_id = 当前用户`;幂等回查按所有者隔离。
- `ArticleQueryService`:收件箱列表 `eq(owner_id)`;详情非本人按 404 处理(不泄露存在性)。
- `NoteService`:想法落库带 `owner_id`;列表/检索按所有者过滤。
- `DevDataSeeder`:dev 样本归属系统用户(匿名可见)。

### 前端契约
- `@cnotes/types`:`CurrentUser`、`AuthProvider`。
- `@cnotes/api-client`:`me()` / `oauthAuthorizeUrl()` / `devLogin()` / `logout()`;
  `fetch` 统一 `credentials:'include'`(会话 cookie 随请求发送);`/me` 空体 → null。

## 端到端验证(真实 HTTP 链路 MockMvc)
- `AuthIsolationApiTest`(本阶段核心取证):
  - `inboxIsIsolatedPerUser`:dev-login 下发 cookie → Alice 带 cookie 收藏 → **Alice 收件箱见、Bob 看不到、
    匿名(系统用户)也看不到**。
  - `sameUrlCollectedByTwoUsersStaysSeparate`:同一 URL 两个用户各存副本,互不串。
  - `meReflectsSessionAndLogoutClears`:`/me` 随会话反映用户;无 cookie 返回空;`logout` 下发过期 cookie。
- `JwtServiceTest`:签发可验签、篡改/换密钥/过期一律拒绝。
- `SchemaMigrationTest`:`app_user`/`auth_identity` 存在 + 系统用户已种(h2/mysql 方言均合法)。
- 全量:`./gradlew test` → **132 / 0 失败 / 8 skipped**。前端 `pnpm -r build` 通过(web+extension 类型检查)。

## 诚实边界(留给后续阶段)
- **回退系统用户**:无 token 请求按系统用户处理(非拒绝)。这刻意保留单租户存量行为、不破既有 124 测试;
  真正"未登录禁止写"需上线后由开关收紧(`auth.require-login`,本阶段未做)。
- **worker 归属**:后台 worker 线程无 `UserContext`,其创建的标签 `owner_id` 暂为 NULL;因标签只经
  "本人文章"间接访问(文章详情已按所有者过滤),阶段 1 不构成越权读。私有标签池的 worker 归属待阶段 2/3。
- **向量库隔离**:chat RAG / 聚类的向量检索尚未按 `userId` 过滤(设计文档已列为难点),留待广场阶段统一处理。
- **三方 OAuth 实跑**:GitHub/Google/微信需真实 client 凭据 + 公网回调,无法在沙箱端到端;本阶段以 dev-login
  打通并验证完整会话+隔离链路,三方换取逻辑已实现待真实凭据联调。
