package com.transcriber.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,   // "user" or "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
