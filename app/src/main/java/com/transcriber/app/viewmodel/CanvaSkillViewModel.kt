package com.transcriber.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.transcriber.app.data.CanvaSkillEntity
import com.transcriber.app.data.CanvaSkillRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CanvaSkillEditorState(
    val id: Int = 0,
    val name: String = "",
    val emoji: String = "✨",
    val colorHex: String = "#49DD7F",
    val outputType: String = "Slide",
    val agentPrompt: String = "",
    val isDefault: Boolean = false,
    val isNew: Boolean = true
)

class CanvaSkillViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CanvaSkillRepository(application)

    // ── List (CanvaSkillManagerScreen) ────────────────────────────────────────
    val skills: StateFlow<List<CanvaSkillEntity>> = repository.allSkills
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Editor (CanvaSkillEditorScreen) ───────────────────────────────────────
    private val _editor = MutableStateFlow(CanvaSkillEditorState())
    val editor: StateFlow<CanvaSkillEditorState> = _editor.asStateFlow()

    fun initEditor(skillId: Int) {
        val current = _editor.value
        if (skillId == 0 && current.isNew) return
        if (skillId != 0 && current.id == skillId && !current.isNew) return

        if (skillId == 0) {
            _editor.value = CanvaSkillEditorState()
            return
        }
        viewModelScope.launch {
            val skill = repository.allSkills.first().find { it.id == skillId } ?: return@launch
            _editor.value = CanvaSkillEditorState(
                id         = skill.id,
                name       = skill.name,
                emoji      = skill.emoji,
                colorHex   = skill.colorHex,
                outputType = skill.outputType,
                agentPrompt = skill.agentPrompt,
                isDefault  = skill.isDefault,
                isNew      = false
            )
        }
    }

    fun updateName(v: String)       { _editor.value = _editor.value.copy(name = v) }
    fun updateEmoji(v: String)      { _editor.value = _editor.value.copy(emoji = v) }
    fun updateColor(v: String)      { _editor.value = _editor.value.copy(colorHex = v) }
    fun updateOutputType(v: String) { _editor.value = _editor.value.copy(outputType = v) }
    fun updatePrompt(v: String)     { _editor.value = _editor.value.copy(agentPrompt = v) }

    fun save(onDone: () -> Unit) {
        val s = _editor.value
        if (s.name.isBlank()) return
        viewModelScope.launch {
            repository.insert(
                CanvaSkillEntity(
                    id          = if (s.isNew) 0 else s.id,
                    name        = s.name.trim(),
                    emoji       = s.emoji.ifBlank { "✨" },
                    colorHex    = s.colorHex,
                    outputType  = s.outputType,
                    agentPrompt = s.agentPrompt.trim(),
                    isDefault   = s.isDefault
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
                CanvaSkillEntity(
                    id = s.id, name = s.name, emoji = s.emoji,
                    colorHex = s.colorHex, outputType = s.outputType,
                    agentPrompt = s.agentPrompt, isDefault = false
                )
            )
            onDone()
        }
    }
}
