package com.easyagent.core

enum class MessageRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
}

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String
)

data class Message(
    val role: MessageRole,
    val content: String,
    val name: String? = null,
    val toolCalls: List<ToolCall>? = null,
    val toolCallId: String? = null
)

data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: JsonSchema
)

data class JsonSchema(
    val type: String = "object",
    val properties: Map<String, JsonProperty> = emptyMap(),
    val required: List<String> = emptyList()
)

data class JsonProperty(
    val type: String,
    val description: String = "",
    val enum: List<String>? = null
)

data class AgentResponse(
    val content: String,
    val toolExecutions: List<ToolExecutionRecord> = emptyList(),
    val stepsUsed: Int = 0,
    val tokenUsage: TokenUsageReport? = null
)

data class ToolExecutionRecord(
    val toolName: String,
    val arguments: String,
    val result: String,
    val success: Boolean
)

sealed class AgentEvent {
    data class Thinking(val message: String) : AgentEvent()
    data class ToolCallStarted(val toolName: String, val arguments: String) : AgentEvent()
    data class ToolCallFinished(val toolName: String, val result: String, val success: Boolean) : AgentEvent()
    data class TextDelta(val delta: String) : AgentEvent()
    data class StepTokenUsage(val step: Int, val usage: TokenUsage) : AgentEvent()
    data class Completed(val response: AgentResponse) : AgentEvent()
    data class Error(val exception: AgentException) : AgentEvent()
}

class AgentException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
