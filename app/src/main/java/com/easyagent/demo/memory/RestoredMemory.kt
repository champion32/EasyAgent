package com.easyagent.demo.memory

import com.easyagent.core.Message
import com.easyagent.memory.Memory
import java.util.concurrent.CopyOnWriteArrayList

/** 可从持久化记录预加载的 Memory，用于恢复会话上下文。 */
class RestoredMemory : Memory {

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
