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

    @Query("SELECT COUNT(*) FROM recurring WHERE wallet_id = :walletId")
    suspend fun countForWallet(walletId: Long): Int

    @Insert
    suspend fun insert(schedule: RecurringEntity): Long

    @Update
    suspend fun update(schedule: RecurringEntity)

    @Query("DELETE FROM recurring WHERE id = :id")
    suspend fun deleteById(id: Long)
}
