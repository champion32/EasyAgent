package com.easyagent.core

import com.easyagent.brain.BrainConfig
import com.easyagent.brain.BrainProvider
import com.easyagent.planner.PlannerMode

data class AgentConfig(
    val brainConfig: BrainConfig,
    val plannerMode: PlannerMode = PlannerMode.FUNCTION_CALLING,
    val maxSteps: Int = 10,
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val contextLimit: Int = 20,
    val temperature: Float = 0.7f
)

const val DEFAULT_SYSTEM_PROMPT =
    "You are a helpful AI assistant running on an Android device. " +
        "You can use available tools to help the user. " +
        "Respond in the same language the user uses."

fun defaultBrainConfig(provider: BrainProvider, apiKey: String, model: String): BrainConfig {
    return BrainConfig(
        provider = provider,
        apiKey = apiKey,
        model = model
    )
}
