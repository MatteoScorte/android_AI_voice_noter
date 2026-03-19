package com.transcriber.app.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Supabase REST API client — syncs meetings across devices.
 * Uses the anon key for authentication (RLS policy allows all operations).
 */
class SupabaseClient(
    private val supabaseUrl: String,
    private val anonKey: String
) {
    private val gson = Gson()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json".toMediaType()

    // ── Data class that mirrors the Supabase `meetings` table ──
    data class SupaMeeting(
        @SerializedName("id") val id: String,
        @SerializedName("title") val title: String,
        @SerializedName("created_at") val createdAt: Long,
        @SerializedName("duration_ms") val durationMs: Long,
        @SerializedName("raw_transcript") val rawTranscript: String,
        @SerializedName("final_transcript") val finalTranscript: String,
        @SerializedName("status") val status: String,
        @SerializedName("error_message") val errorMessage: String,
        @SerializedName("device_id") val deviceId: String
    )

    /** Convert local Meeting to Supabase row */
    fun Meeting.toSupa(deviceId: String) = SupaMeeting(
        id = id, title = title, createdAt = createdAt,
        durationMs = durationMs, rawTranscript = rawTranscript,
        finalTranscript = finalTranscript, status = status.name,
        errorMessage = errorMessage, deviceId = deviceId
    )

    /** Convert Supabase row to local Meeting */
    fun SupaMeeting.toMeeting() = Meeting(
        id = id, title = title, createdAt = createdAt,
        durationMs = durationMs, rawTranscript = rawTranscript,
        finalTranscript = finalTranscript,
        status = runCatching { MeetingStatus.valueOf(status) }.getOrDefault(MeetingStatus.COMPLETED),
        errorMessage = errorMessage
    )

    private fun baseHeaders(req: Request.Builder): Request.Builder = req
        .header("apikey", anonKey)
        .header("Authorization", "Bearer $anonKey")
        .header("Content-Type", "application/json")
        .header("Prefer", "return=minimal")

    /** UPSERT a single meeting (insert or update by id) */
    suspend fun upsertMeeting(meeting: Meeting, deviceId: String): Result<Unit> {
        return runCatching {
            val body = gson.toJson(meeting.toSupa(deviceId)).toRequestBody(JSON)
            val request = baseHeaders(
                Request.Builder()
                    .url("$supabaseUrl/rest/v1/meetings")
                    .header("Prefer", "resolution=merge-duplicates,return=minimal")
            ).post(body).build()

            withContext(Dispatchers.IO) {
                val response = http.newCall(request).execute()
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    response.close()
                    throw Exception("HTTP ${response.code}: $errBody")
                }
                response.close()
            }
        }
    }

    /** Fetch all meetings from cloud, ordered newest first */
    suspend fun fetchAllMeetings(): Result<List<Meeting>> {
        return runCatching {
            val request = baseHeaders(
                Request.Builder()
                    .url("$supabaseUrl/rest/v1/meetings?select=*&order=created_at.desc")
            ).get().build()

            val (code, json) = withContext(Dispatchers.IO) {
                val response = http.newCall(request).execute()
                val body = response.body?.string() ?: "[]"
                response.code to body
            }
            if (code !in 200..299) {
                throw Exception("HTTP $code: $json")
            }
            val type = object : TypeToken<List<SupaMeeting>>() {}.type
            val rows: List<SupaMeeting> = gson.fromJson(json, type) ?: emptyList()
            rows.map { it.toMeeting() }
        }
    }

    /** Update only title for a meeting */
    suspend fun updateTitle(id: String, newTitle: String): Result<Unit> {
        return runCatching {
            val body = gson.toJson(mapOf("title" to newTitle)).toRequestBody(JSON)
            val request = baseHeaders(
                Request.Builder()
                    .url("$supabaseUrl/rest/v1/meetings?id=eq.$id")
            ).patch(body).build()

            withContext(Dispatchers.IO) {
                val response = http.newCall(request).execute()
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    response.close()
                    throw Exception("HTTP ${response.code}: $errBody")
                }
                response.close()
            }
        }
    }

    /** Delete a meeting by id */
    suspend fun deleteMeeting(id: String): Result<Unit> {
        return runCatching {
            val request = baseHeaders(
                Request.Builder()
                    .url("$supabaseUrl/rest/v1/meetings?id=eq.$id")
            ).delete().build()

            withContext(Dispatchers.IO) {
                val response = http.newCall(request).execute()
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    response.close()
                    throw Exception("HTTP ${response.code}: $errBody")
                }
                response.close()
            }
        }
    }
}
