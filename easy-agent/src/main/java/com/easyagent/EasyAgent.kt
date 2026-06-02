package com.easyagent

import android.content.Context
import com.easyagent.brain.Brain
import com.easyagent.brain.BrainConfig
import com.easyagent.brain.BrainFactory
import com.easyagent.core.AgentConfig
import com.easyagent.core.AgentEvent
import com.easyagent.core.AgentLogger
import com.easyagent.core.AgentResponse
import com.easyagent.core.Message
import com.easyagent.core.MessageRole
import com.easyagent.memory.InMemoryShortTermMemory
import com.easyagent.memory.Memory
import com.easyagent.planner.Planner
import com.easyagent.planner.PlannerContext
import com.easyagent.planner.PlannerFactory
import com.easyagent.planner.PlannerMode
import com.easyagent.tools.AgentTool
import com.easyagent.tools.DefaultTools
import com.easyagent.tools.ToolRegistry
import com.easyagent.core.DEFAULT_SYSTEM_PROMPT
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class EasyAgent private constructor(
    private val brain: Brain,
    private val memory: Memory,
    private val planner: Planner,
    private val toolRegistry: ToolRegistry,
    private val config: AgentConfig
) {

    suspend fun chat(message: String): AgentResponse {
        memory.add(Message(role = MessageRole.USER, content = message))

        val result = planner.run(
            userInput = message,
            context = buildPlannerContext()
        )

        memory.add(Message(role = MessageRole.ASSISTANT, content = result.content))

        return AgentResponse(
            content = result.content,
            toolExecutions = result.toolExecutions,
            stepsUsed = result.stepsUsed,
            tokenUsage = result.tokenUsage
        ).also { response ->
            response.tokenUsage?.let { AgentLogger.turnTokenUsage(it) }
        }
    }

    fun chatStream(message: String): Flow<AgentEvent> = callbackFlow {
        memory.add(Message(role = MessageRole.USER, content = message))
        val eventSink = MutableSharedFlow<AgentEvent>(extraBufferCapacity = 1024)
        val collectJob = launch {
            eventSink.collect { trySend(it) }
        }

        try {
            val result = planner.run(
                userInput = message,
                context = buildPlannerContext(eventSink)
            )

            memory.add(Message(role = MessageRole.ASSISTANT, content = result.content))

            val response = AgentResponse(
                content = result.content,
                toolExecutions = result.toolExecutions,
                stepsUsed = result.stepsUsed,
                tokenUsage = result.tokenUsage
            )
            response.tokenUsage?.let { AgentLogger.turnTokenUsage(it) }
            trySend(AgentEvent.Completed(response))
        } catch (e: Exception) {
            AgentLogger.e("Agent error: ${e.message}", e)
            trySend(
                AgentEvent.Error(
                    com.easyagent.core.AgentException(e.message ?: "Unknown error", e)
                )
            )
        } finally {
            collectJob.cancel()
            close()
        }
    }

    fun clearMemory() {
        memory.clear()
    }

    fun registerTool(tool: AgentTool) {
        toolRegistry.register(tool)
    }

    private fun buildPlannerContext(
        eventSink: MutableSharedFlow<AgentEvent>? = null
    ): PlannerContext {
        return PlannerContext(
            brain = brain,
            memory = memory,
            toolRegistry = toolRegistry,
            systemPrompt = config.systemPrompt,
            maxSteps = config.maxSteps,
            temperature = config.temperature,
            contextLimit = config.contextLimit,
            eventSink = eventSink
        )
    }

    class Builder(private val context: Context) {
        private var brainConfig: BrainConfig? = null
        private var plannerMode: PlannerMode = PlannerMode.FUNCTION_CALLING
        private var memory: Memory = InMemoryShortTermMemory()
        private var maxSteps: Int = 10
        private var systemPrompt: String = DEFAULT_SYSTEM_PROMPT
        private var contextLimit: Int = 20
        private var temperature: Float = 0.7f
        private val extraTools = mutableListOf<AgentTool>()

        fun brainConfig(config: BrainConfig) = apply { this.brainConfig = config }

        fun plannerMode(mode: PlannerMode) = apply { this.plannerMode = mode }

        fun memory(memory: Memory) = apply { this.memory = memory }

        fun maxSteps(steps: Int) = apply { this.maxSteps = steps.coerceAtLeast(1) }

        fun systemPrompt(prompt: String) = apply { this.systemPrompt = prompt }

        fun contextLimit(limit: Int) = apply { this.contextLimit = limit.coerceAtLeast(1) }

        fun temperature(value: Float) = apply { this.temperature = value.coerceIn(0f, 2f) }

        fun addTool(tool: AgentTool) = apply { extraTools.add(tool) }

        fun build(): EasyAgent {
            val config = brainConfig
                ?: throw IllegalStateException("brainConfig is required")

            val registry = ToolRegistry().apply {
                registerAll(DefaultTools.create(context))
                registerAll(extraTools)
            }

            val agentConfig = AgentConfig(
                brainConfig = config,
                plannerMode = plannerMode,
                maxSteps = maxSteps,
                systemPrompt = systemPrompt,
                contextLimit = contextLimit,
                temperature = temperature
            )

            return EasyAgent(
                brain = BrainFactory.create(config),
                memory = memory,
                planner = PlannerFactory.create(plannerMode),
                toolRegistry = registry,
                config = agentConfig
            )
        }
    }
}
