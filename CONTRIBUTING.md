# 贡献指南

感谢你对 [EasyAgent](https://github.com/champion32/EasyAgent) 的关注。本文说明如何本地开发、提交改动，以及与现有架构保持一致。

## 开始之前

1. Fork 本仓库，并 clone 到本地。
2. 复制 `local.properties.example` 为 `local.properties`，填写本机 `sdk.dir` 与 API Key（**勿提交** `local.properties`）。
3. 用 Android Studio 打开项目，执行 **Sync Project with Gradle Files**。
4. 阅读模块边界：[`docs/module-responsibility-boundaries.md`](docs/module-responsibility-boundaries.md)。

## 模块边界（必读）

| 模块 | 职责 | 典型改动 |
|------|------|----------|
| `easy-agent/` | SDK：Brain、Planner、Tools、Memory、`EasyAgent` 入口 | 新 Provider、Planner 模式、内置 Tool、核心 API |
| `app/` | Demo：Compose UI、Room、ViewModel | 界面、会话持久化、Demo 配置 |

依赖方向：`app` → `easy-agent`（单向）。**请勿**在 SDK 中引入 Compose / Room 等 Demo 专用依赖。

换机或隔较长时间继续开发时，可先阅读 [`docs/vibecoding-handoff.md`](docs/vibecoding-handoff.md) 恢复上下文。

## 开发环境

与 README 保持一致，版本锁定见 `gradle/libs.versions.toml`：

- JDK **17+**
- AGP **8.7.2** · Gradle **8.9** · Kotlin **2.0.21**
- compileSdk **35** · minSdk **24**

本地验证：

```bash
./gradlew :easy-agent:test
./gradlew :app:assembleDebug
```

## 代码风格

- 使用 **Kotlin** 官方代码风格（`kotlin.code.style=official`）。
- Demo UI 使用 **Jetpack Compose** + Material3，与现有 `ChatScreen.kt` 风格一致。
- **保持最小 diff**：只改与任务相关的文件，避免顺手重构无关代码。
- 注释仅用于非显而易见的业务逻辑或协议细节；避免冗长说明。

## 提交改动

1. 从 `main` 创建功能分支，例如 `feat/xxx` 或 `fix/xxx`。
2. 确保 `./gradlew :app:assembleDebug` 通过。
3. 确认 **未** 包含密钥、编译产物或 IDE 配置：

   ```bash
   git status
   # 不应出现：local.properties、build/、.idea/、*.apk、*.keystore
   ```

4. 提交信息建议简洁说明 **为什么** 改动，例如：
   - `fix: 修复豆包流式响应解析空 delta`
   - `feat: 为 Demo 增加会话导出`

5. 向本仓库发起 Pull Request，说明：
   - 改动动机与范围
   - 如何手动验证（步骤或截图）
   - 是否影响 SDK 公开 API

## 安全与密钥

- **永远不要** 在 Issue、PR 或提交中包含 API Key、`local.properties`、keystore。
- 若不慎泄露密钥，请立即在对应云平台轮换/作废，并在 PR 中说明已处理。

## Issue 与讨论

- **Bug**：请附 Android 版本、复现步骤、Logcat（可脱敏）或截图。
- **功能建议**：说明使用场景，以及期望落在 SDK 还是 Demo。
- **问题咨询**：可先查阅 [README](README.md) 与 `docs/` 下的设计文档。

## 许可证

向本仓库贡献的代码，将按 [Apache License 2.0](LICENSE) 授权发布。
