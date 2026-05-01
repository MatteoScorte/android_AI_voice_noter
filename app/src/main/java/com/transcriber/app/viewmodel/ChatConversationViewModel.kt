package com.transcriber.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.transcriber.app.api.ChatMessage
import com.transcriber.app.api.OpenRouterClient
import com.transcriber.app.data.ChatConversationEntity
import com.transcriber.app.data.ChatMessageEntity
import com.transcriber.app.data.ChatRepository
import com.transcriber.app.data.MeetingRepository
import com.transcriber.app.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ChatConversationUiState(
    val conversation: ChatConversationEntity? = null,
    val messages: List<ChatMessageEntity> = emptyList(),
    val isTyping: Boolean = false,
    val error: String = ""
)

class ChatConversationViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ChatRepository(application)
    private val settingsRepo = SettingsRepository(application)
    private val meetingRepo = MeetingRepository(application)
    private val openRouter = OpenRouterClient()

    private val _uiState = MutableStateFlow(ChatConversationUiState())
    val uiState: StateFlow<ChatConversationUiState> = _uiState.asStateFlow()

    fun loadConversation(conversationId: String) {
        viewModelScope.launch {
            repo.getConversation(conversationId).collect { conv ->
                if (conv != null) _uiState.value = _uiState.value.copy(conversation = conv)
            }
        }
        viewModelScope.launch {
            repo.getMessages(conversationId).collect { messages ->
                _uiState.value = _uiState.value.copy(messages = messages)
            }
        }
    }

    fun sendMessage(text: String) {
        val conv = _uiState.value.conversation ?: return
        val trimmed = text.trim()
        if (trimmed.isBlank()) return

        viewModelScope.launch {
            // Snapshot history before saving user message to avoid race with Room Flow update
            val historySnapshot = _uiState.value.messages.toList()
            repo.saveMessage(conv.id, "user", trimmed)
            _uiState.value = _uiState.value.copy(isTyping = true, error = "")

            try {
                val apiKey = settingsRepo.openRouterApiKey.first()
                if (apiKey.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        isTyping = false,
                        error = "OpenRouter API Key non configurata. Vai nelle Impostazioni."
                    )
                    return@launch
                }
                val model = settingsRepo.selectedModel.first()

                val systemContent = buildString {
                    append("Sei un assistente intelligente e utile. Rispondi in modo chiaro e conciso.")
                    val meetingId = conv.meetingId
                    if (meetingId != null) {
                        val meeting = meetingRepo.getMeeting(meetingId)
                        val transcript = meeting?.finalTranscript
                            ?.ifBlank { meeting.rawTranscript }
                            .orEmpty()
                        if (transcript.isNotBlank()) {
                            append("\n\nHai accesso alla trascrizione completa dell'audio")
                            append(" \"${conv.meetingTitle ?: "Recording"}\".")
                            append(" Usala come contesto per rispondere alle domande dell'utente:\n\n")
                            append(transcript)
                        }
                    }
                }

                val messages = mutableListOf<ChatMessage>()
                messages.add(ChatMessage("system", systemContent))
                historySnapshot.forEach { msg -> messages.add(ChatMessage(msg.role, msg.content)) }
                messages.add(ChatMessage("user", trimmed))

                val result = openRouter.sendChatRequest(apiKey, model, messages)
                if (result.isSuccess) {
                    repo.saveMessage(conv.id, "assistant", result.getOrThrow())
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = result.exceptionOrNull()?.message ?: "Errore nella risposta AI"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Errore sconosciuto")
            } finally {
                _uiState.value = _uiState.value.copy(isTyping = false)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = "")
    }
}
