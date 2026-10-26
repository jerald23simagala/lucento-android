package com.lucento.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A repeating plan. Wallets with schedules cannot be deleted (RESTRICT); the repository explains why.
 * Occurrence dates are derived from [startOn] + [frequency] × [intervalCount] × index.
 */
@Entity(
    tableName = "recurring",
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
    indices = [Index(value = ["wallet_id"]), Index(value = ["category_id"])],
)
data class RecurringEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** EXPENSE or INCOME. */
    val type: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "wallet_id") val walletId: Long,
    @ColumnInfo(name = "category_id") val categoryId: Long?,
    /** [com.lucento.app.domain.Frequency] name. */
    val frequency: String,
    @ColumnInfo(name = "interval_count") val intervalCount: Int,
    @ColumnInfo(name = "start_on") val startOn: Long,
    @ColumnInfo(name = "end_on") val endOn: Long?,
    @ColumnInfo(name = "next_index") val nextIndex: Int,
    val paused: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** v8: the card or loan a debt payment goes to (null for bills and income). Validated by the repository. */
    @ColumnInfo(name = "to_wallet_id") val toWalletId: Long? = null,
    /** v8: the interest part of a debt payment; [amountMinor] is then the part against the debt. */
    @ColumnInfo(name = "interest_minor", defaultValue = "0") val interestMinor: Long = 0,
)
