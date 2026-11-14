package com.lucento.app.data.repository

import com.lucento.app.core.money.FxRate
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
import com.lucento.app.domain.WalletClassification
import com.lucento.app.domain.WalletType
import com.lucento.app.domain.WalletUsage
import com.lucento.app.domain.entitlement.Access
import com.lucento.app.domain.entitlement.Limit
import com.lucento.app.domain.entitlement.ProFeature
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    /** Lucento Shared: false on a Partner's phone, where wallets are the Owner's to change ([StructureAccess]). */
    private val access: StructureAccess = StructureAccess.Always,
    /** Lucento Pro: whether the plan allows one more wallet of this kind ([EntitlementGate]). Only creation is gated. */
    private val gate: EntitlementGate = EntitlementGate.Permissive,
    /** Multicurrency: the ledger's main currency (null until chosen). A wallet in another currency is a Pro kind (docs §3.2). */
    private val mainCurrency: suspend () -> String? = { null },
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
        /** A Partner's phone: wallets are changed by the Shared Space's Owner. Nothing was written. */
        data object NotPermitted : SaveResult
        /** Lucento Pro: the plan allows [limit] of this kind of wallet and [used] exist. Nothing was written; nothing existing changes. */
        data class LimitReached(val feature: ProFeature, val used: Int, val limit: Limit) : SaveResult
        /** Lucento Pro: this kind of wallet is not on the plan. Nothing was written. */
        data class ProRequired(val feature: ProFeature) : SaveResult
    }

    sealed interface DeleteResult {
        data object Deleted : DeleteResult
        /** The wallet still owns [count] transactions; they must be removed first. */
        data class HasTransactions(val count: Int) : DeleteResult
        /** The wallet is used by [count] recurring schedules; they must be removed first. */
        data class HasSchedules(val count: Int) : DeleteResult
        /** A Partner's phone: wallets are changed by the Shared Space's Owner. Nothing was removed. */
        data object NotPermitted : DeleteResult
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
    suspend fun create(
        name: String,
        type: WalletType,
        openingBalance: Money,
        debt: DebtTerms? = null,
        institutionId: String? = null,
        classification: WalletClassification? = null,
        /** Multicurrency: for a wallet in another currency, the rate its opening balance is valued at (1 unit = this many). */
        fxBasisRate: java.math.BigDecimal? = null,
        /**
         * A new credit card only (tester round 3): the part of [openingBalance] not due yet ([com.lucento.app.domain.CardSetup]).
         * The card opens at the rest, and this part is written as one adjustment dated [setupDay], in the same transaction.
         */
        unbilledAtSetup: Money? = null,
        setupDay: java.time.LocalDate = java.time.LocalDate.now(),
    ): SaveResult {
        if (!access.canManageStructure()) return SaveResult.NotPermitted
        val trimmed = name.trim()
        validate(trimmed, openingBalance, excludeId = 0)?.let { return it }
        planGate(type)?.let { return it }
        foreignCurrencyGate(openingBalance.currencyCode)?.let { return it }
        val unbilled = unbilledAtSetup?.takeIf {
            type == WalletType.CREDIT_CARD && it.currencyCode == openingBalance.currencyCode &&
                it.amountMinor > 0L && it.amountMinor <= openingBalance.amountMinor
        }
        val opening = if (unbilled != null) openingBalance - unbilled else openingBalance
        val now = clock()
        return runner.run {
            val id = dao.insert(
                WalletEntity(
                    name = trimmed,
                    type = type.name,
                    currencyCode = openingBalance.currencyCode,
                    openingBalanceMinor = opening.amountMinor,
                    sortOrder = dao.maxSortOrder() + 1,
                    createdAt = now,
                    updatedAt = now,
                    clientUuid = UUID.randomUUID().toString(),
                    institutionId = institutionId?.takeIf(::isInstitutionId),
                    classification = classification?.name,
                    fxBasisRate = fxBasisRate?.takeIf { it.signum() > 0 }?.let(FxRate::encode),
                )
            )
            writeTerms(id, type, debt, now)
            if (unbilled != null) {
                transactionDao.insert(
                    com.lucento.app.data.local.TransactionEntity(
                        type = com.lucento.app.domain.TransactionType.ADJUSTMENT_OUT.name,
                        amountMinor = unbilled.amountMinor,
                        currencyCode = unbilled.currencyCode,
                        walletId = id,
                        categoryId = null,
                        occurredOn = setupDay.toEpochDay(),
                        note = com.lucento.app.domain.CardSetup.NOTE,
                        clientUuid = UUID.randomUUID().toString(),
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            }
            SaveResult.Saved(id)
        }
    }

    /**
     * Lucento Shared: labels a wallet Shared, the Owner's or a Partner's (an organizational label, never an access rule).
     * Bumps updated_at, so the label syncs like any other wallet edit.
     */
    suspend fun setClassification(id: Long, classification: WalletClassification): SaveResult {
        if (!access.canManageStructure()) return SaveResult.NotPermitted
        val existing = dao.getById(id) ?: return SaveResult.NotFound
        if (existing.classification == classification.name) return SaveResult.Saved(id)
        dao.update(existing.copy(classification = classification.name, updatedAt = clock()))
        return SaveResult.Saved(id)
    }

    /**
     * Multicurrency: the rate a foreign wallet's opening balance is valued at in the main currency — for a wallet from
     * before multicurrency, or to correct it. Everything after is replayed from it (docs/CALCULATIONS.md "Exchange rates").
     */
    suspend fun setFxBasis(id: Long, rate: java.math.BigDecimal): SaveResult {
        if (!access.canManageStructure()) return SaveResult.NotPermitted
        if (rate.signum() <= 0) return SaveResult.NotFound
        val existing = dao.getById(id) ?: return SaveResult.NotFound
        val encoded = FxRate.encode(rate)
        if (existing.fxBasisRate == encoded) return SaveResult.Saved(id)
        dao.update(existing.copy(fxBasisRate = encoded, updatedAt = clock()))
        return SaveResult.Saved(id)
    }

    /** Multicurrency: revalue this foreign wallet at each month end (unrealized FX gain or loss). */
    suspend fun setFxRevalue(id: Long, on: Boolean): SaveResult {
        if (!access.canManageStructure()) return SaveResult.NotPermitted
        val existing = dao.getById(id) ?: return SaveResult.NotFound
        if (existing.fxRevalue == on) return SaveResult.Saved(id)
        dao.update(existing.copy(fxRevalue = on, updatedAt = clock()))
        return SaveResult.Saved(id)
    }

    /** Updates a wallet and keeps its institution as it is (callers that don't edit it). */
    suspend fun update(id: Long, name: String, type: WalletType, openingBalance: Money, debt: DebtTerms? = null): SaveResult =
        update(id, name, type, openingBalance, debt, institutionId = dao.getById(id)?.institutionId)

    /**
     * Updates a wallet; [debt] replaces its terms (null, or a non-liability type, removes them). [institutionId] is the
     * wallet's institution after the edit (null = none).
     */
    suspend fun update(
        id: Long,
        name: String,
        type: WalletType,
        openingBalance: Money,
        debt: DebtTerms?,
        institutionId: String?,
    ): SaveResult {
        if (!access.canManageStructure()) return SaveResult.NotPermitted
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
            // A wallet changing *into* another currency is a new foreign wallet as far as the plan is concerned.
            if (existing.currencyCode != openingBalance.currencyCode) foreignCurrencyGate(openingBalance.currencyCode)?.let { return it }
        }
        val now = clock()
        runner.run {
            dao.update(
                existing.copy(
                    name = trimmed,
                    type = type.name,
                    currencyCode = openingBalance.currencyCode,
                    openingBalanceMinor = openingBalance.amountMinor,
                    institutionId = institutionId?.takeIf(::isInstitutionId),
                    updatedAt = now,
                    // A rate belongs to a currency: a (still unused) wallet switched to another currency needs a new one.
                    fxBasisRate = existing.fxBasisRate.takeIf { existing.currencyCode == openingBalance.currencyCode },
                )
            )
            writeTerms(id, type, debt, now)
        }
        return SaveResult.Saved(id)
    }

    private suspend fun writeTerms(id: Long, type: WalletType, debt: DebtTerms?, now: Long) {
        val terms = debtTerms ?: return
        // Cards, loans and receivables (owner round 10) keep terms; any other type drops them.
        if (debt == null || !type.hasTerms) {
            terms.delete(id)
            return
        }
        val monthly = debt.repayment == DebtRepayment.MONTHLY
        // A limit and billing days mean something only on a card; a card switched to a loan drops them.
        val card = type == WalletType.CREDIT_CARD
        terms.upsert(
            DebtTermsEntity(
                walletId = id,
                interestRateBps = debt.interestRateBps,
                repayment = debt.repayment.name,
                termMonths = debt.termMonths.takeIf { monthly },
                installmentMinor = debt.installment?.amountMinor.takeIf { monthly },
                updatedAt = now,
                creditLimitMinor = debt.creditLimit?.amountMinor.takeIf { card },
                statementDay = debt.statementDay.takeIf { card },
                dueDay = debt.dueDay.takeIf { card },
            )
        )
    }

    /** Transactions (including transfer legs) and recurring schedules that reference wallet [id]. */
    suspend fun usage(id: Long): WalletUsage =
        WalletUsage(transactionCount = transactionDao.countForWallet(id), scheduleCount = scheduleCount?.invoke(id) ?: 0)

    /** Refuses to delete a wallet that still has transactions so financial history is never lost silently. */
    suspend fun delete(id: Long): DeleteResult {
        if (!access.canManageStructure()) return DeleteResult.NotPermitted
        val count = transactionDao.countForWallet(id)
        if (count > 0) return DeleteResult.HasTransactions(count)
        val schedules = scheduleCount?.invoke(id) ?: 0
        if (schedules > 0) return DeleteResult.HasSchedules(schedules)
        dao.deleteById(id)
        return DeleteResult.Deleted
    }

    /**
     * Lucento Pro (docs/MONETIZATION.md §3.2): money wallets and debt accounts are counted separately; receivables and
     * manually valued assets are Pro kinds. Counts are of active wallets of the same kind, so what exists is never affected.
     */
    private suspend fun planGate(type: WalletType): SaveResult? {
        val feature = planFeature(type)
        val used = dao.observeActive().first().count { row -> WalletType.entries.firstOrNull { it.name == row.type }?.let(::planFeature) == feature }
        return when (val access = gate.check(feature, used)) {
            Access.Allowed -> null
            is Access.Locked -> SaveResult.ProRequired(access.feature)
            is Access.LimitReached -> SaveResult.LimitReached(access.feature, access.used, access.limit)
        }
    }

    /** Lucento Pro: a wallet in a currency other than the main one. Existing foreign wallets are never touched (§6.3). */
    private suspend fun foreignCurrencyGate(currencyCode: String): SaveResult? {
        val main = mainCurrency() ?: return null
        if (currencyCode == main) return null
        val used = dao.observeActive().first().count { it.currencyCode != main }
        return when (val access = gate.check(ProFeature.FOREIGN_CURRENCY_WALLETS, used)) {
            Access.Allowed -> null
            is Access.Locked -> SaveResult.ProRequired(access.feature)
            is Access.LimitReached -> SaveResult.LimitReached(access.feature, access.used, access.limit)
        }
    }

    private suspend fun validate(name: String, balance: Money, excludeId: Long): SaveResult? = when {
        name.isEmpty() -> SaveResult.BlankName
        balance.isNegative -> SaveResult.NegativeBalance
        dao.countActiveWithName(name, excludeId) > 0 -> SaveResult.DuplicateName
        else -> null
    }
}

/** Which plan limit a wallet of [type] counts against (docs/MONETIZATION.md §3.2). */
internal fun planFeature(type: WalletType): ProFeature = when (type) {
    WalletType.CASH, WalletType.BANK, WalletType.E_WALLET, WalletType.SAVINGS -> ProFeature.MONEY_WALLETS
    WalletType.CREDIT_CARD, WalletType.LOAN -> ProFeature.DEBT_ACCOUNTS
    WalletType.RECEIVABLE -> ProFeature.RECEIVABLES
    WalletType.OTHER_ASSET -> ProFeature.VALUED_ASSETS
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
        institutionId = institutionId,
        debt = terms?.takeIf { walletType.hasTerms }?.toDomain(currencyCode),
        classification = WalletClassification.fromKey(classification),
        fxBasisRate = FxRate.parse(fxBasisRate),
        fxRevalue = fxRevalue,
    )
}

/** Stored terms that no longer pass validation (edited by hand, or from a future version) are dropped, not shown. */
internal fun DebtTermsEntity.toDomain(currencyCode: String): DebtTerms? = runCatching {
    DebtTerms(
        interestRateBps = interestRateBps,
        repayment = DebtRepayment.valueOf(repayment),
        termMonths = termMonths,
        installment = installmentMinor?.let { Money(it, currencyCode) },
        creditLimit = creditLimitMinor?.let { Money(it, currencyCode) },
        statementDay = statementDay,
        dueDay = dueDay,
    )
}.getOrNull()

/** Registry ids are lowercase snake_case ("ph_bdo"); anything else is not stored (the server also caps the length). */
internal fun isInstitutionId(value: String): Boolean = value.length in 1..128 && value.all { it in 'a'..'z' || it in '0'..'9' || it == '_' }
