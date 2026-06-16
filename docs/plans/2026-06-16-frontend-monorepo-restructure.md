# 前端独立化与多端架构 — 规划文档

> 日期:2026-06-16
> 状态:方向已对齐(脑爆第一轮)。技术栈、仓库组织、部署形态三项关键决策已敲定;落地细节(框架二选一、各端启动时间)随实现细化。
> 来源:基于现状(前端内嵌于 `server/.../static/index.html`)与产品/架构计划(`docs/plans/2026-06-15-knowledge-network-product-design.md` 第 4 / 5 节「多端 + 云端后台」结论)的脑爆产出。

---

## 1. 为什么要做

### 现状

- **前端寄居在后端里**:唯一的 Web 阅读端是 `server/src/main/resources/static/index.html`(318 行,内联 CSS+JS 单文件),被 Spring Boot 当静态资源打进 jar,与 `/api/*` 同源。已接通 `/api/articles`、`/api/articles/{id}`、`/api/collect` 三个真实接口。
- **设计原型独立存放**:`docs/prototype/web-reader.html`(597 行)是更完整的阅读端设计稿(含划线记想法、深聊外壳、推荐文),但未接 API,仅作视觉/交互参考。

### 痛点与驱动

产品设计文档已下结论:**「多端场景(手机收微信、电脑读长文)决定架构必为"云端后台 + 多个轻客户端(浏览器插件、微信入口、Web、移动端)+ 实时同步"」**。但当前前端:

1. **焊死在后端**——前端跟着后端构建/部署,无法独立发版;浏览器插件、移动端本就无法这样被 Spring Boot 伺服。
2. **零工程化**——单文件内联,没有模块化、类型、组件复用;多端将不可避免地重复 API 调用、DTO 形状、设计 token。
3. **没有共享层**——web、插件、微信页、移动端都要调同一套 `/api/*`、复用同一套设计语言(琥珀金 + 暖中性),现在没有任何地方承载"共享"。

**一句话目标**:把前端从 `server/static` 抽到独立的 `frontend/` monorepo,工程化(Vite + 框架),并预先架好"多端共享"的骨架,让后续每加一个端都是"加一个 app",而不是"重写一遍"。

---

## 2. 三项已对齐的关键决策

| 决策 | 选择 | 理由 |
|------|------|------|
| **技术栈** | **Vite + 框架(Vue 3 或 React,TS)** | 多端复用组件/逻辑、工程化(TS、模块化、HMR)成熟;浏览器插件/移动端生态友好。代价是引入 Node 构建链——可接受。 |
| **仓库组织** | **monorepo workspace(pnpm workspace,可选 Turborepo)** | `apps/*` 装各端、`packages/*` 装共享(API 客户端、类型、设计 token、UI),共享代码一处维护。 |
| **部署形态** | **前后端分离独立部署** | 前端独立构建产物,各端独立发布;呼应「多端 + 云端后台」形态。后端回归"纯 API 服务"。 |

> 与后端架构文档的呼应:后端「五条预留多机原则」之一是**后端无状态、前后端通过 HTTP 解耦**。前端独立化正是这条原则在前端侧的落地——后端不再关心前端怎么渲染,只暴露 `/api/*`。

---

## 3. 目标目录结构

```
c-notes/
├── server/                      # 后端不动(仅去掉 static 伺服职责,见 §6)
├── frontend/                    # 【新增】前端 monorepo 根
│   ├── package.json             # workspace 根:脚本编排、devDeps
│   ├── pnpm-workspace.yaml       # 声明 apps/* 与 packages/*
│   ├── turbo.json               # 可选:任务编排/缓存(端变多后收益明显)
│   ├── tsconfig.base.json        # 共享 TS 配置,各包/各 app 继承
│   ├── .env.example              # VITE_API_BASE_URL 等
│   │
│   ├── apps/
│   │   └── web/                  # 【本次落地】Web 阅读端(从 static/index.html 迁来)
│   │       ├── index.html
│   │       ├── vite.config.ts    # dev 代理 /api → localhost:8080
│   │       ├── package.json
│   │       └── src/
│   │           ├── main.ts
│   │           ├── App.*         # 收件箱 + 阅读页 + 收藏弹窗
│   │           ├── views/        # Inbox / Reader
│   │           └── components/   # Card / DistillCard / CollectModal …
│   │       # 后续:apps/extension(浏览器插件)、apps/mobile、apps/wechat
│   │
│   └── packages/
│       ├── api-client/           # 【本次落地】封装 fetch:listInbox / getArticle / collect
│       ├── types/                # 【本次落地】共享 DTO 类型(镜像后端 DTO)
│       ├── design-tokens/        # 【本次落地】琥珀金设计 token(从 index.html 抽出)
│       └── ui/                   # 【后置】跨 DOM 端共享组件(web + extension)
│
└── docs/prototype/web-reader.html  # 保留为设计参考(见 §7)
```

### 关键洞察:真正"跨所有端"的共享层是框架无关的

