package com.transcriber.app.api

import com.transcriber.app.data.ActionItem
import com.transcriber.app.data.OutlineItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import org.json.JSONObject

data class ProcessorResult(
    val mappedSpeakers: Map<String, String>,
    val finalTranscript: String,
    val keywords: List<String> = emptyList(),
    val overview: String = "",
    val outline: List<OutlineItem> = emptyList(),
    val bulletNotes: List<String> = emptyList(),
    val actionItems: List<ActionItem> = emptyList()
)

class TranscriptProcessor(private val openRouterClient: OpenRouterClient = OpenRouterClient()) {

    companion object {
        private val HEADER = """
Sei un assistente esperto nell'analisi di riunioni professionali in italiano.
Ricevi una trascrizione grezza diarizzata e produci un'analisi strutturata completa.

REGOLE GENERALI:
1. CORREZIONE: Correggi errori grammaticali e rimuovi esitazioni.
2. SPEAKER: Identifica i nomi reali degli interlocutori. Usa i nomi mappati invece di "Speaker X".
        """.trimIndent()

        private val DEFAULT_VERBALE_SECTION = """
ISTRUZIONI SPECIFICHE PER IL CAMPO "verbale" (Trascrizione Completa):
Sei un professionista esperto nel prendere appunti e redigere verbali dettagliati. Il tuo compito è trasformare la trascrizione grezza fornita in appunti completi, strutturati e direttamente utilizzabili. Il contesto può variare: lezioni universitarie, riunioni di lavoro, o sessioni di brainstorming.

Regole fondamentali per la generazione:
1. ZERO Meta-narrazione: Non scrivere MAI frasi come "Il relatore ha spiegato", "La riunione si è concentrata su", "Hanno discusso di". Non descrivere l'evento, ma estrai le informazioni. Vai direttamente ai fatti, ai concetti, alle direttive e alle decisioni.
2. Contenuto Informativo Diretto: Se viene spiegato un algoritmo, scrivi come funziona. Se viene analizzato un problema di business, scrivi qual è il problema e le soluzioni proposte. Se vengono assegnati compiti, descrivili oggettivamente.
3. Dettaglio e Precisione: Non riassumere eccessivamente a discapito della completezza. Mantieni le definizioni tecniche, i numeri chiave, i ragionamenti logici, le metriche e gli esempi pratici citati.
4. Interazioni e Q&A: Se ci sono domande, obiezioni o dibattiti tra gli speaker, riporta il nodo della questione e la risposta/conclusione finale a cui si è giunti, senza focalizzarti su chi ha detto cosa (a meno che non sia cruciale per il contesto).

Formato di output per il "verbale":
* Usa rigorosamente il Markdown per strutturare il testo.
* Usa intestazioni (## e ###) per separare i macro-argomenti, le fasi della riunione o i capitoli della lezione.
* Usa elenchi puntati per caratteristiche, regole o concetti correlati per favorire la leggibilità.
* Usa il **grassetto** per evidenziare termini tecnici, decisioni finali o concetti cardine.
        """.trimIndent()

        private val FOOTER = """
OUTPUT JSON OBBLIGATORIO (solo JSON puro, senza markdown ```json):
{
  "speaker_map": {
    "Speaker 0": "Matteo",
    "Speaker 1": "Dott. Rossi"
  },
  "keywords": ["Budget", "Scadenza_Progetto", "Feedback_Cliente", "Q2", "Fornitore"],
  "overview": "Paragrafo di 3-5 righe che descrive in modo conciso l'obiettivo e il risultato dell'incontro. NON citare chi ha detto cosa. Descrivere solo il cuore della conversazione e le conclusioni raggiunte.",
  "outline": [
    {"timestamp": "00:00", "title": "Apertura e presentazioni"},
    {"timestamp": "03:20", "title": "Revisione budget Q1"},
    {"timestamp": "11:45", "title": "Pianificazione attività Q2"},
    {"timestamp": "18:30", "title": "Conclusioni e prossimi passi"}
  ],
  "bullet_notes": [
    "Il team ha deciso di posticipare il lancio al secondo trimestre per garantire maggiore qualità",
    "Il cliente ha espresso soddisfazione per la demo del prototipo",
    "Sono emerse criticità legate ai tempi di consegna del fornitore principale"
  ],
  "action_items": [
    {"task": "Preparare il report finanziario aggiornato", "assignee": "Marco", "deadline": "entro venerdì"},
    {"task": "Contattare il fornitore per confermare le date di consegna", "assignee": "Laura", "deadline": ""},
    {"task": "Inviare il verbale a tutti i partecipanti", "assignee": "", "deadline": "domani"}
  ],
  "verbale": "Inserisci qui gli appunti generati applicando alla lettera le istruzioni specificate sopra ed usando la corretta formattazione Markdown richiesta."
}

ISTRUZIONI DETTAGLIATE AGGIUNTIVE:
- keywords: 5-10 tag tematici chiave (usa underscore al posto degli spazi, senza #)
- overview: paragrafo unico di 3-5 righe, in terza persona, senza citare interventi specifici
- outline: stima i capitoli principali con timestamp approssimativi in formato MM:SS
- bullet_notes: 5-15 punti che riassumono decisioni, opinioni rilevanti e informazioni tecniche; completi e autonomi
- action_items: estrai SOLO i compiti espliciti o chiaramente impliciti; se assignee o deadline manchevoli lascia stringa vuota
        """.trimIndent()

        fun buildSystemPrompt(categoryPrompt: String): String {
            val verbaleSection = if (categoryPrompt.isNotBlank()) categoryPrompt else DEFAULT_VERBALE_SECTION
            return "$HEADER\n\n$verbaleSection\n\n$FOOTER"
        }
    }

