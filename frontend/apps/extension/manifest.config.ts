import { defineManifest } from '@crxjs/vite-plugin';

// Manifest V3。host_permissions 决定 popup 能 fetch 到哪个后端(MV3 下扩展
// 对 host_permissions 内的域发请求不受 CORS 限制,故后端无需为插件改 CORS)。
// 上线时把生产 API 域名追加到 host_permissions。
export default defineManifest({
  manifest_version: 3,
  name: '知识炼金炉 · 收集',
  version: '0.0.1',
  description: '一键把当前页面收进知识炼金炉:本地提取正文 + DOM 快照,提交后台异步沉淀。',
  action: {
    default_popup: 'src/popup/index.html',
    default_title: '收藏本页到知识炼金炉',
  },
  content_scripts: [
    {
      matches: ['<all_urls>'],
      js: ['src/content/extract.ts'],
      run_at: 'document_idle',
    },
  ],
  permissions: ['activeTab', 'scripting'],
  host_permissions: ['http://localhost:8080/*'],
});
