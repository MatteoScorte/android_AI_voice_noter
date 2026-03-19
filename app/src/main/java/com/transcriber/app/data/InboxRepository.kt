package com.transcriber.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
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
import java.util.UUID

class InboxRepository private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: InboxRepository? = null

        operator fun invoke(context: Context): InboxRepository {
            return instance ?: synchronized(this) {
                instance ?: InboxRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private val gson = Gson()
    private val inboxFile: File get() = File(context.filesDir, "inbox.json")
    private val _items = MutableStateFlow<List<InboxItem>>(emptyList())
    val items: StateFlow<List<InboxItem>> = _items.asStateFlow()

    private val mutex = Mutex()

    init { loadItems() }

    fun getInboxDir(): File {
        val dir = File(context.filesDir, "inbox")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun loadItems() {
        try {
            if (inboxFile.exists()) {
                val type = object : TypeToken<List<InboxItem>>() {}.type
                _items.value = gson.fromJson(inboxFile.readText(), type) ?: emptyList()
            }
        } catch (_: Exception) { _items.value = emptyList() }
    }

    private suspend fun saveItems() = withContext(Dispatchers.IO) {
        val tmpFile = File(context.filesDir, "inbox.json.tmp")
        tmpFile.writeText(gson.toJson(_items.value))
        if (!tmpFile.renameTo(inboxFile)) {
            inboxFile.writeText(tmpFile.readText())
            tmpFile.delete()
        }
    }

    suspend fun addItem(item: InboxItem) = mutex.withLock {
        _items.value = listOf(item) + _items.value
        saveItems()
    }

    /**
     * Removes an item from the inbox.
     * @param deleteFile if true, also deletes the local file (use false when the
     *   file is being transferred to a Meeting which will then own it).
     */
    suspend fun removeItem(id: String, deleteFile: Boolean = true) = mutex.withLock {
        if (deleteFile) {
            _items.value.find { it.id == id }?.let { File(it.localPath).delete() }
        }
        _items.value = _items.value.filter { it.id != id }
        saveItems()
    }

    /**
     * Copies the content pointed to by [uri] into the app's private inbox directory,
     * then creates and persists an InboxItem for it.
     * Safe to call from any coroutine — switches to IO internally.
     */
    suspend fun importFromUri(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val displayName = resolveDisplayName(context, uri)
                ?: "audio_${System.currentTimeMillis()}.m4a"
            val itemId = UUID.randomUUID().toString()
            val destFile = File(getInboxDir(), "${itemId}_${sanitizeFileName(displayName)}")

            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext false

            addItem(
                InboxItem(
                    id = itemId,
                    displayName = displayName,
                    localPath = destFile.absolutePath,
                    addedAt = System.currentTimeMillis(),
                    sizeBytes = destFile.length()
                )
            )
            true
        } catch (e: Exception) {
            Log.e("InboxRepository", "Failed to import URI", e)
            false
        }
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && col >= 0) return cursor.getString(col)
        }
        return uri.path?.substringAfterLast('/')
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9._\\-]"), "_").take(80)
}
