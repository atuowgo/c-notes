---
name: phased-dev
description: 按阶段编排开发流程 —— brainstorm/plan 阶段依赖 opusplan + Plan Mode（或手动 opus/fable），execute 阶段强制所有派发的 subagent 使用 sonnet 模型，与主循环模型无关。
---

# Phased Development Orchestrator

按模型成本/能力分层编排 brainstorm → plan → execute 三个阶段，全部在**同一个会话**内完成。

默认会话模型建议保持 `opusplan`：日常简单操作不在 Plan Mode 内，自动用 sonnet，不浪费；一旦进入本 skill 的长程任务流程，靠 Plan Mode 状态自动决定当前该用 opus 还是 sonnet，不需要额外的成本判断逻辑。

## 前置条件：检查 Plan Mode / 模型状态

开始阶段 1 之前，检查当前状态（能否使用 Edit/Write 等工具即可判断是否在 Plan Mode 内）：

- **当前模型是 opusplan 且不在 Plan Mode**：提醒用户 "当前是 opusplan 但不在 Plan Mode，brainstorm/plan 阶段不会自动用到 opus。建议先 Shift+Tab 进入 Plan Mode 再继续 —— 这样 opusplan 会自动切到 opus；等阶段 3 执行时退出 Plan Mode，主循环会自动落回 sonnet（阶段 3 还会对每个 subagent 显式强制 sonnet，双重保险）。"
- **当前模型是 opusplan 且已在 Plan Mode**：不需要提醒，直接开始，opus 已经在生效。
- **当前模型手动锁定为 opus/fable**：不需要提醒，直接开始。
- **当前模型是 sonnet/haiku 且不是 opusplan**：提醒用户可以 `/model opusplan` 一次性解决（之后每次进入 Plan Mode 自动用 opus，退出自动落回 sonnet），或者临时手动 `/model opus`。
- 如果用户坚持用当前状态继续，尊重用户选择，不要反复阻拦。

## 阶段 1: Brainstorm（主循环内进行）

调用 `superpowers:brainstorming`，按其流程正常进行——不派发 subagent，直接在当前对话中提问、探讨、呈现设计。

## 阶段 2: Plan（主循环内进行）

设计确认后，调用 `superpowers:writing-plans`，写出 `docs/plans/YYYY-MM-DD-<feature-name>.md`。

同样不派发 subagent，主循环模型全程沿用阶段 1 的模型。若使用 opusplan，全程保持在 Plan Mode 内即可持续用 opus。

## 阶段 3: Execute（强制所有 subagent 使用 sonnet）

计划写完后，调用 `superpowers:subagent-driven-development` 执行计划。

**硬性规则，不可省略：**
- 无论当前主循环处于什么模型（opus/fable/sonnet 均可），本阶段每一次派发 Task/Agent 调用时——
  包括 implementer subagent、spec-reviewer subagent、code-quality-reviewer subagent、
  以及收尾时 `finishing-a-development-branch` 触发的最终 code-reviewer subagent——
  **必须显式传入 `model: "sonnet"` 参数**。
- 这条规则优先于 `agents/code-reviewer.md` 里 `model: inherit` 的默认行为：调用它时同样显式覆盖为 `model: "sonnet"`。
- 主循环自身模型不变；只有派发出去的 subagent 强制用 sonnet。

## 阶段间衔接

- 阶段 1→2：设计确认后自动进入阶段 2，无需重新调用 skill。
- 阶段 2→3：计划保存后，按 `writing-plans` 的 Handoff 询问用户选择 "Subagent-Driven（本会话）" 还是 "Parallel Session（新会话）"。
  - 选 "Subagent-Driven" → 提醒用户可以退出 Plan Mode 再继续（若用 opusplan，主循环会自动落回 sonnet），并走本 skill 阶段 3 的强制 sonnet 规则。
  - 选 "Parallel Session" → 提醒用户新会话里手动 `/model sonnet`（或 opusplan 且不进 Plan Mode），本 skill 的强制规则不适用于新会话（新会话不会自动加载本 skill 的上下文）。

## Remember

- Brainstorm/Plan：不强制模型，靠 opusplan + Plan Mode 状态自动决定，或提醒用户手动切换。
- Execute：无条件对每个 subagent 派发强制 `model: "sonnet"`，即使用户没有要求；这与主循环是否退出 Plan Mode 无关，双重保险。
