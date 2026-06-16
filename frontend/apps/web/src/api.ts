import { createClient } from '@cnotes/api-client';

// web 端:开发期空 baseURL 走 Vite 代理;生产同源反代亦空。
// 若部署为真跨域,设 VITE_API_BASE_URL 为后端绝对地址(后端需开 CORS)。
export const api = createClient(import.meta.env.VITE_API_BASE_URL ?? '');
