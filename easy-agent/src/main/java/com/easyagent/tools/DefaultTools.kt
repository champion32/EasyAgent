package com.easyagent.tools

import android.content.Context

object DefaultTools {

    fun create(context: Context): List<AgentTool> {
        return listOf(
            DeviceInfoTool(context),
            SystemActionTool(context),
            LocalStorageTool(context),
            HttpRequestTool()
        )
    }
}
