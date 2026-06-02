# EasyAgent 模块责任边界

> 本文档说明 `easy-agent`（SDK Library）与 `app`（Demo Application）之间的职责划分，便于扩展功能时判断代码应落在哪一层。

## 总体关系

本项目为 Gradle 多模块 Android 工程：

| 模块 | 类型 | 包名 | 定位 |
|------|------|------|------|
| **`easy-agent`** | Android Library | `com.easyagent` | 可复用的 **AI Agent SDK** |
| **`app`** | Android Application | `com.easyagent.demo` | **Demo 参考应用**，展示如何集成 SDK |

**依赖方向**：`app` → `easy-agent`（单向）。SDK 不依赖 Demo，可被任意 Android 项目引用。

```
EasyAgent/
├── easy-agent/          # SDK library module
├── app/                 # Demo App
├── docs/                # 设计文档（含本文档）
└── README.md
```

---

## easy-agent：Agent 运行时（SDK 层）

### 职责

完成「用户输入 → LLM 推理 → Tool 调用 → 最终回复」的 **Agent 闭环**。对外唯一入口为 `EasyAgent`。

### 架构

```
User → EasyAgent → Planner → Brain ↔ Memory
                      ↓
                   ToolRegistry → Tools
```

| 子模块 | 路径 | 职责 |
|--------|------|------|
| **EasyAgent** | `EasyAgent.kt` | Builder 组装、`chat()` / `chatStream()`、`registerTool()`、`clearMemory()` |
| **Brain** | `brain/` | OpenAI / 通义千问 / 豆包 LLM 适配（含 HTTP 客户端） |
| **Planner** | `planner/` | Function Calling（默认）与 ReAct 两种推理策略 |
| **Memory** | `memory/` | `Memory` 接口 + 默认 `InMemoryShortTermMemory` |
| **Tools** | `tools/` | 内置 Tool + `ToolRegistry` + `AgentTool` 扩展点 |
| **core** | `core/` | `Message`、`AgentEvent`、`AgentResponse` 等运行时类型 |
| **Java Bridge** | `EasyAgentJavaBridge.kt` | Java 互操作 |

### 内置 Tools（V1）

| Tool | 说明 |
|------|------|
| `device_info` | 设备型号、系统版本、网络状态 |
| `system_action` | 打开 URL、分享文本、Toast |
| `local_storage` | SharedPreferences KV 读写 |
| `http_request` | HTTP GET/POST 请求 |

### 技术约束

- **依赖极少**：Coroutines + OkHttp + kotlinx.serialization
- **无 UI**：不含 Compose、Activity、ViewModel
- **无持久化**：不含 Room、SQLite；仅提供 `Memory` 接口供外部注入

### 对外 API 摘要

```kotlin
class EasyAgent {
    suspend fun chat(message: String): AgentResponse
    fun chatStream(message: String): Flow<AgentEvent>
    fun clearMemory()
    fun registerTool(tool: AgentTool)
}

class EasyAgent.Builder(context: Context) {
    fun brainConfig(config: BrainConfig): Builder
    fun plannerMode(mode: PlannerMode): Builder
    fun memory(memory: Memory): Builder
    fun maxSteps(steps: Int): Builder
    fun systemPrompt(prompt: String): Builder
    fun contextLimit(limit: Int): Builder
    fun temperature(value: Float): Builder
    fun addTool(tool: AgentTool): Builder
    fun build(): EasyAgent
}
```

### 不包含

- 聊天 UI、主题、导航
- 多会话管理、历史记录持久化
- API Key 配置界面与安全存储
- 应用级错误展示、Snackbar 等 UX

---

## app：Demo 集成层（应用层）

### 职责

把 SDK 包装成可运行的聊天 App：**UI 展示、会话管理、消息持久化、Demo 配置** 均在此层实现。

### 模块结构

| 层次 | 路径 | 职责 |
|------|------|------|
| **UI** | `demo/ui/` | Compose 聊天界面、主题、`MainActivity` |
| **编排** | `ChatViewModel.kt` | 创建 `EasyAgent`、订阅 `chatStream`、将 `AgentEvent` 映射为 UI 状态 |
| **持久化** | `demo/data/` | Room 数据库、`ChatRepository`：多会话、消息历史、Token 用量 |
| **Memory 桥接** | `demo/memory/RestoredMemory.kt` | 实现 SDK `Memory`，从 Room 恢复 User/Assistant 上下文 |
| **Demo 配置** | `ApiKeys.kt`、`DemoDefaults.kt`、`BuildConfig` | 从 `local.properties` 注入 API Key、默认模型 |
| **UI 模型** | `ChatMessage.kt`、`ChatSession.kt`、`MessageKind.kt` | 面向展示与持久化的消息/会话类型 |

