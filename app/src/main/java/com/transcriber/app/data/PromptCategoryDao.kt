package com.transcriber.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PromptCategoryDao {

    /** Emits the full list whenever any row changes. Default categories come first. */
    @Query("SELECT * FROM prompt_categories ORDER BY isDefault DESC, id ASC")
    fun getAllCategories(): Flow<List<PromptCategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: PromptCategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<PromptCategoryEntity>)

    @Update
    suspend fun update(category: PromptCategoryEntity)

    @Delete
    suspend fun delete(category: PromptCategoryEntity)
}
