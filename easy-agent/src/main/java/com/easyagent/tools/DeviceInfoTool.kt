package com.easyagent.tools

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.easyagent.core.JsonProperty
import com.easyagent.core.JsonSchema

class DeviceInfoTool(private val context: Context) : AgentTool {

    override val name = "device_info"

    override val description =
        "Get Android device information including model, OS version, and network status."

    override val parameters = JsonSchema(
        properties = mapOf(
            "field" to JsonProperty(
                type = "string",
                description = "Specific field to query: all, model, brand, os_version, sdk_int, network. Default: all.",
                enum = listOf("all", "model", "brand", "os_version", "sdk_int", "network")
            )
        )
    )

    override suspend fun execute(args: Map<String, Any>): ToolResult {
        val field = (args["field"] as? String)?.lowercase() ?: "all"

        val info = linkedMapOf<String, String>()
        when (field) {
            "model" -> info["model"] = Build.MODEL ?: "unknown"
            "brand" -> info["brand"] = Build.BRAND ?: "unknown"
            "os_version" -> info["os_version"] = Build.VERSION.RELEASE ?: "unknown"
            "sdk_int" -> info["sdk_int"] = Build.VERSION.SDK_INT.toString()
            "network" -> info["network"] = getNetworkStatus()
            "all" -> {
                info["model"] = Build.MODEL ?: "unknown"
                info["brand"] = Build.BRAND ?: "unknown"
                info["os_version"] = Build.VERSION.RELEASE ?: "unknown"
                info["sdk_int"] = Build.VERSION.SDK_INT.toString()
                info["network"] = getNetworkStatus()
            }
            else -> return ToolResult.fail("Unknown field: $field")
        }

        return ToolResult.ok(info.entries.joinToString(", ") { "${it.key}=${it.value}" })
    }

    private fun getNetworkStatus(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "unknown"

        val network = cm.activeNetwork ?: return "offline"
        val caps = cm.getNetworkCapabilities(network) ?: return "offline"

        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "connected"
        }
    }
}
