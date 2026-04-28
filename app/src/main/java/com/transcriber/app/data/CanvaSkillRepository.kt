package com.transcriber.app.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class CanvaSkillRepository(context: Context) {

    private val dao = AppDatabase.getInstance(context).canvaSkillDao()

    val allSkills: Flow<List<CanvaSkillEntity>> = dao.getAllSkills()

    suspend fun insert(skill: CanvaSkillEntity): Long = dao.insert(skill)

    suspend fun update(skill: CanvaSkillEntity) = dao.update(skill)

    suspend fun delete(skill: CanvaSkillEntity) {
        if (!skill.isDefault) dao.delete(skill)
    }
}
