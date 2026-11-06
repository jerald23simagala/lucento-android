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
    /**
     * v10: the institution from the bundled banking registry (e.g. "ph_bdo"), chosen by the user; null = none (every
     * wallet before v10, cash, or a bank not in the registry). Never inferred from the wallet's name.
     */
    @ColumnInfo(name = "institution_id") val institutionId: String? = null,
    /**
     * v12, Lucento Shared: [com.lucento.app.domain.WalletClassification] name (Shared / the Owner's / a Partner's), set by
     * the Owner. An organizational label only. Null = never classified (every wallet before v12).
     */
    @ColumnInfo(name = "classification") val classification: String? = null,
    /**
     * v13, multicurrency: for a wallet in another currency, the rate its opening balance is valued at in the main currency
     * (1 unit = this many), stored as an exact decimal string ("56.1234"; [com.lucento.app.core.money.FxRate.encode]).
     * Null: not valued yet (main-currency wallets, and foreign wallets from before multicurrency until they get one).
     */
    @ColumnInfo(name = "fx_basis_rate") val fxBasisRate: String? = null,
    /** v13: revalue this foreign wallet at each month end (unrealized FX gain or loss). */
    @ColumnInfo(name = "fx_revalue", defaultValue = "0") val fxRevalue: Boolean = false,
)

/** Money can be posted to this wallet: it is active and not a manually valued asset (see WalletType.isValuedManually). */
val WalletEntity.acceptsTransactions: Boolean
    get() = !archived && type != WalletType.OTHER_ASSET.name
