package com.lucento.app.data.repository

import com.lucento.app.core.money.Money
import com.lucento.app.data.local.CategoryDao
import com.lucento.app.data.local.TransactionDao
import com.lucento.app.data.local.TransactionEntity
import com.lucento.app.data.local.WalletDao
import com.lucento.app.data.local.acceptsTransactions
import com.lucento.app.domain.BalanceAdjustment
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.WalletBalanceCalculator
import com.lucento.app.domain.WalletType
import com.lucento.app.domain.fx.FxSource
import com.lucento.app.domain.fx.FxValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import java.time.LocalDate

class TransactionRepository(
    private val transactions: TransactionDao,
    private val wallets: WalletDao,
    private val categories: CategoryDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val runner: TransactionRunner = TransactionRunner.Direct,
    /** The app's scope, so screens share one whole-table query (null in JVM tests: each collector reads directly). */
    private val shareScope: kotlinx.coroutines.CoroutineScope? = null,
    /**
     * Lucento Shared: the signed-in account, stamped on rows this phone adds or changes so "Added by" reads right before
     * the next sync. Display only — the server stamps the same values itself and never takes them from the app.
     */
    private val actor: suspend () -> String? = { null },
) {

    /** Everything needed to create or update a transaction. [clientUuid] makes creation idempotent. */
    data class Input(
        val id: Long?,
        val clientUuid: String,
        val type: TransactionType,
        val amount: Money,
        val walletId: Long,
        val categoryId: Long?,
        val date: LocalDate,
        val note: String,
        /**
         * Multicurrency: the value in the main currency, for a wallet in another currency. Null: follows the wallet's
         * average (no gain or loss). On an edit it replaces what was stored, so callers pass the existing value to keep it.
         */
        val fx: FxValue? = null,
    )

    sealed interface SaveResult {
        data class Saved(val id: Long) : SaveResult
        data object AmountNotPositive : SaveResult
        data object WalletNotFound : SaveResult
        data object CurrencyMismatch : SaveResult
        data object CategoryMismatch : SaveResult
        data object TransactionNotFound : SaveResult
        /** Transfers are saved with [saveTransfer]; a transfer leg can't be edited as income or expense. */
        data object InvalidType : SaveResult
    }

    /**
     * A movement between two of the user's wallets. [group] is null when creating. [amount] leaves the source wallet in its
     * currency. Between currencies (multicurrency), [received] is what arrived in the destination's currency — the two real
     * amounts are the rate. [outFx] / [inFx] are each leg's value in the main currency where its wallet is in another
     * currency ([com.lucento.app.domain.fx.FxTransfer.legValues] works them out); null legs follow their wallet's average.
     */
    data class TransferInput(
        val group: String?,
        val clientUuid: String,
        val fromWalletId: Long,
        val toWalletId: Long,
        val amount: Money,
        val date: LocalDate,
        val note: String,
        val received: Money? = null,
        val outFx: FxValue? = null,
        val inFx: FxValue? = null,
    ) {
        /** What the destination wallet receives: [received] between currencies, otherwise the same [amount]. */
        val arriving: Money get() = received ?: amount
    }

    sealed interface TransferResult {
        data class Saved(val group: String) : TransferResult
        data object AmountNotPositive : TransferResult
        data object WalletNotFound : TransferResult
        data object SameWallet : TransferResult
        /**
         * Each wallet must use its leg's currency, and between two currencies the amount received is required: Lucento
         * never converts with a guessed rate.
         */
        data object CurrencyMismatch : TransferResult
        data object TransferNotFound : TransferResult
    }

    sealed interface AdjustResult {
        data class Adjusted(val id: Long) : AdjustResult
        /** The tracked balance on that date already equals the actual one: nothing was written. */
        data object Unchanged : AdjustResult
        data object WalletNotFound : AdjustResult
        data object CurrencyMismatch : AdjustResult
        /** A balance can be overridden for today or an earlier day, never the future. */
        data object FutureDate : AdjustResult
    }

    /**
     * Every transaction, newest first. With a [shareScope] (the app's), all screens share one Room query and one mapping
     * per change instead of running their own (P2-8, N-12); the cache is dropped when nobody listens, so a returning
     * screen never sees stale rows first.
     */
    private val all: Flow<List<Transaction>> = transactions.observeAll().map { list -> list.map { it.toDomain() } }.let { flow ->
        if (shareScope == null) flow
        else flow.shareIn(shareScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000, replayExpirationMillis = 0), replay = 1)
    }

    fun observeTransactions(): Flow<List<Transaction>> = all

    /** How many transactions exist (P2-8: Settings' export summary needs only this). */
    fun observeCount(): Flow<Int> = transactions.observeCount()

    /** How many income and expense entries exist (P2-8: Home's opt-in usage count needs only this). */
    fun observeCashFlowCount(): Flow<Int> = transactions.observeCashFlowCount()

    /** One transaction and, for a transfer, its other leg (P2-8: the detail page needs only these). */
    fun observeWithTransferLegs(id: Long): Flow<List<Transaction>> =
        transactions.observeWithTransferLegs(id).map { list -> list.map { it.toDomain() } }

    suspend fun save(input: Input): SaveResult {
        if (!input.type.isCashFlow) return SaveResult.InvalidType
        if (input.amount.amountMinor <= 0L) return SaveResult.AmountNotPositive

        val wallet = wallets.getById(input.walletId)
        if (wallet == null || !wallet.acceptsTransactions) return SaveResult.WalletNotFound
        if (wallet.currencyCode != input.amount.currencyCode) return SaveResult.CurrencyMismatch

        if (input.categoryId != null) {
            val category = categories.getById(input.categoryId) ?: return SaveResult.CategoryMismatch
            if (category.kind != input.type.name) return SaveResult.CategoryMismatch
        }

        val note = input.note.trim().take(MAX_NOTE)
        val now = clock()
        val by = actor()

        if (input.id == null) {
            // Idempotency: a retry or double-tap carrying the same key returns the original row.
            transactions.findByClientUuid(input.clientUuid)?.let { return SaveResult.Saved(it.id) }
            val id = insertOnce(input.clientUuid) {
                TransactionEntity(
                    type = input.type.name,
                    amountMinor = input.amount.amountMinor,
                    currencyCode = input.amount.currencyCode,
                    walletId = input.walletId,
                    categoryId = input.categoryId,
                    occurredOn = input.date.toEpochDay(),
                    note = note,
                    clientUuid = input.clientUuid,
                    createdAt = now,
                    updatedAt = now,
                    createdBy = by,
                ).withFx(input.fx)
            }
            return SaveResult.Saved(id)
        }

        val existing = transactions.getById(input.id) ?: return SaveResult.TransactionNotFound
        if (existing.transferGroup != null || existing.type !in CASH_FLOW_TYPES) return SaveResult.InvalidType
        transactions.update(
            existing.copy(
                type = input.type.name,
                amountMinor = input.amount.amountMinor,
                currencyCode = input.amount.currencyCode,
                walletId = input.walletId,
                categoryId = input.categoryId,
                occurredOn = input.date.toEpochDay(),
                note = note,
                updatedAt = now,
                updatedBy = by ?: existing.updatedBy,
            ).withFx(input.fx)
        )
        return SaveResult.Saved(input.id)
    }

    /** Deletes a transaction; deleting either leg of a transfer deletes the whole transfer. */
    suspend fun delete(id: Long) {
        val existing = transactions.getById(id) ?: return
        val group = existing.transferGroup
        if (group != null) runner.run { transactions.deleteByTransferGroup(group) } else transactions.deleteById(id)
    }

    /**
     * Creates or updates a transfer as two linked legs written atomically: TRANSFER_OUT on the source and
     * TRANSFER_IN on the destination, both with the same amount, currency, date and note. Creation is
     * idempotent on [TransferInput.clientUuid].
     */
    suspend fun saveTransfer(input: TransferInput): TransferResult {
        if (input.amount.amountMinor <= 0L || input.arriving.amountMinor <= 0L) return TransferResult.AmountNotPositive
        if (input.fromWalletId == input.toWalletId) return TransferResult.SameWallet
        val from = wallets.getById(input.fromWalletId)
        val to = wallets.getById(input.toWalletId)
        if (from == null || !from.acceptsTransactions || to == null || !to.acceptsTransactions) return TransferResult.WalletNotFound
        if (!currenciesFit(from.currencyCode, to.currencyCode, input)) return TransferResult.CurrencyMismatch
        val note = input.note.trim().take(MAX_NOTE)
        val now = clock()
        val by = actor()

        if (input.group == null) {
            val outKey = input.clientUuid + OUT_SUFFIX
            transactions.findByClientUuid(outKey)?.transferGroup?.let { return TransferResult.Saved(it) }
            val group = input.clientUuid
            fun leg(type: TransactionType, walletId: Long, key: String, amount: Money, fx: FxValue?) = TransactionEntity(
                type = type.name,
                amountMinor = amount.amountMinor,
                currencyCode = amount.currencyCode,
                walletId = walletId,
                categoryId = null,
                occurredOn = input.date.toEpochDay(),
                note = note,
                clientUuid = key,
                createdAt = now,
                updatedAt = now,
                transferGroup = group,
                createdBy = by,
            ).withFx(fx)
            // Both legs in one database transaction: if the second insert fails, the first is rolled back, so a
            // transfer can never exist as a single leg. The duplicate check is repeated inside the transaction.
            try {
                runner.run {
                    if (transactions.findByClientUuid(outKey) == null) {
                        transactions.insert(leg(TransactionType.TRANSFER_OUT, from.id, outKey, input.amount, input.outFx))
                        transactions.insert(leg(TransactionType.TRANSFER_IN, to.id, input.clientUuid + IN_SUFFIX, input.arriving, input.inFx))
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // A concurrent submit with the same key won the race: report the transfer it created.
                transactions.findByClientUuid(outKey)?.transferGroup?.let { return TransferResult.Saved(it) }
                throw e
            }
            return TransferResult.Saved(group)
        }

        val legs = transactions.getByTransferGroup(input.group)
        val outLeg = legs.firstOrNull { it.type == TransactionType.TRANSFER_OUT.name }
        val inLeg = legs.firstOrNull { it.type == TransactionType.TRANSFER_IN.name }
        if (outLeg == null || inLeg == null) return TransferResult.TransferNotFound
        fun TransactionEntity.edited(walletId: Long, amount: Money, fx: FxValue?) = copy(
            amountMinor = amount.amountMinor,
            currencyCode = amount.currencyCode,
            walletId = walletId,
            occurredOn = input.date.toEpochDay(),
            note = note,
            updatedAt = now,
            updatedBy = by ?: updatedBy,
        ).withFx(fx)
        runner.run {
            transactions.update(outLeg.edited(from.id, input.amount, input.outFx))
            transactions.update(inLeg.edited(to.id, input.arriving, input.inFx))
        }
        return TransferResult.Saved(input.group)
    }

    /**
     * Overrides wallet [walletId]'s balance at the end of [date] with [actual] (the amount owed for a card or loan).
     * The gap from the tracked balance on that date is written as one ADJUSTMENT_IN / ADJUSTMENT_OUT row, so later
     * transactions still apply on top and nothing already recorded is rewritten. Reading the balance and inserting
     * happen in one database transaction; creation is idempotent on [clientUuid].
     */
    suspend fun adjustBalance(walletId: Long, actual: Money, date: LocalDate, clientUuid: String, today: LocalDate = LocalDate.now()): AdjustResult {
        if (date.isAfter(today)) return AdjustResult.FutureDate
        val wallet = wallets.getById(walletId)
        if (wallet == null || !wallet.acceptsTransactions) return AdjustResult.WalletNotFound
        if (wallet.currencyCode != actual.currencyCode) return AdjustResult.CurrencyMismatch
        val type = runCatching { WalletType.valueOf(wallet.type) }.getOrDefault(WalletType.CASH)
        transactions.findByClientUuid(clientUuid)?.let { return AdjustResult.Adjusted(it.id) }
        val now = clock()
        val by = actor()
        var result: AdjustResult = AdjustResult.Unchanged
        runner.run {
            val tracked = WalletBalanceCalculator.balanceOn(
                opening = Money(wallet.openingBalanceMinor, wallet.currencyCode),
                type = type,
                walletId = walletId,
                transactions = transactions.getForWallet(walletId).map { it.toDomain() },
                date = date,
            )
            val plan = BalanceAdjustment.plan(type, tracked, actual) ?: return@run
            val id = transactions.insert(
                TransactionEntity(
                    type = plan.type.name,
                    amountMinor = plan.amount.amountMinor,
                    currencyCode = plan.amount.currencyCode,
                    walletId = walletId,
                    categoryId = null,
                    occurredOn = date.toEpochDay(),
                    note = "",
                    clientUuid = clientUuid,
                    createdAt = now,
                    updatedAt = now,
                    createdBy = by,
                )
            )
            result = AdjustResult.Adjusted(id)
        }
        return result
    }

    /** One new transaction or transfer in a batch written by [saveBatch]. */
    sealed interface BatchEntry {
        data class Single(val input: Input) : BatchEntry
        data class Move(val input: TransferInput) : BatchEntry
    }

    sealed interface BatchResult {
        /** Every entry is in the database (entries already saved under the same key count once, not twice). */
        data class Saved(val count: Int) : BatchResult
        /** Nothing was written: entry [index] broke a rule. */
        data class Rejected(val index: Int, val reason: Any) : BatchResult
    }

    /**
     * Saves several new transactions and transfers as one unit: every entry is validated first with the same rules as
     * [save] and [saveTransfer], then all rows are written in a single database transaction, so a failure part-way
     * leaves nothing behind. Each entry keeps its own idempotency key, so retrying a batch after an error, or a second
     * tap, never creates duplicates. Only creation is supported here (ids and groups must be null).
     */
    suspend fun saveBatch(entries: List<BatchEntry>): BatchResult {
        if (entries.isEmpty()) return BatchResult.Saved(0)
        entries.forEachIndexed { index, entry ->
            val problem: Any? = when (entry) {
                is BatchEntry.Single -> validateNew(entry.input)
                is BatchEntry.Move -> validateNewTransfer(entry.input)
            }
            if (problem != null) return BatchResult.Rejected(index, problem)
        }
        val now = clock()
        val by = actor()
        runner.run {
            entries.forEach { entry ->
                when (entry) {
                    is BatchEntry.Single -> {
                        val input = entry.input
                        if (transactions.findByClientUuid(input.clientUuid) == null) {
                            transactions.insert(
                                TransactionEntity(
                                    type = input.type.name,
                                    amountMinor = input.amount.amountMinor,
                                    currencyCode = input.amount.currencyCode,
                                    walletId = input.walletId,
                                    categoryId = input.categoryId,
                                    occurredOn = input.date.toEpochDay(),
                                    note = input.note.trim().take(MAX_NOTE),
                                    clientUuid = input.clientUuid,
                                    createdAt = now,
                                    updatedAt = now,
                                    createdBy = by,
                                ).withFx(input.fx)
                            )
                        }
                    }
                    is BatchEntry.Move -> {
                        val input = entry.input
                        val outKey = input.clientUuid + OUT_SUFFIX
                        if (transactions.findByClientUuid(outKey) == null) {
                            fun leg(type: TransactionType, walletId: Long, key: String, amount: Money, fx: FxValue?) = TransactionEntity(
                                type = type.name,
                                amountMinor = amount.amountMinor,
                                currencyCode = amount.currencyCode,
                                walletId = walletId,
                                categoryId = null,
                                occurredOn = input.date.toEpochDay(),
                                note = input.note.trim().take(MAX_NOTE),
                                clientUuid = key,
                                createdAt = now,
                                updatedAt = now,
                                transferGroup = input.clientUuid,
                                createdBy = by,
                            ).withFx(fx)
                            transactions.insert(leg(TransactionType.TRANSFER_OUT, input.fromWalletId, outKey, input.amount, input.outFx))
                            transactions.insert(
                                leg(TransactionType.TRANSFER_IN, input.toWalletId, input.clientUuid + IN_SUFFIX, input.arriving, input.inFx),
                            )
                        }
                    }
                }
            }
        }
        return BatchResult.Saved(entries.size)
    }

    private suspend fun validateNew(input: Input): SaveResult? {
        if (input.id != null || !input.type.isCashFlow) return SaveResult.InvalidType
        if (input.amount.amountMinor <= 0L) return SaveResult.AmountNotPositive
        val wallet = wallets.getById(input.walletId)
        if (wallet == null || !wallet.acceptsTransactions) return SaveResult.WalletNotFound
        if (wallet.currencyCode != input.amount.currencyCode) return SaveResult.CurrencyMismatch
        if (input.categoryId != null) {
            val category = categories.getById(input.categoryId) ?: return SaveResult.CategoryMismatch
            if (category.kind != input.type.name || category.archived) return SaveResult.CategoryMismatch
        }
        return null
    }

    private suspend fun validateNewTransfer(input: TransferInput): TransferResult? {
        if (input.group != null) return TransferResult.TransferNotFound
        if (input.amount.amountMinor <= 0L || input.arriving.amountMinor <= 0L) return TransferResult.AmountNotPositive
        if (input.fromWalletId == input.toWalletId) return TransferResult.SameWallet
        val from = wallets.getById(input.fromWalletId)
        val to = wallets.getById(input.toWalletId)
        if (from == null || !from.acceptsTransactions || to == null || !to.acceptsTransactions) return TransferResult.WalletNotFound
        if (!currenciesFit(from.currencyCode, to.currencyCode, input)) return TransferResult.CurrencyMismatch
        return null
    }

    /**
     * Each leg in its wallet's currency. Between two currencies the amount received is required (the rate is what the
     * two amounts say); within one currency there is nothing to receive separately.
     */
    private fun currenciesFit(fromCurrency: String, toCurrency: String, input: TransferInput): Boolean {
        if (fromCurrency != input.amount.currencyCode || toCurrency != input.arriving.currencyCode) return false
        return if (fromCurrency == toCurrency) input.received == null || input.received == input.amount else input.received != null
    }

    /**
     * Inserts a row keyed by [clientUuid]. If the unique index rejects it because a concurrent submit with the
     * same key got there first, returns that row's id instead of failing; any other error is rethrown.
     */
    private suspend fun insertOnce(clientUuid: String, entity: () -> TransactionEntity): Long =
        try {
            transactions.insert(entity())
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            transactions.findByClientUuid(clientUuid)?.id ?: throw e
        }

    companion object {
        const val MAX_NOTE = 200
        private val CASH_FLOW_TYPES = setOf(TransactionType.INCOME.name, TransactionType.EXPENSE.name)
        private const val OUT_SUFFIX = "-out"
        private const val IN_SUFFIX = "-in"
    }
}

internal fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = id,
    type = runCatching { TransactionType.valueOf(type) }.getOrDefault(TransactionType.EXPENSE),
    amount = Money(amountMinor, currencyCode),
    walletId = walletId,
    categoryId = categoryId,
    date = LocalDate.ofEpochDay(occurredOn),
    note = note,
    createdAtEpochMillis = createdAt,
    transferGroup = transferGroup,
    clientUuid = clientUuid,
    createdBy = createdBy,
    updatedBy = updatedBy,
    baseAmount = baseAmountMinor?.let { minor -> baseCurrency?.takeIf { it.length == 3 }?.let { Money(minor, it) } },
    fxSource = FxSource.fromName(fxSource),
)

