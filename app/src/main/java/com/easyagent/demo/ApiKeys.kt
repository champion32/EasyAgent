package com.easyagent.demo

import com.easyagent.brain.BrainProvider

/**
 * Demo 应用 API Key 来源：打包时从 [local.properties] 注入 [BuildConfig]，
 * 运行时优先使用界面输入，为空则回退到 BuildConfig 常量。
 *
 * 在 `local.properties` 中配置（参见 [local.properties.example]）：
 * - `EASY_AGENT_OPENAI_API_KEY`
 * - `EASY_AGENT_QWEN_API_KEY`
 * - `EASY_AGENT_DOUBAO_API_KEY`
 */

object ApiKeys {

    fun forProvider(provider: BrainProvider): String = when (provider) {
        BrainProvider.OPENAI -> BuildConfig.OPENAI_API_KEY
        BrainProvider.QWEN -> BuildConfig.QWEN_API_KEY
        BrainProvider.DOUBAO -> BuildConfig.DOUBAO_API_KEY
    }

    fun resolve(provider: BrainProvider, input: String): String {
        val trimmed = input.trim()
        if (trimmed.isNotEmpty()) return trimmed
        return forProvider(provider).trim()
    }

    fun missingKeyMessage(provider: BrainProvider): String {
        val propertyName = when (provider) {
            BrainProvider.OPENAI -> "EASY_AGENT_OPENAI_API_KEY"
            BrainProvider.QWEN -> "EASY_AGENT_QWEN_API_KEY"
            BrainProvider.DOUBAO -> "EASY_AGENT_DOUBAO_API_KEY"
        }
        return "API Key 为空。请在 local.properties 配置 $propertyName 后 Sync + Rebuild，或在界面手动输入。"
    }

}
