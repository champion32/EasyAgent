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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class OpenAiCompatibleClient(
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

    suspend fun chatCompletion(
        messages: List<Message>,
        tools: List<ToolDefinition>?,
        temperature: Float,
        onTextDelta: ((String) -> Unit)? = null
    ): BrainResponse = withContext(Dispatchers.IO) {
        if (onTextDelta != null && tools.isNullOrEmpty()) {
            chatCompletionStreaming(messages, temperature, onTextDelta)
        } else {
            chatCompletionBlocking(messages, tools, temperature)
        }
    }

    private suspend fun chatCompletionBlocking(
        messages: List<Message>,
        tools: List<ToolDefinition>?,
        temperature: Float
    ): BrainResponse = withContext(Dispatchers.IO) {
        val body = buildRequestBody(messages, tools, temperature)
        val url = "$baseUrl/chat/completions"
        AgentLogger.apiRequest("OpenAI-Compatible", url, model)
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
            AgentLogger.e("Chat Completions API returned empty body")
            throw AgentException("Empty response from API")
        }

        if (!response.isSuccessful) {
            AgentLogger.apiError("OpenAI-Compatible", url, response.code, responseBody)
            throw AgentException("API error ${response.code}: $responseBody")
        }

        AgentLogger.d("Response body: $responseBody")
        parseResponse(responseBody)
    }

    private fun buildRequestBody(
        messages: List<Message>,
        tools: List<ToolDefinition>?,
        temperature: Float
    ): String {
        val root = buildJsonObject {
            put("model", JsonPrimitive(model))
            put("temperature", JsonPrimitive(temperature))
            put("messages", buildMessagesArray(messages))
            if (!tools.isNullOrEmpty()) {
                put("tools", buildToolsArray(tools))
            }
        }
        return root.toString()
    }

    private fun buildMessagesArray(messages: List<Message>): JsonArray {
        return buildJsonArray {
            messages.forEach { msg ->
                add(buildJsonObject {
                    put("role", JsonPrimitive(msg.role.name.lowercase()))
                    if (msg.content.isNotBlank()) {
                        put("content", JsonPrimitive(msg.content))
                    }
                    msg.name?.let { put("name", JsonPrimitive(it)) }
                    msg.toolCallId?.let { put("tool_call_id", JsonPrimitive(it)) }
                    msg.toolCalls?.let { calls ->
                        put("tool_calls", buildJsonArray {
                            calls.forEach { call ->
                                add(buildJsonObject {
                                    put("id", JsonPrimitive(call.id))
                                    put("type", JsonPrimitive("function"))
                                    put("function", buildJsonObject {
                                        put("name", JsonPrimitive(call.name))
                                        put("arguments", JsonPrimitive(call.arguments))
                                    })
                                })
                            }
                        })
                    }
                })
            }
        }
    }

    private fun buildToolsArray(tools: List<ToolDefinition>): JsonArray {
        return buildJsonArray {
            tools.forEach { tool ->
                add(buildJsonObject {
                    put("type", JsonPrimitive("function"))
                    put("function", buildJsonObject {
                        put("name", JsonPrimitive(tool.name))
                        put("description", JsonPrimitive(tool.description))
                        put("parameters", ToolRegistry.schemaToJsonObject(tool.parameters))
                    })
                })
            }
        }
    }

    private fun parseResponse(responseBody: String): BrainResponse {
        val root = json.parseToJsonElement(responseBody).jsonObject
        val choices = root["choices"]?.let { it as? JsonArray } ?: return BrainResponse(null)
        val first = choices.firstOrNull()?.jsonObject ?: return BrainResponse(null)
        val message = first["message"]?.jsonObject ?: return BrainResponse(null)
        val finishReason = first["finish_reason"]?.jsonPrimitive?.content

        val content = message["content"]?.jsonPrimitive?.content

        val toolCalls = message["tool_calls"]?.let { tc ->
            (tc as JsonArray).mapNotNull { element ->
                val obj = element.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val function = obj["function"]?.jsonObject ?: return@mapNotNull null
                val name = function["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val arguments = function["arguments"]?.jsonPrimitive?.content ?: "{}"
                ToolCall(id, name, arguments)
            }
        }
        val usage = TokenUsage.fromUsageJson(root["usage"]?.jsonObject)

        return BrainResponse(content, toolCalls, finishReason, usage)
    }

    private fun chatCompletionStreaming(
        messages: List<Message>,
        temperature: Float,
        onTextDelta: (String) -> Unit
    ): BrainResponse {
        val body = buildJsonObject {
            put("model", JsonPrimitive(model))
            put("temperature", JsonPrimitive(temperature))
            put("stream", JsonPrimitive(true))
            put("stream_options", buildJsonObject {
                put("include_usage", JsonPrimitive(true))
            })
            put("messages", buildMessagesArray(messages))
        }.toString()

        val url = "$baseUrl/chat/completions"
        AgentLogger.apiRequest("OpenAI-Compatible", url, model)

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
            AgentLogger.apiError("OpenAI-Compatible", url, response.code, errorBody)
            throw AgentException("API error ${response.code}: $errorBody")
        }

        val contentBuilder = StringBuilder()
        var usage: TokenUsage? = null
        var finishReason: String? = null

        response.body?.byteStream()?.bufferedReader()?.forEachLine { line ->
            if (!line.startsWith("data:")) return@forEachLine
            val data = line.removePrefix("data:").trim()
            if (data.isEmpty() || data == "[DONE]") return@forEachLine
            val root = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return@forEachLine
            root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.let { choice ->
                choice["delta"]?.jsonObject?.get("content")?.jsonPrimitive?.content?.let { delta ->
                    if (delta.isNotEmpty()) {
                        contentBuilder.append(delta)
                        onTextDelta(delta)
                    }
                }
                choice["finish_reason"]?.jsonPrimitive?.content?.let { finishReason = it }
            }
            TokenUsage.fromUsageJson(root["usage"]?.jsonObject)?.let { usage = it }
        }

        val content = contentBuilder.toString().trim().ifBlank { null }
        return BrainResponse(content, null, finishReason, usage)
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
