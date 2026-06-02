package com.easyagent.brain

class QwenBrain(config: BrainConfig) : Brain {

    private val client = OpenAiCompatibleClient(
        apiKey = config.apiKey,
        baseUrl = config.baseUrl ?: DEFAULT_BASE_URL,
        model = config.model,
        timeoutMs = config.timeoutMs
    )

    override suspend fun complete(request: BrainRequest): BrainResponse {
        return client.chatCompletion(
            request.messages,
            request.tools,
            request.temperature,
            request.onTextDelta
        )
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"
        const val DEFAULT_MODEL = "qwen-plus"
    }
}
