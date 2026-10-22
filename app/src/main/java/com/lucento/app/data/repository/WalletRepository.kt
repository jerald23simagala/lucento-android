package com.lucento.app.data.repository

import com.lucento.app.core.money.Money
import com.lucento.app.data.local.TransactionDao
import com.lucento.app.data.local.WalletDao
import com.lucento.app.data.local.WalletEntity
import com.lucento.app.data.local.WalletFlowRow
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletBalanceCalculator
import com.lucento.app.domain.WalletType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class WalletRepository(
    private val dao: WalletDao,
    private val transactionDao: TransactionDao,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Counts recurring schedules that use a wallet; null when schedules are not wired (tests). */
    private val scheduleCount: (suspend (Long) -> Int)? = null,
) {

    sealed interface SaveResult {
        data class Saved(val id: Long) : SaveResult
        data object BlankName : SaveResult
        data object DuplicateName : SaveResult
        data object NegativeBalance : SaveResult
        /** Currency can't change once transactions exist in the wallet. */
        data object CurrencyLocked : SaveResult
    }

    sealed interface DeleteResult {
        data object Deleted : DeleteResult
        /** The wallet still owns [count] transactions; they must be removed first. */
        data class HasTransactions(val count: Int) : DeleteResult
        /** The wallet is used by [count] recurring schedules; they must be removed first. */
        data class HasSchedules(val count: Int) : DeleteResult
    }

    /** Active wallets with balances derived from opening balance plus transactions. */
    fun observeWallets(): Flow<List<Wallet>> =
        combine(dao.observeActive(), transactionDao.observeWalletFlows()) { entities, flows ->
            val byWallet = flows.associateBy { it.walletId }
            entities.map { it.toDomain(byWallet[it.id]) }
        }

    suspend fun create(name: String, type: WalletType, openingBalance: Money): SaveResult {
        val trimmed = name.trim()
        validate(trimmed, openingBalance, excludeId = 0)?.let { return it }
        val now = clock()
        val id = dao.insert(
            WalletEntity(
                name = trimmed,
                type = type.name,
                currencyCode = openingBalance.currencyCode,
                openingBalanceMinor = openingBalance.amountMinor,
                sortOrder = dao.maxSortOrder() + 1,
                createdAt = now,
                updatedAt = now,
            )
        )
        return SaveResult.Saved(id)
    }

    suspend fun update(id: Long, name: String, type: WalletType, openingBalance: Money): SaveResult {
        val existing = dao.getById(id) ?: return SaveResult.BlankName
        val trimmed = name.trim()
        validate(trimmed, openingBalance, excludeId = id)?.let { return it }
        // A wallet's currency is part of every recorded transaction; it cannot change once used.
        if (existing.currencyCode != openingBalance.currencyCode && transactionDao.countForWallet(id) > 0) {
            return SaveResult.CurrencyLocked
        }
        dao.update(
            existing.copy(
                name = trimmed,
                type = type.name,
                currencyCode = openingBalance.currencyCode,
                openingBalanceMinor = openingBalance.amountMinor,
                updatedAt = clock(),
            )
        )
        return SaveResult.Saved(id)
    }

    /** Refuses to delete a wallet that still has transactions so financial history is never lost silently. */
    suspend fun delete(id: Long): DeleteResult {
        val count = transactionDao.countForWallet(id)
        if (count > 0) return DeleteResult.HasTransactions(count)
        val schedules = scheduleCount?.invoke(id) ?: 0
        if (schedules > 0) return DeleteResult.HasSchedules(schedules)
        dao.deleteById(id)
        return DeleteResult.Deleted
    }

    private suspend fun validate(name: String, balance: Money, excludeId: Long): SaveResult? = when {
        name.isEmpty() -> SaveResult.BlankName
        balance.isNegative -> SaveResult.NegativeBalance
        dao.countActiveWithName(name, excludeId) > 0 -> SaveResult.DuplicateName
        else -> null
    }
}

internal fun WalletEntity.toDomain(flow: WalletFlowRow? = null): Wallet {
    val walletType = runCatching { WalletType.valueOf(type) }.getOrDefault(WalletType.CASH)
    val opening = Money(openingBalanceMinor, currencyCode)
    return Wallet(
        id = id,
        name = name,
        type = walletType,
        balance = WalletBalanceCalculator.currentBalance(
            opening = opening,
            type = walletType,
            incomeMinor = flow?.incomeMinor ?: 0L,
            expenseMinor = flow?.expenseMinor ?: 0L,
            transferInMinor = flow?.transferInMinor ?: 0L,
            transferOutMinor = flow?.transferOutMinor ?: 0L,
        ),
        createdAtEpochMillis = createdAt,
        openingBalance = opening,
    )
}
