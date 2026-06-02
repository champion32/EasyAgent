package com.easyagent.memory

import com.easyagent.core.Message
import java.util.concurrent.CopyOnWriteArrayList

class InMemoryShortTermMemory : Memory {

    private val messages = CopyOnWriteArrayList<Message>()

    override fun add(message: Message) {
        messages.add(message)
    }

    override fun getContext(limit: Int): List<Message> {
        if (limit <= 0 || messages.isEmpty()) return emptyList()
        val start = (messages.size - limit).coerceAtLeast(0)
        return messages.subList(start, messages.size).toList()
    }

    override fun clear() {
        messages.clear()
    }
}
