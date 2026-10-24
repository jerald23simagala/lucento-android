package com.lucento.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY kind ASC, sort_order ASC, id ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories ORDER BY kind ASC, sort_order ASC, id ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Query(
        "SELECT COUNT(*) FROM categories WHERE archived = 0 AND kind = :kind " +
            "AND name = :name COLLATE NOCASE AND id != :excludeId"
    )
    suspend fun countActiveWithName(kind: String, name: String, excludeId: Long): Int

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM categories WHERE kind = :kind")
    suspend fun maxSortOrder(kind: String): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE category_id = :id")
    suspend fun transactionCount(id: Long): Int

    /** Budgets linked to the category. The budgets FK cascades, so these must be checked before any delete. */
    @Query("SELECT COUNT(*) FROM budgets WHERE category_id = :id")
    suspend fun budgetCount(id: Long): Int

    /** Recurring schedules linked to the category (their FK would silently clear the link on delete). */
    @Query("SELECT COUNT(*) FROM recurring WHERE category_id = :id")
    suspend fun recurringCount(id: Long): Int

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long)

    // --- cloud sync (see docs/SYNC.md); unused unless the user has opted in ---

    @Query("SELECT * FROM categories WHERE dirty = 1")
    suspend fun getDirty(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE client_uuid = :clientUuid LIMIT 1")
    suspend fun findByClientUuid(clientUuid: String): CategoryEntity?

    @Query("UPDATE categories SET dirty = 0, synced_at = :syncedAt WHERE client_uuid = :clientUuid AND updated_at = :expectedUpdatedAt")
    suspend fun clearDirtyIfUnchanged(clientUuid: String, expectedUpdatedAt: Long, syncedAt: Long): Int
}