### 数据流

```
用户操作
  → ChatScreen（Compose UI）
  → ChatViewModel（编排）
      ├→ ChatRepository → Room DB（持久化）
      ├→ RestoredMemory（Agent 上下文）
      └→ EasyAgent.chatStream()（SDK 执行）
           → AgentEvent 流
  → ViewModel 映射事件 → 更新 UI + 写入 Room
```

### 技术约束

- 依赖 `easy-agent` + Compose + Room + Lifecycle
- 包名 `com.easyagent.demo`，与 SDK 包名隔离

### 不包含

- LLM HTTP 调用、Planner 循环、Tool 执行逻辑（全部委托 SDK）
- Agent 核心协议定义（复用 SDK 的 `AgentEvent`、`BrainConfig` 等）

---

## 关键边界：两套「消息」模型

SDK 与 App 各有一套消息类型，**用途不同，不可混用**。

| | SDK（`com.easyagent.core.Message`） | App（`ChatMessage` + `MessageKind`） |
|---|-------------------------------------|-------------------------------------|
| **用途** | Agent 推理上下文 | UI 展示 + Room 持久化 |
| **角色** | USER / ASSISTANT / SYSTEM / TOOL | User / Assistant / Tool / System / Error |
| **范围** | 仅参与 LLM 上下文 | 含 Tool 步骤、Thinking、Error 等展示信息 |

### 会话恢复规则

App 切换会话时，**仅将 User + Assistant 消息写回 `RestoredMemory`**。Tool、System、Error 等消息只用于 UI 展示，**不参与 Agent 上下文恢复**。

```kotlin
// ChatViewModel.restoreSessionMemory() 逻辑摘要
when (entity.kind) {
    MessageKind.User.name -> MessageRole.USER
    MessageKind.Assistant.name -> MessageRole.ASSISTANT
    else -> return@forEach  // Tool/System/Error 跳过
}
```

---

## 一次发消息的调用链

```
1. 用户点击发送
2. ChatScreen → ChatViewModel.sendMessage()
3. [App] 校验 API Key
4. [App] Room 持久化 User 消息
5. [App] EasyAgent.Builder(...).memory(sessionMemory).build()
6. [SDK] agent.chatStream(text)
       → Memory 记录 user message
       → Planner 循环（Brain 调用 + Tool 执行）
       → 发出 AgentEvent 流
7. [App] ViewModel 订阅 AgentEvent
       → ToolCallStarted/Finished → 写入 Room + 更新 UI
       → TextDelta → 流式打字机效果
       → Completed → 持久化 Assistant 回复
       → Error → 展示错误气泡
```

---

## 扩展指南：功能应落在哪一层？

| 需求 | 归属 | 说明 |
|------|------|------|
| 新增 LLM Provider | `easy-agent/brain` | Brain 适配，在 `BrainFactory` 注册 |
| 新增 Planner 模式 | `easy-agent/planner` | 实现 `Planner` 接口 |
| 新增通用 Tool | `easy-agent/tools` 或 `registerTool()` | 所有集成方共享的能力 |
| 自定义 Memory（向量检索等） | `easy-agent` 接口 + 实现，或 App 注入 | 通过 `EasyAgent.Builder.memory()` 注入 |
| 聊天 UI、主题、导航 | `app` | 纯展示 concern |
| 多会话、历史记录 | `app/data` | SDK 不管理持久化 |
| 设置页、账号体系 | `app` | 应用层 concern |
| API Key 安全存储 | `app`（或独立 security 模块） | SDK 只接收 `BrainConfig.apiKey` 字符串 |
| ToolPack 插件化 | `easy-agent`（V2 Roadmap） | SDK 扩展机制 |

### 判断原则

1. **是否所有 Android Agent 集成方都需要？** → 是则放 `easy-agent`
2. **是否只与 Demo 展示/持久化相关？** → 放 `app`
3. **是否可通过 SDK 接口注入？** → 实现放 App，接口定义放 SDK（如 `Memory`、`AgentTool`）

---

## 一句话总结

| 模块 | 角色 | 负责 |
|------|------|------|
| **`easy-agent`** | Agent 引擎 | **怎么想、怎么调 Tool、怎么调 LLM** |
| **`app`** | Demo 壳子 | **怎么展示、怎么存、怎么配** |

`app` 是 SDK 消费者的参考实现，不是框架本身的一部分。

---

## 相关文档

- [README](../README.md) — 快速开始与 API 文档
- [设计规格](superpowers/specs/2026-05-29-android-easy-agent-design.md) — V1 架构与接口定义
- [实现计划](superpowers/plans/2026-05-29-android-easy-agent.md) — V1 交付清单
