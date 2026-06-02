package com.easyagent.demo

data class ChatSession(
    val id: String,
    val brief: String,
    val updatedAt: Long,
    val selected: Boolean = false
)