    suspend fun processTranscript(
        apiKey: String,
        model: String,
        rawTranscript: String,
        meetingStartTime: Long,
        categorySystemPrompt: String = ""
    ): Result<ProcessorResult> {
        val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.ITALY).format(Date(meetingStartTime))
        val timeStr = SimpleDateFormat("HH:mm", Locale.ITALY).format(Date(meetingStartTime))

        val userMessage = """
Data della riunione: $dateStr
Ora di inizio: $timeStr

Ecco la trascrizione grezza (già diarizzata con Speaker X):

$rawTranscript
        """.trimIndent()

        val messages = listOf(
            ChatMessage(role = "system", content = buildSystemPrompt(categorySystemPrompt)),
            ChatMessage(role = "user", content = userMessage)
        )
        val result = openRouterClient.sendChatRequest(apiKey = apiKey, model = model, messages = messages)

        return result.mapCatching { jsonResponse ->
            val cleanJson = jsonResponse.trim()
                .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val jsonObj = JSONObject(cleanJson)

            val mappedSpeakers = mutableMapOf<String, String>()
            if (jsonObj.has("speaker_map")) {
                val mapObj = jsonObj.getJSONObject("speaker_map")
                mapObj.keys().forEach { key -> mappedSpeakers[key] = mapObj.getString(key) }
            }

            val keywords = mutableListOf<String>()
            if (jsonObj.has("keywords")) {
                val arr = jsonObj.getJSONArray("keywords")
                for (i in 0 until arr.length()) keywords.add(arr.getString(i))
            }

            val overview = jsonObj.optString("overview", "")

            val outline = mutableListOf<OutlineItem>()
            if (jsonObj.has("outline")) {
                val arr = jsonObj.getJSONArray("outline")
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    outline.add(OutlineItem(
                        timestamp = item.optString("timestamp", ""),
                        title = item.optString("title", "")
                    ))
                }
            }

            val bulletNotes = mutableListOf<String>()
            if (jsonObj.has("bullet_notes")) {
                val arr = jsonObj.getJSONArray("bullet_notes")
                for (i in 0 until arr.length()) bulletNotes.add(arr.getString(i))
            }

            val actionItems = mutableListOf<ActionItem>()
            if (jsonObj.has("action_items")) {
                val arr = jsonObj.getJSONArray("action_items")
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    actionItems.add(ActionItem(
                        task = item.optString("task", ""),
                        assignee = item.optString("assignee", ""),
                        deadline = item.optString("deadline", "")
                    ))
                }
            }

            val verbale = jsonObj.optString("verbale", "")

            ProcessorResult(
                mappedSpeakers = mappedSpeakers,
                finalTranscript = verbale,
                keywords = keywords,
                overview = overview,
                outline = outline,
                bulletNotes = bulletNotes,
                actionItems = actionItems
            )
        }
    }
}
