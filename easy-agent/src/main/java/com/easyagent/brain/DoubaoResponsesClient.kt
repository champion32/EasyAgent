package com.easyagent.brain

import com.easyagent.core.AgentLogger
import com.easyagent.core.AgentException
import com.easyagent.core.Message
import com.easyagent.core.MessageRole
import com.easyagent.core.TokenUsage
import com.easyagent.core.ToolCall
import com.easyagent.core.ToolDefinition
import com.easyagent.tools.ToolRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * 火山方舟 Responses API 客户端。
 *
 * 请求体对齐官方示例：`input` 中 `content` 为 `[{type, text}]` 数组，
 * `Authorization: Bearer <apiKey>`，端点 `POST /api/v3/responses`。
 * Agent 在需要打字机效果时使用 `stream: true`（SSE）；工具调用步骤同样走流式，
 * 最终从 `response.completed` 事件解析 tool call 与 usage。
 *
 * @see <a href="https://www.volcengine.com/docs/82379/1399008?lang=zh">快速入门</a>
 * @see <a href="https://www.volcengine.com/docs/82379/1585128?lang=zh">迁移至 Responses API</a>
 */
class DoubaoResponsesClient(
    private val apiKey: String,
    private val baseUrl: String,
    private val model: String,
    timeoutMs: Long = 60_000
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun createResponse(
        messages: List<Message>,
        tools: List<ToolDefinition>?,
        temperature: Float,
        previousResponseId: String? = null
    ): DoubaoResponsesResult = withContext(Dispatchers.IO) {
        val body = buildRequestBody(messages, tools, temperature, previousResponseId, stream = false)
        executeRequest(body) { parseResponse(it) }
    }

    suspend fun createResponseStreaming(
        messages: List<Message>,
        tools: List<ToolDefinition>?,
        temperature: Float,
        onTextDelta: (String) -> Unit,
        previousResponseId: String? = null
    ): DoubaoResponsesResult = withContext(Dispatchers.IO) {
        val body = buildRequestBody(messages, tools, temperature, previousResponseId, stream = true)
        executeStreamingRequest(body, onTextDelta)
    }

    private fun executeRequest(body: String, parser: (String) -> DoubaoResponsesResult): DoubaoResponsesResult {
        val url = "$baseUrl/responses"
        AgentLogger.apiRequest("Doubao", url, model)
        AgentLogger.d("Request body: $body")

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()
        if (responseBody == null) {
            AgentLogger.e("Doubao Responses API returned empty body")
            throw AgentException("Empty response from Doubao Responses API")
        }

        if (!response.isSuccessful) {
            AgentLogger.apiError("Doubao", url, response.code, responseBody)
            throw AgentException("API error ${response.code}: $responseBody")
        }

        AgentLogger.d("Response body: $responseBody")
        return parser(responseBody)
    }

    private fun executeStreamingRequest(body: String, onTextDelta: (String) -> Unit): DoubaoResponsesResult {
        val url = "$baseUrl/responses"
        AgentLogger.apiRequest("Doubao", url, model)
        AgentLogger.d("Request body (stream): $body")

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string().orEmpty()
            AgentLogger.apiError("Doubao", url, response.code, errorBody)
            throw AgentException("API error ${response.code}: $errorBody")
        }

        val responseBody = response.body
            ?: throw AgentException("Empty streaming response from Doubao Responses API")

        responseBody.use { body ->
            val accumulator = DoubaoStreamAccumulator()
            body.byteStream().use { input ->
                readSseEvents(input) { data ->
                    handleStreamEvent(data, accumulator, onTextDelta)
                }
            }
            val result = accumulator.toResult(::parseResponseObject)
            AgentLogger.d("Stream completed: contentLength=${result.content?.length ?: 0}")
            return result
        }
    }

    private fun readSseEvents(input: InputStream, onData: (String) -> Unit) {
        input.bufferedReader().forEachLine { line ->
            when {
                line.isBlank() || line.startsWith(":") -> Unit
                line.startsWith("data:") -> {
                    val data = line.removePrefix("data:").trim()
                    if (data.isNotEmpty() && data != "[DONE]") {
                        onData(data)
                    }
                }
            }
        }
    }

    private fun handleStreamEvent(
        data: String,
        accumulator: DoubaoStreamAccumulator,
        onTextDelta: (String) -> Unit
    ) {
        val root = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return
        when (root["type"]?.jsonPrimitive?.content) {
            "response.output_text.delta" -> {
                val delta = root.stringField("delta")
                    ?: root["data"]?.jsonObject?.stringField("delta")
                    ?: return
                if (delta.isEmpty()) return
                accumulator.appendText(delta)
                onTextDelta(delta)
            }
            "response.completed" -> {
                val responseObj = root["response"]?.jsonObject ?: root
                accumulator.setCompletedResponse(responseObj)
            }
            "error" -> {
                val message = root.stringField("message")
                    ?: root["error"]?.jsonObject?.stringField("message")
                    ?: data
                throw AgentException("Stream error: $message")
            }
        }
    }

    private fun JsonObject.stringField(key: String): String? {
        return this[key]?.jsonPrimitive?.content
    }

    private fun buildRequestBody(
        messages: List<Message>,
        tools: List<ToolDefinition>?,
        temperature: Float,
        previousResponseId: String?,
        stream: Boolean
    ): String {
        val systemPrompt = messages
            .filter { it.role == MessageRole.SYSTEM }
            .joinToString("\n\n") { it.content }
            .trim()
            .ifBlank { null }
        val conversationMessages = messages.filter { it.role != MessageRole.SYSTEM }

        val root = buildJsonObject {
            put("model", JsonPrimitive(model))
            put("input", buildInputArray(conversationMessages))
            put("stream", JsonPrimitive(stream))
            if (temperature >= 0f) {
                put("temperature", JsonPrimitive(temperature))
            }
            if (!tools.isNullOrEmpty()) {
                put("tools", buildToolsArray(tools))
            }
            systemPrompt?.let { put("instructions", JsonPrimitive(it)) }
            previousResponseId?.let { put("previous_response_id", JsonPrimitive(it)) }
        }
        return root.toString()
    }

    private fun buildInputArray(messages: List<Message>): JsonArray {
        return buildJsonArray {
            messages.forEach { msg ->
                when {
                    msg.role == MessageRole.TOOL -> {
                        add(buildJsonObject {
                            put("type", JsonPrimitive("function_call_output"))
                            put("status", JsonPrimitive(ITEM_STATUS_COMPLETED))
                            put("call_id", JsonPrimitive(msg.toolCallId.orEmpty()))
                            put("output", JsonPrimitive(msg.content))
                        })
                    }
                    !msg.toolCalls.isNullOrEmpty() -> {
                        if (msg.content.isNotBlank()) {
                            add(roleMessage(msg.role, msg.content))
                        }
                        msg.toolCalls.forEach { call ->
                            add(buildJsonObject {
                                put("type", JsonPrimitive("function_call"))
                                put("status", JsonPrimitive(ITEM_STATUS_COMPLETED))
                                put("call_id", JsonPrimitive(call.id))
                                put("id", JsonPrimitive(call.id))
                                put("name", JsonPrimitive(call.name))
                                put("arguments", JsonPrimitive(call.arguments))
                            })
                        }
                    }
                    else -> add(roleMessage(msg.role, msg.content))
                }
            }
        }
    }

    private fun roleMessage(role: MessageRole, content: String): JsonObject {
        val contentType = when (role) {
            MessageRole.ASSISTANT -> "output_text"
            else -> "input_text"
        }
        return buildJsonObject {
            put("type", JsonPrimitive("message"))
            put("role", JsonPrimitive(role.name.lowercase()))
            if (role == MessageRole.ASSISTANT) {
                put("status", JsonPrimitive(ITEM_STATUS_COMPLETED))
            }
            put("content", buildJsonArray {
                add(buildJsonObject {
                    put("type", JsonPrimitive(contentType))
                    put("text", JsonPrimitive(content))
                })
            })
        }
    }

    private fun buildToolsArray(tools: List<ToolDefinition>): JsonArray {
        return buildJsonArray {
            tools.forEach { tool ->
                add(buildJsonObject {
                    put("type", JsonPrimitive("function"))
                    put("name", JsonPrimitive(tool.name))
                    put("description", JsonPrimitive(tool.description))
                    put("parameters", ToolRegistry.schemaToJsonObject(tool.parameters))
                })
            }
        }
    }

    private fun parseResponse(responseBody: String): DoubaoResponsesResult {
        val root = json.parseToJsonElement(responseBody).jsonObject
        return parseResponseObject(root)
    }

    private fun parseResponseObject(root: JsonObject): DoubaoResponsesResult {
        val responseId = root["id"]?.jsonPrimitive?.content
        val output = root["output"]?.jsonArray ?: JsonArray(emptyList())

        val toolCalls = mutableListOf<ToolCall>()
        val textParts = mutableListOf<String>()

        root["output_text"]?.jsonPrimitive?.content
            ?.takeIf { it.isNotBlank() }
            ?.let { textParts.add(it) }

        output.forEach { element ->
            parseOutputItem(element, toolCalls, textParts)
        }

        val status = root["status"]?.jsonPrimitive?.content
        val finishReason = when {
            toolCalls.isNotEmpty() -> "tool_calls"
            status != null -> status
            else -> null
        }
        val usage = TokenUsage.fromUsageJson(root["usage"]?.jsonObject)

        return DoubaoResponsesResult(
            responseId = responseId,
            content = textParts.joinToString("\n").trim().ifBlank { null },
            toolCalls = toolCalls.ifEmpty { null },
            finishReason = finishReason,
            usage = usage
        )
    }

    private fun parseOutputItem(
        element: JsonElement,
        toolCalls: MutableList<ToolCall>,
        textParts: MutableList<String>
    ) {
        val obj = element.jsonObject
        when (obj["type"]?.jsonPrimitive?.content) {
            "function_call" -> {
                val id = obj["call_id"]?.jsonPrimitive?.content
                    ?: obj["id"]?.jsonPrimitive?.content
                    ?: return
                val name = obj["name"]?.jsonPrimitive?.content ?: return
                val arguments = obj["arguments"]?.jsonPrimitive?.content ?: "{}"
                toolCalls.add(ToolCall(id, name, arguments))
            }
            "message" -> extractTextFromContent(obj["content"], textParts)
            "text" -> {
                obj["content"]?.jsonPrimitive?.content
                    ?.takeIf { it.isNotBlank() }
                    ?.let { textParts.add(it) }
            }
            "reasoning" -> Unit
        }
    }

    private fun extractTextFromContent(contentElement: JsonElement?, textParts: MutableList<String>) {
        val contentArray = contentElement?.jsonArray ?: return
        contentArray.forEach { part ->
            val partObj = part.jsonObject
            when (partObj["type"]?.jsonPrimitive?.content) {
                "output_text", "text" -> {
                    partObj["text"]?.jsonPrimitive?.content
                        ?.takeIf { it.isNotBlank() }
                        ?.let { textParts.add(it) }
                }
            }
        }
    }

    companion object {
        private const val ITEM_STATUS_COMPLETED = "completed"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

private class DoubaoStreamAccumulator {
    private val streamedText = StringBuilder()
    private var completedResponse: JsonObject? = null

    fun appendText(delta: String) {
        streamedText.append(delta)
    }

    fun setCompletedResponse(response: JsonObject) {
        completedResponse = response
    }

    fun toResult(parse: (JsonObject) -> DoubaoResponsesResult): DoubaoResponsesResult {
        completedResponse?.let { return parse(it) }
        val text = streamedText.toString().trim().ifBlank { null }
        return DoubaoResponsesResult(
            responseId = null,
            content = text,
            toolCalls = null,
            finishReason = "completed",
            usage = null
        )
    }
}

data class DoubaoResponsesResult(
    val responseId: String?,
    val content: String?,
    val toolCalls: List<ToolCall>?,
    val finishReason: String?,
    val usage: TokenUsage? = null
)