/**
 * Stores a movement's main-currency value (multicurrency). Nothing is stored when there is none, or when it is in the
 * row's own currency (a main-currency wallet needs no translation).
 */
internal fun TransactionEntity.withFx(fx: FxValue?): TransactionEntity =
    if (fx == null || fx.base.currencyCode == currencyCode) {
        copy(baseAmountMinor = null, baseCurrency = null, fxSource = null)
    } else {
        copy(baseAmountMinor = fx.base.amountMinor, baseCurrency = fx.base.currencyCode, fxSource = fx.source.name)
    }

/**
 * Like [TransactionRepository.save] but returns null instead of throwing when the database refuses the write
 * (constraint failure, disk full, overflow). Screens show an error rather than crash, and never report success.
 */
suspend fun TransactionRepository.saveOrNull(input: TransactionRepository.Input): TransactionRepository.SaveResult? =
    try {
        save(input)
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        null
    }

/** [TransactionRepository.saveBatch] that returns null instead of throwing; the whole batch is rolled back together. */
suspend fun TransactionRepository.saveBatchOrNull(entries: List<TransactionRepository.BatchEntry>): TransactionRepository.BatchResult? =
    try {
        saveBatch(entries)
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        null
    }

/** [TransactionRepository.adjustBalance] that returns null instead of throwing (constraint failure, disk full, overflow). */
suspend fun TransactionRepository.adjustBalanceOrNull(walletId: Long, actual: Money, date: LocalDate, clientUuid: String): TransactionRepository.AdjustResult? =
    try {
        adjustBalance(walletId, actual, date, clientUuid)
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        null
    }

/** [TransactionRepository.saveTransfer] that returns null instead of throwing; both legs are rolled back together. */
suspend fun TransactionRepository.saveTransferOrNull(input: TransactionRepository.TransferInput): TransactionRepository.TransferResult? =
    try {
        saveTransfer(input)
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        null
    }
