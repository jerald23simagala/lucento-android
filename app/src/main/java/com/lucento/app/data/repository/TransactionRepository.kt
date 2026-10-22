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
    }

    fun observeTransactions(): Flow<List<Transaction>> =
        transactions.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun save(input: Input): SaveResult {
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
            val id = transactions.insert(
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
            )
            return SaveResult.Saved(id)
        }

        val existing = transactions.getById(input.id) ?: return SaveResult.TransactionNotFound
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

    suspend fun delete(id: Long) = transactions.deleteById(id)

    companion object {
        const val MAX_NOTE = 200
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
)
