package com.transcriber.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.transcriber.app.api.AVAILABLE_MODELS
import com.transcriber.app.api.LlmModel
import com.transcriber.app.data.MeetingRepository
import com.transcriber.app.data.SettingsRepository
import com.transcriber.app.data.SupabaseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SettingsUiState(
    val openRouterApiKey: String = "",
    val whisperApiKey: String = "",
    val selectedModel: String = "",
    val whisperEndpoint: String = "https://api.openai.com/v1/audio/transcriptions",
    val availableModels: List<LlmModel> = AVAILABLE_MODELS,
    val supabaseUrl: String = SettingsRepository.DEFAULT_SUPABASE_URL,
    val supabaseAnonKey: String = "",
    val supabaseSyncEnabled: Boolean = false,
    val deepgramApiKey: String = "",
    val selectedLanguage: String = "it",
    val isSaved: Boolean = false,
    val syncStatus: String = ""
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = SettingsRepository(application)
    private val meetingRepo = MeetingRepository(application)
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Load all settings
            val or = repo.openRouterApiKey.first()
            val w = repo.whisperApiKey.first()
            val m = repo.selectedModel.first()
            val e = repo.whisperEndpoint.first()
            val supaUrl = repo.supabaseUrl.first()
            val supaKey = repo.supabaseAnonKey.first()
            val supaEnabled = repo.supabaseSyncEnabled.first()
            val dgKey = repo.deepgramApiKey.first()
            val lang = repo.selectedLanguage.first()
            _uiState.value = SettingsUiState(
                openRouterApiKey = or, whisperApiKey = w, selectedModel = m,
                whisperEndpoint = e, supabaseUrl = supaUrl, supabaseAnonKey = supaKey,
                supabaseSyncEnabled = supaEnabled, deepgramApiKey = dgKey, selectedLanguage = lang, 
                availableModels = AVAILABLE_MODELS
            )
        }
    }

    fun updateOpenRouterApiKey(key: String) { _uiState.value = _uiState.value.copy(openRouterApiKey = key, isSaved = false) }
    fun updateWhisperApiKey(key: String) { _uiState.value = _uiState.value.copy(whisperApiKey = key, isSaved = false) }
    fun updateSelectedModel(model: String) { _uiState.value = _uiState.value.copy(selectedModel = model, isSaved = false) }
    fun updateWhisperEndpoint(ep: String) { _uiState.value = _uiState.value.copy(whisperEndpoint = ep, isSaved = false) }
    fun updateSupabaseUrl(url: String) { _uiState.value = _uiState.value.copy(supabaseUrl = url, isSaved = false) }
    fun updateSupabaseAnonKey(key: String) { _uiState.value = _uiState.value.copy(supabaseAnonKey = key, isSaved = false) }
    fun updateSupabaseSyncEnabled(enabled: Boolean) { _uiState.value = _uiState.value.copy(supabaseSyncEnabled = enabled, isSaved = false) }
    fun updateDeepgramApiKey(key: String) { _uiState.value = _uiState.value.copy(deepgramApiKey = key, isSaved = false) }
    fun updateSelectedLanguage(lang: String) { _uiState.value = _uiState.value.copy(selectedLanguage = lang, isSaved = false) }

    fun saveSettings() {
        viewModelScope.launch {
            val s = _uiState.value
            repo.updateOpenRouterApiKey(s.openRouterApiKey)
            repo.updateWhisperApiKey(s.whisperApiKey)
            repo.updateSelectedModel(s.selectedModel)
            repo.updateWhisperEndpoint(s.whisperEndpoint)
            repo.updateSupabaseUrl(s.supabaseUrl)
            repo.updateSupabaseAnonKey(s.supabaseAnonKey)
            repo.updateSupabaseSyncEnabled(s.supabaseSyncEnabled)
            repo.updateDeepgramApiKey(s.deepgramApiKey)
            repo.updateSelectedLanguage(s.selectedLanguage)
            _uiState.value = _uiState.value.copy(isSaved = true, syncStatus = "")
        }
    }

    /** Manual sync: push local completed meetings UP, then pull remote ones DOWN */
    fun syncNow() {
        viewModelScope.launch {
            val url = _uiState.value.supabaseUrl
            val key = _uiState.value.supabaseAnonKey
            if (url.isBlank() || key.isBlank()) {
                _uiState.value = _uiState.value.copy(syncStatus = "⚠️ Inserisci URL e chiave Supabase")
                return@launch
            }
            _uiState.value = _uiState.value.copy(syncStatus = "Caricamento riunioni locali...")
            val client = SupabaseClient(url, key)
            val deviceId = meetingRepo.deviceId

            // 1. Push only meetings explicitly shared by the user (isShared = true)
            val localShared = meetingRepo.meetings.value.filter { it.isShared }
            var pushErrors = 0
            localShared.forEach { meeting ->
                val r = client.upsertMeeting(meeting, deviceId)
                if (r.isFailure) pushErrors++
            }

            // 2. Pull all remote meetings and merge
            _uiState.value = _uiState.value.copy(syncStatus = "Download riunioni cloud...")
            val pullResult = client.fetchAllMeetings()
            if (pullResult.isSuccess) {
                val remoteList = pullResult.getOrThrow()
                meetingRepo.mergeRemoteMeetings(remoteList)

                // 3. Download audio files for remote meetings that have no local audio
                _uiState.value = _uiState.value.copy(syncStatus = "Download audio...")
                val localMeetings = meetingRepo.meetings.value
                var audioDownloaded = 0
                for (remote in remoteList) {
                    val local = localMeetings.find { it.id == remote.id } ?: continue
                    if (local.audioFilePath.isNotEmpty() && java.io.File(local.audioFilePath).exists()) continue
                    val destFile = java.io.File(meetingRepo.getAudioDir(), "${remote.id}.m4a")
                    val dlResult = client.tryDownloadAudio(remote.id, destFile)
                    if (dlResult.getOrDefault(false)) {
                        meetingRepo.updateMeeting(local.copy(audioFilePath = destFile.absolutePath))
                        audioDownloaded++
                    }
                }

                val total = remoteList.size
                val pushInfo = if (pushErrors > 0) " (⚠️ $pushErrors errori upload)" else ""
                val audioInfo = if (audioDownloaded > 0) ", $audioDownloaded audio scaricati" else ""
                _uiState.value = _uiState.value.copy(
                    syncStatus = "✓ Sincronizzato — $total riunioni nel cloud, ${localShared.size} caricate$pushInfo$audioInfo"
                )
            } else {
                val err = pullResult.exceptionOrNull()?.message ?: pullResult.exceptionOrNull()?.toString() ?: "Errore sconosciuto"
                _uiState.value = _uiState.value.copy(syncStatus = "❌ $err")
            }
        }
    }
}
