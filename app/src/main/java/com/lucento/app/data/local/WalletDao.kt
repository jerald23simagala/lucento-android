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
}
