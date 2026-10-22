package com.lucento.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Per-wallet sums computed in SQL. SQLite's SUM raises on integer overflow instead of wrapping. */
data class WalletFlowRow(
    @ColumnInfo(name = "wallet_id") val walletId: Long,
    @ColumnInfo(name = "income_minor") val incomeMinor: Long,
    @ColumnInfo(name = "expense_minor") val expenseMinor: Long,
)

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY occurred_on DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query(
        "SELECT wallet_id AS wallet_id, " +
            "COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount_minor ELSE 0 END), 0) AS income_minor, " +
            "COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount_minor ELSE 0 END), 0) AS expense_minor " +
            "FROM transactions GROUP BY wallet_id"
    )
    fun observeWalletFlows(): Flow<List<WalletFlowRow>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE client_uuid = :clientUuid LIMIT 1")
    suspend fun findByClientUuid(clientUuid: String): TransactionEntity?

    @Query("SELECT COUNT(*) FROM transactions WHERE wallet_id = :walletId")
    suspend fun countForWallet(walletId: Long): Int

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
