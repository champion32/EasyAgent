# Android Easy Agent SDK 设计规格

> 日期：2026-05-29  
> 状态：已批准

## 目标

构建一个轻量、原生、可扩展的 Android AI Agent 开发框架（V1 在线闭环），对外唯一入口 `EasyAgent`，支持 OpenAI / 通义千问 / 豆包 API，Planner 支持 Function Calling（默认）与 ReAct 两种模式。

## 范围

### V1 交付

| 模块 | V1 实现 | 扩展预留 |
|------|---------|----------|
| EasyAgent | 入口、Builder、chat/chatStream | Java Bridge |
| Brain | OpenAI / Qwen / Doubao | LocalBrain 接口 |
| Memory | InMemoryShortTermMemory | LongTerm / Vector |
| Planner | FunctionCalling + ReAct | 多步子任务 |
| Tools | 设备信息、系统能力、KV、HTTP | registerTool / ToolPack |
| Demo App | 聊天 UI + 配置切换 | — |
| README | 开源项目文档 | — |

### V2（不在 V1 范围）

- 离线模型：TFLite / ONNX / Llama.cpp
- 长期记忆：SQLite + 向量检索

## 架构

Pipeline 管道式：EasyAgent 持有 Brain / Memory / Planner / ToolRegistry，Planner 按 `PlannerMode` 选择策略。

```
User → EasyAgent → Planner → Brain ↔ Memory
                      ↓
                   ToolRegistry → Tools
```

## 项目结构

```
easy-agent/
├── easy-agent/              # SDK library module
│   └── com.easyagent/
│       ├── EasyAgent.kt
│       ├── core/
│       ├── brain/
│       ├── memory/
│       ├── planner/
│       └── tools/
├── app/                     # Demo App
├── README.md
└── docs/superpowers/
```

## 技术栈

- Kotlin 为主，Java 互操作（EasyAgentJavaBridge）
- minSdk 24，targetSdk 35
- Coroutines + OkHttp + kotlinx.serialization
- 单 module SDK + Demo App

## 核心接口

### EasyAgent

```kotlin
class EasyAgent private constructor(builder: Builder) {
    suspend fun chat(message: String): AgentResponse
    fun chatStream(message: String): Flow<AgentEvent>
    fun clearMemory()
    fun registerTool(tool: AgentTool)
}
```

### Brain

```kotlin
interface Brain {
    suspend fun complete(request: BrainRequest): BrainResponse
    fun supportsFunctionCalling(): Boolean
}
```

### Memory

```kotlin
interface Memory {
    fun add(message: Message)
    fun getContext(limit: Int = 20): List<Message>
    fun clear()
}
```

### Planner

```kotlin
enum class PlannerMode { FUNCTION_CALLING, REACT }

interface Planner {
    suspend fun run(userInput: String, context: PlannerContext): PlannerResult
}
```

### Tools

```kotlin
interface AgentTool {
    val name: String
    val description: String
    val parameters: JsonSchema
    suspend fun execute(args: Map<String, Any>): ToolResult
}
```

## V1 内置 Tools

1. **DeviceInfoTool** — 设备型号、系统版本、网络状态
2. **SystemActionTool** — 打开 URL、分享文本、Toast
3. **LocalStorageTool** — SharedPreferences KV 读写
4. **HttpRequestTool** — HTTP GET/POST

## Planner 模式

- **FUNCTION_CALLING（默认）**：利用各 API 原生 tools 能力，解析 tool_calls 循环执行
- **REACT**：System prompt 约束 Thought/Action/Observation 格式，Planner 解析文本协议
- 用户通过 `PlannerMode` 选择，默认 A

## Brain 适配

| Provider | FC 支持 | 配置 |
|----------|---------|------|
| OpenAI | ✅ | apiKey, model, baseUrl? |
| 通义千问 | ✅ | apiKey, model |
| 豆包 | ✅ | apiKey, endpointId |

## 执行流程

1. 用户输入 → Memory 记录 user message
2. Planner 循环（maxSteps 默认 10）：
   - 调用 Brain（带 tools schema + 历史上下文）
   - 若有 tool_calls → ToolRegistry 执行 → 结果回填 Memory
   - 若无 → 返回最终答案
3. Memory 记录 assistant message → 返回 AgentResponse

## Demo App

- Provider / PlannerMode 下拉选择
- API Key 运行时输入
- 聊天气泡 + Tool 调用过程展示
- 预设 Demo 指令

## 错误处理

- API 超时/错误 → `AgentException`
- maxSteps 耗尽 → 返回当前最佳结果 + 警告
- Tool 执行失败 → Observation 回填，Planner 继续或终止

## 开源 README 要求

- 项目简介与架构图
- 快速开始（Gradle 依赖、最小示例）
- API 文档（EasyAgent Builder 配置项）
- 内置 Tools 说明
- Demo 运行指南
- 扩展指南（自定义 Tool / Memory / Brain）
- License（Apache 2.0）
