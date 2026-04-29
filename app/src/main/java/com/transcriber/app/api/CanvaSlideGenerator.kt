package com.transcriber.app.api

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CanvaSlideGenerator {

    private val openRouter = OpenRouterClient()
    private val gson = Gson()

    suspend fun generateSlides(
        apiKey: String,
        model: String,
        transcript: String,
        skillPrompt: String,
        meetingTitle: String
    ): Result<List<PptxSlide>> = withContext(Dispatchers.IO) {
        val trimmedTranscript = transcript.take(12_000) // stay within context limits

        val messages = listOf(
            ChatMessage(
                role = "system",
                content = """Sei un esperto di presentazioni. Dato un trascritto di riunione e le istruzioni della skill, genera il contenuto per una presentazione.
Rispondi ESCLUSIVAMENTE con JSON valido in questo formato (nessun testo prima o dopo):
{"slides":[{"title":"...","bullets":["...","...","..."]}]}
Genera 6-10 slide. Ogni slide ha un titolo conciso e 3-5 bullet points. Scrivi in italiano."""
            ),
            ChatMessage(
                role = "user",
                content = """Istruzioni skill: $skillPrompt

Titolo riunione: $meetingTitle

Trascritto:
$trimmedTranscript"""
            )
        )

        val result = openRouter.sendChatRequest(apiKey = apiKey, model = model, messages = messages)
        if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)

        val raw = result.getOrThrow().trim()
        // Strip markdown code fences if the model wraps the JSON
        val json = raw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

        try {
            val parsed = gson.fromJson(json, SlidesJson::class.java)
            val slides = parsed.slides?.mapNotNull { s ->
                val title = s.title?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                PptxSlide(title = title, bullets = s.bullets ?: emptyList())
            } ?: emptyList()
            if (slides.isEmpty()) Result.failure(Exception("Nessuna slide generata"))
            else Result.success(slides)
        } catch (e: JsonSyntaxException) {
            Result.failure(Exception("Risposta JSON non valida: ${e.message}"))
        }
    }

    private data class SlidesJson(val slides: List<SlideJson>? = null)
    private data class SlideJson(val title: String? = null, val bullets: List<String>? = null)
}
