package com.lucento.app.data.repository

import com.lucento.app.core.money.Money
import com.lucento.app.data.local.CategoryDao
import com.lucento.app.data.local.TransactionDao
import com.lucento.app.data.local.TransactionEntity
import com.lucento.app.data.local.WalletDao
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class TransactionRepository(
    private val transactions: TransactionDao,
    private val wallets: WalletDao,
    private val categories: CategoryDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val runner: TransactionRunner = TransactionRunner.Direct,
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

    /** A movement between two of the user's wallets. [group] is null when creating. */
    data class TransferInput(
        val group: String?,
        val clientUuid: String,
        val fromWalletId: Long,
        val toWalletId: Long,
        val amount: Money,
        val date: LocalDate,
        val note: String,
    )

    sealed interface TransferResult {
        data class Saved(val group: String) : TransferResult
        data object AmountNotPositive : TransferResult
        data object WalletNotFound : TransferResult
        data object SameWallet : TransferResult
        /** Both wallets must use the transfer's currency; Lucento never converts with a guessed rate. */
        data object CurrencyMismatch : TransferResult
        data object TransferNotFound : TransferResult
    }

    fun observeTransactions(): Flow<List<Transaction>> =
        transactions.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun save(input: Input): SaveResult {
        if (input.type.isTransfer) return SaveResult.InvalidType
        if (input.amount.amountMinor <= 0L) return SaveResult.AmountNotPositive

        val wallet = wallets.getById(input.walletId)
        if (wallet == null || wallet.archived) return SaveResult.WalletNotFound
        if (wallet.currencyCode != input.amount.currencyCode) return SaveResult.CurrencyMismatch

        if (input.categoryId != null) {
            val category = categories.getById(input.categoryId) ?: return SaveResult.CategoryMismatch
            if (category.kind != input.type.name) return SaveResult.CategoryMismatch
        }

        val note = input.note.trim().take(MAX_NOTE)
        val now = clock()

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
                )
            }
            return SaveResult.Saved(id)
        }

        val existing = transactions.getById(input.id) ?: return SaveResult.TransactionNotFound
        if (existing.transferGroup != null) return SaveResult.InvalidType
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
            )
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
        if (input.amount.amountMinor <= 0L) return TransferResult.AmountNotPositive
        if (input.fromWalletId == input.toWalletId) return TransferResult.SameWallet
        val from = wallets.getById(input.fromWalletId)
        val to = wallets.getById(input.toWalletId)
        if (from == null || from.archived || to == null || to.archived) return TransferResult.WalletNotFound
        if (from.currencyCode != input.amount.currencyCode || to.currencyCode != input.amount.currencyCode) {
            return TransferResult.CurrencyMismatch
        }
        val note = input.note.trim().take(MAX_NOTE)
        val now = clock()

        if (input.group == null) {
            val outKey = input.clientUuid + OUT_SUFFIX
            transactions.findByClientUuid(outKey)?.transferGroup?.let { return TransferResult.Saved(it) }
            val group = input.clientUuid
            fun leg(type: TransactionType, walletId: Long, key: String) = TransactionEntity(
                type = type.name,
                amountMinor = input.amount.amountMinor,
                currencyCode = input.amount.currencyCode,
                walletId = walletId,
                categoryId = null,
                occurredOn = input.date.toEpochDay(),
                note = note,
                clientUuid = key,
                createdAt = now,
                updatedAt = now,
                transferGroup = group,
            )
            // Both legs in one database transaction: if the second insert fails, the first is rolled back, so a
            // transfer can never exist as a single leg. The duplicate check is repeated inside the transaction.
            try {
                runner.run {
                    if (transactions.findByClientUuid(outKey) == null) {
                        transactions.insert(leg(TransactionType.TRANSFER_OUT, from.id, outKey))
                        transactions.insert(leg(TransactionType.TRANSFER_IN, to.id, input.clientUuid + IN_SUFFIX))
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
        fun TransactionEntity.edited(walletId: Long) = copy(
            amountMinor = input.amount.amountMinor,
            currencyCode = input.amount.currencyCode,
            walletId = walletId,
            occurredOn = input.date.toEpochDay(),
            note = note,
            updatedAt = now,
        )
        runner.run {
            transactions.update(outLeg.edited(from.id))
            transactions.update(inLeg.edited(to.id))
        }
        return TransferResult.Saved(input.group)
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
)

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

/** [TransactionRepository.saveTransfer] that returns null instead of throwing; both legs are rolled back together. */
suspend fun TransactionRepository.saveTransferOrNull(input: TransactionRepository.TransferInput): TransactionRepository.TransferResult? =
    try {
        saveTransfer(input)
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        null
    }
