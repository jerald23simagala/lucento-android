package com.lucento.app.data.repository

import com.lucento.app.core.money.Money
import com.lucento.app.data.local.DebtTermsDao
import com.lucento.app.data.local.DebtTermsEntity
import com.lucento.app.data.local.TransactionDao
import com.lucento.app.data.local.WalletDao
import com.lucento.app.data.local.WalletEntity
import com.lucento.app.data.local.WalletFlowRow
import com.lucento.app.domain.DebtRepayment
import com.lucento.app.domain.DebtTerms
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletBalanceCalculator
import com.lucento.app.domain.WalletChangePolicy
import com.lucento.app.domain.WalletChangeViolation
import com.lucento.app.domain.WalletType
import com.lucento.app.domain.WalletUsage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

class WalletRepository(
    private val dao: WalletDao,
    private val transactionDao: TransactionDao,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Counts recurring schedules that use a wallet; null when schedules are not wired (tests). */
    private val scheduleCount: (suspend (Long) -> Int)? = null,
    /** Debt terms of cards and loans; null in tests that don't need them. */
    private val debtTerms: DebtTermsDao? = null,
    private val runner: TransactionRunner = TransactionRunner.Direct,
) {

    sealed interface SaveResult {
        data class Saved(val id: Long) : SaveResult
        data object BlankName : SaveResult
        data object DuplicateName : SaveResult
        data object NegativeBalance : SaveResult
        /** Currency can't change while transactions or recurring schedules use the wallet. */
        data class CurrencyLocked(val usage: WalletUsage) : SaveResult
        /** Can't switch between an asset and a liability type while anything uses the wallet. */
        data class TypeLocked(val usage: WalletUsage) : SaveResult
        data object NotFound : SaveResult
    }

    sealed interface DeleteResult {
        data object Deleted : DeleteResult
        /** The wallet still owns [count] transactions; they must be removed first. */
        data class HasTransactions(val count: Int) : DeleteResult
        /** The wallet is used by [count] recurring schedules; they must be removed first. */
        data class HasSchedules(val count: Int) : DeleteResult
    }

    /** Active wallets with balances derived from opening balance plus transactions, and any debt terms. */
    fun observeWallets(): Flow<List<Wallet>> =
        combine(dao.observeActive(), transactionDao.observeWalletFlows(), debtTerms?.observeAll() ?: flowOf(emptyList())) { entities, flows, terms ->
            val byWallet = flows.associateBy { it.walletId }
            val termsByWallet = terms.associateBy { it.walletId }
            entities.map { it.toDomain(byWallet[it.id], termsByWallet[it.id]) }
        }

    /**
     * Creates a wallet. [debt] is kept only for a card or loan; it is written in the same database transaction.
     */
    suspend fun create(name: String, type: WalletType, openingBalance: Money, debt: DebtTerms? = null): SaveResult {
        val trimmed = name.trim()
        validate(trimmed, openingBalance, excludeId = 0)?.let { return it }
        val now = clock()
        return runner.run {
            val id = dao.insert(
                WalletEntity(
                    name = trimmed,
                    type = type.name,
                    currencyCode = openingBalance.currencyCode,
                    openingBalanceMinor = openingBalance.amountMinor,
                    sortOrder = dao.maxSortOrder() + 1,
                    createdAt = now,
                    updatedAt = now,
                    clientUuid = UUID.randomUUID().toString(),
                )
            )
            writeTerms(id, type, debt, now)
            SaveResult.Saved(id)
        }
    }

    /** Updates a wallet; [debt] replaces its terms (null, or a non-liability type, removes them). */
    suspend fun update(id: Long, name: String, type: WalletType, openingBalance: Money, debt: DebtTerms? = null): SaveResult {
        val existing = dao.getById(id) ?: return SaveResult.NotFound
        val trimmed = name.trim()
        validate(trimmed, openingBalance, excludeId = id)?.let { return it }
        val currentType = runCatching { WalletType.valueOf(existing.type) }.getOrNull()
        val typeOrCurrencyChanges = currentType != type || existing.currencyCode != openingBalance.currencyCode
        if (typeOrCurrencyChanges) {
            // An unreadable stored type is treated as a side change, so a used wallet can't be rewritten.
            val violation = WalletChangePolicy.check(
                currentType = currentType ?: if (type.isLiability) WalletType.CASH else WalletType.CREDIT_CARD,
                currentCurrency = existing.currencyCode,
                newType = type,
                newCurrency = openingBalance.currencyCode,
                usage = usage(id),
            )
            when (violation) {
                is WalletChangeViolation.CurrencyLocked -> return SaveResult.CurrencyLocked(violation.usage)
                is WalletChangeViolation.TypeLocked -> return SaveResult.TypeLocked(violation.usage)
                null -> Unit
            }
        }
        val now = clock()
        runner.run {
            dao.update(
                existing.copy(
                    name = trimmed,
                    type = type.name,
                    currencyCode = openingBalance.currencyCode,
                    openingBalanceMinor = openingBalance.amountMinor,
                    updatedAt = now,
                )
            )
            writeTerms(id, type, debt, now)
        }
        return SaveResult.Saved(id)
    }

    private suspend fun writeTerms(id: Long, type: WalletType, debt: DebtTerms?, now: Long) {
        val terms = debtTerms ?: return
        if (debt == null || !type.isLiability) {
            terms.delete(id)
            return
        }
        val monthly = debt.repayment == DebtRepayment.MONTHLY
        terms.upsert(
            DebtTermsEntity(
                walletId = id,
                interestRateBps = debt.interestRateBps,
                repayment = debt.repayment.name,
                termMonths = debt.termMonths.takeIf { monthly },
                installmentMinor = debt.installment?.amountMinor.takeIf { monthly },
                updatedAt = now,
            )
        )
    }

    /** Transactions (including transfer legs) and recurring schedules that reference wallet [id]. */
    suspend fun usage(id: Long): WalletUsage =
        WalletUsage(transactionCount = transactionDao.countForWallet(id), scheduleCount = scheduleCount?.invoke(id) ?: 0)

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

internal fun WalletEntity.toDomain(flow: WalletFlowRow? = null, terms: DebtTermsEntity? = null): Wallet {
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
            adjustmentInMinor = flow?.adjustmentInMinor ?: 0L,
            adjustmentOutMinor = flow?.adjustmentOutMinor ?: 0L,
        ),
        createdAtEpochMillis = createdAt,
        openingBalance = opening,
        debt = terms?.takeIf { walletType.isLiability }?.toDomain(currencyCode),
    )
}

/** Stored terms that no longer pass validation (edited by hand, or from a future version) are dropped, not shown. */
internal fun DebtTermsEntity.toDomain(currencyCode: String): DebtTerms? = runCatching {
    DebtTerms(
        interestRateBps = interestRateBps,
        repayment = DebtRepayment.valueOf(repayment),
        termMonths = termMonths,
        installment = installmentMinor?.let { Money(it, currencyCode) },
    )
}.getOrNull()
