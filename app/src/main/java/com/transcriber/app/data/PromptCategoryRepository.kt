package com.transcriber.app.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class PromptCategoryRepository(context: Context) {

    private val dao = AppDatabase.getInstance(context).promptCategoryDao()

    /** Reactive stream of all categories; emits on every insert/update/delete. */
    val allCategories: Flow<List<PromptCategoryEntity>> = dao.getAllCategories()

    suspend fun insert(category: PromptCategoryEntity): Long = dao.insert(category)

    suspend fun update(category: PromptCategoryEntity) = dao.update(category)

    /** Silently refuses to delete default categories. */
    suspend fun delete(category: PromptCategoryEntity) {
        if (!category.isDefault) dao.delete(category)
    }
}
