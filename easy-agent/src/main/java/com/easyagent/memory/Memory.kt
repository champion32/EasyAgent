package com.easyagent.memory

import com.easyagent.core.Message

interface Memory {
    fun add(message: Message)
    fun getContext(limit: Int = 20): List<Message>
    fun clear()
}
