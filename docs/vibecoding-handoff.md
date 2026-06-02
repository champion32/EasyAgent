# EasyAgent Vibe Coding 交接文档

> **用途**：换机 / 新同事 / 新 Cursor 会话时，用本文档恢复项目上下文，无需依赖旧机器上的聊天历史。  
> **用法**：在新设备 Cursor 中 `@docs/vibecoding-handoff.md`，并说明「按此文档继续开发」。

最后更新：2026-06-01

---

## 1. 项目一句话

Android 多模块工程：**`easy-agent`** = 可复用 Agent SDK；**`app`** = Compose Demo（豆包默认，Room 持久化聊天，侧栏会话，SSE 打字机）。

---

## 2. 新机器 5 分钟清单

```text
1. 克隆/拷贝仓库（不要拷贝 **/build/、.gradle/）
2. 复制 local.properties.example → local.properties，填入 DOUBAO_API_KEY 等
3. Android Studio：Open → Sync Gradle → Rebuild
4. Cursor：打开 EasyAgent 根目录
5. 新对话第一条：@docs/vibecoding-handoff.md @docs/module-responsibility-boundaries.md
```

**不要提交**：`local.properties`（已在 .gitignore）

**可选同步**：用户级 Cursor Skills / MCP（本机 `~/.cursor/skills`、`~/.agents/skills`），与仓库无关。

---

## 3. 架构速查

| 模块 | 职责 |
|------|------|
| `easy-agent/` | SDK：`EasyAgent`、`Brain`、`Planner`、`Tools`、`Memory`、`AgentEvent` |
| `app/` | Demo：`ChatViewModel`、`ChatScreen`、Room `ChatRepository` |

依赖方向：`app` → `easy-agent`（单向）。详见 `docs/module-responsibility-boundaries.md`。

**两套「记忆」**（勿混淆）：

- SDK `InMemoryShortTermMemory` / `RestoredMemory`：Agent 推理上下文（User + Assistant）
- App Room：UI 展示 + 会话列表；切换会话时用 `RestoredMemory` 同步 Agent

---

## 4. 已实现功能（会话摘要）

### 4.1 配置与 Provider

- API Key：`local.properties` → Gradle `BuildConfig` → `DemoDefaults` / `ApiKeys.kt`
- 模板：`local.properties.example`
- 默认 Provider：**DOUBAO**，模型：**`deepseek-v3-2-251201`**
- UI 已隐藏 Key/Model 输入

### 4.2 豆包 Responses API

- 端点：`POST /api/v3/responses`（`DoubaoResponsesClient.kt`）
- `input` 使用 `type: message` + `content: [{type, text}]`
- System → 顶层 `instructions`
- 历史 assistant / tool / function_call 需 `status: "completed"`
- **流式**：`stream: true`，SSE 事件 `response.output_text.delta` → `AgentEvent.TextDelta`
- 完成事件 `response.completed` 解析 tool call、usage

### 4.3 Demo UI（Compose）

- `MainActivity` + `ChatScreen.kt`，Material3，侧栏 `ModalNavigationDrawer`
- Provider / Planner 下拉；Demo 快捷指令 Chips

### 4.4 会话持久化（Room v2）

- 库名：`easy_agent_chat.db`
- 表：`chat_sessions`、`chat_messages`（FK CASCADE）
- **草稿会话**：`currentSessionId == null` 不落库；多次「新会话」同一草稿；**首条用户消息发送后**才 `createSessionFromFirstMessage`
- 历史列表仅含**有消息**的会话；启动 `deleteEmptySessions()`
- `clearChat()`：删当前会话并回草稿

### 4.5 Token 用量

- 解析 `usage`：`input_tokens`/`output_tokens`/`total_tokens`（兼 Chat API 字段名）
- SDK：`TokenUsage`、`TokenUsageReport`、`AgentEvent.StepTokenUsage`
- Logcat 标签 **`EasyAgent`**：每步 DEBUG、每轮 INFO
- UI：Assistant 气泡下展示；Room 字段 `promptTokens`/`completionTokens`/`totalTokens`/`apiSteps`

### 4.6 打字机（SSE）

- Planner 传入 `BrainRequest.onTextDelta` → `AgentEvent.TextDelta`
- `ChatViewModel.streamingText` + 列表底部流式气泡（`▍`）
- 工具步骤 / Thinking 时清空流式态；完成后写 Room
- OpenAI/通义：仅 **ReAct（无 tools）** 走 Chat Completions 流式；Function Calling 仍等非流式完整响应

