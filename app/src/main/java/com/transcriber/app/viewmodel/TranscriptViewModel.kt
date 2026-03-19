package com.transcriber.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.transcriber.app.api.DeepgramClient
import com.transcriber.app.api.TranscriptProcessor
import com.transcriber.app.data.ActionItem
import com.transcriber.app.data.MeetingRepository
import com.transcriber.app.data.MeetingStatus
import com.transcriber.app.data.OutlineItem
import com.transcriber.app.data.SettingsRepository
import com.transcriber.app.data.SupabaseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
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
    val actionItems: List<ActionItem> = emptyList()
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
    private val deepgramClient = DeepgramClient()
    private val transcriptProcessor = TranscriptProcessor()

    private val _uiState = MutableStateFlow(TranscriptUiState())
    val uiState: StateFlow<TranscriptUiState> = _uiState.asStateFlow()

    fun loadMeeting(meetingId: String) {
        val m = meetingRepository.getMeeting(meetingId) ?: return

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

        _uiState.value = TranscriptUiState(
            meetingId = m.id, title = m.title, durationMs = m.durationMs,
            rawTranscript = m.rawTranscript, finalTranscript = m.finalTranscript,
            status = effectiveStatus, errorMessage = if (isStuck) "" else m.errorMessage,
            speakers = speakersToShow,
            keywords = m.keywords ?: emptyList(),
            overview = m.overview ?: "",
            outline = m.outline ?: emptyList(),
            bulletNotes = m.bulletNotes ?: emptyList(),
            actionItems = m.actionItems ?: emptyList()
        )
    }

    /**
     * Detect speaker labels from the final transcript.
     * Looks for lines starting with "Label:" or "[Label]" patterns.
     * Returns a map of originalLabel -> displayName (initially identical).
     */
    private fun detectSpeakers(text: String): LinkedHashMap<String, String> {
        if (text.isBlank()) return linkedMapOf()

        val map = LinkedHashMap<String, String>()
        // Match patterns like: "Speaker 1:", "Marco:", "[Speaker 2]", "**Speaker 1**:"
        val pattern = Regex(
            """(?m)^\*{0,2}(\[?(?:Speaker|Interlocutore|Partecipante|Voce|Ospite|Host|Cliente|Agente)\s*\d*\]?)\*{0,2}\s*:""",
            RegexOption.IGNORE_CASE
        )
        pattern.findAll(text).forEach { match ->
            val label = match.groupValues[1].trim().removeSurrounding("[", "]")
            if (!map.containsKey(label)) map[label] = label
        }

        // Also try generic "Word Word:" patterns at line start (for names the AI may have inferred)—but never ALL CAPS.
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

    /**
     * Rename a speaker: replaces the original label with the new name everywhere in finalTranscript,
     * saves both the updated transcript AND the alias map so it persists across navigation.
     */
    fun renameSpeaker(originalLabel: String, newName: String) {
        val trimmed = newName.trim().ifBlank { return }
        val current = _uiState.value
        val id = current.meetingId

        val updatedTranscript = current.finalTranscript.replace(originalLabel, trimmed)

        val updatedSpeakers = LinkedHashMap<String, String>()
        current.speakers.forEach { (orig, display) ->
            if (orig == originalLabel) {
                updatedSpeakers[trimmed] = trimmed
            } else {
                updatedSpeakers[orig] = display
            }
        }

        _uiState.value = current.copy(finalTranscript = updatedTranscript, speakers = updatedSpeakers)

        viewModelScope.launch {
            meetingRepository.getMeeting(id)?.let { existing ->
                val newAliases = (existing.speakerAliases ?: emptyMap()).toMutableMap()
                newAliases.remove(originalLabel)
                newAliases[trimmed] = trimmed
                val updated = existing.copy(
                    finalTranscript = updatedTranscript,
                    speakerAliases = newAliases
                )
                meetingRepository.updateMeeting(updated)
                val syncEnabled = settingsRepository.supabaseSyncEnabled.first()
                if (syncEnabled) {
                    val url = settingsRepository.supabaseUrl.first()
                    val key = settingsRepository.supabaseAnonKey.first()
                    if (url.isNotBlank() && key.isNotBlank()) {
                        SupabaseClient(url, key).upsertMeeting(updated, meetingRepository.deviceId)
                    }
                }
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
            val syncEnabled = settingsRepository.supabaseSyncEnabled.first()
            if (syncEnabled) {
                val url = settingsRepository.supabaseUrl.first()
                val key = settingsRepository.supabaseAnonKey.first()
                if (url.isNotBlank() && key.isNotBlank()) {
                    SupabaseClient(url, key).updateTitle(id, trimmed)
                }
            }
        }
    }

    fun setEditingTitle(editing: Boolean) {
        _uiState.value = _uiState.value.copy(isEditingTitle = editing)
    }

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    fun startFullProcessing(meetingId: String) {
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
                // Skip Deepgram if a raw transcript already exists (e.g. retry after LLM failure)
                val rawText: String
                if (meeting.rawTranscript.isNotBlank()) {
                    rawText = meeting.rawTranscript
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

                    val audioFile = File(meeting.audioFilePath)
                    if (!audioFile.exists()) { onError(meetingId, "File audio non trovato: ${meeting.audioFilePath}"); return@launch }

                    val transcriptionResult = deepgramClient.transcribeAndDiarize(apiKey = deepgramKey, audioFile = audioFile, language = language)
                    if (transcriptionResult.isFailure) { onError(meetingId, "Errore Deepgram: ${transcriptionResult.exceptionOrNull()?.message}"); return@launch }

                    rawText = transcriptionResult.getOrThrow()
                    _uiState.value = _uiState.value.copy(
                        rawTranscript = rawText,
                        processingStep = "Analisi e stesura riassunto strutturato...",
                        status = MeetingStatus.PROCESSING
                    )
                    meetingRepository.updateMeeting(meeting.copy(rawTranscript = rawText, status = MeetingStatus.PROCESSING))
                }

                // ── STEP 2: ANALISI LLM ──
                val processingResult = transcriptProcessor.processTranscript(
                    apiKey = openRouterKey, model = model,
                    rawTranscript = rawText, meetingStartTime = meeting.createdAt
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
                    speakerAliases = finalSpeakersMap,
                    status = MeetingStatus.COMPLETED,
                    keywords = resultData.keywords,
                    overview = resultData.overview,
                    outline = resultData.outline,
                    bulletNotes = resultData.bulletNotes,
                    actionItems = resultData.actionItems
                )

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
                    actionItems = resultData.actionItems
                )
                meetingRepository.updateMeeting(completedMeeting)

                // ── STEP 3: SYNC CLOUD (optional) ──
                val syncEnabled = settingsRepository.supabaseSyncEnabled.first()
                if (syncEnabled) {
                    val supaUrl = settingsRepository.supabaseUrl.first()
                    val supaKey = settingsRepository.supabaseAnonKey.first()
                    if (supaUrl.isNotBlank() && supaKey.isNotBlank()) {
                        _uiState.value = _uiState.value.copy(processingStep = "Caricamento su cloud...")
                        SupabaseClient(supaUrl, supaKey).upsertMeeting(completedMeeting, meetingRepository.deviceId)
                        _uiState.value = _uiState.value.copy(processingStep = "")
                    }
                }
            } finally {
                // Always remove from tracker — whether completed, errored, or cancelled
                activeProcessingIds.remove(meetingId)
            }
        }
    }

    private suspend fun onError(meetingId: String, message: String) {
        _uiState.value = _uiState.value.copy(isProcessing = false, processingStep = "", errorMessage = message, status = MeetingStatus.ERROR)
        meetingRepository.getMeeting(meetingId)?.let {
            meetingRepository.updateMeeting(it.copy(status = MeetingStatus.ERROR, errorMessage = message))
        }
    }
}
