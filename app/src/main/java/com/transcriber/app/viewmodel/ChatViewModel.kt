package com.transcriber.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.transcriber.app.data.ChatConversationEntity
import com.transcriber.app.data.ChatRepository
import com.transcriber.app.data.Meeting
import com.transcriber.app.data.MeetingRepository
import com.transcriber.app.data.MeetingStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatUiState(
    val conversations: List<ChatConversationEntity> = emptyList(),
    val completedMeetings: List<Meeting> = emptyList()
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ChatRepository(application)
    private val meetingRepo = MeetingRepository(application)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<String>()
    val navigationEvent: SharedFlow<String> = _navigationEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            repo.allConversations.collect { convs ->
                _uiState.value = _uiState.value.copy(conversations = convs)
            }
        }
        viewModelScope.launch {
            meetingRepo.meetings.collect { meetings ->
                _uiState.value = _uiState.value.copy(
                    completedMeetings = meetings.filter { it.status == MeetingStatus.COMPLETED }
                )
            }
        }
    }

    fun createConversation(
        title: String,
        meetingId: String? = null,
        meetingTitle: String? = null
    ) {
        viewModelScope.launch {
            val conv = repo.createConversation(title, meetingId, meetingTitle)
            _navigationEvent.emit(conv.id)
        }
    }

    fun deleteConversation(conversation: ChatConversationEntity) {
        viewModelScope.launch { repo.deleteConversation(conversation) }
    }
}
