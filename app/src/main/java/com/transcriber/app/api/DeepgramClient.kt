package com.transcriber.app.api

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

// Deepgram Response Data Classes
data class DeepgramResponse(val results: DgResults?)
data class DgResults(val channels: List<DgChannel>?)
data class DgChannel(val alternatives: List<DgAlternative>?)
data class DgAlternative(
    val transcript: String?,
    val words: List<DgWord>?,
    val paragraphs: DgParagraphs?
)
data class DgParagraphs(val transcript: String?, val paragraphs: List<DgParagraph>?)
data class DgParagraph(
    val sentences: List<DgSentence>?,
    val speaker: Int?,
    val num_words: Int?,
    val start: Double?,
    val end: Double?
)
data class DgSentence(val text: String?, val start: Double?, val end: Double?)
data class DgWord(val word: String, val punctuated_word: String?, val speaker: Int?)

class DeepgramClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Maps a file extension to its MIME type for the Deepgram API.
     * Files from the Audio Inbox can arrive in many formats (WhatsApp uses .ogg,
     * file pickers can return .mp3, .wav, etc.) — sending the wrong Content-Type
     * causes Deepgram to reject or mishandle the request.
     */
    private fun mimeTypeFor(file: File): String = when (file.extension.lowercase()) {
        "m4a"                -> "audio/mp4"
        "mp4"                -> "audio/mp4"
        "mp3"                -> "audio/mpeg"
        "ogg", "oga", "opus" -> "audio/ogg"
        "wav"                -> "audio/wav"
        "flac"               -> "audio/flac"
        "aac"                -> "audio/aac"
        "webm"               -> "audio/webm"
        "3gp"                -> "audio/3gpp"
        else                 -> "audio/mpeg"  // safe fallback — Deepgram auto-detects
    }

    suspend fun transcribeAndDiarize(
        apiKey: String,
        audioFile: File,
        language: String = "it"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val mimeType = mimeTypeFor(audioFile)
            val requestBody = audioFile.asRequestBody(mimeType.toMediaType())

            // Build request URL with query params
            val langParam = if (language == "auto") "detect_language=true" else "language=$language"
            val url = "https://api.deepgram.com/v1/listen" +
                    "?model=nova-2" +
                    "&$langParam" +
                    "&diarize=true" +
                    "&smart_format=true"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Token $apiKey")
                .addHeader("Content-Type", mimeType)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                    ?: return@withContext Result.failure(IOException("Empty response from Deepgram"))

                val dgResponse = gson.fromJson(body, DeepgramResponse::class.java)
                val alternative = dgResponse.results?.channels?.firstOrNull()?.alternatives?.firstOrNull()

                // 1. Try to use paragraphs (recommended for smart_format and diarize)
                val paragraphs = alternative?.paragraphs?.paragraphs
                if (!paragraphs.isNullOrEmpty()) {
                    val compiledTranscript = compileParagraphs(paragraphs)
                    return@withContext Result.success(compiledTranscript)
                }

                // 2. Fallback to words
                val words = alternative?.words
                if (!words.isNullOrEmpty()) {
                    val compiledTranscript = compileDiarizedTranscript(words)
                    return@withContext Result.success(compiledTranscript)
                }

                // 3. Fallback to plain transcript
                val plainText = alternative?.transcript ?: ""
                Result.success(plainText)
            } else {
                Result.failure(IOException("Deepgram API error ${response.code}: ${response.body?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun compileParagraphs(paragraphs: List<DgParagraph>): String {
        val sb = StringBuilder()
        for (paragraph in paragraphs) {
            val speaker = paragraph.speaker ?: 0
            sb.append("Speaker $speaker:\n")

            paragraph.sentences?.forEach { sentence ->
                val text = sentence.text?.trim() ?: ""
                sb.append(text).append(" ")
            }
            sb.append("\n\n")
        }
        return sb.toString().trim()
    }

    private fun compileDiarizedTranscript(words: List<DgWord>): String {
        if (words.isEmpty()) return ""

        val sb = StringBuilder()
        var currentSpeaker = words.first().speaker ?: 0

        sb.append("Speaker $currentSpeaker:\n")

        for (w in words) {
            val wordSpeaker = w.speaker ?: 0
            if (wordSpeaker != currentSpeaker) {
                // Speaker changed, start a new paragraph
                currentSpeaker = wordSpeaker
                sb.append("\n\nSpeaker $currentSpeaker:\n")
            }
            // Use punctuated_word if available, fallback to word
            val textToAppend = w.punctuated_word ?: w.word
            sb.append(textToAppend).append(" ")
        }

        return sb.toString().trim()
    }
}
