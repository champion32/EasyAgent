package com.easyagent.tools

import android.content.Context
import com.easyagent.core.JsonProperty
import com.easyagent.core.JsonSchema

class LocalStorageTool(context: Context) : AgentTool {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override val name = "local_storage"

    override val description =
        "Read or write key-value pairs in local SharedPreferences storage."

    override val parameters = JsonSchema(
        properties = mapOf(
            "operation" to JsonProperty(
                type = "string",
                description = "Operation: get, set, delete, list",
                enum = listOf("get", "set", "delete", "list")
            ),
            "key" to JsonProperty(type = "string", description = "Storage key"),
            "value" to JsonProperty(type = "string", description = "Value to store (for set)")
        ),
        required = listOf("operation")
    )

    override suspend fun execute(args: Map<String, Any>): ToolResult {
        val operation = args["operation"] as? String ?: return ToolResult.fail("Missing operation")

        return when (operation) {
            "get" -> {
                val key = args["key"] as? String ?: return ToolResult.fail("Missing key")
                val value = prefs.getString(key, null)
                if (value != null) ToolResult.ok(value)
                else ToolResult.ok("Key '$key' not found")
            }
            "set" -> {
                val key = args["key"] as? String ?: return ToolResult.fail("Missing key")
                val value = args["value"]?.toString() ?: return ToolResult.fail("Missing value")
                prefs.edit().putString(key, value).apply()
                ToolResult.ok("Stored key='$key'")
            }
            "delete" -> {
                val key = args["key"] as? String ?: return ToolResult.fail("Missing key")
                prefs.edit().remove(key).apply()
                ToolResult.ok("Deleted key='$key'")
            }
            "list" -> {
                val keys = prefs.all.keys.joinToString(", ")
                ToolResult.ok(if (keys.isBlank()) "No keys stored" else keys)
            }
            else -> ToolResult.fail("Unknown operation: $operation")
        }
    }

    companion object {
        private const val PREFS_NAME = "easy_agent_storage"
    }
}
