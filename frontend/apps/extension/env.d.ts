/// <reference types="vite/client" />
/// <reference types="chrome" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
}
interface ImportMeta {
  readonly env: ImportMetaEnv;
}

// 内容脚本 ←→ popup 的消息协议
interface ExtractRequest {
  type: 'EXTRACT';
}
type ExtractResponse =
  | { ok: true; payload: import('@cnotes/types').CollectRequest }
  | { ok: false; error: string };
