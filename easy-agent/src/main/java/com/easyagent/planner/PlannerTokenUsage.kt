package com.easyagent.planner

import com.easyagent.core.AgentEvent
import com.easyagent.core.AgentLogger
import com.easyagent.core.TokenUsage
import com.easyagent.core.TokenUsageReport
import com.easyagent.brain.BrainResponse
import kotlinx.coroutines.flow.MutableSharedFlow

internal class PlannerTokenAccumulator {
    private val stepUsages = mutableListOf<TokenUsage>()
    private var total = TokenUsage.ZERO

    suspend fun recordStep(
        step: Int,
        response: BrainResponse,
        eventSink: MutableSharedFlow<AgentEvent>?
    ) {
        val usage = response.usage ?: return
        stepUsages.add(usage)
        total += usage
        AgentLogger.stepTokenUsage(step, usage)
        eventSink?.emit(AgentEvent.StepTokenUsage(step, usage))
    }

    fun toReport(): TokenUsageReport? {
        if (stepUsages.isEmpty()) return null
        return TokenUsageReport(total = total, stepUsages = stepUsages.toList())
    }
}
