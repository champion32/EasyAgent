package com.easyagent.tools

import com.easyagent.core.JsonProperty
import com.easyagent.core.JsonSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class HttpRequestTool(
    private val client: OkHttpClient = defaultClient()
) : AgentTool {

    override val name = "http_request"

    override val description =
        "Make HTTP GET or POST requests to external APIs. Response body is truncated to 4000 chars."

    override val parameters = JsonSchema(
        properties = mapOf(
            "method" to JsonProperty(
                type = "string",
                description = "HTTP method: GET or POST",
                enum = listOf("GET", "POST")
            ),
            "url" to JsonProperty(type = "string", description = "Request URL"),
            "body" to JsonProperty(type = "string", description = "Request body for POST (JSON string)"),
            "headers" to JsonProperty(type = "string", description = "Optional JSON object of headers")
        ),
        required = listOf("method", "url")
    )

    override suspend fun execute(args: Map<String, Any>): ToolResult {
        val method = (args["method"] as? String)?.uppercase() ?: return ToolResult.fail("Missing method")
        val url = args["url"] as? String ?: return ToolResult.fail("Missing url")

        return withContext(Dispatchers.IO) {
            try {
                val builder = Request.Builder().url(url)

                when (method) {
                    "GET" -> builder.get()
                    "POST" -> {
                        val body = args["body"]?.toString() ?: ""
                        val mediaType = "application/json; charset=utf-8".toMediaType()
                        builder.post(body.toRequestBody(mediaType))
                    }
                    else -> return@withContext ToolResult.fail("Unsupported method: $method")
                }

                val response = client.newCall(builder.build()).execute()
                val responseBody = response.body?.string()?.take(MAX_RESPONSE_LENGTH) ?: ""
                val result = "status=${response.code}, body=$responseBody"
                ToolResult.ok(result)
            } catch (e: Exception) {
                ToolResult.fail("HTTP request failed: ${e.message}")
            }
        }
    }

    companion object {
        private const val MAX_RESPONSE_LENGTH = 4000

        fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }
}
