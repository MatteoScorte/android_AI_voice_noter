package com.transcriber.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.transcriber.app.api.ChatMessage
import com.transcriber.app.api.OpenRouterClient
import com.transcriber.app.data.CanvaSkillEntity
import com.transcriber.app.data.CanvaSkillRepository
import com.transcriber.app.data.ChatConversationEntity
import com.transcriber.app.data.ChatMessageEntity
import com.transcriber.app.data.ChatRepository
import com.transcriber.app.data.MeetingRepository
import com.transcriber.app.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

sealed class ChatExportStatus {
    object Idle : ChatExportStatus()
    object Generating : ChatExportStatus()
    data class Error(val message: String) : ChatExportStatus()
}

data class ChatConversationUiState(
    val conversation: ChatConversationEntity? = null,
    val messages: List<ChatMessageEntity> = emptyList(),
    val skills: List<CanvaSkillEntity> = emptyList(),
    val currentModel: String = "",
    val isTyping: Boolean = false,
    val error: String = "",
    val exportStatus: ChatExportStatus = ChatExportStatus.Idle
)

class ChatConversationViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ChatRepository(application)
    private val settingsRepo = SettingsRepository(application)
    private val meetingRepo = MeetingRepository(application)
    private val skillsRepo = CanvaSkillRepository(application)
    private val openRouter = OpenRouterClient()
    private val httpClient = OkHttpClient()
    private val gson = Gson()

    private val _uiState = MutableStateFlow(ChatConversationUiState())
    val uiState: StateFlow<ChatConversationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            skillsRepo.allSkills.collect { skills ->
                _uiState.value = _uiState.value.copy(skills = skills)
            }
        }
        viewModelScope.launch {
            settingsRepo.selectedModel.collect { model ->
                _uiState.value = _uiState.value.copy(currentModel = model)
            }
        }
    }

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
            // Exclude system_link messages — they are UI cards, not LLM context
            val historySnapshot = _uiState.value.messages
                .filter { it.role != "system_link" }
                .toList()
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

    fun exportToWebhook(skill: CanvaSkillEntity) {
        val conv = _uiState.value.conversation ?: return
        val meetingId = conv.meetingId ?: return

        _uiState.value = _uiState.value.copy(exportStatus = ChatExportStatus.Generating)

        viewModelScope.launch {
            try {
                val webhookUrl = settingsRepo.n8nWebhookUrl.first()
                if (webhookUrl.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        exportStatus = ChatExportStatus.Error(
                            "n8n webhook URL non configurato. Vai nelle Impostazioni."
                        )
                    )
                    return@launch
                }

                val meeting = meetingRepo.getMeeting(meetingId)
                val transcript = meeting?.finalTranscript?.ifBlank { meeting.rawTranscript }.orEmpty()
                if (transcript.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        exportStatus = ChatExportStatus.Error("Trascritto non disponibile per questo audio.")
                    )
                    return@launch
                }

                val model = settingsRepo.selectedModel.first()
                val payload = WebhookPayload(
                    title = conv.meetingTitle ?: conv.title,
                    transcript = transcript,
                    skill_name = skill.name,
                    skill_prompt = skill.agentPrompt,
                    output_type = skill.outputType,
                    model = model
                )

                val response = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url(webhookUrl)
                        .post(gson.toJson(payload).toRequestBody("application/json".toMediaType()))
                        .build()
                    httpClient.newCall(request).execute()
                }

                if (!response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        exportStatus = ChatExportStatus.Error("Errore webhook: HTTP ${response.code}")
                    )
                    return@launch
                }

                val webhookResponse = gson.fromJson(response.body?.string() ?: "", WebhookResponse::class.java)
                if (webhookResponse.link.isNotBlank()) {
                    // Persist the link as a special chat message so it's always visible
                    repo.saveMessage(conv.id, "system_link", "${skill.name}\n${webhookResponse.link}")
                    _uiState.value = _uiState.value.copy(exportStatus = ChatExportStatus.Idle)
                } else {
                    _uiState.value = _uiState.value.copy(
                        exportStatus = ChatExportStatus.Error("Nessun link ricevuto dal webhook.")
                    )
                }
            } catch (e: IOException) {
                _uiState.value = _uiState.value.copy(
                    exportStatus = ChatExportStatus.Error("Errore di rete: ${e.message ?: "Sconosciuto"}")
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    exportStatus = ChatExportStatus.Error(e.message ?: "Errore sconosciuto")
                )
            }
        }
    }

    fun resetExport() {
        _uiState.value = _uiState.value.copy(exportStatus = ChatExportStatus.Idle)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = "")
    }

    private data class WebhookPayload(
        val title: String,
        val transcript: String,
        val skill_name: String,
        val skill_prompt: String,
        val output_type: String,
        val model: String
    )

    private data class WebhookResponse(
        val link: String = "",
        val status: String = ""
    )
}
