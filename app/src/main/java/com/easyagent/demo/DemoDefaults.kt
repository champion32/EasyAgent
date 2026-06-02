package com.easyagent.demo

import com.easyagent.brain.BrainProvider
import com.easyagent.brain.QwenBrain

object DemoDefaults {
    const val DOUBAO_MODEL = "deepseek-v3-2-251201"
    val DEFAULT_PROVIDER = BrainProvider.DOUBAO

    fun modelFor(provider: BrainProvider): String = when (provider) {
        BrainProvider.OPENAI -> "gpt-4o-mini"
        BrainProvider.QWEN -> QwenBrain.DEFAULT_MODEL
        BrainProvider.DOUBAO -> DOUBAO_MODEL
    }

    fun apiKeyFor(provider: BrainProvider): String = ApiKeys.resolve(provider, "")
}
