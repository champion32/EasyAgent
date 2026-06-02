package com.easyagent.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.easyagent.demo.ui.ChatScreen
import com.easyagent.demo.ui.theme.EasyAgentDemoTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EasyAgentDemoTheme {
                ChatScreen(viewModel = viewModel)
            }
        }
    }
}
