package com.easyagent.planner

import com.easyagent.brain.BrainRequest
import com.easyagent.core.AgentEvent
import com.easyagent.core.Message
import com.easyagent.core.MessageRole
import com.easyagent.core.ToolExecutionRecord

class ReActPlanner : Planner {

    override suspend fun run(userInput: String, context: PlannerContext): PlannerResult {
        val toolExecutions = mutableListOf<ToolExecutionRecord>()
        val workingMessages = buildInitialMessages(context)
        val tokenAccumulator = PlannerTokenAccumulator()
        var stepsUsed = 0

        while (stepsUsed < context.maxSteps) {
            stepsUsed++
            context.eventSink?.emit(AgentEvent.Thinking("ReAct step $stepsUsed..."))

            val response = context.brain.complete(
                BrainRequest(
                    messages = workingMessages,
                    tools = null,
                    temperature = context.temperature,
                    onTextDelta = { delta ->
                        context.eventSink?.tryEmit(AgentEvent.TextDelta(delta))
                    }
                )
            )
            tokenAccumulator.recordStep(stepsUsed, response, context.eventSink)

            val rawContent = response.content?.trim().orEmpty()
            if (rawContent.isBlank()) {
                return PlannerResult(
                    content = "No response from model.",
                    toolExecutions = toolExecutions,
                    stepsUsed = stepsUsed,
                    messages = workingMessages,
                    tokenUsage = tokenAccumulator.toReport()
                )
            }

            workingMessages.add(Message(role = MessageRole.ASSISTANT, content = rawContent))

            if (isFinalAnswer(rawContent)) {
                val answer = extractFinalAnswer(rawContent)
                return PlannerResult(
                    content = answer,
                    toolExecutions = toolExecutions,
                    stepsUsed = stepsUsed,
                    messages = workingMessages,
                    tokenUsage = tokenAccumulator.toReport()
                )
            }

            val action = parseAction(rawContent) ?: run {
                return PlannerResult(
                    content = rawContent,
                    toolExecutions = toolExecutions,
                    stepsUsed = stepsUsed,
                    messages = workingMessages,
                    tokenUsage = tokenAccumulator.toReport()
                )
            }

            context.eventSink?.emit(
                AgentEvent.ToolCallStarted(action.toolName, action.argumentsJson)
            )

            val result = context.toolRegistry.execute(action.toolName, action.argumentsJson)
            toolExecutions.add(
                ToolExecutionRecord(
                    toolName = action.toolName,
                    arguments = action.argumentsJson,
                    result = result.output,
                    success = result.success
                )
            )

            context.eventSink?.emit(
                AgentEvent.ToolCallFinished(action.toolName, result.output, result.success)
            )

            val observation = "Observation: ${result.output}"
            workingMessages.add(Message(role = MessageRole.USER, content = observation))
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
        val toolDescriptions = context.toolRegistry.all().joinToString("\n") { tool ->
            "- ${tool.name}: ${tool.description}"
        }

        val reactPrompt = buildString {
            append(context.systemPrompt)
            append("\n\nYou must follow the ReAct format:\n")
            append("Thought: your reasoning\n")
            append("Action: tool_name\n")
            append("Action Input: {\"key\": \"value\"}  (JSON object)\n")
            append("... (repeat Thought/Action/Action Input as needed)\n")
            append("Final Answer: your final response to the user\n\n")
            append("Available tools:\n")
            append(toolDescriptions)
        }

        val messages = mutableListOf<Message>()
        messages.add(Message(role = MessageRole.SYSTEM, content = reactPrompt))
        messages.addAll(context.memory.getContext(context.contextLimit))
        return messages
    }

    private fun isFinalAnswer(content: String): Boolean {
        return FINAL_ANSWER_REGEX.containsMatchIn(content)
    }

    private fun extractFinalAnswer(content: String): String {
        val match = FINAL_ANSWER_REGEX.find(content)
        return match?.groupValues?.get(1)?.trim() ?: content
    }

    private fun parseAction(content: String): ParsedAction? {
        val actionMatch = ACTION_REGEX.find(content) ?: return null
        val toolName = actionMatch.groupValues[1].trim()
        val inputMatch = ACTION_INPUT_REGEX.find(content)
        val argumentsJson = inputMatch?.groupValues?.get(1)?.trim() ?: "{}"
        return ParsedAction(toolName, argumentsJson)
    }

    private data class ParsedAction(val toolName: String, val argumentsJson: String)

    companion object {
        private val FINAL_ANSWER_REGEX = Regex("Final Answer:\\s*(.+)", RegexOption.DOT_MATCHES_ALL)
        private val ACTION_REGEX = Regex("Action:\\s*(\\S+)")
        private val ACTION_INPUT_REGEX = Regex("Action Input:\\s*(\\{.*\\})", RegexOption.DOT_MATCHES_ALL)
    }
}
