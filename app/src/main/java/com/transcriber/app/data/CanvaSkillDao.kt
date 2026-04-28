package com.transcriber.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CanvaSkillDao {

    @Query("SELECT * FROM canva_skills ORDER BY isDefault DESC, id ASC")
    fun getAllSkills(): Flow<List<CanvaSkillEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(skill: CanvaSkillEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(skills: List<CanvaSkillEntity>)

    @Update
    suspend fun update(skill: CanvaSkillEntity)

    @Delete
    suspend fun delete(skill: CanvaSkillEntity)
}
