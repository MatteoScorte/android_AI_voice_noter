package com.transcriber.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "canva_skills")
data class CanvaSkillEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val emoji: String,
    val colorHex: String,
    /** Short tag shown as a chip: "Slide", "Locandina", "Infografica", "Social", "Altro" */
    val outputType: String,
    val agentPrompt: String,
    val isDefault: Boolean = false
)
