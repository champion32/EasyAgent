package com.easyagent.demo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.easyagent.brain.BrainProvider
import com.easyagent.demo.ChatMessage
import com.easyagent.demo.ChatSession
import com.easyagent.demo.ChatViewModel
import com.easyagent.demo.DemoDefaults
import com.easyagent.demo.MessageKind
import com.easyagent.demo.R
import com.easyagent.demo.ui.theme.AssistantBubbleDark
import com.easyagent.demo.ui.theme.AssistantBubbleLight
import com.easyagent.demo.ui.theme.ErrorBubbleDark
import com.easyagent.demo.ui.theme.ErrorBubbleLight
import com.easyagent.demo.ui.theme.ToolBubbleDark
import com.easyagent.demo.ui.theme.ToolBubbleLight
import com.easyagent.demo.ui.theme.UserBubbleDark
import com.easyagent.demo.ui.theme.UserBubbleLight
import com.easyagent.planner.PlannerMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private val demoPrompts = listOf(
    "查看当前设备信息和网络状态",
    "帮我存一条笔记：明天开会",
    "读取刚才存的笔记",
    "请求 https://httpbin.org/get 并总结返回内容"
)

private val compactDropdownTextStyle
    @Composable get() = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val currentSessionId by viewModel.currentSessionId.collectAsStateWithLifecycle()
    val isDraftSession by viewModel.isDraftSession.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val streamingText by viewModel.streamingText.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val currentBrief = if (isDraftSession) {
        stringResource(R.string.new_session)
    } else {
        sessions.firstOrNull { it.id == currentSessionId }?.brief
            ?: stringResource(R.string.app_name)
    }

    var inputText by rememberSaveable { mutableStateOf("") }
    var provider by remember { mutableStateOf(DemoDefaults.DEFAULT_PROVIDER) }
    var plannerMode by remember { mutableStateOf(PlannerMode.FUNCTION_CALLING) }

    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val errorToastHint = stringResource(R.string.error_toast_hint)

    // 首帧后再组合 Drawer / ConfigRow / DemoPrompt，加快首屏可见
    var secondaryContentReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameMillis { }
        secondaryContentReady = true
    }
    val drawerOpen = drawerState.currentValue != DrawerValue.Closed ||
        drawerState.targetValue != DrawerValue.Closed

    LaunchedEffect(currentSessionId, messages.size, streamingText) {
        if (messages.isEmpty() && streamingText == null) return@LaunchedEffect
        val extra = if (streamingText != null) 1 else 0
        val lastIndex = messages.size + extra - 1
        if (lastIndex >= 0) {
            listState.scrollToItem(lastIndex)
        }
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(errorToastHint)
        }
    }

    val chatBody: @Composable () -> Unit = {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(currentBrief) },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (secondaryContentReady) {
                                    scope.launch { drawerState.open() }
                                }
                            },
                            enabled = secondaryContentReady
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = stringResource(R.string.open_sessions)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.createNewSession() }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.new_session)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                ChatInputBar(
                    text = inputText,
                    onTextChange = { inputText = it },
                    isLoading = isLoading,
                    onSend = {
                        viewModel.sendMessage(
                            text = inputText,
                            provider = provider,
                            plannerMode = plannerMode
                        )
                        inputText = ""
                    },
                    onClear = { viewModel.clearChat() }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (secondaryContentReady) {
                    ConfigRow(
                        provider = provider,
                        onProviderChange = { provider = it },
                        plannerMode = plannerMode,
                        onPlannerModeChange = { plannerMode = it }
                    )
                }

                DemoPromptRow(
                    prompts = demoPrompts,
                    onPromptClick = { inputText = it },
                    visible = secondaryContentReady
                )

                if (isLoading && streamingText == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (messages.isEmpty() && streamingText == null) {
                        item(key = "empty_history") {
                            Text(
                                text = stringResource(R.string.empty_session_history),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp)
                            )
                        }
                    } else {
                        items(
                            items = messages,
                            key = { message -> message.id }
                        ) { message ->
                            ChatMessageCard(message = message)
                        }
                        streamingText?.let { text ->
                            item(key = "streaming_assistant") {
                                ChatMessageCard(
                                    message = ChatMessage(
                                        role = "Assistant",
                                        content = text,
                                        kind = MessageKind.Assistant,
                                        isStreaming = true
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (secondaryContentReady || drawerOpen) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                Surface(
                    modifier = Modifier.fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp
                ) {
                    SessionDrawer(
                        sessions = sessions,
                        isDraftSelected = isDraftSession,
                        onSessionClick = { sessionId ->
                            viewModel.selectSession(sessionId)
                            scope.launch { drawerState.close() }
                        },
                        onNewSession = {
                            viewModel.createNewSession()
                            scope.launch { drawerState.close() }
                        }
                    )
                }
            }
        ) {
            chatBody()
        }
    } else {
        chatBody()
    }
}

@Composable
private fun SessionDrawer(
    sessions: List<ChatSession>,
    isDraftSelected: Boolean,
    onSessionClick: (String) -> Unit,
    onNewSession: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.85f)
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.sessions),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        TextButton(
            onClick = onNewSession,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(
                text = stringResource(R.string.new_session),
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            if (isDraftSelected) {
                item(key = "draft_session") {
                    DraftSessionListItem(onClick = onNewSession)
                }
            }
            items(sessions, key = { it.id }) { session ->
                SessionListItem(
                    session = session,
                    onClick = { onSessionClick(session.id) }
                )
            }
        }
    }
}

