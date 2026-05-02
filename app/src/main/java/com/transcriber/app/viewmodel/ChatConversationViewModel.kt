package com.transcriber.app.viewmodel

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.transcriber.app.R
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
import java.util.concurrent.TimeUnit

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
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val notifManager = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val _uiState = MutableStateFlow(ChatConversationUiState())
    val uiState: StateFlow<ChatConversationUiState> = _uiState.asStateFlow()

    init {
        createNotificationChannel()
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
                    append(VOXLOG_SYSTEM_PROMPT)
                    if (conv.agentPrompt.isNotBlank()) {
                        append("\n\n<istruzioni_personalizzate>\n${conv.agentPrompt}\n</istruzioni_personalizzate>")
                    }
                    val meetingId = conv.meetingId
                    if (meetingId != null) {
                        val meeting = meetingRepo.getMeeting(meetingId)
                        val transcript = meeting?.finalTranscript
                            ?.ifBlank { meeting.rawTranscript }
                            .orEmpty()
                        if (transcript.isNotBlank()) {
                            append("\n\n<trascrizione titolo=\"${conv.meetingTitle ?: "Recording"}\">\n")
                            append(transcript)
                            append("\n</trascrizione>")
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

    fun exportToWebhook(skill: CanvaSkillEntity, style: String = "blank", modelId: String = "", slideCount: Int = 10, fileName: String = "") {
        val conv = _uiState.value.conversation ?: return
        val meetingId = conv.meetingId ?: return
        val notifId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

        viewModelScope.launch {
            val resolvedTitle = conv.meetingTitle ?: conv.title
            val resolvedFileName = fileName.ifBlank { resolvedTitle }
            notifyGenerating(notifId, skill.name, resolvedFileName)

            try {
                val webhookUrl = settingsRepo.n8nWebhookUrl.first()
                if (webhookUrl.isBlank()) {
                    notifyError(notifId, "n8n webhook URL non configurato. Vai nelle Impostazioni.")
                    return@launch
                }

                val meeting = meetingRepo.getMeeting(meetingId)
                val transcript = meeting?.finalTranscript?.ifBlank { meeting.rawTranscript }.orEmpty()
                if (transcript.isBlank()) {
                    notifyError(notifId, "Trascritto non disponibile per questo audio.")
                    return@launch
                }

                val model = if (modelId.isNotBlank()) modelId else settingsRepo.selectedModel.first()
                val payload = WebhookPayload(
                    title = resolvedTitle,
                    transcript = transcript,
                    skill_name = skill.name,
                    skill_prompt = skill.agentPrompt,
                    output_type = skill.outputType,
                    model = model,
                    style = style,
                    slide_count = slideCount,
                    file_name = resolvedFileName
                )

                val response = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url(webhookUrl)
                        .post(gson.toJson(payload).toRequestBody("application/json".toMediaType()))
                        .build()
                    httpClient.newCall(request).execute()
                }

                if (!response.isSuccessful) {
                    notifyError(notifId, "Errore webhook: HTTP ${response.code}")
                    return@launch
                }

                val webhookResponse = gson.fromJson(response.body?.string() ?: "", WebhookResponse::class.java)
                if (webhookResponse.link.isNotBlank()) {
                    repo.saveMessage(conv.id, "system_link", "${skill.name}\n${webhookResponse.link}")
                    notifySuccess(notifId, skill.name, webhookResponse.link)
                } else {
                    notifyError(notifId, "Nessun link ricevuto dal webhook.")
                }
            } catch (e: IOException) {
                notifyError(notifId, "Errore di rete: ${e.message ?: "Sconosciuto"}")
            } catch (e: Exception) {
                notifyError(notifId, e.message ?: "Errore sconosciuto")
            }
        }
    }

    fun resetExport() {
        _uiState.value = _uiState.value.copy(exportStatus = ChatExportStatus.Idle)
    }

    // ── Slide notification helpers ────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SLIDES_CHANNEL_ID,
                "Generazione Slide",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Notifiche per la generazione delle presentazioni" }
            notifManager.createNotificationChannel(channel)
        }
    }

    private fun notifyGenerating(notifId: Int, skillName: String, fileName: String) {
        val notif = NotificationCompat.Builder(getApplication(), SLIDES_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_mic)
            .setContentTitle("Generazione slide in corso…")
            .setContentText("$skillName — $fileName")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()
        notifManager.notify(notifId, notif)
    }

    private fun notifySuccess(notifId: Int, skillName: String, link: String) {
        val openIntent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
        val pi = PendingIntent.getActivity(
            getApplication(), notifId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(getApplication(), SLIDES_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_play)
            .setContentTitle("Slide pronte!")
            .setContentText("$skillName — tocca per aprire")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notifManager.notify(notifId, notif)
    }

    private fun notifyError(notifId: Int, message: String) {
        val notif = NotificationCompat.Builder(getApplication(), SLIDES_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_stop)
            .setContentTitle("Errore generazione slide")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        notifManager.notify(notifId, notif)
    }

    companion object {
        private const val SLIDES_CHANNEL_ID = "slides_generation"

        // Fixed, non-editable system prompt injected into every chat.
        // Describes Voxlog and its UI so the agent can answer "how do I...?" questions accurately.
        val VOXLOG_SYSTEM_PROMPT = """
<system>
  <identità>
    Sei l'assistente AI integrato in Voxlog, un'applicazione Android per la registrazione audio, la trascrizione automatica e l'analisi intelligente di riunioni, lezioni, interviste e conversazioni.
    Il tuo scopo è aiutare l'utente a ricavare il massimo valore dai propri audio e migliorare la propria produttività scolastica e lavorativa.
  </identità>

  <funzionalità_app>
    Voxlog offre le seguenti funzionalità principali:
    1. Registrazione audio: avvio/pausa/stop dalla schermata Home con il pulsante rosso in basso.
    2. Importazione file: è possibile importare file audio da altre applicazioni tramite il menu Home.
    3. Trascrizione automatica: Deepgram (nova-2) con riconoscimento automatico di più speaker.
    4. Analisi AI: dopo la trascrizione, l'AI genera riassunto strutturato, scaletta con timestamp, parole chiave, note bullet e action items.
    5. Rinomina speaker: nella schermata Transcript si possono rinominare i partecipanti; i nomi si aggiornano ovunque nel testo.
    6. Generazione presentazioni: invia la trascrizione a un webhook n8n che gestisce la creazione di slide tramite AI. Il risultato arriva come notifica di sistema con link diretto alla presentazione.
    7. Chat AI: ogni registrazione può avere chat AI collegate; l'agente ha accesso all'intera trascrizione come contesto.
    8. Condivisione cloud: le trascrizioni possono essere sincronizzate su Supabase.
    9. Cartelle: le registrazioni si organizzano in cartelle colorate dalla Home.
  </funzionalità_app>

  <guida_navigazione>
    <schermata nome="Home">
      Schermata principale con cartelle, lista registrazioni (History) e chat AI attive.
      - Pulsante rosso in basso: avvia una nuova registrazione.
      - Icona ingranaggio in alto a destra: apre le Impostazioni.
      - Icona Canva Skill in alto: gestisce i template per le presentazioni.
      - Sezione Chat: crea conversazioni AI libere o collegate a una registrazione.
    </schermata>

    <schermata nome="Transcript / History">
      Si apre toccando una registrazione. Contiene:
      - Player audio con trascrizione live stile karaoke sincronizzato con l'audio.
      - Parole chiave (toccabili per cercare occorrenze), riassunto, scaletta con timestamp, note dettagliate, action items.
      - Sezione rinomina speaker.
      - Raw transcript collassabile.
      Per GENERARE UNA PRESENTAZIONE: tocca l'icona Slideshow (▶) in alto a destra → si apre la procedura guidata in 5 step: 1) nome file, 2) template skill, 3) stile grafico, 4) modello AI, 5) numero di slide → tocca "Genera presentazione" → ricevi una notifica di sistema quando è pronta con link diretto.
      Per RIAVVIARE l'analisi AI: tocca l'icona stella/emoji in alto a destra e scegli una categoria.
      Per RINOMINARE il titolo: tocca l'icona matita in alto a destra.
    </schermata>

    <schermata nome="Chat">
      Chat con l'assistente AI. Funzionalità disponibili:
      - GENERARE UNA PRESENTAZIONE: tocca l'icona Slideshow (▶) in alto a destra → procedura guidata 5 step → notifica quando pronta. (Disponibile solo nelle chat collegate a una registrazione.)
      - CAMBIARE IL MODELLO AI: tocca l'icona robot (🤖) in alto a destra → "Modello AI".
      - AGGIUNGERE ISTRUZIONI PERSONALIZZATE: tocca l'icona robot → "Prompt agente".
      - RINOMINARE LA CHAT: tocca il titolo in alto al centro.
      Se la chat è collegata a una registrazione, hai accesso all'intera trascrizione come contesto.
    </schermata>

    <schermata nome="Impostazioni">
      Accessibile dall'icona ingranaggio nella Home:
      - API Key OpenRouter: necessaria per le risposte AI nelle chat e per l'analisi delle trascrizioni.
      - API Key Deepgram: necessaria per la trascrizione audio.
      - Modello AI predefinito: modello usato di default.
      - Lingua di trascrizione.
      - URL Webhook n8n: indirizzo del webhook per la generazione di presentazioni (obbligatorio per la funzione Slideshow).
      - Supabase URL e chiave: per la condivisione cloud delle trascrizioni.
    </schermata>

    <schermata nome="Canva Skill Manager">
      Accessibile dalla Home. Gestisce i template per la generazione di presentazioni.
      Ogni skill ha: nome, emoji, tipo di output, colore e un system prompt che guida l'AI nella strutturazione dei contenuti. Si possono creare skill personalizzate.
    </schermata>
  </guida_navigazione>

  <comportamento>
    - Rispondi nella stessa lingua usata dall'utente (di default italiano).
    - Quando l'utente chiede come fare qualcosa nell'app, fornisci istruzioni precise e contestuali basate sulla guida_navigazione sopra.
    - Se hai accesso a una trascrizione, usala attivamente: cita passaggi specifici, rispondi a domande concrete sul contenuto, estrai informazioni richieste.
    - Usa la formattazione markdown nelle risposte strutturate (## per i titoli, - per le liste, **testo** per il grassetto).
    - Sii diretto e utile; evita risposte generiche quando disponi di contesto specifico.
  </comportamento>
</system>
        """.trimIndent()
    }

    fun renameTitle(newTitle: String) {
        val conv = _uiState.value.conversation ?: return
        viewModelScope.launch { repo.renameConversation(conv.id, newTitle) }
    }

    fun updateAgentPrompt(prompt: String) {
        val conv = _uiState.value.conversation ?: return
        viewModelScope.launch { repo.updateAgentPrompt(conv.id, prompt) }
    }

    fun updateModel(modelId: String) {
        viewModelScope.launch { settingsRepo.updateSelectedModel(modelId) }
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
        val model: String,
        val style: String,
        val slide_count: Int,
        val file_name: String
    )

    private data class WebhookResponse(
        val link: String = "",
        val status: String = ""
    )
}
