package com.easyagent.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.easyagent.core.JsonProperty
import com.easyagent.core.JsonSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SystemActionTool(private val context: Context) : AgentTool {

    override val name = "system_action"

    override val description =
        "Perform system actions: open a URL in browser, share text, or show a toast message."

    override val parameters = JsonSchema(
        properties = mapOf(
            "action" to JsonProperty(
                type = "string",
                description = "Action type: open_url, share, toast",
                enum = listOf("open_url", "share", "toast")
            ),
            "url" to JsonProperty(type = "string", description = "URL to open (for open_url)"),
            "text" to JsonProperty(type = "string", description = "Text content (for share or toast)")
        ),
        required = listOf("action")
    )

    override suspend fun execute(args: Map<String, Any>): ToolResult {
        val action = args["action"] as? String ?: return ToolResult.fail("Missing action")

        return withContext(Dispatchers.Main) {
            when (action) {
                "open_url" -> {
                    val url = args["url"] as? String ?: return@withContext ToolResult.fail("Missing url")
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        ToolResult.ok("Opened URL: $url")
                    } catch (e: Exception) {
                        ToolResult.fail("Failed to open URL: ${e.message}")
                    }
                }
                "share" -> {
                    val text = args["text"] as? String ?: return@withContext ToolResult.fail("Missing text")
                    try {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share via").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                        ToolResult.ok("Share dialog opened")
                    } catch (e: Exception) {
                        ToolResult.fail("Failed to share: ${e.message}")
                    }
                }
                "toast" -> {
                    val text = args["text"] as? String ?: return@withContext ToolResult.fail("Missing text")
                    Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
                    ToolResult.ok("Toast shown: $text")
                }
                else -> ToolResult.fail("Unknown action: $action")
            }
        }
    }
}
