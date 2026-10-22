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
}
