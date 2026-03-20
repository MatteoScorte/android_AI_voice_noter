package com.transcriber.app.data

import com.google.gson.annotations.SerializedName

enum class MeetingStatus {
    RECORDING, RECORDED, TRANSCRIBING, PROCESSING, COMPLETED, ERROR
}

data class OutlineItem(
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("title") val title: String
)

data class ActionItem(
    @SerializedName("task") val task: String,
    @SerializedName("assignee") val assignee: String = "",
    @SerializedName("deadline") val deadline: String = ""
)

/** Word-level timestamp from Deepgram — stored compact (short keys) to keep the JSON lean. */
data class WordTimestamp(
    @SerializedName("w") val word: String,
    @SerializedName("s") val start: Double,  // seconds from audio start
    @SerializedName("e") val end: Double     // seconds from audio start
)

data class Meeting(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("createdAt") val createdAt: Long,
    @SerializedName("durationMs") val durationMs: Long = 0,
    @SerializedName("audioFilePath") val audioFilePath: String = "",
    @SerializedName("rawTranscript") val rawTranscript: String = "",
    @SerializedName("finalTranscript") val finalTranscript: String = "",
    @SerializedName("language") val language: String = "it",
    @SerializedName("status") val status: MeetingStatus = MeetingStatus.RECORDING,
    @SerializedName("errorMessage") val errorMessage: String = "",
    // Stores user-assigned speaker names: original label -> display name
    @SerializedName("speakerAliases") val speakerAliases: Map<String, String>? = emptyMap(),
    // Structured summary fields (nullable for Gson backward-compatibility with old JSON)
    @SerializedName("keywords") val keywords: List<String>? = null,
    @SerializedName("overview") val overview: String? = null,
    @SerializedName("outline") val outline: List<OutlineItem>? = null,
    @SerializedName("bulletNotes") val bulletNotes: List<String>? = null,
    @SerializedName("actionItems") val actionItems: List<ActionItem>? = null,
    // Nullable for backward-compatibility with meetings recorded before this feature
    @SerializedName("wordTimestamps") val wordTimestamps: List<WordTimestamp>? = null,
    // Whether this meeting has been explicitly shared to Supabase cloud storage
    @SerializedName("isShared") val isShared: Boolean = false
)
