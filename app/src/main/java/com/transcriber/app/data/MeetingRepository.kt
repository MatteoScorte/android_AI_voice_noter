package com.transcriber.app.data

import android.content.Context
import android.provider.Settings
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class MeetingRepository private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: MeetingRepository? = null

        operator fun invoke(context: Context): MeetingRepository {
            return instance ?: synchronized(this) {
                instance ?: MeetingRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private val gson = Gson()
    private val meetingsFile: File get() = File(context.filesDir, "meetings.json")
    private val _meetings = MutableStateFlow<List<Meeting>>(emptyList())
    val meetings: StateFlow<List<Meeting>> = _meetings.asStateFlow()

    // Serialises all read-modify-write operations to prevent race conditions between
    // viewModelScope (HomeViewModel) and GlobalScope (TranscriptViewModel).
    private val mutex = Mutex()

    val deviceId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown"
    }

    init { loadMeetings() }

    private fun loadMeetings() {
        try {
            // Try primary file first; fall back to the temp file if primary is missing/corrupt
            val source = when {
                meetingsFile.exists() -> meetingsFile
                File(context.filesDir, "meetings.json.tmp").exists() ->
                    File(context.filesDir, "meetings.json.tmp")
                else -> null
            }
            source?.let {
                val type = object : TypeToken<List<Meeting>>() {}.type
                _meetings.value = gson.fromJson(it.readText(), type) ?: emptyList()
            }
        } catch (_: Exception) { _meetings.value = emptyList() }
    }

    /**
     * Atomic write: serialise to a temp file then rename.
     * rename() on the same filesystem is a POSIX atomic operation — a crash mid-write
     * leaves the original file intact rather than producing a corrupt file.
     */
    private suspend fun saveMeetings() = withContext(Dispatchers.IO) {
        val tmpFile = File(context.filesDir, "meetings.json.tmp")
        tmpFile.writeText(gson.toJson(_meetings.value))
        if (!tmpFile.renameTo(meetingsFile)) {
            // Fallback on the rare case rename fails (different mount points)
            meetingsFile.writeText(tmpFile.readText())
            tmpFile.delete()
        }
    }

    suspend fun addMeeting(meeting: Meeting) = mutex.withLock {
        _meetings.value = listOf(meeting) + _meetings.value
        saveMeetings()
    }

    suspend fun updateMeeting(meeting: Meeting) = mutex.withLock {
        _meetings.value = _meetings.value.map { if (it.id == meeting.id) meeting else it }
        saveMeetings()
    }

    suspend fun deleteMeeting(meetingId: String) = mutex.withLock {
        _meetings.value.find { it.id == meetingId }?.let {
            if (it.audioFilePath.isNotEmpty()) File(it.audioFilePath).delete()
        }
        _meetings.value = _meetings.value.filter { it.id != meetingId }
        saveMeetings()
    }

    fun getMeeting(meetingId: String): Meeting? = _meetings.value.find { it.id == meetingId }

    fun getAudioDir(): File {
        val dir = File(context.filesDir, "recordings")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun mergeRemoteMeetings(remote: List<Meeting>) = mutex.withLock {
        val localMap = _meetings.value.associateBy { it.id }.toMutableMap()

        for (remoteMeeting in remote) {
            val local = localMap[remoteMeeting.id]
            if (local == null) {
                localMap[remoteMeeting.id] = remoteMeeting.copy(audioFilePath = "")
            } else if (remoteMeeting.status == MeetingStatus.COMPLETED && local.status != MeetingStatus.COMPLETED) {
                localMap[remoteMeeting.id] = remoteMeeting.copy(audioFilePath = local.audioFilePath)
            }
        }

        _meetings.value = localMap.values.sortedByDescending { it.createdAt }
        saveMeetings()
    }
}