- **`packages/types` 与 `packages/api-client` 是纯 TS,框架无关**——web、插件、微信页、甚至 React Native 移动端都能直接用。这是多端复用的**真·核心资产**,也是本次最该先立起来的东西。
- **`packages/ui` 只在 DOM 端之间共享**(web + 浏览器插件,因为都是同一框架的组件)。移动端若走原生/Flutter/RN,复用的是 types + api-client,而非 UI 组件。
- **结论**:框架二选一(Vue/React)其实是**低风险决策**——选错最多影响 `apps/web` + `apps/extension` + `packages/ui`,动不到最值钱的 types/api-client。所以不必在框架上纠结过久。

### 框架建议(待你拍板,非阻塞)

- **Vue 3 + TS**:更轻、上手快、单人自用项目心智负担小,与原型的清爽风格契合;在浏览器插件里也成熟。
- **React + TS**:生态最大,若 V3 移动端打算走 **React Native**,可与 web 共享更多(但 UI 层跨 web/RN 共享仍有限)。
- **倾向**:**Vue 3**(自用优先、简单为王);若你已笃定移动端走 RN,则选 React。**这一项我会在动手前用一个问题跟你最终确认。**

---

## 4. 共享包设计(本次落地三个)

### 4.1 `packages/types` — 共享 DTO 类型

镜像后端现有 DTO,作为多端的"数据契约单一来源":

```ts
// 对应 server 的 ArticleCardDto / ArticleDetailDto / CollectRequest
export type ArticleStatus = 'pending' | 'processing' | 'done' | 'failed';
export type SourceType = 'browser' | 'wechat';

export interface ArticleCard {
  id: string; title?: string; author?: string;
  sourceType: SourceType; summary?: string;
  status: ArticleStatus; createTime: string;
}
export interface ArticleDetail extends ArticleCard {
  content?: string; keyPoints: string[];
}
export interface CollectRequest {
  url: string; title?: string; author?: string;
  content?: string; domSnapshot?: string; sourceType?: SourceType;
}
```

> 注:MVP 阶段手写镜像即可(后端 DTO 很少)。将来若想消除"前后端类型漂移",可评估由后端 OpenAPI 自动生成 TS 类型——记进待定项,不阻塞。

### 4.2 `packages/api-client` — 类型化 API 客户端

把现在散落在 `index.html` 里的三处 `fetch('/api/...')` 收敛成一个客户端,**baseURL 可配置**(这是"前后端分离"的关键——不同端、不同环境指向不同后端):

```ts
import type { ArticleCard, ArticleDetail, CollectRequest } from '@cnotes/types';

export function createClient(baseUrl = '') {
  const json = async (r: Response) => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json(); };
  return {
    listInbox: (): Promise<ArticleCard[]> => fetch(`${baseUrl}/api/articles`).then(json),
    getArticle: (id: string): Promise<ArticleDetail> =>
      fetch(`${baseUrl}/api/articles/${encodeURIComponent(id)}`).then(json),
    collect: (req: CollectRequest): Promise<{ id: string }> =>
      fetch(`${baseUrl}/api/collect`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(req),
      }).then(json),
  };
}
```

- **web** 用空 baseURL(经 dev 代理 / 生产同源反代,见 §5)。
- **浏览器插件 / 移动端**用绝对地址 `VITE_API_BASE_URL` 指向云端后台。
- 将来加鉴权(JWT)、重试、错误规范化,都只改这一处——呼应后端"任务投递抽象一层"的同构思路。

### 4.3 `packages/design-tokens` — 设计 token

把 `index.html` `:root` 里那套琥珀金变量(`--bg/--ink/--gold/--radius/--shadow/--maxw` 等)抽成 token,**同时导出 CSS 变量与 TS 常量**,让 web/插件/(未来)移动端共用同一套设计语言:

```ts
export const tokens = {
  color: { bg: '#f6f4f0', surface: '#fff', ink: '#23201c', gold: '#b45309', /* … */ },
  radius: '14px', maxWidth: '760px',
} as const;
```
并产出一份 `tokens.css`(`:root{ --bg: … }`)供直接 `@import`。

---

## 5. 开发与部署:跨域怎么处理

前后端分离后,前端(Vite,如 `localhost:5173`)与后端(`localhost:8080`)不同源。处理方式:

- **开发期**:Vite dev server **代理** `/api` → `http://localhost:8080`。前端代码仍用相对路径,**零 CORS**,体验同现在。
  ```ts
  // vite.config.ts
  server: { proxy: { '/api': 'http://localhost:8080' } }
  ```
- **生产期(推荐 · 同源反代)**:Nginx 同时伺服前端静态产物 + 把 `/api` 反代到后端。**前端与后端对外同源,无需 CORS**,最干净。
- **生产期(备选 · 真跨域)**:前端走对象存储/CDN,直连后端 API 域名 → 后端开 **CORS**(`@CrossOrigin` 或全局 `CorsConfiguration`)。浏览器插件、移动端**本就是跨域调用**,所以后端**无论如何最终都要支持 CORS / 鉴权**——这部分随 V2 多端接入时落地,本次不强求。

