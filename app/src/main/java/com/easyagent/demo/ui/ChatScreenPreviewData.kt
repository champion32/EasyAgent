package com.easyagent.demo.ui

import com.easyagent.brain.BrainProvider
import com.easyagent.demo.ChatMessage
import com.easyagent.demo.ChatSession
import com.easyagent.demo.MessageKind
import com.easyagent.planner.PlannerMode

object ChatScreenPreviewData {

    val emptyState = ChatScreenUiState(
        currentBrief = "新会话",
        isDraftSession = true,
        secondaryContentReady = true
    )

    val withMessages = ChatScreenUiState(
        currentBrief = "查看设备信息",
        isDraftSession = false,
        messages = listOf(
            ChatMessage(id = 1, role = "User", content = "查看当前设备信息和网络状态", kind = MessageKind.User),
            ChatMessage(
                id = 2,
                role = "Tool",
                content = "▶ device_info({\"field\":\"all\"})",
                kind = MessageKind.Tool
            ),
            ChatMessage(
                id = 3,
                role = "Assistant",
                content = "当前设备为 Pixel 模拟器，Android 14，网络已连接。",
                kind = MessageKind.Assistant,
                promptTokens = 120,
                completionTokens = 45,
                totalTokens = 165,
                apiSteps = 2
            )
        ),
        sessions = listOf(
            ChatSession(
                id = "s1",
                brief = "查看设备信息",
                updatedAt = System.currentTimeMillis(),
                selected = true
            ),
            ChatSession(
                id = "s2",
                brief = "帮我存一条笔记",
                updatedAt = System.currentTimeMillis() - 86_400_000,
                selected = false
            )
        ),
        provider = BrainProvider.DOUBAO,
        plannerMode = PlannerMode.FUNCTION_CALLING,
        secondaryContentReady = true
    )

    val streaming = withMessages.copy(
        isLoading = true,
        streamingText = "正在整理设备信息"
    )
}
