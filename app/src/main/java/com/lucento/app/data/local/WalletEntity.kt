package com.lucento.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lucento.app.domain.WalletType

@Entity(
    tableName = "wallets",
    indices = [
        Index(value = ["archived", "sort_order"]),
        Index(value = ["client_uuid"], unique = true),
    ],
)
data class WalletEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** [com.lucento.app.domain.WalletType] name. */
    val type: String,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    /** Opening balance in minor units. The current balance is derived: this plus recorded transactions. */
    @ColumnInfo(name = "opening_balance_minor") val openingBalanceMinor: Long,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    val archived: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /**
     * Cloud sync bookkeeping, added in database v6 (see MIGRATION_5_6 and docs/SYNC.md). Nothing here is
     * read unless the user has opted into cloud sync.
     */
    @ColumnInfo(name = "client_uuid") val clientUuid: String? = null,
    /** True until this row's current [updatedAt] has been confirmed pushed. New rows start dirty. */
    @ColumnInfo(name = "dirty", defaultValue = "1") val dirty: Boolean = true,
    @ColumnInfo(name = "synced_at") val syncedAt: Long? = null,
    /** Server `revision` this row was last synced at; a push based on it is a causal successor (v7, docs/SYNC.md). */
    @ColumnInfo(name = "base_revision") val baseRevision: Long? = null,
)

/** Money can be posted to this wallet: it is active and not a manually valued asset (see WalletType.isValuedManually). */
val WalletEntity.acceptsTransactions: Boolean
    get() = !archived && type != WalletType.OTHER_ASSET.name
