package com.easyagent.tools

data class ToolResult(
    val success: Boolean,
    val output: String
) {
    companion object {
        fun ok(output: String) = ToolResult(success = true, output = output)
        fun fail(message: String) = ToolResult(success = false, output = message)
    }
}
