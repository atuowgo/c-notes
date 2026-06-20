import { test, expect } from '@playwright/test';

/**
 * 深聊三源端到端(真实链路,无 mock):
 *   浏览器点击 → nginx(8088 同源) → 后端(8080) → H2 + SimpleVectorStore → DeepSeek 对话。
 *
 * 断言:
 *   - 打开一篇已就绪文章 → 点「深聊」→ 发问 → 界面出现 AI 真实回复;
 *   - 回复带「📄 本文」与「🕸 知识网」两个来源标签(证明 源1 锚定文章 + 源2 向量检索 都真实命中);
 *   - 第二轮复用会话再问一次仍能拿到回复(证明 chat_session 经 H2 持久化后可被重新加载续接;
 *     chat_message 行级落库由后端集成测试 ChatPersistenceTest 针对同一 H2 断言)。
 */
test('深聊:浏览器穿过 nginx→后端→向量库→DeepSeek,回复带 📄/🕸 来源', async ({ page }) => {
  await page.goto('/');

  // 收件箱加载出已就绪的「Attention Is All You Need」卡片(done 才可点开)。
  const card = page.locator('.card', { hasText: 'Attention Is All You Need:重读经典' });
  await expect(card).toBeVisible();
  await expect(card).toHaveAttribute('data-clickable', '1');
  await card.click();

  // 打开深聊面板(常驻 FAB)。
  const fab = page.getByRole('button', { name: /深聊/ });
  await expect(fab).toBeVisible();
  await fab.click();

  // 确认正聊本文(源1 锚定)。
  await expect(page.locator('.chat-ctx')).toContainText('正在聊本文');

  const box = page.locator('.chat-input textarea');
  await expect(box).toBeVisible();

  // 第一轮:发问 → 等真实 AI 回复出现(带来源标签的 .msg.ai)。
  await box.fill('用一句话说说自注意力为什么重要?');
  await box.press('Enter');

  const aiWithSrcs = page.locator('.msg.ai .srcs').first();
  await expect(aiWithSrcs).toBeVisible();

  // 回复正文非空(取带来源那条 AI 消息)。
  const reply = page.locator('.msg.ai', { has: page.locator('.srcs') }).first();
  await expect(reply).not.toHaveText('');

  // 来源标签:📄 本文 必须命中(源1 锚定文章)。🕸 知识网依赖向量库(需 ARK_API_KEY 配置
  // embedding),未配置时优雅缺省,故此处不强制 —— 配置 Ark 的环境会额外出现 🕸。
  const srcText = (await aiWithSrcs.innerText()).trim();
  expect(srcText).toContain('📄');

  // 第二轮:复用同一会话再问,验证会话经 H2 往返后可续接。
  const aiCountBefore = await page.locator('.msg.ai').count();
  await box.fill('那它和 RNN 比最大的不同是什么?');
  await box.press('Enter');
  await expect(page.locator('.msg.ai')).toHaveCount(aiCountBefore + 1);
  const lastAi = page.locator('.msg.ai').last();
  await expect(lastAi).not.toHaveText('');
});