> 部署产物:`pnpm --filter web build` → `apps/web/dist/`(纯静态),交给 Nginx/对象存储。后端 jar 不再含前端。

---

## 6. 后端侧改动(最小化)

1. **去掉静态伺服职责**:迁移完成、`frontend/apps/web` 能独立跑通后,删除 `server/src/main/resources/static/index.html`。
   - *过渡策略*:可先保留 `static/index.html` 作为"后端自带的应急简版页",待 `frontend` 部署链路验证通过再删,避免青黄不接。本计划倾向**迁移验证通过后即删**,保持单一前端来源,不留两份会漂移的 UI。
2. **CORS**:本次若走"同源反代",**后端不需要任何改动**;若选真跨域或为后续插件铺路,加一个全局 CORS 配置(仅放行前端来源)。**默认本次不动后端代码**,把 CORS 留到多端真正接入时。
3. 后端 `build.gradle`、`/api/*` 接口**完全不动**。

---

## 7. 原型(web-reader.html)怎么处置

`docs/prototype/web-reader.html` 比当前 `static/index.html` 功能更全(划线记想法、深聊外壳、推荐文——对应产品文档 §6.4 / 6.5)。处理:

- **保留为设计参考**,不删。
- 本次迁移**先搬当前已接通 API 的 `index.html`**(确保"收→读"闭环不退化),落成 `apps/web` 的 v1。
- 原型里那些更丰富的 UX(划线/想法、深聊壳)作为 `apps/web` 的**演进目标**,随产品 MVP 阅读端实现计划逐步补齐——届时它们天然落在 `apps/web/src/components` 下,并复用 `packages/*`。

---

## 8. 多端预留原则(对标后端"五条预留多机")

立骨架时零成本预留,将来加端不返工:

1. **共享层框架无关**:`types`、`api-client` 不依赖任何 UI 框架 → 任何端(含 RN/Flutter via 类型契约)都能复用。
2. **API baseURL 配置化**:不硬编码后端地址 → 同一份 api-client 服务 web(同源)、插件、移动端(绝对地址)。
3. **设计 token 单一来源**:颜色/圆角/字号集中在 `design-tokens` → 多端视觉一致,改一处全端生效。
4. **app 即插即用**:新端 = `apps/` 下加一个目录 + 复用 `packages/*`,不碰其他端。
5. **鉴权/错误处理收敛在 api-client**:将来加 JWT、统一错误,只改一层 → 呼应后端无状态 + token 鉴权。

---

## 9. 落地步骤(实现计划骨架,执行时再 TDD 细化)

> 本次只做 **web 端迁移 + 三个共享包立骨架**,不碰插件/移动端(YAGNI)。

1. **脚手架**:建 `frontend/` workspace 根(`pnpm-workspace.yaml`、根 `package.json`、`tsconfig.base.json`、`.env.example`、`.gitignore` 补充 `frontend/**/dist`)。
2. **`packages/types`**:手写镜像后端三个 DTO。
3. **`packages/design-tokens`**:从 `index.html` `:root` 抽 token(CSS + TS 双出口)。
4. **`packages/api-client`**:封装三个接口,baseURL 可配置,依赖 `@cnotes/types`。
5. **`apps/web`**:Vite + 框架脚手架;把 `index.html` 的结构/样式/逻辑拆成组件(Inbox 列表、Card、Reader、DistillCard、CollectModal);数据层改用 `api-client`;样式改用 `design-tokens`;`vite.config.ts` 配 `/api` 代理。
6. **跑通验证**:`pnpm --filter web dev` + 后端起 → 收件箱/详情/收藏三条路径与现状一致。
7. **清理后端**:删 `server/.../static/index.html`(过渡策略见 §6.1)。
8. **文档**:`frontend/README.md` 写清 dev/build/部署;在产品设计文档补一句"前端已独立至 `frontend/` monorepo"。

> **网络提醒**:`pnpm install` 需访问 npm registry。若当前环境白名单不含 npm,安装/构建须在放开白名单的环境执行——同后端计划里"Maven Central"的处置方式。本会话可完成"写代码 + 结构 + git push",`install/build` 验证可能需在你本机跑。

---

## 10. 待定项(不阻塞动手)

1. **框架二选一**:Vue 3(倾向)vs React(若移动端笃定走 RN)。动手前一个问题确认。
2. **Turborepo 是否现在就上**:端只有 1 个时收益小;可先 pnpm workspace,端变多再加 `turbo.json`。倾向**先不加**。
3. **类型契约自动化**:手写镜像 vs 后端 OpenAPI 生成 TS。MVP 手写,漂移成痛点再自动化。
4. **生产部署细节**:同源反代(推荐)vs CDN+CORS。随上线环境定;本次只保证"独立可构建"。
5. **状态管理**:web 端是否需要 Pinia/Zustand 等。MVP 数据简单,先用框架内置即可,需要再引。
