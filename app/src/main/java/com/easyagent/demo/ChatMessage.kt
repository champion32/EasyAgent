package com.easyagent.demo

data class ChatMessage(

    val id: Long = 0L,

    val role: String,

    val content: String,

    val kind: MessageKind,

    val promptTokens: Int? = null,

    val completionTokens: Int? = null,

    val totalTokens: Int? = null,

    val apiSteps: Int? = null,

    val isStreaming: Boolean = false

)
