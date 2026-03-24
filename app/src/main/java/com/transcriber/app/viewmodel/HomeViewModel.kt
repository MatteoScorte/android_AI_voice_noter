package com.transcriber.app.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.transcriber.app.data.FolderRepository
import com.transcriber.app.data.FOLDER_ID_NONE
import com.transcriber.app.data.InboxItem
import com.transcriber.app.data.InboxRepository
import com.transcriber.app.data.Meeting
import com.transcriber.app.data.MeetingFolder
import com.transcriber.app.data.MeetingRepository
import com.transcriber.app.data.MeetingStatus
import com.transcriber.app.data.SettingsRepository
import com.transcriber.app.data.SupabaseClient
import com.transcriber.app.service.RecordingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class HomeUiState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val currentMeetingId: String? = null,
    val recordingDurationMs: Long = 0,
    val meetings: List<Meeting> = emptyList(),
    val inboxItems: List<InboxItem> = emptyList(),
    val folders: List<MeetingFolder> = emptyList(),
    // null = tutte, FOLDER_ID_NONE = senza cartella, altrimenti id cartella
    val selectedFolderId: String? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    val meetingRepository = MeetingRepository(application)
    val inboxRepository = InboxRepository(application)
    private val folderRepository = FolderRepository(application)
    private val settingsRepository = SettingsRepository(application)
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Emits meetingId when an inbox item is promoted to a Meeting, triggering navigation
    private val _navigationEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigationEvent: SharedFlow<String> = _navigationEvent.asSharedFlow()

    private var recordingStartTime: Long = 0
    private var totalPausedTimeMs: Long = 0
    private var lastPauseTimeMs: Long = 0
    
    private val timerHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (_uiState.value.isRecording && !_uiState.value.isPaused) {
                _uiState.value = _uiState.value.copy(
                    recordingDurationMs = SystemClock.elapsedRealtime() - recordingStartTime - totalPausedTimeMs
                )
                timerHandler.postDelayed(this, 100)
            }
        }
    }

    private val recordingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val mid = intent?.getStringExtra(RecordingService.EXTRA_MEETING_ID) ?: ""
            when (intent?.getStringExtra(RecordingService.EXTRA_STATUS)) {
                RecordingService.STATUS_PAUSED -> {
                    _uiState.value = _uiState.value.copy(isPaused = true)
                }
                RecordingService.STATUS_STOPPED -> {
                    val elapsed = intent.getLongExtra(RecordingService.EXTRA_ELAPSED_TIME, 0)
                    viewModelScope.launch {
                        meetingRepository.getMeeting(mid)?.let {
                            meetingRepository.updateMeeting(it.copy(durationMs = elapsed, status = MeetingStatus.RECORDED))
                        }
                    }
                    _uiState.value = _uiState.value.copy(isRecording = false, isPaused = false, currentMeetingId = null, recordingDurationMs = 0)
                    totalPausedTimeMs = 0
                }
                RecordingService.STATUS_ERROR -> {
                    val error = intent.getStringExtra(RecordingService.EXTRA_ERROR_MSG) ?: ""
                    viewModelScope.launch {
                        meetingRepository.getMeeting(mid)?.let {
                            meetingRepository.updateMeeting(it.copy(status = MeetingStatus.ERROR, errorMessage = error))
                        }
                    }
                    _uiState.value = _uiState.value.copy(isRecording = false, isPaused = false, currentMeetingId = null, recordingDurationMs = 0)
                    totalPausedTimeMs = 0
                }
            }
        }
    }

    init {
        val filter = IntentFilter(RecordingService.BROADCAST_RECORDING_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(recordingReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            application.registerReceiver(recordingReceiver, filter)
        }
        viewModelScope.launch {
            meetingRepository.meetings.collect { _uiState.value = _uiState.value.copy(meetings = it) }
        }
        viewModelScope.launch {
            inboxRepository.items.collect { _uiState.value = _uiState.value.copy(inboxItems = it) }
        }
        viewModelScope.launch {
            folderRepository.folders.collect { _uiState.value = _uiState.value.copy(folders = it) }
        }
        // Auto-pull from Supabase on launch
        viewModelScope.launch {
            val syncEnabled = settingsRepository.supabaseSyncEnabled.first()
            if (syncEnabled) {
                val url = settingsRepository.supabaseUrl.first()
                val key = settingsRepository.supabaseAnonKey.first()
                if (url.isNotBlank() && key.isNotBlank()) {
                    val client = SupabaseClient(url, key)
                    val result = client.fetchAllMeetings()
                    result.getOrNull()?.let { remoteList ->
                        meetingRepository.mergeRemoteMeetings(remoteList)
                        // Download audio for remote meetings that have no local audio file
                        val localMeetings = meetingRepository.meetings.value
                        for (remote in remoteList) {
                            val local = localMeetings.find { it.id == remote.id } ?: continue
                            if (local.audioFilePath.isNotEmpty() && java.io.File(local.audioFilePath).exists()) continue
                            val destFile = java.io.File(meetingRepository.getAudioDir(), "${remote.id}.m4a")
                            val dlResult = client.tryDownloadAudio(remote.id, destFile)
                            if (dlResult.getOrDefault(false)) {
                                meetingRepository.updateMeeting(local.copy(audioFilePath = destFile.absolutePath))
                            }
                        }
                    }
                }
            }
        }
    }

    fun startRecording(customTitle: String, language: String, folderId: String? = null) {
        val ctx = getApplication<Application>()
        val meetingId = UUID.randomUUID().toString()
        val fileName = "meeting_${SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())}.m4a"
        val filePath = File(meetingRepository.getAudioDir(), fileName).absolutePath

        viewModelScope.launch {
            meetingRepository.addMeeting(Meeting(
                id = meetingId,
                title = customTitle.ifBlank { "Riunione ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALIAN).format(Date())}" },
                createdAt = System.currentTimeMillis(),
                language = language,
                audioFilePath = filePath,
                status = MeetingStatus.RECORDING,
                folderId = folderId
            ))
        }

        Intent(ctx, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START
            putExtra(RecordingService.EXTRA_FILE_PATH, filePath)
            putExtra(RecordingService.EXTRA_MEETING_ID, meetingId)
        }.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(it)
            else ctx.startService(it)
        }

        recordingStartTime = SystemClock.elapsedRealtime()
        totalPausedTimeMs = 0
        lastPauseTimeMs = 0
        _uiState.value = _uiState.value.copy(isRecording = true, isPaused = false, currentMeetingId = meetingId, recordingDurationMs = 0)
        timerHandler.post(timerRunnable)
    }

    fun pauseRecording() {
        val ctx = getApplication<Application>()
        ctx.startService(Intent(ctx, RecordingService::class.java).apply { action = RecordingService.ACTION_PAUSE })
        lastPauseTimeMs = SystemClock.elapsedRealtime()
        _uiState.value = _uiState.value.copy(isPaused = true)
    }

    fun resumeRecording() {
        val ctx = getApplication<Application>()
        ctx.startService(Intent(ctx, RecordingService::class.java).apply { action = RecordingService.ACTION_RESUME })
        totalPausedTimeMs += (SystemClock.elapsedRealtime() - lastPauseTimeMs)
        _uiState.value = _uiState.value.copy(isPaused = false)
        timerHandler.post(timerRunnable)
    }

    fun stopRecording() {
        val ctx = getApplication<Application>()
        ctx.startService(Intent(ctx, RecordingService::class.java).apply { action = RecordingService.ACTION_STOP })
        timerHandler.removeCallbacks(timerRunnable)
        _uiState.value = _uiState.value.copy(isRecording = false, isPaused = false)
    }

    /** Copies the content at [uri] into the app's inbox directory and adds it to the list. */
    fun importFromUri(uri: Uri) {
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            inboxRepository.importFromUri(ctx, uri)
        }
    }

    /**
     * Promotes an inbox item to a Meeting with status RECORDED, then navigates to
     * the TranscriptScreen. The local audio file is transferred to the Meeting;
     * the InboxItem record is removed without deleting the file.
     */
    fun processInboxItem(item: InboxItem, title: String, language: String, folderId: String? = null) {
        viewModelScope.launch {
            val meetingId = UUID.randomUUID().toString()
            meetingRepository.addMeeting(
                Meeting(
                    id = meetingId,
                    title = title.ifBlank { item.displayName.substringBeforeLast('.') },
                    createdAt = item.addedAt,
                    language = language,
                    audioFilePath = item.localPath,
                    status = MeetingStatus.RECORDED,
                    folderId = folderId
                )
            )
            inboxRepository.removeItem(item.id, deleteFile = false)
            _navigationEvent.emit(meetingId)
        }
    }

    fun deleteInboxItem(id: String) {
        viewModelScope.launch { inboxRepository.removeItem(id) }
    }

    fun deleteMeeting(meetingId: String) {
        viewModelScope.launch {
            meetingRepository.deleteMeeting(meetingId)
            val syncEnabled = settingsRepository.supabaseSyncEnabled.first()
            if (syncEnabled) {
                val url = settingsRepository.supabaseUrl.first()
                val key = settingsRepository.supabaseAnonKey.first()
                if (url.isNotBlank() && key.isNotBlank()) {
                    SupabaseClient(url, key).deleteMeeting(meetingId)
                }
            }
        }
    }

    // ── Folder management ─────────────────────────────────────────────────────

    fun setSelectedFolder(folderId: String?) {
        _uiState.value = _uiState.value.copy(selectedFolderId = folderId)
    }

    fun createFolder(name: String, colorHex: String) {
        viewModelScope.launch {
            folderRepository.addFolder(MeetingFolder(name = name.trim(), colorHex = colorHex))
        }
    }

    fun updateFolder(id: String, newName: String, colorHex: String) {
        val trimmed = newName.trim().ifBlank { return }
        viewModelScope.launch {
            folderRepository.folders.value.find { it.id == id }?.let {
                folderRepository.updateFolder(it.copy(name = trimmed, colorHex = colorHex))
            }
        }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch {
            // Unassign all meetings from this folder before deleting it
            meetingRepository.meetings.value
                .filter { it.folderId == folderId }
                .forEach { meetingRepository.updateMeeting(it.copy(folderId = null)) }
            folderRepository.deleteFolder(folderId)
            // If the deleted folder was selected, reset filter to "all"
            if (_uiState.value.selectedFolderId == folderId) {
                _uiState.value = _uiState.value.copy(selectedFolderId = null)
            }
        }
    }

    fun assignFolderToMeeting(meetingId: String, folderId: String?) {
        viewModelScope.launch {
            meetingRepository.getMeeting(meetingId)?.let {
                meetingRepository.updateMeeting(it.copy(folderId = folderId))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerHandler.removeCallbacks(timerRunnable)
        try { getApplication<Application>().unregisterReceiver(recordingReceiver) } catch (_: Exception) {}
    }
}
