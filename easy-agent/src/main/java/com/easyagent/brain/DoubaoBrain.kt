package com.easyagent.brain

import com.easyagent.core.AgentException

/**
 * 火山方舟（豆包）Brain，基于 [Responses API](https://www.volcengine.com/docs/82379/1585128?lang=zh)。
 *
 * [BrainConfig.model] 填写模型 ID（如 `doubao-seed-1-8-251228`）或推理接入点 ID（`ep-` 开头）。
 *
 * @see <a href="https://www.volcengine.com/docs/82379/1399008?lang=zh">快速入门</a>
 */
class DoubaoBrain(config: BrainConfig) : Brain {

    private val model = validateModel(config.model)

    private val client = DoubaoResponsesClient(
        apiKey = config.apiKey,
        baseUrl = config.baseUrl ?: DEFAULT_BASE_URL,
        model = model,
        timeoutMs = config.timeoutMs
    )

    override suspend fun complete(request: BrainRequest): BrainResponse {
        val result = if (request.onTextDelta != null) {
            client.createResponseStreaming(
                messages = request.messages,
                tools = request.tools,
                temperature = request.temperature,
                onTextDelta = request.onTextDelta
            )
        } else {
            client.createResponse(
                messages = request.messages,
                tools = request.tools,
                temperature = request.temperature
            )
        }
        return BrainResponse(
            content = result.content,
            toolCalls = result.toolCalls,
            finishReason = result.finishReason,
            usage = result.usage
        )
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3"

        /** 默认模型 ID，需在控制台开通权限。 */
        const val EXAMPLE_MODEL = "deepseek-v3-2-251201"

        const val MODEL_HINT =
            "豆包 model 需填写模型 ID（如 deepseek-v3-2-251201）或推理接入点 ID（ep- 开头）"

        fun validateModel(model: String): String {
            val trimmed = model.trim()
            if (trimmed.isEmpty()) {
                throw AgentException(MODEL_HINT)
            }
            if (trimmed == "doubao-pro-32k") {
                throw AgentException(
                    "无效的豆包 model：$trimmed。请使用完整模型 ID 或 ep- 接入点。$MODEL_HINT"
                )
            }
            return trimmed
        }
    }
}
