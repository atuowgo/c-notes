import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

// 开发期把 /api 代理到本机后端,前端代码仍用相对路径,零 CORS。
// 生产同源反代(Nginx 同时伺服 dist + 反代 /api)时同样无需改前端。
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_API_PROXY_TARGET || 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
