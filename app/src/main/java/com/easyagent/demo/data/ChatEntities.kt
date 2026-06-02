package com.easyagent.demo.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")

data class ChatSessionEntity(

    @PrimaryKey val id: String,

    val brief: String,

    val createdAt: Long,

    val updatedAt: Long

)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]

)

data class ChatMessageEntity(

    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    val sessionId: String,

    val role: String,

    val content: String,

    val kind: String,

    val createdAt: Long,

    val promptTokens: Int? = null,

    val completionTokens: Int? = null,

    val totalTokens: Int? = null,

    val apiSteps: Int? = null

)
