package com.easyagent.tools

import com.easyagent.core.ToolDefinition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class ToolRegistry {
    private val tools = linkedMapOf<String, AgentTool>()

    fun register(tool: AgentTool) {
        tools[tool.name] = tool
    }

    fun registerAll(tools: List<AgentTool>) {
        tools.forEach { register(it) }
    }

    fun get(name: String): AgentTool? = tools[name]

    fun all(): List<AgentTool> = tools.values.toList()

    fun definitions(): List<ToolDefinition> {
        return tools.values.map { tool ->
            ToolDefinition(
                name = tool.name,
                description = tool.description,
                parameters = tool.parameters
            )
        }
    }

    suspend fun execute(name: String, argumentsJson: String): ToolResult {
        val tool = tools[name]
            ?: return ToolResult.fail("Tool not found: $name")

        return try {
            val args = parseArguments(argumentsJson)
            tool.execute(args)
        } catch (e: Exception) {
            ToolResult.fail("Tool execution failed: ${e.message}")
        }
    }

    private fun parseArguments(argumentsJson: String): Map<String, Any> {
        if (argumentsJson.isBlank() || argumentsJson == "{}") return emptyMap()

        val element = Json.parseToJsonElement(argumentsJson)
        if (element !is JsonObject) return emptyMap()

        return element.mapValues { (_, value) ->
            when (value) {
                is JsonPrimitive -> {
                    if (value.isString) value.content
                    else value.content
                }
                else -> value.toString()
            }
        }
    }

    companion object {
        fun schemaToJsonObject(schema: com.easyagent.core.JsonSchema): JsonObject {
            return buildJsonObject {
                put("type", JsonPrimitive(schema.type))
                put("properties", buildJsonObject {
                    schema.properties.forEach { (key, prop) ->
                        put(key, buildJsonObject {
                            put("type", JsonPrimitive(prop.type))
                            if (prop.description.isNotBlank()) {
                                put("description", JsonPrimitive(prop.description))
                            }
                            prop.enum?.let { values ->
                                put("enum", kotlinx.serialization.json.buildJsonArray {
                                    values.forEach { add(JsonPrimitive(it)) }
                                })
                            }
                        })
                    }
                })
                if (schema.required.isNotEmpty()) {
                    put("required", kotlinx.serialization.json.buildJsonArray {
                        schema.required.forEach { add(JsonPrimitive(it)) }
                    })
                }
            }
        }
    }
}
