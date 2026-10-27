package com.lucento.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Optional terms of a liability wallet, one row per card or loan (added in v8, see MIGRATION_7_8). Removed with its
 * wallet (CASCADE; only empty wallets are ever deleted). Synced as columns of the cloud `wallets` row (v9): a terms
 * change always bumps its wallet's `updated_at` (WalletRepository), so the wallet goes out again with them.
 */
@Entity(
    tableName = "debt_terms",
    foreignKeys = [
        ForeignKey(
            entity = WalletEntity::class,
            parentColumns = ["id"],
            childColumns = ["wallet_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class DebtTermsEntity(
    @PrimaryKey @ColumnInfo(name = "wallet_id") val walletId: Long,
    /** Yearly rate in basis points (12.5 % = 1250); null when not entered. */
    @ColumnInfo(name = "interest_rate_bps") val interestRateBps: Int?,
    /** [com.lucento.app.domain.DebtRepayment] name. */
    val repayment: String,
    @ColumnInfo(name = "term_months") val termMonths: Int?,
    /** Monthly instalment in the wallet's currency, minor units. */
    @ColumnInfo(name = "installment_minor") val installmentMinor: Long?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Dao
interface DebtTermsDao {
    @Query("SELECT * FROM debt_terms")
    fun observeAll(): Flow<List<DebtTermsEntity>>

    @Upsert
    suspend fun upsert(terms: DebtTermsEntity)

    @Query("DELETE FROM debt_terms WHERE wallet_id = :walletId")
    suspend fun delete(walletId: Long)

    /** Read by cloud sync, which carries a debt's terms on its wallet row (see docs/SYNC.md). */
    @Query("SELECT * FROM debt_terms WHERE wallet_id = :walletId")
    suspend fun get(walletId: Long): DebtTermsEntity?
}
