package com.easyagent.planner

import com.easyagent.brain.Brain
import com.easyagent.core.AgentEvent
import com.easyagent.core.Message
import com.easyagent.core.TokenUsageReport
import com.easyagent.core.ToolExecutionRecord
import com.easyagent.memory.Memory
import com.easyagent.tools.ToolRegistry
import kotlinx.coroutines.flow.MutableSharedFlow

data class PlannerContext(
    val brain: Brain,
    val memory: Memory,
    val toolRegistry: ToolRegistry,
    val systemPrompt: String,
    val maxSteps: Int,
    val temperature: Float,
    val contextLimit: Int,
    val eventSink: MutableSharedFlow<AgentEvent>? = null
)

data class PlannerResult(
    val content: String,
    val toolExecutions: List<ToolExecutionRecord> = emptyList(),
    val stepsUsed: Int = 0,
    val messages: List<Message> = emptyList(),
    val tokenUsage: TokenUsageReport? = null
)

interface Planner {
    suspend fun run(userInput: String, context: PlannerContext): PlannerResult
}
