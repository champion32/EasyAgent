package com.easyagent

import com.easyagent.core.AgentEvent
import com.easyagent.core.AgentException
import com.easyagent.core.AgentResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Java-friendly wrapper for [EasyAgent].
 */
class EasyAgentJavaBridge(private val agent: EasyAgent) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun chat(message: String, callback: AgentCallback) {
        scope.launch {
            try {
                val response = agent.chat(message)
                callback.onSuccess(response)
            } catch (e: Exception) {
                callback.onError(AgentException(e.message ?: "Unknown error", e))
            }
        }
    }

    fun chatStream(message: String, callback: StreamCallback) {
        scope.launch {
            try {
                agent.chatStream(message).collect { event ->
                    callback.onEvent(event)
                }
            } catch (e: Exception) {
                callback.onError(AgentException(e.message ?: "Unknown error", e))
            }
        }
    }

    fun clearMemory() = agent.clearMemory()

    interface AgentCallback {
        fun onSuccess(response: AgentResponse)
        fun onError(error: AgentException)
    }

    interface StreamCallback {
        fun onEvent(event: AgentEvent)
        fun onError(error: AgentException)
    }
}
