# 知识炼金炉 · 前端 monorepo

多端前端工作区。共享层(API 客户端、类型、设计 token)集中维护,各端按需复用。

## 结构

```
frontend/
├── apps/
│   ├── web/          Web 阅读端(Vite + Vue 3)
│   └── extension/    浏览器收集插件(Manifest V3 + Vue popup)
└── packages/
    ├── types/          共享 DTO 类型(与后端契约对齐)
    ├── api-client/      类型化 API 客户端,baseURL 可配置
    └── design-tokens/   琥珀金设计 token(CSS 变量 + TS 常量)
```

> 真正"跨所有端"的共享层是框架无关的纯 TS(`types` + `api-client`);`design-tokens` 同源导出
> CSS 与 TS。UI 组件目前各端自持,后续可在 `packages/ui` 抽 web+插件共享组件。

## 先决条件

- Node 20+、pnpm 10+(`corepack enable` 即可用仓库 `packageManager` 指定的版本)。

## 安装

```bash
cd frontend
pnpm install
```

## Web 阅读端

```bash
pnpm dev:web          # http://localhost:5173,/api 自动代理到 localhost:8080
pnpm build:web        # 产物在 apps/web/dist(纯静态)
```

- 后端需在 `localhost:8080` 运行(`cd server && ./gradlew bootRun`)。
- 改后端地址:`VITE_API_PROXY_TARGET=http://其它:端口 pnpm dev:web`。
- 生产推荐 Nginx 同时伺服 `dist/` 并把 `/api` 反代到后端(同源,无需 CORS);
  若真跨域,设 `VITE_API_BASE_URL` 为后端绝对地址并在后端开 CORS。

## 浏览器收集插件

```bash
pnpm build:extension  # 产物在 apps/extension/dist
```

加载到浏览器(开发):

1. Chrome / Edge 打开 `chrome://extensions`,开启「开发者模式」。
2. 「加载已解压的扩展程序」→ 选 `apps/extension/dist`。
3. 在任意文章页点插件图标 → 「＋ 收藏本页」:本地 Readability 提取正文 +
   DOM 快照,POST 到 `/api/collect`。

说明:

- 默认提交到 `http://localhost:8080`(见 `manifest.config.ts` 的 `host_permissions`)。
  上线把生产 API 域名追加进 `host_permissions`,或构建时设 `VITE_API_BASE_URL`。
- MV3 下扩展对 `host_permissions` 内的域发请求不受 CORS 限制,故**后端无需为插件改 CORS**。
- 插件安装前已打开的页面需刷新一次,内容脚本才会注入(否则 popup 会提示刷新重试)。
- 热更新开发:`pnpm dev:extension` 后加载 `apps/extension/dist`(crxjs 提供 HMR)。

## 移动端(规划)

V3 移动端走 **Capacitor 包壳本 Web 应用**:`apps/web` 构建产物直接装进原生壳,复用全部
`packages/*`。需要原生能力(分享、推送、离线)时用 Capacitor 插件的 JS API,无需写原生代码。
详见 `docs/plans/2026-06-16-frontend-monorepo-restructure.md`。
