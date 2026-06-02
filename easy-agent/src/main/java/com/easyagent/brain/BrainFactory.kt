package com.easyagent.brain

object BrainFactory {

    fun create(config: BrainConfig): Brain {
        return when (config.provider) {
            BrainProvider.OPENAI -> OpenAiBrain(config)
            BrainProvider.QWEN -> QwenBrain(config)
            BrainProvider.DOUBAO -> DoubaoBrain(config)
        }
    }
}
