package com.transcriber.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.transcriber.app.data.PromptCategoryEntity
import com.transcriber.app.data.PromptCategoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoryEditorState(
    val id: Int = 0,
    val name: String = "",
    val emoji: String = "📝",
    val colorHex: String = "#49DD7F",
    val systemPrompt: String = "",
    val isDefault: Boolean = false,
    val isNew: Boolean = true
)

class CategoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PromptCategoryRepository(application)

    // ── List (CategoryManagerScreen) ─────────────────────────────────────────
    val categories: StateFlow<List<PromptCategoryEntity>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Editor (CategoryEditorScreen) ─────────────────────────────────────────
    private val _editor = MutableStateFlow(CategoryEditorState())
    val editor: StateFlow<CategoryEditorState> = _editor.asStateFlow()

    /**
     * Called from LaunchedEffect in the editor screen.
     * categoryId == 0 → new category; otherwise loads the existing one.
     * Guards against re-loading on rotation if the correct category is already in state.
     */
    fun initEditor(categoryId: Int) {
        val current = _editor.value
        // Skip reload if we already have the right data (e.g. screen rotation)
        if (categoryId == 0 && current.isNew) return
        if (categoryId != 0 && current.id == categoryId && !current.isNew) return

        if (categoryId == 0) {
            _editor.value = CategoryEditorState()
            return
        }
        viewModelScope.launch {
            val cat = repository.allCategories.first().find { it.id == categoryId } ?: return@launch
            _editor.value = CategoryEditorState(
                id = cat.id,
                name = cat.name,
                emoji = cat.emoji,
                colorHex = cat.colorHex,
                systemPrompt = cat.systemPrompt,
                isDefault = cat.isDefault,
                isNew = false
            )
        }
    }

    fun updateName(v: String)   { _editor.value = _editor.value.copy(name = v) }
    fun updateEmoji(v: String)  { _editor.value = _editor.value.copy(emoji = v) }
    fun updateColor(v: String)  { _editor.value = _editor.value.copy(colorHex = v) }
    fun updatePrompt(v: String) { _editor.value = _editor.value.copy(systemPrompt = v) }

    fun save(onDone: () -> Unit) {
        val s = _editor.value
        if (s.name.isBlank()) return
        viewModelScope.launch {
            repository.insert(
                PromptCategoryEntity(
                    id            = if (s.isNew) 0 else s.id,
                    name          = s.name.trim(),
                    emoji         = s.emoji.ifBlank { "📝" },
                    colorHex      = s.colorHex,
                    systemPrompt  = s.systemPrompt.trim(),
                    isDefault     = s.isDefault
                )
            )
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val s = _editor.value
        if (s.isDefault || s.isNew) return
        viewModelScope.launch {
            repository.delete(
                PromptCategoryEntity(
                    id = s.id, name = s.name, emoji = s.emoji,
                    colorHex = s.colorHex, systemPrompt = s.systemPrompt, isDefault = false
                )
            )
            onDone()
        }
    }
}