@Composable
private fun DraftSessionListItem(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = true, onClick = onClick)
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(
                text = stringResource(R.string.new_session),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.draft_session_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
            )
        }
    }
}

@Composable
private fun SessionListItem(
    session: ChatSession,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .background(
                color = if (session.selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = session.selected,
            onClick = onClick
        )
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(
                text = session.brief,
                style = MaterialTheme.typography.bodyLarge,
                color = if (session.selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatSessionTime(session.updatedAt),
                style = MaterialTheme.typography.bodySmall,
                color = if (session.selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

private fun formatSessionTime(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigRow(
    provider: BrainProvider,
    onProviderChange: (BrainProvider) -> Unit,
    plannerMode: PlannerMode,
    onPlannerModeChange: (PlannerMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        EnumDropdown(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.provider),
            options = BrainProvider.entries,
            selected = provider,
            onSelected = onProviderChange,
            optionLabel = { it.name }
        )
        EnumDropdown(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.planner_mode),
            options = PlannerMode.entries,
            selected = plannerMode,
            onSelected = onPlannerModeChange,
            optionLabel = { it.name }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    modifier: Modifier = Modifier,
    label: String,
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    optionLabel: (T) -> String
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = optionLabel(selected),
                        style = compactDropdownTextStyle,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option), style = compactDropdownTextStyle) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        onClick = {
                            onSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DemoPromptRow(
    prompts: List<String>,
    onPromptClick: (String) -> Unit,
    visible: Boolean = true
) {
    if (!visible) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        prompts.forEach { prompt ->
            FilterChip(
                selected = false,
                onClick = { onPromptClick(prompt) },
                label = { Text(prompt) }
            )
        }
    }
}

@Composable
private fun ChatMessageCard(message: ChatMessage) {
    val darkTheme = isSystemInDarkTheme()
    val bubbleColor = bubbleColorFor(message.kind, darkTheme)
    val contentColor = contentColorFor(bubbleColor, darkTheme)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = bubbleColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = message.role,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (message.isStreaming) "${message.content}▍" else message.content,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (message.kind == MessageKind.Assistant && message.totalTokens != null) {
                TokenUsageLine(message = message)
            }
        }
    }
}

@Composable
private fun TokenUsageLine(message: ChatMessage) {
    Text(
        text = stringResource(
            R.string.token_usage_format,
            message.promptTokens ?: 0,
            message.completionTokens ?: 0,
            message.totalTokens ?: 0,
            message.apiSteps ?: 1
        ),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    isLoading: Boolean,
    onSend: () -> Unit,
    onClear: () -> Unit
) {
    val canSend = !isLoading && text.isNotBlank()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .navigationBarsPadding()
            .imePadding(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.hint_message)) },
            minLines = 1,
            maxLines = 4,
            enabled = !isLoading
        )
        Button(
            onClick = onSend,
            enabled = canSend,
            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            )
        ) {
            Text(
                text = stringResource(R.string.send),
                fontWeight = if (canSend) FontWeight.SemiBold else FontWeight.Normal
            )
        }
        OutlinedButton(
            onClick = onClear,
            enabled = !isLoading
        ) {
            Text(stringResource(R.string.clear))
        }
    }
}

@Composable
private fun bubbleColorFor(kind: MessageKind, darkTheme: Boolean): Color {
    return when (kind) {
        MessageKind.User -> if (darkTheme) UserBubbleDark else UserBubbleLight
        MessageKind.Assistant -> if (darkTheme) AssistantBubbleDark else AssistantBubbleLight
        MessageKind.Tool, MessageKind.System -> if (darkTheme) ToolBubbleDark else ToolBubbleLight
        MessageKind.Error -> if (darkTheme) ErrorBubbleDark else ErrorBubbleLight
    }
}

private fun contentColorFor(bubbleColor: Color, darkTheme: Boolean): Color {
    val luminance = bubbleColor.red * 0.299f + bubbleColor.green * 0.587f + bubbleColor.blue * 0.114f
    return if (luminance > 0.55f && !darkTheme) {
        Color(0xFF1C1B1F)
    } else {
        Color(0xFFE6E1E5)
    }
}
