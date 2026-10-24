package com.lucento.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    @Query("SELECT * FROM budgets ORDER BY category_id IS NOT NULL, id ASC")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getById(id: Long): BudgetEntity?

    @Query(
        "SELECT COUNT(*) FROM budgets WHERE currency_code = :currencyCode AND id != :excludeId " +
            "AND ((:categoryId IS NULL AND category_id IS NULL) OR category_id = :categoryId)"
    )
    suspend fun countMatching(categoryId: Long?, currencyCode: String, excludeId: Long): Int

    @Insert
    suspend fun insert(budget: BudgetEntity): Long

    @Update
    suspend fun update(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM budgets WHERE category_id = :categoryId AND currency_code = :currencyCode ORDER BY id ASC LIMIT 1")
    suspend fun findAllocation(categoryId: Long, currencyCode: String): BudgetEntity?

    /** Removes pre-0.5 "budget for everything" rows (category_id NULL). Transactions are never touched. */
    @Query("DELETE FROM budgets WHERE category_id IS NULL")
    suspend fun deleteLegacyOverall(): Int

    // --- cloud sync (see docs/SYNC.md); unused unless the user has opted in ---

    @Query("SELECT * FROM budgets WHERE dirty = 1")
    suspend fun getDirty(): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE client_uuid = :clientUuid LIMIT 1")
    suspend fun findByClientUuid(clientUuid: String): BudgetEntity?

    @Query("UPDATE budgets SET dirty = 0, synced_at = :syncedAt WHERE client_uuid = :clientUuid AND updated_at = :expectedUpdatedAt")
    suspend fun clearDirtyIfUnchanged(clientUuid: String, expectedUpdatedAt: Long, syncedAt: Long): Int
}
