package com.transcriber.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

class FolderRepository private constructor(private val context: Context) {

    companion object {
        @Volatile private var instance: FolderRepository? = null
        operator fun invoke(context: Context): FolderRepository =
            instance ?: synchronized(this) {
                instance ?: FolderRepository(context.applicationContext).also { instance = it }
            }
    }

    private val gson = Gson()
    private val file: File get() = File(context.filesDir, "folders.json")

    private val _folders = MutableStateFlow<List<MeetingFolder>>(emptyList())
    val folders: StateFlow<List<MeetingFolder>> = _folders.asStateFlow()

    init { load() }

    private fun load() {
        try {
            if (file.exists()) {
                val type = object : TypeToken<List<MeetingFolder>>() {}.type
                _folders.value = gson.fromJson(file.readText(), type) ?: emptyList()
            }
        } catch (_: Exception) { _folders.value = emptyList() }
    }

    private suspend fun save() = withContext(Dispatchers.IO) {
        file.writeText(gson.toJson(_folders.value))
    }

    suspend fun addFolder(folder: MeetingFolder) {
        _folders.value = _folders.value + folder
        save()
    }

    suspend fun updateFolder(folder: MeetingFolder) {
        _folders.value = _folders.value.map { if (it.id == folder.id) folder else it }
        save()
    }

    suspend fun deleteFolder(folderId: String) {
        _folders.value = _folders.value.filter { it.id != folderId }
        save()
    }
}
