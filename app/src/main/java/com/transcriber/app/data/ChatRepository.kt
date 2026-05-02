package com.transcriber.app.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ChatRepository(context: Context) {
    private val dao = AppDatabase.getInstance(context).chatDao()

    val allConversations: Flow<List<ChatConversationEntity>> = dao.getAllConversations()

    fun getConversation(id: String): Flow<ChatConversationEntity?> = dao.getConversationById(id)

    fun getMessages(conversationId: String): Flow<List<ChatMessageEntity>> =
        dao.getMessages(conversationId)

    suspend fun createConversation(
        title: String,
        meetingId: String? = null,
        meetingTitle: String? = null
    ): ChatConversationEntity {
        val conv = ChatConversationEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            meetingId = meetingId,
            meetingTitle = meetingTitle
        )
        dao.insertConversation(conv)
        return conv
    }

    suspend fun saveMessage(conversationId: String, role: String, content: String): ChatMessageEntity {
        val msg = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            role = role,
            content = content
        )
        dao.insertMessage(msg)
        // Update conversation's updatedAt and last preview (only for user messages so the list
        // shows the last thing the user asked, not the AI's reply which may be very long)
        dao.getConversationByIdOnce(conversationId)?.let { conv ->
            dao.updateConversation(
                conv.copy(
                    updatedAt = System.currentTimeMillis(),
                    lastMessagePreview = if (role == "user") content.take(80) else conv.lastMessagePreview
                )
            )
        }
        return msg
    }

    suspend fun renameConversation(id: String, newTitle: String) {
        dao.getConversationByIdOnce(id)?.let { conv ->
            dao.updateConversation(conv.copy(title = newTitle))
        }
    }

    suspend fun updateAgentPrompt(id: String, prompt: String) {
        dao.getConversationByIdOnce(id)?.let { conv ->
            dao.updateConversation(conv.copy(agentPrompt = prompt))
        }
    }

    suspend fun deleteConversation(conversation: ChatConversationEntity) {
        dao.deleteMessagesForConversation(conversation.id)
        dao.deleteConversation(conversation)
    }
}
