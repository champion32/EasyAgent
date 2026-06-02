package com.easyagent.demo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.easyagent.EasyAgent
import com.easyagent.brain.BrainConfig
import com.easyagent.brain.BrainProvider
import com.easyagent.brain.DoubaoBrain
import com.easyagent.brain.QwenBrain
import com.easyagent.core.AgentEvent
import com.easyagent.core.AgentException
import com.easyagent.core.AgentLogger
import com.easyagent.core.Message
import com.easyagent.core.MessageRole
import com.easyagent.demo.data.ChatDatabase
import com.easyagent.demo.data.ChatRepository
import com.easyagent.demo.memory.RestoredMemory
import com.easyagent.planner.PlannerMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repositoryDeferred = CompletableDeferred<ChatRepository>()

    private val sessionMemory = RestoredMemory()

    /** null 表示未落库的草稿会话（新会话） */

    private val _currentSessionId = MutableStateFlow<String?>(null)

    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    val isDraftSession: StateFlow<Boolean> = _currentSessionId
        .map { it == null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())

    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())

    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)

    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)

    val error: StateFlow<String?> = _error.asStateFlow()

    /** 流式输出中的 Assistant 文本（打字机效果，完成后写入 Room 并清空）。 */

    private val _streamingText = MutableStateFlow<String?>(null)

    val streamingText: StateFlow<String?> = _streamingText.asStateFlow()

    private var agent: EasyAgent? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val repository = ChatRepository(ChatDatabase.get(getApplication()))
            repositoryDeferred.complete(repository)
            launch {
                _currentSessionId
                    .flatMapLatest { sessionId ->
                        if (sessionId == null) {
                            flowOf(emptyList())
                        } else {
                            repository.observeMessages(sessionId)
                        }
                    }
                    .collect { history ->
                        if (_currentSessionId.value != null) {
                            _messages.value = history
                        }
                    }
            }
            launch {
                _currentSessionId
                    .flatMapLatest { currentId -> repository.observeSessions(currentId) }
                    .collect { _sessions.value = it }
            }
            repository.deleteEmptySessions()
            val latestId = repository.getLatestSessionWithMessages()
            withContext(Dispatchers.Main.immediate) {
                if (latestId != null) {
                    selectSessionInternal(latestId, repository)
                } else {
                    enterDraftSession()
                }
            }
        }
    }

    fun createNewSession() {
        enterDraftSession()
    }

    fun selectSession(sessionId: String) {
        viewModelScope.launch {
            val repository = repositoryDeferred.await()
            selectSessionInternal(sessionId, repository)
        }
    }

    fun sendMessage(
        text: String,
        provider: BrainProvider,
        plannerMode: PlannerMode
    ) {
        if (text.isBlank()) return
        val apiKey = DemoDefaults.apiKeyFor(provider)
        val model = DemoDefaults.modelFor(provider)
        if (apiKey.isBlank()) {
            reportError(null, ApiKeys.missingKeyMessage(provider))
            return
        }
        if (provider == BrainProvider.DOUBAO && model.isBlank()) {
            reportError(null, DoubaoBrain.MODEL_HINT)
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val repository = repositoryDeferred.await()
            val sessionId = ensurePersistedSession(text, repository)
            persistMessage(sessionId, "User", text, MessageKind.User, repository)
            try {
                val easyAgent = ensureAgent(apiKey, provider, plannerMode, model)
                easyAgent.chatStream(text).collect { event ->
                    when (event) {
                        is AgentEvent.Thinking -> {
                            _streamingText.value = null
                            persistMessage(
                                sessionId, "System", event.message, MessageKind.System, repository
                            )
                        }
                        is AgentEvent.ToolCallStarted -> {
                            _streamingText.value = null
                            persistMessage(
                                sessionId,
                                "Tool",
                                "▶ ${event.toolName}(${event.arguments})",
                                MessageKind.Tool,
                                repository
                            )
                        }
                        is AgentEvent.ToolCallFinished -> persistMessage(
                            sessionId,
                            "Tool",
                            if (event.success) "✓ ${event.result}" else "✗ ${event.result}",
                            MessageKind.Tool,
                            repository
                        )
                        is AgentEvent.Completed -> {
                            _streamingText.value = null
                            persistMessage(
                                sessionId,
                                "Assistant",
                                event.response.content,
                                MessageKind.Assistant,
                                repository,
                                event.response.tokenUsage
                            )
                        }
                        is AgentEvent.StepTokenUsage -> Unit
                        is AgentEvent.TextDelta -> {
                            _streamingText.update { current ->
                                (current ?: "") + event.delta
                            }
                        }
                        is AgentEvent.Error -> {
                            _streamingText.value = null
                            reportError(
                                sessionId,
                                event.exception.message ?: "Unknown agent error",
                                event.exception,
                                repository
                            )
                        }
                    }
                }
            } catch (e: AgentException) {
                reportError(sessionId, e.message ?: "Agent error", e, repository)
            } catch (e: Exception) {
                reportError(sessionId, e.message ?: "Unknown error", e, repository)
            } finally {
                _streamingText.value = null
                _isLoading.value = false
            }
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            val repository = repositoryDeferred.await()
            val sessionId = _currentSessionId.value
            if (sessionId != null) {
                repository.deleteSession(sessionId)
            }
            enterDraftSession()
        }
    }

    private fun enterDraftSession() {
        _currentSessionId.value = null
        agent = null
        sessionMemory.clear()
        _messages.value = emptyList()
        _streamingText.value = null
        _error.value = null
    }

    private suspend fun ensurePersistedSession(
        firstUserMessage: String, repository: ChatRepository
    ): String {
        _currentSessionId.value?.let { return it }
        val sessionId = repository.createSessionFromFirstMessage(firstUserMessage)
        _currentSessionId.value = sessionId
        return sessionId
    }

    private suspend fun selectSessionInternal(sessionId: String, repository: ChatRepository) {
        _currentSessionId.value = sessionId
        agent = null
        _error.value = null
        restoreSessionMemory(sessionId, repository)
        _messages.value = repository.getMessages(sessionId)
    }

    private suspend fun restoreSessionMemory(sessionId: String, repository: ChatRepository) {
        sessionMemory.clear()
        repository.loadAgentMemoryMessages(sessionId).forEach { entity ->
            val role = when (entity.kind) {
                MessageKind.User.name -> MessageRole.USER
                MessageKind.Assistant.name -> MessageRole.ASSISTANT
                else -> return@forEach
            }
            sessionMemory.add(Message(role = role, content = entity.content))
        }
    }

    private fun ensureAgent(
        apiKey: String,
        provider: BrainProvider,
        plannerMode: PlannerMode,
        model: String
    ): EasyAgent {
        agent?.let { return it }
        val config = BrainConfig(
            provider = provider,
            apiKey = apiKey,
            model = resolveModel(provider, model)
        )
        return EasyAgent.Builder(getApplication())
            .brainConfig(config)
            .plannerMode(plannerMode)
            .memory(sessionMemory)
            .build()
            .also { agent = it }
    }

    private fun resolveModel(provider: BrainProvider, model: String): String {
        if (model.isNotBlank()) return model
        return when (provider) {
            BrainProvider.OPENAI -> "gpt-4o-mini"
            BrainProvider.QWEN -> QwenBrain.DEFAULT_MODEL
            BrainProvider.DOUBAO -> DoubaoBrain.EXAMPLE_MODEL
        }
    }

    private suspend fun persistMessage(
        sessionId: String,
        role: String,
        content: String,
        kind: MessageKind,
        repository: ChatRepository,
        tokenUsage: com.easyagent.core.TokenUsageReport? = null
    ) {
        repository.insertMessage(sessionId, role, content, kind, tokenUsage)
    }

    private fun reportError(
        sessionId: String?,
        message: String,
        throwable: Throwable? = null,
        repository: ChatRepository? = null
    ) {
        AgentLogger.e("Error: $message", throwable)
        _error.value = message
        if (sessionId != null) {
            viewModelScope.launch {
                val repo = repository ?: repositoryDeferred.await()
                persistMessage(sessionId, "Error", message, MessageKind.Error, repo)
            }
        } else {
            appendDraftMessage("Error", message, MessageKind.Error)
        }
    }

    private fun appendDraftMessage(role: String, content: String, kind: MessageKind) {
        _messages.update {
            it + ChatMessage(
                id = -System.currentTimeMillis(),
                role = role,
                content = content,
                kind = kind
            )
        }
    }

}
