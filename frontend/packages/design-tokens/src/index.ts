// 与 tokens.css 同源的 token 值,供需要在 JS/TS 里取色(图表、canvas、
// 非 DOM 端如 React Native)的场景使用。改动须与 tokens.css 保持一致。
export const tokens = {
  color: {
    bg: '#f6f4f0',
    surface: '#ffffff',
    ink: '#23201c',
    inkSoft: '#6b6359',
    inkFaint: '#9a9186',
    line: '#e7e2d9',
    gold: '#b45309',
    goldSoft: '#f2e6d2',
    green: '#3f7d4e',
    red: '#b3402e',
  },
  radius: '14px',
  maxWidth: '760px',
  fontSans:
    '-apple-system,BlinkMacSystemFont,"PingFang SC","Hiragino Sans GB","Microsoft YaHei",system-ui,sans-serif',
} as const;

export type Tokens = typeof tokens;
