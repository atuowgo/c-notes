# 多用户 + 精品广场 —— 设计评审

> 日期:2026-06-20
> 状态:**设计评审中(未实现)**。本文为实现蓝图,经评审后按四阶段分批落地,每阶段独立可验证。

## 背景与目标

当前系统是**纯单租户**:`article`/`note`/`tag`/`cluster` 无任何归属字段,
`SimpleVectorStore` 是单一全局文件,worker 轮询全表。要让用户能**选择性分享**自己收藏的文章、
想法、知识库,并形成一个「精品广场」的发现/互动场域,需要补三层能力:

1. **身份层** —— 谁是用户(三方授权登录,OTP 预留)。
2. **隔离层** —— 数据归属与跨用户检索隔离(向量库是隐藏难点)。
3. **广场层** —— 分级分享权限 + 质量分排序的内容发现 + 社交互动。

分享权限按用户要求分级(单调递增):
`只读 → 可收藏 → 可收录 → 可发表想法(批注)→ 可评论`。

## 改造的爆炸半径(诚实评估)

多用户化的真正成本不在「加一张 user 表」,而在单租户假设已渗透到每条查询、worker、向量库:

| 现状(单租户假设) | 多用户后必须改 |
|---|---|
| `article` 无归属 | +`owner_id`,所有 list/get 查询加过滤 |
| `worker` 轮询全表 `status=pending` | 处理逻辑不变(跨用户处理 OK),但产物归属要对 |
| `SimpleVectorStore` 单一全局文件 | **最痛**:语义检索/聚类会跨用户串味 → 按 `userId` metadata 过滤 |
| `tag` 全局池 | 决策已定:**私有标签池**,加 `owner_id` |
| `cluster` 全局聚类 | 按 user 聚类;chat RAG 仅命中「我的 + 我收录的」 |

> 结论:向量库隔离是隐藏的大工程。采用 SimpleVectorStore 原生 metadata filter(`userId`),
> **不拆多文件**——避免文件句柄与一致性维护成本。

## 已锁定的产品决策(评审回填)

1. **登录方式**:先收敛为 **GitHub / Google / 微信扫码** 三方授权;
   暂不接邮箱验证码(OTP),但在表结构与 provider 枚举上**预留**,将来零改表上线。
2. **「收录」语义**:**链接引用 + 本地笔记**,不深拷贝。原文删除时收录方见「原文已撤回」占位 +
   保留自己的 `personal_note`;发布者主页显示「被收录 N 次」作为激励。
3. **标签池**:**私有标签池**——每人独立分类体系;跨用户发现走广场质量分,不靠标签。
4. **广场质量分**:行为分纳入 **收录 / 点赞 / 收藏 / 评论** 四个用户信号 + AI 深度分。

## 数据模型

### 身份(新表)

```sql
-- V8
user(
  id            VARCHAR(32) PK,
  email         VARCHAR(255) NULL,          -- 可空;三方未回传或 OTP 未上线时为空
  nickname      VARCHAR(64),
  avatar_url    VARCHAR(512),
  default_share_level VARCHAR(20) DEFAULT 'PRIVATE',
  create_time   DATETIME
)

auth_identity(
  id            VARCHAR(32) PK,
  user_id       VARCHAR(32),               -- → user.id
  provider      VARCHAR(20),               -- github | google | wechat (email 预留)
  provider_uid  VARCHAR(128),              -- GitHub id / Google sub / 微信 openid
  create_time   DATETIME,
  UNIQUE(provider, provider_uid)
)

-- email_otp:本期不建表,仅在设计上预留;将来加验证码登录只需新增此表 + 一个 provider 分支:
-- email_otp(email, code_hash, expires_at, consumed)
```

**账号绑定**:首次登录建 `user` + 一条 `auth_identity`;若 provider 回传 email 命中已有
`user.email`,则把新 identity 绑到同一 user(三方互通)。email 为空时各自独立,OTP 上线后再补主动绑定。

### 归属(改现有表)

```sql
-- V9
ALTER TABLE article ADD COLUMN owner_id    VARCHAR(32);
ALTER TABLE article ADD COLUMN share_level VARCHAR(20) DEFAULT NULL;  -- NULL=继承账号默认
ALTER TABLE note    ADD COLUMN author_id   VARCHAR(32);
ALTER TABLE note    ADD COLUMN visibility  VARCHAR(20) DEFAULT 'PRIVATE'; -- PRIVATE | PUBLIC
ALTER TABLE tag     ADD COLUMN owner_id    VARCHAR(32);
ALTER TABLE cluster ADD COLUMN owner_id    VARCHAR(32);   -- 若 cluster 持久化;否则在聚类入参按 user 过滤
```

> 迁移既有单租户数据:V9 同时把存量行的 `owner_id` 回填为一个「系统初始用户」,避免孤儿数据。

### 收录 + 社交(新表)

