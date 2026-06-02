package com.easyagent.demo.data



import com.easyagent.demo.ChatMessage

import com.easyagent.demo.ChatSession

import com.easyagent.demo.MessageKind

import com.easyagent.core.TokenUsageReport

import kotlinx.coroutines.flow.Flow

import kotlinx.coroutines.flow.map

import java.util.UUID



class ChatRepository(private val database: ChatDatabase) {



    private val sessionDao = database.sessionDao()

    private val messageDao = database.messageDao()



    fun observeSessions(currentSessionId: String?): Flow<List<ChatSession>> {

        return sessionDao.observeSessionsWithMessages().map { entities ->

            entities.map { entity ->

                ChatSession(

                    id = entity.id,

                    brief = entity.brief,

                    updatedAt = entity.updatedAt,

                    selected = entity.id == currentSessionId

                )

            }

        }

    }



    fun observeMessages(sessionId: String): Flow<List<ChatMessage>> {

        return messageDao.observeMessages(sessionId).map { entities ->

            entities.map { it.toUiMessage() }

        }

    }



    suspend fun getMessages(sessionId: String): List<ChatMessage> {

        return messageDao.getMessages(sessionId).map { it.toUiMessage() }

    }



    suspend fun getLatestSessionWithMessages(): String? {

        return sessionDao.getLatestSessionWithMessages()?.id

    }



    suspend fun deleteEmptySessions() {

        sessionDao.deleteEmptySessions()

    }



    suspend fun createSessionFromFirstMessage(firstUserMessage: String): String {

        val now = System.currentTimeMillis()

        val id = UUID.randomUUID().toString()

        sessionDao.insertSession(

            ChatSessionEntity(

                id = id,

                brief = truncateBrief(firstUserMessage),

                createdAt = now,

                updatedAt = now

            )

        )

        return id

    }



    suspend fun insertMessage(

        sessionId: String,

        role: String,

        content: String,

        kind: MessageKind,

        tokenUsage: TokenUsageReport? = null

    ): Long {

        val now = System.currentTimeMillis()

        sessionDao.touchSession(sessionId, now)

        val usage = tokenUsage?.total

        return messageDao.insertMessage(

            ChatMessageEntity(

                sessionId = sessionId,

                role = role,

                content = content,

                kind = kind.name,

                createdAt = now,

                promptTokens = usage?.promptTokens,

                completionTokens = usage?.completionTokens,

                totalTokens = usage?.totalTokens,

                apiSteps = tokenUsage?.apiCallCount?.takeIf { it > 0 }

            )

        )

    }



    suspend fun deleteSession(sessionId: String) {

        sessionDao.deleteSession(sessionId)

    }



    suspend fun loadAgentMemoryMessages(sessionId: String): List<ChatMessageEntity> {

        return messageDao.getAgentMemoryMessages(sessionId)

    }



    companion object {

        const val DRAFT_BRIEF = "新会话"

        const val BRIEF_MAX_LENGTH = 32



        fun truncateBrief(text: String): String {

            val trimmed = text.trim().replace("\n", " ")

            if (trimmed.isEmpty()) return DRAFT_BRIEF

            return if (trimmed.length <= BRIEF_MAX_LENGTH) {

                trimmed

            } else {

                trimmed.take(BRIEF_MAX_LENGTH) + "…"

            }

        }

    }

}



private fun ChatMessageEntity.toUiMessage(): ChatMessage {

    return ChatMessage(

        id = id,

        role = role,

        content = content,

        kind = runCatching { MessageKind.valueOf(kind) }.getOrDefault(MessageKind.System),

        promptTokens = promptTokens,

        completionTokens = completionTokens,

        totalTokens = totalTokens,

        apiSteps = apiSteps

    )

}


