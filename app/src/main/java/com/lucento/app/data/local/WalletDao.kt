package com.lucento.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletDao {

    @Query("SELECT * FROM wallets WHERE archived = 0 ORDER BY sort_order ASC, created_at ASC")
    fun observeActive(): Flow<List<WalletEntity>>

    @Query("SELECT * FROM wallets WHERE id = :id")
    suspend fun getById(id: Long): WalletEntity?

    @Query(
        "SELECT COUNT(*) FROM wallets WHERE archived = 0 AND name = :name COLLATE NOCASE AND id != :excludeId"
    )
    suspend fun countActiveWithName(name: String, excludeId: Long): Int

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM wallets")
    suspend fun maxSortOrder(): Int

    @Insert
    suspend fun insert(wallet: WalletEntity): Long

    @Update
    suspend fun update(wallet: WalletEntity)

    @Query("DELETE FROM wallets WHERE id = :id")
    suspend fun deleteById(id: Long)

    // --- cloud sync (see docs/SYNC.md); unused unless the user has opted in ---

    @Query("SELECT * FROM wallets WHERE dirty = 1")
    suspend fun getDirty(): List<WalletEntity>

    @Query("SELECT * FROM wallets WHERE client_uuid = :clientUuid LIMIT 1")
    suspend fun findByClientUuid(clientUuid: String): WalletEntity?

    /** No-ops (0 rows affected) if the row changed again after it was read for push; it stays dirty and is retried. */
    @Query("UPDATE wallets SET dirty = 0, synced_at = :syncedAt WHERE client_uuid = :clientUuid AND updated_at = :expectedUpdatedAt")
    suspend fun clearDirtyIfUnchanged(clientUuid: String, expectedUpdatedAt: Long, syncedAt: Long): Int
}