```sql
-- V10 收录(链接引用)
collection(id, user_id, source_article_id, personal_note TEXT, collected_at)

-- V11 互动信号 + 社交
bookmark(user_id, article_id, create_time, PK(user_id, article_id))      -- 收藏(只读列表)
article_like(user_id, article_id, create_time, PK(user_id, article_id))  -- 点赞
comment(id, article_id, author_id, parent_id NULL, body TEXT, create_time) -- 线程式评论
follow(follower_id, followee_id, create_time, PK(follower_id, followee_id))
```

## 权限分级 —— 单调 enum

```java
enum ShareLevel { PRIVATE, READ_ONLY, BOOKMARKABLE, COLLECTABLE, ANNOTATABLE, COMMENTABLE }
```

能力随级别**单调累加**,一个 `level.ordinal() >= required.ordinal()` 判定全部权限:

| 级别 | 解锁能力 |
|---|---|
| `READ_ONLY` | 看全文 / AI 摘要 / 标签 |
| `BOOKMARKABLE` | + 加入「我的阅读列表」(`bookmark`,轻,不进知识库) |
| `COLLECTABLE` | + 「收录到我的知识库」(`collection`,链接引用 + 本地笔记) |
| `ANNOTATABLE` | + 在原文发表**公开批注**(`note.visibility=PUBLIC`) |
| `COMMENTABLE` | + 线程式评论(`comment`) |

文章生效级别 = `article.share_level ?? user.default_share_level`(逐篇覆盖账号默认)。

## 精品广场质量分

利用已有 AI 能力做排序,这是普通分享社区做不到的 edge:

```
质量分 = 行为分 + AI深度分
行为分 = 收录×W_collect + 点赞×W_like + 收藏×W_bookmark + 评论×W_comment
AI深度分 = 摘要丰富度 + 该文在 cluster 中的连通度(degree)
```

- 默认权重(可配 `plaza.score.*`):`收录3 / 点赞2 / 收藏2 / 评论1`。
- **冷启动**:用户量小时把 AI 深度分权重调高、行为分调低,避免排序全靠零星点赞。
- 文章在知识网络里**连通度越高 = 越是枢纽知识 = 越该被推荐**,是 `cluster`/`ArticleRelation` 数据的天然变现。

广场信息流:
```
广场首页
├── 发现流:全平台公开文章,按 [质量分 × 新鲜度] 排序
├── 关注流:我关注的用户最新公开内容
└── 话题流:按 AI 聚类主题浏览
用户公开主页:已分享文章 + 公开知识地图(cluster 可视化)
```

## 隔离层落地要点

- **Spring Security + JWT**:JWT 放 HttpOnly Cookie;`UserIdFilter` 从 token 解出 `userId` 放入
  请求上下文(ThreadLocal / `SecurityContext`)。
- **查询过滤**:所有「我的」列表/详情查询加 `owner_id = currentUser`;广场查询走独立的
  「公开可见」过滤(`生效 share_level >= READ_ONLY`)。
- **向量库**:写入时给 metadata 打 `userId`;chat RAG / 聚类检索按 `userId` filter,
  收录的文章额外允许命中其 `source` 的向量(或在收录时复制一份带本人 `userId` 的向量条目)。
- **worker 不变**:跨用户串行处理 pending,产物写回各自 `owner_id`。

## 落地顺序(四阶段,每阶段独立可验证)

1. **地基(最大、不可逆)**:`user`/`auth_identity` 表 + 微信/GitHub/Google 三方登录 + JWT +
   `UserIdFilter`;现有表加 `owner_id` + 存量回填;全部查询加过滤;向量库加 `userId` metadata。
2. **共享 + 收录**:`share_level` 字段 + 账号默认设置 UI + 公开文章匿名只读 URL +
   收藏(`bookmark`)/ 收录(`collection`)按钮与接口。
3. **广场发现**:公开 Feed API(质量分排序,分页)+ 广场首页 Vue 页 + 用户公开主页。
4. **社交互动**:公开批注(`ANNOTATABLE`)+ 评论(`comment`)+ 点赞(`article_like`)+
   关注(`follow`)+ 通知。

> 阶段 1 是其余三阶段的前提且改动面最广,适合单独成一个可验证 commit。

## 诚实边界与风险

- **存量数据迁移**:V9 必须可靠回填 `owner_id`,否则单租户老数据成孤儿。需在迁移测试中覆盖。
- **向量库隔离**:SimpleVectorStore 的 metadata filter 在大体量下是线性扫描;用户/文章规模增长后
  需迁移到带索引的向量库(pgvector / Milvus)。本期先用 filter,留接口。
- **匿名只读**:公开文章的免登录访问需要一条不经 `UserIdFilter` 的白名单路由,注意别泄露私有字段
  (`dom_snapshot`、私有 `note`、`last_error` 等)——公开 DTO 与内部实体必须分离。
- **OTP 预留**:`user.email` 可空 + `auth_identity.provider` 可扩,确保将来加验证码登录不改表。
