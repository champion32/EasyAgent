package com.easyagent.demo.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.easyagent.demo.ui.theme.EasyAgentDemoTheme
@Preview(name = "新会话", showBackground = true, showSystemUi = true)
@Composable
private fun ChatScreenEmptyPreview() {
    EasyAgentDemoTheme {
        ChatScreenContent(
            state = ChatScreenPreviewData.emptyState,
            actions = ChatScreenActions(),
            drawerState = rememberDrawerState(DrawerValue.Closed),
            scope = rememberCoroutineScope(),
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(name = "有消息", showBackground = true, showSystemUi = true)
@Composable
private fun ChatScreenWithMessagesPreview() {
    EasyAgentDemoTheme {
        ChatScreenContent(
            state = ChatScreenPreviewData.withMessages,
            actions = ChatScreenActions(),
            drawerState = rememberDrawerState(DrawerValue.Closed),
            scope = rememberCoroutineScope(),
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(name = "流式输出", showBackground = true, showSystemUi = true)
@Composable
private fun ChatScreenStreamingPreview() {
    EasyAgentDemoTheme {
        ChatScreenContent(
            state = ChatScreenPreviewData.streaming,
            actions = ChatScreenActions(),
            drawerState = rememberDrawerState(DrawerValue.Closed),
            scope = rememberCoroutineScope(),
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(
    name = "深色",
    showBackground = true,
    showSystemUi = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun ChatScreenDarkPreview() {
    EasyAgentDemoTheme {
        ChatScreenContent(
            state = ChatScreenPreviewData.withMessages,
            actions = ChatScreenActions(),
            drawerState = rememberDrawerState(DrawerValue.Closed),
            scope = rememberCoroutineScope(),
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(name = "侧栏", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ChatScreenDrawerPreview() {
    EasyAgentDemoTheme {
        ChatScreenContent(
            state = ChatScreenPreviewData.withMessages,
            actions = ChatScreenActions(),
            drawerState = rememberDrawerState(DrawerValue.Open),
            scope = rememberCoroutineScope(),
            modifier = Modifier.fillMaxSize()
        )
    }
}
