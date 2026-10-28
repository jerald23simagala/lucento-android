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

    @Query("SELECT * FROM budgets WHERE category_id = :categoryId ORDER BY id ASC")
    suspend fun getForCategory(categoryId: Long): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE dirty = 1")
    suspend fun getDirty(): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE client_uuid = :clientUuid LIMIT 1")
    suspend fun findByClientUuid(clientUuid: String): BudgetEntity?

    /**
     * After the server accepted a push at [revision]: always records it as the new base; clears dirty only if the row
     * wasn't edited again meanwhile (that edit stays dirty and goes out next cycle as a causal successor).
     */
    @Query(
        "UPDATE budgets SET base_revision = :revision, synced_at = :syncedAt, " +
            "dirty = CASE WHEN updated_at = :expectedUpdatedAt THEN 0 ELSE dirty END WHERE client_uuid = :clientUuid"
    )
    suspend fun markPushed(clientUuid: String, expectedUpdatedAt: Long, revision: Long, syncedAt: Long): Int

    @Query("SELECT * FROM budgets ORDER BY id ASC")
    suspend fun getAll(): List<BudgetEntity>

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()

    @Query("SELECT * FROM budgets WHERE client_uuid IS NULL")
    suspend fun getWithoutClientUuid(): List<BudgetEntity>

    @Query("UPDATE budgets SET client_uuid = :clientUuid, dirty = 1 WHERE id = :id AND client_uuid IS NULL")
    suspend fun assignClientUuid(id: Long, clientUuid: String)
}
