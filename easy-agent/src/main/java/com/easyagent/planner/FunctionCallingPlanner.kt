package com.easyagent.planner

import com.easyagent.brain.BrainRequest
import com.easyagent.core.AgentEvent
import com.easyagent.core.Message
import com.easyagent.core.MessageRole
import com.easyagent.core.ToolExecutionRecord

class FunctionCallingPlanner : Planner {

    override suspend fun run(userInput: String, context: PlannerContext): PlannerResult {
        val toolExecutions = mutableListOf<ToolExecutionRecord>()
        val workingMessages = buildInitialMessages(context)
        val tokenAccumulator = PlannerTokenAccumulator()
        var stepsUsed = 0

        while (stepsUsed < context.maxSteps) {
            stepsUsed++
            context.eventSink?.emit(AgentEvent.Thinking("Step $stepsUsed: calling brain..."))

            val response = context.brain.complete(
                BrainRequest(
                    messages = workingMessages,
                    tools = context.toolRegistry.definitions(),
                    temperature = context.temperature,
                    onTextDelta = { delta ->
                        context.eventSink?.tryEmit(AgentEvent.TextDelta(delta))
                    }
                )
            )
            tokenAccumulator.recordStep(stepsUsed, response, context.eventSink)

            val toolCalls = response.toolCalls
            if (toolCalls.isNullOrEmpty()) {
                val content = response.content?.trim().orEmpty()
                    .ifBlank { "No response from model." }
                return PlannerResult(
                    content = content,
                    toolExecutions = toolExecutions,
                    stepsUsed = stepsUsed,
                    messages = workingMessages,
                    tokenUsage = tokenAccumulator.toReport()
                )
            }

            workingMessages.add(
                Message(
                    role = MessageRole.ASSISTANT,
                    content = response.content.orEmpty(),
                    toolCalls = toolCalls
                )
            )

            for (call in toolCalls) {
                context.eventSink?.emit(
                    AgentEvent.ToolCallStarted(call.name, call.arguments)
                )

                val result = context.toolRegistry.execute(call.name, call.arguments)
                toolExecutions.add(
                    ToolExecutionRecord(
                        toolName = call.name,
                        arguments = call.arguments,
                        result = result.output,
                        success = result.success
                    )
                )

                context.eventSink?.emit(
                    AgentEvent.ToolCallFinished(call.name, result.output, result.success)
                )

                workingMessages.add(
                    Message(
                        role = MessageRole.TOOL,
                        content = result.output,
                        toolCallId = call.id,
                        name = call.name
                    )
                )
            }
        }

        return PlannerResult(
            content = "Reached maximum steps (${context.maxSteps}) without final answer.",
            toolExecutions = toolExecutions,
            stepsUsed = stepsUsed,
            messages = workingMessages,
            tokenUsage = tokenAccumulator.toReport()
        )
    }

    private fun buildInitialMessages(context: PlannerContext): MutableList<Message> {
        val messages = mutableListOf<Message>()
        messages.add(Message(role = MessageRole.SYSTEM, content = context.systemPrompt))
        messages.addAll(context.memory.getContext(context.contextLimit))
        return messages
    }
}
