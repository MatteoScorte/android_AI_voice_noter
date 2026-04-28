package com.transcriber.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.transcriber.app.api.AVAILABLE_MODELS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        private val KEY_OPENROUTER_API_KEY = stringPreferencesKey("openrouter_api_key")
        private val KEY_WHISPER_API_KEY = stringPreferencesKey("whisper_api_key")
        private val KEY_SELECTED_MODEL = stringPreferencesKey("selected_model")
        private val KEY_WHISPER_ENDPOINT = stringPreferencesKey("whisper_endpoint")
        private val KEY_SUPABASE_URL = stringPreferencesKey("supabase_url")
        private val KEY_SUPABASE_ANON_KEY = stringPreferencesKey("supabase_anon_key")
        private val KEY_SUPABASE_SYNC_ENABLED = booleanPreferencesKey("supabase_sync_enabled")
        private val KEY_DEEPGRAM_API_KEY = stringPreferencesKey("deepgram_api_key")
        private val KEY_SELECTED_LANGUAGE = stringPreferencesKey("selected_language")

        // Pre-filled defaults for quick setup
        const val DEFAULT_SUPABASE_URL = "https://qsqmpqqqdkbeakgintvi.supabase.co"
        const val DEFAULT_WHISPER_ENDPOINT = "https://api.openai.com/v1/audio/transcriptions"
    }

    val openRouterApiKey: Flow<String> = context.dataStore.data.map {
        it[KEY_OPENROUTER_API_KEY] ?: ""
    }
    val whisperApiKey: Flow<String> = context.dataStore.data.map {
        it[KEY_WHISPER_API_KEY] ?: ""
    }
    val selectedModel: Flow<String> = context.dataStore.data.map {
        it[KEY_SELECTED_MODEL] ?: AVAILABLE_MODELS.first().id
    }
    val whisperEndpoint: Flow<String> = context.dataStore.data.map {
        it[KEY_WHISPER_ENDPOINT] ?: DEFAULT_WHISPER_ENDPOINT
    }
    val supabaseUrl: Flow<String> = context.dataStore.data.map {
        it[KEY_SUPABASE_URL] ?: DEFAULT_SUPABASE_URL
    }
    val supabaseAnonKey: Flow<String> = context.dataStore.data.map {
        it[KEY_SUPABASE_ANON_KEY] ?: ""
    }
    val supabaseSyncEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_SUPABASE_SYNC_ENABLED] ?: false
    }
    val deepgramApiKey: Flow<String> = context.dataStore.data.map {
        it[KEY_DEEPGRAM_API_KEY] ?: ""
    }
    val selectedLanguage: Flow<String> = context.dataStore.data.map {
        it[KEY_SELECTED_LANGUAGE] ?: "it"
    }

    suspend fun updateOpenRouterApiKey(key: String) {
        context.dataStore.edit { it[KEY_OPENROUTER_API_KEY] = key }
    }
    suspend fun updateWhisperApiKey(key: String) {
        context.dataStore.edit { it[KEY_WHISPER_API_KEY] = key }
    }
    suspend fun updateSelectedModel(model: String) {
        context.dataStore.edit { it[KEY_SELECTED_MODEL] = model }
    }
    suspend fun updateWhisperEndpoint(endpoint: String) {
        context.dataStore.edit { it[KEY_WHISPER_ENDPOINT] = endpoint }
    }
    suspend fun updateSupabaseUrl(url: String) {
        context.dataStore.edit { it[KEY_SUPABASE_URL] = url }
    }
    suspend fun updateSupabaseAnonKey(key: String) {
        context.dataStore.edit { it[KEY_SUPABASE_ANON_KEY] = key }
    }
    suspend fun updateSupabaseSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SUPABASE_SYNC_ENABLED] = enabled }
    }
    suspend fun updateDeepgramApiKey(key: String) {
        context.dataStore.edit { it[KEY_DEEPGRAM_API_KEY] = key }
    }
    suspend fun updateSelectedLanguage(language: String) {
        context.dataStore.edit { it[KEY_SELECTED_LANGUAGE] = language }
    }
}
