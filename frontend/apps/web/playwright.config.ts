import { defineConfig, devices } from '@playwright/test';

/**
 * 深聊端到端(真实链路)配置。
 *
 * 被测系统是「同源生产形态」:浏览器 → nginx(8088) → 后端 Spring Boot(8080) → H2 内存库 + SimpleVectorStore。
 * 前置条件(见 docs/ops/local-build-run.md §5/§6):
 *   1) 后端 dev 档已起(make server-dev),且向量库已就绪(≥2 篇同标签 done 文章触发簇综述入库);
 *   2) 已构建 dist 并起 nginx(ops/nginx/cnotes.dev.conf,监听 8088)。
 * 本配置不自动拉起服务:服务依赖真实 DeepSeek/Ark 密钥,由手册步骤显式启动,测试只负责真实点击与断言。
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.e2e.ts',
  fullyParallel: false,
  workers: 1,
  // 一轮深聊要等真实 LLM 返回,单条断言给足超时。
  timeout: 120_000,
  expect: { timeout: 90_000 },
  reporter: [['list']],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8088',
    trace: 'retain-on-failure',
    actionTimeout: 15_000,
  },
  projects: [
    // Playwright 自带 chromium(`pnpm exec playwright install chromium`)。root 容器下加
    // --no-sandbox。真实浏览器内核,真实点击/渲染/网络行为,满足「真实端到端」要求。
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        channel: undefined,
        launchOptions: { args: ['--no-sandbox', '--disable-dev-shm-usage'] },
      },
    },
  ],
});
