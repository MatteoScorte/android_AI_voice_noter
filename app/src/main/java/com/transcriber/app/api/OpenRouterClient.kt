package com.transcriber.app.api

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenRouterClient {

    companion object {
        private const val BASE_URL = "https://openrouter.ai/api/v1/chat/completions"
        private const val MAX_RETRIES = 3
        private const val INITIAL_BACKOFF_MS = 1000L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun sendChatRequest(
        apiKey: String,
        model: String,
        messages: List<ChatMessage>
    ): Result<String> = withContext(Dispatchers.IO) {
        val request = ChatRequest(model = model, messages = messages)
        val jsonBody = gson.toJson(request)
        val body = jsonBody.toRequestBody("application/json".toMediaType())

        val httpRequest = Request.Builder()
            .url(BASE_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://audio-transcriber.app")
            .addHeader("X-Title", "Voxlog")
            .post(body)
            .build()

        var lastException: Exception? = null
        for (attempt in 0 until MAX_RETRIES) {
            try {
                val response = client.newCall(httpRequest).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                        ?: return@withContext Result.failure(IOException("Empty response body"))
                    val chatResponse = gson.fromJson(responseBody, ChatResponse::class.java)
                    val content = chatResponse.choices?.firstOrNull()?.message?.content
                        ?: return@withContext Result.failure(IOException("No content in response"))
                    return@withContext Result.success(content)
                } else if (response.code in listOf(429, 500, 502, 503)) {
                    val backoff = INITIAL_BACKOFF_MS * (1 shl attempt)
                    delay(backoff)
                    lastException = IOException("HTTP ${response.code}: ${response.body?.string()}")
                } else {
                    return@withContext Result.failure(
                        IOException("HTTP ${response.code}: ${response.body?.string() ?: "Unknown error"}")
                    )
                }
            } catch (e: IOException) {
                lastException = e
                delay(INITIAL_BACKOFF_MS * (1 shl attempt))
            }
        }
        Result.failure(lastException ?: IOException("Max retries exceeded"))
    }
}
