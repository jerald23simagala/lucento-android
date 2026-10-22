package com.lucento.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "wallets",
    indices = [Index(value = ["archived", "sort_order"])],
)
data class WalletEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** [com.lucento.app.domain.WalletType] name. */
    val type: String,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    /** Opening balance in minor units. Transactions will adjust the derived balance in a later schema. */
    @ColumnInfo(name = "opening_balance_minor") val openingBalanceMinor: Long,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    val archived: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
