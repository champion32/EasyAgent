package com.easyagent.tools

import com.easyagent.core.JsonSchema

interface AgentTool {
    val name: String
    val description: String
    val parameters: JsonSchema

    suspend fun execute(args: Map<String, Any>): ToolResult
}
