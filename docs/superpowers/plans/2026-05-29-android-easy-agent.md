# Android Easy Agent SDK Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建 V1 在线 Agent SDK（单 module + Demo App + 开源 README），Pipeline 架构，Brain 支持三家 API，Planner 支持 FC/ReAct，Tools 可扩展。

**Architecture:** EasyAgent 入口串联 Brain/Memory/Planner/ToolRegistry；Planner 按 PlannerMode 选择 FunctionCallingPlanner 或 ReActPlanner；Memory/Tools 接口预留 V2 扩展。

**Tech Stack:** Kotlin, Coroutines, OkHttp, kotlinx.serialization, Android SDK 24+

---

### Task 1: Gradle 工程骨架

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`
- Create: `easy-agent/build.gradle.kts`, `app/build.gradle.kts`
- Create: `gradle/wrapper/gradle-wrapper.properties`

- [ ] 创建根工程与 version catalog
- [ ] 配置 `:easy-agent` library module 与 `:app` demo module
- [ ] 添加 INTERNET 权限到 manifests

### Task 2: Core 类型

**Files:**
- Create: `easy-agent/src/main/java/com/easyagent/core/*.kt`

- [ ] Message, MessageRole, ToolCall, ToolDefinition, JsonSchema
- [ ] AgentConfig, AgentResponse, AgentEvent, AgentException

### Task 3: Memory 模块

**Files:**
- Create: `easy-agent/src/main/java/com/easyagent/memory/Memory.kt`
- Create: `easy-agent/src/main/java/com/easyagent/memory/InMemoryShortTermMemory.kt`

- [ ] Memory 接口 + InMemoryShortTermMemory 实现

### Task 4: Tools 模块

**Files:**
- Create: `easy-agent/src/main/java/com/easyagent/tools/*.kt`

- [ ] AgentTool 接口, ToolResult, ToolRegistry
- [ ] DeviceInfoTool, SystemActionTool, LocalStorageTool, HttpRequestTool
- [ ] DefaultTools 工厂

### Task 5: Brain 模块

**Files:**
- Create: `easy-agent/src/main/java/com/easyagent/brain/*.kt`

- [ ] Brain 接口, BrainRequest/Response, BrainConfig, BrainFactory
- [ ] OpenAiCompatibleClient（OkHttp + JSON）
- [ ] OpenAiBrain, QwenBrain, DoubaoBrain

### Task 6: Planner 模块

**Files:**
- Create: `easy-agent/src/main/java/com/easyagent/planner/*.kt`

- [ ] Planner 接口, PlannerMode, PlannerContext, PlannerResult
- [ ] FunctionCallingPlanner, ReActPlanner, PlannerFactory

### Task 7: EasyAgent 入口

**Files:**
- Create: `easy-agent/src/main/java/com/easyagent/EasyAgent.kt`
- Create: `easy-agent/src/main/java/com/easyagent/EasyAgentJavaBridge.kt`

- [ ] Builder 模式, chat(), chatStream(), registerTool(), clearMemory()
- [ ] Java Bridge Callback API

### Task 8: Demo App

**Files:**
- Create: `app/src/main/java/com/easyagent/demo/*.kt`
- Create: `app/src/main/res/layout/*.xml`

- [ ] MainActivity + ChatViewModel
- [ ] Provider/PlannerMode/API Key 配置 UI
- [ ] 聊天气泡 + Tool 调用展示

### Task 9: README 与验证

**Files:**
- Create: `README.md`

- [ ] 开源 README（简介、架构、快速开始、扩展指南、License）
- [ ] Gradle assembleDebug 验证编译
