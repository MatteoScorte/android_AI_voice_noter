package com.transcriber.app.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.transcriber.app.api.DeepgramClient
import com.transcriber.app.api.TranscriptProcessor
import com.transcriber.app.data.ActionItem
import com.transcriber.app.data.MeetingRepository
import com.transcriber.app.data.MeetingStatus
import com.transcriber.app.data.OutlineItem
import com.transcriber.app.data.PromptCategoryEntity
import com.transcriber.app.data.PromptCategoryRepository
import com.transcriber.app.data.SettingsRepository
import com.transcriber.app.data.SupabaseClient
import com.transcriber.app.data.WordTimestamp
import com.transcriber.app.service.PlaybackService
import com.transcriber.app.util.Phrase
import com.transcriber.app.util.groupIntoPhrases
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

data class TranscriptUiState(
    val meetingId: String = "",
    val title: String = "",
    val durationMs: Long = 0,
    val rawTranscript: String = "",
    val finalTranscript: String = "",
    val status: MeetingStatus = MeetingStatus.RECORDED,
    val errorMessage: String = "",
    val isProcessing: Boolean = false,
    val processingStep: String = "",
    val isEditingTitle: Boolean = false,
    // Speaker renaming: original label -> display name
    val speakers: LinkedHashMap<String, String> = linkedMapOf(),
    // Structured summary
    val keywords: List<String> = emptyList(),
    val overview: String = "",
    val outline: List<OutlineItem> = emptyList(),
    val bulletNotes: List<String> = emptyList(),
    val actionItems: List<ActionItem> = emptyList(),
    // Audio player
    val audioFilePath: String = "",
    val isPlayerReady: Boolean = false,
    val isPlayerPlaying: Boolean = false,
    val playerCurrentMs: Long = 0L,
    val playerDurationMs: Long = 0L,
    // Karaoke: word-level timestamps from Deepgram
    val wordTimestamps: List<WordTimestamp> = emptyList(),
    // Spotify-lyrics: words pre-grouped into displayable phrases (derived, not persisted)
    val phrases: List<Phrase> = emptyList(),
    // Cloud sharing
    val isShared: Boolean = false,
    val isSharing: Boolean = false,
    // Category picker
    val categories: List<PromptCategoryEntity> = emptyList(),
    val categoryId: Int = 0,
    val categoryName: String = "",
    val categoryEmoji: String = "",
    val categoryColorHex: String = ""
)

class TranscriptViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        /**
         * Tracks meeting IDs that have an active GlobalScope processing job.
         * Lives in-memory for the duration of the app process — cleared on process kill.
         * This lets loadMeeting() distinguish "still running" from "was interrupted".
         */
        private val activeProcessingIds = java.util.concurrent.CopyOnWriteArraySet<String>()
        fun isProcessingActive(meetingId: String): Boolean = meetingId in activeProcessingIds
    }

    private val meetingRepository = MeetingRepository(application)
    private val settingsRepository = SettingsRepository(application)
    private val promptCategoryRepository = PromptCategoryRepository(application)
    private val deepgramClient = DeepgramClient()
    private val transcriptProcessor = TranscriptProcessor()

    private val _uiState = MutableStateFlow(TranscriptUiState())

    init {
        viewModelScope.launch {
            promptCategoryRepository.allCategories.collect { cats ->
                _uiState.value = _uiState.value.copy(categories = cats)
            }
        }
    }
    val uiState: StateFlow<TranscriptUiState> = _uiState.asStateFlow()

    // ── Audio Player (foreground service) ─────────────────────────────────────

    private var playbackBinder: PlaybackService.PlaybackBinder? = null
    private var isBound = false
    private var playerUpdateJob: Job? = null
    private var eventCollectorJob: Job? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            playbackBinder = service as PlaybackService.PlaybackBinder
            isBound = true

            // Collect state events emitted by the service
            eventCollectorJob?.cancel()
            eventCollectorJob = viewModelScope.launch {
                playbackBinder!!.events.collect { event ->
                    when (event) {
                        is PlaybackService.PlaybackEvent.DurationAvailable -> {
                            _uiState.value = _uiState.value.copy(playerDurationMs = event.ms)
                        }
                        PlaybackService.PlaybackEvent.Ready -> {
                            _uiState.value = _uiState.value.copy(isPlayerReady = true)
                        }
                        PlaybackService.PlaybackEvent.Completed -> {
                            playerUpdateJob?.cancel()
                            _uiState.value = _uiState.value.copy(
                                isPlayerPlaying = false,
                                playerCurrentMs = 0L
                            )
                        }
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            playbackBinder = null
            isBound = false
            eventCollectorJob?.cancel()
        }
    }

    fun loadMeeting(meetingId: String) {
        val m = meetingRepository.getMeeting(meetingId) ?: return
        val prev = _uiState.value

        // True when the same meeting is reloaded after a configuration change (rotation, etc.).
        // In that case the ViewModel — and its viewModelScope jobs — is still alive;
        // we must NOT reset player state or re-initialise the service.
        val isReload = prev.meetingId == meetingId

        // If the meeting is stuck in TRANSCRIBING/PROCESSING but no job is running
        // (process was killed mid-transcription), reset to RECORDED so the user can retry.
        val isStuck = (m.status == MeetingStatus.TRANSCRIBING || m.status == MeetingStatus.PROCESSING)
                && !isProcessingActive(meetingId)
        val effectiveStatus = if (isStuck) MeetingStatus.RECORDED else m.status
        if (isStuck) {
            viewModelScope.launch {
                meetingRepository.updateMeeting(
                    m.copy(status = MeetingStatus.RECORDED, errorMessage = "")
                )
            }
        }

        val aliases = m.speakerAliases ?: emptyMap()
        val speakersToShow = if (aliases.isNotEmpty()) {
            LinkedHashMap(aliases)
        } else {
            val detected = detectSpeakers(m.finalTranscript)
            if (detected.isNotEmpty()) {
                viewModelScope.launch {
                    meetingRepository.updateMeeting(m.copy(speakerAliases = detected))
                }
            }
            detected
        }

        val processingNow = isProcessingActive(meetingId)

        _uiState.value = TranscriptUiState(
            meetingId = m.id, title = m.title, durationMs = m.durationMs,
            rawTranscript = m.rawTranscript, finalTranscript = m.finalTranscript,
            status = effectiveStatus, errorMessage = if (isStuck) "" else m.errorMessage,
            speakers = speakersToShow,
            keywords = m.keywords ?: emptyList(),
            overview = m.overview ?: "",
            outline = m.outline ?: emptyList(),
            bulletNotes = m.bulletNotes ?: emptyList(),
            actionItems = m.actionItems ?: emptyList(),
            audioFilePath = m.audioFilePath,
            wordTimestamps = m.wordTimestamps ?: emptyList(),
            phrases = groupIntoPhrases(m.wordTimestamps ?: emptyList()),
            isShared = m.isShared,
            categoryId = m.categoryId,
            categoryName = m.categoryName.orEmpty(),
            categoryEmoji = m.categoryEmoji.orEmpty(),
            categoryColorHex = m.categoryColorHex.orEmpty(),
            // ── Bug 2 fix: restore processing indicator if the background job is still alive ──
            isProcessing   = processingNow,
            processingStep = if (isReload && processingNow) prev.processingStep else "",
            // ── Bug 1 fix: preserve live player state across config-change reloads ──
            isPlayerReady    = if (isReload) prev.isPlayerReady    else false,
            isPlayerPlaying  = if (isReload) prev.isPlayerPlaying  else false,
            playerCurrentMs  = if (isReload) prev.playerCurrentMs  else 0L,
            playerDurationMs = if (isReload) prev.playerDurationMs else 0L
        )

        // Only start/bind the service when it is not already alive.
        // On a config-change reload the service and binder are still alive;
        // sending ACTION_INIT again with the same path causes the service to
        // re-emit Ready/Duration so the ViewModel can restore its state.
        if (m.audioFilePath.isNotEmpty()) {
            initPlayer(m.audioFilePath)
        }
    }

    private fun initPlayer(audioFilePath: String) {
        val app = getApplication<Application>()

        // Start the foreground service (or send ACTION_INIT to an already-running one)
        val initIntent = Intent(app, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_INIT
            putExtra(PlaybackService.EXTRA_FILE_PATH, audioFilePath)
        }
        ContextCompat.startForegroundService(app, initIntent)

        // Bind for position polling and event collection (idempotent — skip if already bound)
        if (!isBound) {
            _uiState.value = _uiState.value.copy(
                isPlayerReady = false, isPlayerPlaying = false,
                playerCurrentMs = 0L, playerDurationMs = 0L
            )
            app.bindService(
                Intent(app, PlaybackService::class.java),
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
        }
    }

    fun playPause() {
        val binder = playbackBinder ?: return
        if (!binder.isReady()) return

        getApplication<Application>().startService(
            Intent(getApplication(), PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_PLAY_PAUSE
            }
        )

        // Optimistic state update — the service command is processed synchronously
        // on the main thread so the toggle is immediate for all practical purposes.
        val willBePlaying = !_uiState.value.isPlayerPlaying
        _uiState.value = _uiState.value.copy(isPlayerPlaying = willBePlaying)

        if (willBePlaying) {
            startPositionPolling()
        } else {
            playerUpdateJob?.cancel()
        }
    }

    fun seekTo(ms: Long) {
        if (playbackBinder == null) return
        val clamped = ms.coerceIn(0L, _uiState.value.playerDurationMs)
        getApplication<Application>().startService(
            Intent(getApplication(), PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_SEEK
                putExtra(PlaybackService.EXTRA_SEEK_MS, clamped)
            }
        )
        _uiState.value = _uiState.value.copy(playerCurrentMs = clamped)
        // Restart polling so position updates resume immediately after seek
        if (_uiState.value.isPlayerPlaying) startPositionPolling()
    }

    private fun startPositionPolling() {
        playerUpdateJob?.cancel()
        playerUpdateJob = viewModelScope.launch {
            while (isActive) {
                val pos = playbackBinder?.currentPositionMs() ?: 0L
                _uiState.value = _uiState.value.copy(playerCurrentMs = pos)
                delay(80)   // ~12 updates/s — smooth enough for karaoke
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerUpdateJob?.cancel()
        eventCollectorJob?.cancel()
        if (isBound) {
            getApplication<Application>().unbindService(serviceConnection)
            isBound = false
        }
        // Stop the playback service when leaving the transcript screen permanently
        getApplication<Application>().startService(
            Intent(getApplication(), PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_STOP
            }
        )
    }

    // ── Speaker detection ─────────────────────────────────────────────────────

    /**
     * Detect speaker labels from the final transcript.
     * Looks for lines starting with "Label:" or "[Label]" patterns.
     * Returns a map of originalLabel -> displayName (initially identical).
     */
    private fun detectSpeakers(text: String): LinkedHashMap<String, String> {
        if (text.isBlank()) return linkedMapOf()

        val map = LinkedHashMap<String, String>()
        val pattern = Regex(
            """(?m)^\*{0,2}(\[?(?:Speaker|Interlocutore|Partecipante|Voce|Ospite|Host|Cliente|Agente)\s*\d*\]?)\*{0,2}\s*:""",
            RegexOption.IGNORE_CASE
        )
        pattern.findAll(text).forEach { match ->
            val label = match.groupValues[1].trim().removeSurrounding("[", "]")
            if (!map.containsKey(label)) map[label] = label
        }

        if (map.isEmpty()) {
            val genericPattern = Regex("""(?m)^([A-ZÀÈÌÒÙ][a-zA-ZÀ-ÿ]+(?:\s[A-ZÀÈÌÒÙ][a-zA-ZÀ-ÿ]+)?)\s*:""")
            genericPattern.findAll(text).forEach { match ->
                val label = match.groupValues[1].trim()
                val isAllCaps = (label == label.uppercase(Locale.getDefault()))
                val excluded = setOf("Data", "Ora", "Note", "Verbale", "Partecipanti", "Decisioni", "Oggetto", "Luogo")
                if (!isAllCaps && !excluded.any { it.equals(label, ignoreCase = true) } && !map.containsKey(label)) {
                    map[label] = label
                }
            }
        }
        return map
    }

    // ── Speaker renaming ──────────────────────────────────────────────────────

    fun renameSpeaker(originalLabel: String, newName: String) {
        val trimmed = newName.trim().ifBlank { return }
        val current = _uiState.value
        val id = current.meetingId

        val updatedTranscript = current.finalTranscript.replace(originalLabel, trimmed)

        val updatedSpeakers = LinkedHashMap<String, String>()
        current.speakers.forEach { (orig, display) ->
            if (orig == originalLabel) updatedSpeakers[trimmed] = trimmed
            else updatedSpeakers[orig] = display
        }

        _uiState.value = current.copy(finalTranscript = updatedTranscript, speakers = updatedSpeakers)

        viewModelScope.launch {
            meetingRepository.getMeeting(id)?.let { existing ->
                val newAliases = (existing.speakerAliases ?: emptyMap()).toMutableMap()
                newAliases.remove(originalLabel)
                newAliases[trimmed] = trimmed
                meetingRepository.updateMeeting(
                    existing.copy(finalTranscript = updatedTranscript, speakerAliases = newAliases)
                )
            }
        }
    }

    fun renameTitle(newTitle: String) {
        val id = _uiState.value.meetingId
        val trimmed = newTitle.trim().ifBlank { return }
        _uiState.value = _uiState.value.copy(title = trimmed, isEditingTitle = false)
        viewModelScope.launch {
            meetingRepository.getMeeting(id)?.let {
                meetingRepository.updateMeeting(it.copy(title = trimmed))
            }
        }
    }

    fun setEditingTitle(editing: Boolean) {
        _uiState.value = _uiState.value.copy(isEditingTitle = editing)
    }

    // ── Cloud sharing ─────────────────────────────────────────────────────────

    /**
     * Explicitly uploads this meeting to Supabase and marks it as shared locally.
     * Only called on user request — there is no automatic sync.
     */
    fun shareMeeting() {
        val id = _uiState.value.meetingId
        _uiState.value = _uiState.value.copy(isSharing = true)
        viewModelScope.launch {
            try {
                val url = settingsRepository.supabaseUrl.first()
                val key = settingsRepository.supabaseAnonKey.first()
                if (url.isBlank() || key.isBlank()) {
                    _uiState.value = _uiState.value.copy(isSharing = false)
                    return@launch
                }
                val meeting = meetingRepository.getMeeting(id) ?: run {
                    _uiState.value = _uiState.value.copy(isSharing = false)
                    return@launch
                }
                val sharedMeeting = meeting.copy(isShared = true)
                SupabaseClient(url, key).upsertMeeting(sharedMeeting, meetingRepository.deviceId)
                meetingRepository.updateMeeting(sharedMeeting)
                _uiState.value = _uiState.value.copy(isSharing = false, isShared = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSharing = false,
                    errorMessage = "Errore condivisione: ${e.message}"
                )
            }
        }
    }

    // ── Full processing pipeline ──────────────────────────────────────────────

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    fun startFullProcessing(meetingId: String, category: PromptCategoryEntity) {
        activeProcessingIds.add(meetingId)
        kotlinx.coroutines.GlobalScope.launch {
            try {
                val meeting = meetingRepository.getMeeting(meetingId)
                if (meeting == null) { onError(meetingId, "Riunione non trovata"); return@launch }

                val deepgramKey = settingsRepository.deepgramApiKey.first()
                val openRouterKey = settingsRepository.openRouterApiKey.first()
                val model = settingsRepository.selectedModel.first()
                val language = meeting.language

                if (deepgramKey.isBlank()) { onError(meetingId, "Inserisci la API Key di Deepgram nelle Impostazioni"); return@launch }
                if (openRouterKey.isBlank()) { onError(meetingId, "Inserisci la API Key di OpenRouter nelle Impostazioni"); return@launch }

                // ── STEP 1: TRASCRIZIONE ──
                val rawText: String
                var wordTimestamps: List<WordTimestamp>
                if (meeting.rawTranscript.isNotBlank()) {
                    rawText = meeting.rawTranscript
                    wordTimestamps = meeting.wordTimestamps ?: emptyList()
                    _uiState.value = _uiState.value.copy(
                        isProcessing = true,
                        rawTranscript = rawText,
                        processingStep = "Analisi e stesura riassunto strutturato...",
                        status = MeetingStatus.PROCESSING,
                        errorMessage = ""
                    )
                    meetingRepository.updateMeeting(meeting.copy(status = MeetingStatus.PROCESSING))
                } else {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = true,
                        processingStep = "Trascrizione audio con Deepgram (nova-2)...",
                        status = MeetingStatus.TRANSCRIBING,
                        errorMessage = ""
                    )
                    meetingRepository.updateMeeting(meeting.copy(status = MeetingStatus.TRANSCRIBING))

                    val audioFile = java.io.File(meeting.audioFilePath)
                    if (!audioFile.exists()) { onError(meetingId, "File audio non trovato: ${meeting.audioFilePath}"); return@launch }

                    val transcriptionResult = deepgramClient.transcribeAndDiarize(apiKey = deepgramKey, audioFile = audioFile, language = language)
                    if (transcriptionResult.isFailure) { onError(meetingId, "Errore Deepgram: ${transcriptionResult.exceptionOrNull()?.message}"); return@launch }

                    val dgResult = transcriptionResult.getOrThrow()
                    rawText = dgResult.transcript
                    wordTimestamps = dgResult.wordTimestamps

                    _uiState.value = _uiState.value.copy(
                        rawTranscript = rawText,
                        wordTimestamps = wordTimestamps,
                        phrases = groupIntoPhrases(wordTimestamps),
                        processingStep = "Analisi e stesura riassunto strutturato...",
                        status = MeetingStatus.PROCESSING
                    )
                    meetingRepository.updateMeeting(
                        meeting.copy(rawTranscript = rawText, wordTimestamps = wordTimestamps, status = MeetingStatus.PROCESSING)
                    )
                }

                // ── STEP 2: ANALISI LLM ──
                val processingResult = transcriptProcessor.processTranscript(
                    apiKey = openRouterKey, model = model,
                    rawTranscript = rawText, meetingStartTime = meeting.createdAt,
                    categorySystemPrompt = category.systemPrompt
                )
                if (processingResult.isFailure) { onError(meetingId, "Errore LLM: ${processingResult.exceptionOrNull()?.message}"); return@launch }

                val resultData = processingResult.getOrThrow()
                val finalText = resultData.finalTranscript
                val llmMappedSpeakers = resultData.mappedSpeakers

                val finalSpeakersMap = LinkedHashMap<String, String>()
                if (llmMappedSpeakers.isNotEmpty()) {
                    llmMappedSpeakers.forEach { (originalKey, mappedName) -> finalSpeakersMap[originalKey] = mappedName }
                } else {
                    finalSpeakersMap.putAll(detectSpeakers(finalText))
                }

                val completedMeeting = meeting.copy(
                    rawTranscript = rawText,
                    finalTranscript = finalText,
                    wordTimestamps = wordTimestamps,
                    speakerAliases = finalSpeakersMap,
                    status = MeetingStatus.COMPLETED,
                    keywords = resultData.keywords,
                    overview = resultData.overview,
                    outline = resultData.outline,
                    bulletNotes = resultData.bulletNotes,
                    actionItems = resultData.actionItems,
                    categoryId = category.id,
                    categoryName = category.name,
                    categoryEmoji = category.emoji,
                    categoryColorHex = category.colorHex
                )

                meetingRepository.updateMeeting(completedMeeting)

                _uiState.value = _uiState.value.copy(
                    finalTranscript = finalText,
                    isProcessing = false,
                    processingStep = "",
                    status = MeetingStatus.COMPLETED,
                    speakers = finalSpeakersMap,
                    keywords = resultData.keywords,
                    overview = resultData.overview,
                    outline = resultData.outline,
                    bulletNotes = resultData.bulletNotes,
                    actionItems = resultData.actionItems,
                    wordTimestamps = wordTimestamps,
                    phrases = groupIntoPhrases(wordTimestamps),
                    categoryId = category.id,
                    categoryName = category.name,
                    categoryEmoji = category.emoji,
                    categoryColorHex = category.colorHex
                )

                // Cloud sync is now explicit via shareMeeting() — no automatic upload here.

            } finally {
                activeProcessingIds.remove(meetingId)
            }
        }
    }

    private suspend fun onError(meetingId: String, message: String) {
        _uiState.value = _uiState.value.copy(
            isProcessing = false, processingStep = "", errorMessage = message, status = MeetingStatus.ERROR
        )
        meetingRepository.getMeeting(meetingId)?.let {
            meetingRepository.updateMeeting(it.copy(status = MeetingStatus.ERROR, errorMessage = message))
        }
    }
}
