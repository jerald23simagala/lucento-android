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

    /**
     * After the server accepted a push at [revision]: always records it as the new base; clears dirty only if the row
     * wasn't edited again meanwhile (that edit stays dirty and goes out next cycle as a causal successor).
     */
    @Query(
        "UPDATE categories SET base_revision = :revision, synced_at = :syncedAt, " +
            "dirty = CASE WHEN updated_at = :expectedUpdatedAt THEN 0 ELSE dirty END WHERE client_uuid = :clientUuid"
    )
    suspend fun markPushed(clientUuid: String, expectedUpdatedAt: Long, revision: Long, syncedAt: Long): Int

    /** Fresh-install seed categories (CategorySeed must stay valid at every schema version, so it can't set one). */
    /** Re-points a merged category's transactions to its keeper ([com.lucento.app.domain.CategoryDedup]); they sync as edits. */
    @Query("UPDATE transactions SET category_id = :to, updated_at = :now WHERE category_id = :from")
    suspend fun moveTransactions(from: Long, to: Long, now: Long)

    @Query("UPDATE recurring SET category_id = :to, updated_at = :now WHERE category_id = :from")
    suspend fun moveRecurring(from: Long, to: Long, now: Long)

    @Query("SELECT * FROM categories WHERE client_uuid IS NULL")
    suspend fun getWithoutClientUuid(): List<CategoryEntity>

    /** Leaves updated_at alone, so the dirty trigger does not fire; the row's sync state is unchanged. */
    @Query("UPDATE categories SET client_uuid = :clientUuid WHERE id = :id AND client_uuid IS NULL")
    suspend fun assignClientUuid(id: Long, clientUuid: String)
}
