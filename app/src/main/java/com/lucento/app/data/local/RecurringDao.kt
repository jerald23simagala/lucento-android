package com.lucento.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringDao {

    @Query("SELECT * FROM recurring ORDER BY id ASC")
    fun observeAll(): Flow<List<RecurringEntity>>

    @Query("SELECT * FROM recurring WHERE id = :id")
    suspend fun getById(id: Long): RecurringEntity?

    @Query("SELECT COUNT(*) FROM recurring WHERE wallet_id = :walletId OR to_wallet_id = :walletId")
    suspend fun countForWallet(walletId: Long): Int

    @Insert
    suspend fun insert(schedule: RecurringEntity): Long

    @Update
    suspend fun update(schedule: RecurringEntity)

    @Query("DELETE FROM recurring WHERE id = :id")
    suspend fun deleteById(id: Long)

    // --- cloud sync (see docs/SYNC.md); unused unless the user has opted in ---

    @Query("SELECT * FROM recurring WHERE dirty = 1")
    suspend fun getDirty(): List<RecurringEntity>

    @Query("SELECT * FROM recurring WHERE client_uuid = :clientUuid LIMIT 1")
    suspend fun findByClientUuid(clientUuid: String): RecurringEntity?

    /** As BudgetDao.markPushed: records the accepted revision; clears dirty only if not edited again meanwhile. */
    @Query(
        "UPDATE recurring SET base_revision = :revision, synced_at = :syncedAt, " +
            "dirty = CASE WHEN updated_at = :expectedUpdatedAt THEN 0 ELSE dirty END WHERE client_uuid = :clientUuid"
    )
    suspend fun markPushed(clientUuid: String, expectedUpdatedAt: Long, revision: Long, syncedAt: Long): Int

    @Query("SELECT * FROM recurring ORDER BY id ASC")
    suspend fun getAll(): List<RecurringEntity>

    @Query("DELETE FROM recurring")
    suspend fun deleteAll()

    @Query("SELECT * FROM recurring WHERE client_uuid IS NULL")
    suspend fun getWithoutClientUuid(): List<RecurringEntity>

    @Query("UPDATE recurring SET client_uuid = :clientUuid, dirty = 1 WHERE id = :id AND client_uuid IS NULL")
    suspend fun assignClientUuid(id: Long, clientUuid: String)
}