---

## 5. 关键文件索引

| 路径 | 说明 |
|------|------|
| `app/.../ChatViewModel.kt` | 会话、发送、流式、持久化 |
| `app/.../ui/ChatScreen.kt` | Compose 主界面 |
| `app/.../data/ChatRepository.kt` | Room 仓储 |
| `app/.../data/ChatDatabase.kt` | DB v2 + Migration 1→2 |
| `easy-agent/.../DoubaoResponsesClient.kt` | 豆包 API（含 SSE） |
| `easy-agent/.../EasyAgent.kt` | `chat()` / `chatStream()` |
| `easy-agent/.../planner/FunctionCallingPlanner.kt` | 默认 Planner |
| `easy-agent/.../core/TokenUsage.kt` | Token 解析 |
| `easy-agent/.../core/Types.kt` | `AgentEvent`（含 `TextDelta`） |
| `local.properties.example` | Key 模板 |

---

## 6. 已知问题与排错

| 现象 | 处理 |
|------|------|
| BuildConfig Key 为空 | Gradle Sync + Rebuild；检查 `local.properties` |
| `MissingParameter: input.status` | 历史/tool 项补 `status: "completed"` |
| Token 显示 `0/0/887` | 已修：需同时解析 `input_tokens`/`output_tokens` |
| 无打字机效果 | 确认豆包 Provider；看 Logcat 是否有 `data:` SSE 行 |
| 切换会话历史空白 | 检查 `observeMessages` + `selectSessionInternal` |
| 无 `gradlew` | 用 Android Studio 打开并 Sync，或 `gradle wrapper` 生成 |

---

## 7. 未做 / 可延续方向

- 侧栏删除单条历史、编辑 brief、导出聊天
- OpenAI Function Calling 流式 + tool call 增量解析
- SDK 层长期记忆；与 Room 更深整合
- 单元测试（Token 解析、SSE 解析、草稿会话逻辑）
- README 补充 Demo 特性（Room、草稿、Token、SSE）

---

## 8. 在 Cursor 里延续 Vibe Coding

### 8.1 聊天上下文不会自动跟仓库走

- Cursor 对话存在**本机**，换机默认丢失。
- **可靠做法**：代码进 Git + 本文档进 `docs/` + 可选 Rules。

### 8.2 推荐工作流

1. **新会话第一条**（复制即用）：

   ```text
   请阅读 @docs/vibecoding-handoff.md 和 @docs/module-responsibility-boundaries.md，
   了解 EasyAgent 当前进度。我要继续：[你的具体任务]。
   默认 Provider 豆包，回复用简体中文，改动遵循现有模块边界。
   ```

2. **任务尽量带文件**：`@ChatViewModel.kt` 比纯口述省 token、少幻觉。

3. **可选：项目 Rule**（`.cursor/rules/easyagent.mdc`）写入「先读 handoff」「Demo 改 app、协议改 easy-agent」「中文回复」。

4. **旧机备份（可选）**：
   - 拷贝 `%USERPROFILE%\.cursor\projects\<project-id>\agent-transcripts\*.jsonl`（体积大，仅作考古，非必需）
   - 或在本机 Cursor 导出/复制重要对话摘要，粘贴进本文档 §9

### 8.3 压缩原则（写进任何 handoff 都应遵守）

- 只记：**决策、约束、未做项、坑**，不贴长代码（代码在 Git 里）
- 每条功能：**行为一句话 + 关键文件**
- 每季度或每个大里程碑更新本文档日期与 §4

---

## 9. 会话决策 log（可追加）

| 日期 | 决策 |
|------|------|
| 2026-06 | 草稿会话：首条消息前不写 DB，多次新建视为同一草稿 |
| 2026-06 | Token：Responses API 用 input/output_tokens；Planner 多步累加 |
| 2026-06 | 打字机：豆包 SSE delta；Completed 时落 Room；工具步不打字 |

<!-- 换机后在此追加新决策，保持单页 <500 行 -->

---

## 10. Git 迁移命令参考

```bash
# 旧机：确保已 commit（勿 commit local.properties）
git status
git add -A && git commit -m "chore: snapshot before machine migration"

# 新机
git clone <your-remote-url>
cd EasyAgent
cp local.properties.example local.properties
# 编辑 local.properties 填入 Key
```

若无 remote：用 U 盘 / 网盘打包整个目录，**排除** `**/build/`、`.gradle/`、`local.properties`（新机重建 secrets）。
