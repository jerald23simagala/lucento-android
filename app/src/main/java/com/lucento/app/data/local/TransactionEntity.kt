package com.lucento.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Wallets cannot be deleted while they own transactions (RESTRICT) so history is never silently lost.
 * Deleting a category keeps its transactions and clears the link (SET NULL).
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = WalletEntity::class,
            parentColumns = ["id"],
            childColumns = ["wallet_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["wallet_id"]),
        Index(value = ["category_id"]),
        Index(value = ["occurred_on"]),
        Index(value = ["client_uuid"], unique = true),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [com.lucento.app.domain.TransactionType] name. */
    val type: String,
    /** Always positive; direction comes from [type]. */
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "wallet_id") val walletId: Long,
    @ColumnInfo(name = "category_id") val categoryId: Long?,
    /** Calendar date as epoch day (LocalDate.toEpochDay), independent of time zone. */
    @ColumnInfo(name = "occurred_on") val occurredOn: Long,
    val note: String,
    /** Idempotency key: a second insert with the same key is rejected, so double-submits cannot duplicate. */
    @ColumnInfo(name = "client_uuid") val clientUuid: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
