package com.easyagent.brain

import com.easyagent.core.Message
import com.easyagent.core.ToolDefinition

enum class BrainProvider {
    OPENAI,
    QWEN,
    DOUBAO
}

data class BrainConfig(
    val provider: BrainProvider,
    val apiKey: String,
    val model: String,
    val baseUrl: String? = null,
    val timeoutMs: Long = 60_000
)

data class BrainRequest(
    val messages: List<Message>,
    val tools: List<ToolDefinition>? = null,
    val temperature: Float = 0.7f,
    /** 非 null 时启用 SSE 流式，每收到文本片段回调一次（用于打字机 UI）。 */
    val onTextDelta: ((String) -> Unit)? = null
)

data class BrainResponse(
    val content: String?,
    val toolCalls: List<com.easyagent.core.ToolCall>? = null,
    val finishReason: String? = null,
    val usage: com.easyagent.core.TokenUsage? = null
)

interface Brain {
    suspend fun complete(request: BrainRequest): BrainResponse
    fun supportsFunctionCalling(): Boolean = true
}
