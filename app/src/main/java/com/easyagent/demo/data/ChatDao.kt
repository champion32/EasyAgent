package com.easyagent.demo.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao

interface ChatSessionDao {

    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")

    fun observeSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC LIMIT 1")

    suspend fun getLatestSession(): ChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)

    suspend fun insertSession(session: ChatSessionEntity)

    @Query("UPDATE chat_sessions SET brief = :brief, updatedAt = :updatedAt WHERE id = :sessionId")

    suspend fun updateBrief(sessionId: String, brief: String, updatedAt: Long)

    @Query("UPDATE chat_sessions SET updatedAt = :updatedAt WHERE id = :sessionId")

    suspend fun touchSession(sessionId: String, updatedAt: Long)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")

    suspend fun deleteMessages(sessionId: String)

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")

    suspend fun deleteSession(sessionId: String)

    @Query(
        """
        DELETE FROM chat_sessions WHERE id NOT IN (
            SELECT DISTINCT sessionId FROM chat_messages
        )
        """
    )

    suspend fun deleteEmptySessions()

    @Query(
        """
        SELECT s.* FROM chat_sessions s
        INNER JOIN chat_messages m ON s.id = m.sessionId
        GROUP BY s.id
        ORDER BY s.updatedAt DESC
        """
    )

    fun observeSessionsWithMessages(): Flow<List<ChatSessionEntity>>

    @Query(
        """
        SELECT s.* FROM chat_sessions s
        INNER JOIN chat_messages m ON s.id = m.sessionId
        GROUP BY s.id
        ORDER BY s.updatedAt DESC
        LIMIT 1
        """
    )

    suspend fun getLatestSessionWithMessages(): ChatSessionEntity?

}

@Dao

interface ChatMessageDao {

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC, id ASC")

    suspend fun getMessages(sessionId: String): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC, id ASC")

    fun observeMessages(sessionId: String): Flow<List<ChatMessageEntity>>

    @Insert

    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query(
        """
        SELECT * FROM chat_messages
        WHERE sessionId = :sessionId AND kind IN ('User', 'Assistant')
        ORDER BY createdAt ASC, id ASC
        """
    )

    suspend fun getAgentMemoryMessages(sessionId: String): List<ChatMessageEntity>

}
