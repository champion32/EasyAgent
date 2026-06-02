package com.easyagent.demo.ui

import com.easyagent.brain.BrainProvider
import com.easyagent.demo.ChatMessage
import com.easyagent.demo.ChatSession
import com.easyagent.demo.DemoDefaults
import com.easyagent.planner.PlannerMode

/** ChatScreen 展示状态（供 Preview 与 [ChatScreenContent] 使用）。 */
data class ChatScreenUiState(
    val messages: List<ChatMessage> = emptyList(),
    val sessions: List<ChatSession> = emptyList(),
    val isDraftSession: Boolean = true,
    val isLoading: Boolean = false,
    val streamingText: String? = null,
    val currentBrief: String = "",
    val inputText: String = "",
    val provider: BrainProvider = DemoDefaults.DEFAULT_PROVIDER,
    val plannerMode: PlannerMode = PlannerMode.FUNCTION_CALLING,
    /** Preview 为 true；运行时由首帧优化逻辑控制。 */
    val secondaryContentReady: Boolean = false
)

/** ChatScreen 用户操作回调。 */
data class ChatScreenActions(
    val onInputChange: (String) -> Unit = {},
    val onProviderChange: (BrainProvider) -> Unit = {},
    val onPlannerModeChange: (PlannerMode) -> Unit = {},
    val onSend: () -> Unit = {},
    val onClear: () -> Unit = {},
    val onNewSession: () -> Unit = {},
    val onSelectSession: (String) -> Unit = {}
)
