package com.transcriber.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prompt_categories")
data class PromptCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val emoji: String,
    val colorHex: String,
    val systemPrompt: String,
    /** Default categories cannot be deleted from the UI. */
    val isDefault: Boolean = false
)
