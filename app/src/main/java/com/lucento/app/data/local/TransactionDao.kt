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
    @ColumnInfo(name = "transfer_in_minor") val transferInMinor: Long = 0,
    @ColumnInfo(name = "transfer_out_minor") val transferOutMinor: Long = 0,
    @ColumnInfo(name = "adjustment_in_minor") val adjustmentInMinor: Long = 0,
    @ColumnInfo(name = "adjustment_out_minor") val adjustmentOutMinor: Long = 0,
)

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY occurred_on DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    // P2-8 (N-12): scoped reads for screens that don't need the whole table.
    @Query("SELECT COUNT(*) FROM transactions")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM transactions WHERE type IN ('INCOME', 'EXPENSE')")
    fun observeCashFlowCount(): Flow<Int>

    /** One transaction and, for a transfer, its other leg. */
    @Query(
        "SELECT * FROM transactions WHERE id = :id OR (transfer_group IS NOT NULL AND " +
            "transfer_group = (SELECT transfer_group FROM transactions WHERE id = :id))"
    )
    fun observeWithTransferLegs(id: Long): Flow<List<TransactionEntity>>

    @Query(
        "SELECT wallet_id AS wallet_id, " +
            "COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount_minor ELSE 0 END), 0) AS income_minor, " +
            "COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount_minor ELSE 0 END), 0) AS expense_minor, " +
            "COALESCE(SUM(CASE WHEN type = 'TRANSFER_IN' THEN amount_minor ELSE 0 END), 0) AS transfer_in_minor, " +
            "COALESCE(SUM(CASE WHEN type = 'TRANSFER_OUT' THEN amount_minor ELSE 0 END), 0) AS transfer_out_minor, " +
            "COALESCE(SUM(CASE WHEN type = 'ADJUSTMENT_IN' THEN amount_minor ELSE 0 END), 0) AS adjustment_in_minor, " +
            "COALESCE(SUM(CASE WHEN type = 'ADJUSTMENT_OUT' THEN amount_minor ELSE 0 END), 0) AS adjustment_out_minor " +
            "FROM transactions GROUP BY wallet_id"
    )
    fun observeWalletFlows(): Flow<List<WalletFlowRow>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE client_uuid = :clientUuid LIMIT 1")
    suspend fun findByClientUuid(clientUuid: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE transfer_group = :group")
    suspend fun getByTransferGroup(group: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE wallet_id = :walletId")
    suspend fun getForWallet(walletId: Long): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions WHERE wallet_id = :walletId")
    suspend fun countForWallet(walletId: Long): Int

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transactions WHERE transfer_group = :group")
    suspend fun deleteByTransferGroup(group: String)

    // --- cloud sync (see docs/SYNC.md); unused unless the user has opted in ---

    @Query("SELECT * FROM transactions WHERE dirty = 1")
    suspend fun getDirty(): List<TransactionEntity>

    /**
     * After the server accepted a push at [revision]: always records it as the new base; clears dirty only if the row
     * wasn't edited again meanwhile (that edit stays dirty and goes out next cycle as a causal successor).
     */
    @Query(
        "UPDATE transactions SET base_revision = :revision, synced_at = :syncedAt, " +
            "dirty = CASE WHEN updated_at = :expectedUpdatedAt THEN 0 ELSE dirty END WHERE client_uuid = :clientUuid"
    )
    suspend fun markPushed(clientUuid: String, expectedUpdatedAt: Long, revision: Long, syncedAt: Long): Int

    /**
     * Lucento Shared: the server's attribution for a row that is otherwise unchanged. Leaves `updated_at` alone, so the
     * dirty trigger doesn't fire and nothing is uploaded again.
     */
    @Query("UPDATE transactions SET created_by = :createdBy, updated_by = :updatedBy WHERE id = :id")
    suspend fun updateAttribution(id: Long, createdBy: String?, updatedBy: String?)

    @Query("SELECT * FROM transactions ORDER BY occurred_on ASC, id ASC")
    suspend fun getAll(): List<TransactionEntity>

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}
