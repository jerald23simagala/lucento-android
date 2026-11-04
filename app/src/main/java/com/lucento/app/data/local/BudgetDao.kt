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

    /** Rows with the same scope: category, currency, override month and recurring start (nulls compared as equal). */
    @Query(
        "SELECT COUNT(*) FROM budgets WHERE currency_code = :currencyCode AND id != :excludeId " +
            "AND ((:categoryId IS NULL AND category_id IS NULL) OR category_id = :categoryId) " +
            "AND month IS :month AND effective_from IS :effectiveFrom"
    )
    suspend fun countMatching(categoryId: Long?, currencyCode: String, excludeId: Long, month: String? = null, effectiveFrom: String? = null): Int

    /** The row with exactly this scope (budget lifecycle): an override when [month] is set, else a recurring row. */
    @Query(
        "SELECT * FROM budgets WHERE category_id = :categoryId AND currency_code = :currencyCode " +
            "AND month IS :month AND effective_from IS :effectiveFrom ORDER BY id ASC LIMIT 1"
    )
    suspend fun findScope(categoryId: Long, currencyCode: String, month: String?, effectiveFrom: String?): BudgetEntity?

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
