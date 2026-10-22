package com.lucento.app.data.repository

import com.lucento.app.core.money.Money
import com.lucento.app.data.local.CategoryDao
import com.lucento.app.data.local.RecurringDao
import com.lucento.app.data.local.RecurringEntity
import com.lucento.app.data.local.WalletDao
import com.lucento.app.domain.Frequency
import com.lucento.app.domain.RecurringMath
import com.lucento.app.domain.RecurringSchedule
import com.lucento.app.domain.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Recurring schedules are plans. Nothing is posted automatically: [markPaid] records a real transaction
 * (keyed by schedule + occurrence, so it can never be recorded twice) and advances the schedule in one
 * atomic step; [skip] advances without recording anything.
 */
class RecurringRepository(
    private val dao: RecurringDao,
    private val wallets: WalletDao,
    private val categories: CategoryDao,
    private val transactions: TransactionRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val runner: TransactionRunner = TransactionRunner.Direct,
) {

    data class Input(
        val id: Long?,
        val name: String,
        val type: TransactionType,
        val amount: Money,
        val walletId: Long,
        val categoryId: Long?,
        val frequency: Frequency,
        val interval: Int,
        val startDate: LocalDate,
        val endDate: LocalDate?,
    )

    sealed interface SaveResult {
        data class Saved(val id: Long) : SaveResult
        data object BlankName : SaveResult
        data object AmountNotPositive : SaveResult
        data object InvalidType : SaveResult
        data object InvalidInterval : SaveResult
        data object EndBeforeStart : SaveResult
        data object WalletNotFound : SaveResult
        data object CurrencyMismatch : SaveResult
        data object CategoryMismatch : SaveResult
        data object NotFound : SaveResult
    }

    sealed interface PayResult {
        data class Recorded(val transactionId: Long) : PayResult
        data object NotDue : PayResult
        data object NotFound : PayResult
        data class Rejected(val reason: TransactionRepository.SaveResult) : PayResult
    }

    fun observeSchedules(): Flow<List<RecurringSchedule>> =
        dao.observeAll().map { list -> list.mapNotNull { it.toDomainOrNull() } }

    suspend fun save(input: Input): SaveResult {
        val name = input.name.trim().take(MAX_NAME)
        if (name.isEmpty()) return SaveResult.BlankName
        if (input.type.isTransfer) return SaveResult.InvalidType
        if (input.amount.amountMinor <= 0L) return SaveResult.AmountNotPositive
        if (input.interval !in 1..MAX_INTERVAL) return SaveResult.InvalidInterval
        if (input.endDate != null && input.endDate.isBefore(input.startDate)) return SaveResult.EndBeforeStart
        val wallet = wallets.getById(input.walletId)
        if (wallet == null || wallet.archived) return SaveResult.WalletNotFound
        if (wallet.currencyCode != input.amount.currencyCode) return SaveResult.CurrencyMismatch
        if (input.categoryId != null) {
            val category = categories.getById(input.categoryId) ?: return SaveResult.CategoryMismatch
            if (category.kind != input.type.name) return SaveResult.CategoryMismatch
        }
        val now = clock()
        if (input.id == null) {
            val id = dao.insert(
                RecurringEntity(
                    name = name,
                    type = input.type.name,
                    amountMinor = input.amount.amountMinor,
                    currencyCode = input.amount.currencyCode,
                    walletId = input.walletId,
                    categoryId = input.categoryId,
                    frequency = input.frequency.name,
                    intervalCount = input.interval,
                    startOn = input.startDate.toEpochDay(),
                    endOn = input.endDate?.toEpochDay(),
                    nextIndex = 0,
                    paused = false,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            return SaveResult.Saved(id)
        }
        val existing = dao.getById(input.id) ?: return SaveResult.NotFound
        // Changing the timing restarts the occurrence count from the new start date; already recorded
        // transactions stay as they are.
        val timingChanged = existing.startOn != input.startDate.toEpochDay() ||
            existing.frequency != input.frequency.name || existing.intervalCount != input.interval
        dao.update(
            existing.copy(
                name = name,
                type = input.type.name,
                amountMinor = input.amount.amountMinor,
                currencyCode = input.amount.currencyCode,
                walletId = input.walletId,
                categoryId = input.categoryId,
                frequency = input.frequency.name,
                intervalCount = input.interval,
                startOn = input.startDate.toEpochDay(),
                endOn = input.endDate?.toEpochDay(),
                nextIndex = if (timingChanged) 0 else existing.nextIndex,
                updatedAt = now,
            )
        )
        return SaveResult.Saved(input.id)
    }

    /**
     * Records the waiting occurrence as a real transaction dated [date] for [amount] (the scheduled
     * amount by default — bills vary), then advances the schedule. Safe to call twice.
     */
    suspend fun markPaid(id: Long, amount: Money? = null, date: LocalDate? = null): PayResult {
        val entity = dao.getById(id) ?: return PayResult.NotFound
        val schedule = entity.toDomainOrNull() ?: return PayResult.NotFound
        val index = schedule.nextIndex
        val due = schedule.nextDue ?: return PayResult.NotDue
        return runner.run {
            val result = transactions.save(
                TransactionRepository.Input(
                    id = null,
                    clientUuid = schedule.occurrenceKey(index),
                    type = schedule.type,
                    amount = amount ?: schedule.amount,
                    walletId = schedule.walletId,
                    categoryId = schedule.categoryId,
                    date = date ?: due,
                    note = schedule.name,
                )
            )
            if (result is TransactionRepository.SaveResult.Saved) {
                dao.update(entity.copy(nextIndex = maxOf(entity.nextIndex, index + 1), updatedAt = clock()))
                PayResult.Recorded(result.id)
            } else {
                PayResult.Rejected(result)
            }
        }
    }

    /** Skips the waiting occurrence without recording a transaction. */
    suspend fun skip(id: Long) {
        val entity = dao.getById(id) ?: return
        dao.update(entity.copy(nextIndex = entity.nextIndex + 1, updatedAt = clock()))
    }

    suspend fun setPaused(id: Long, paused: Boolean) {
        val entity = dao.getById(id) ?: return
        dao.update(entity.copy(paused = paused, updatedAt = clock()))
    }

    /** Deleting a schedule keeps every transaction it already recorded. */
    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun countForWallet(walletId: Long): Int = dao.countForWallet(walletId)

    companion object {
        const val MAX_NAME = 40
        const val MAX_INTERVAL = 52
    }
}

internal fun RecurringEntity.toDomainOrNull(): RecurringSchedule? = runCatching {
    RecurringSchedule(
        id = id,
        name = name,
        type = TransactionType.valueOf(type),
        amount = Money(amountMinor, currencyCode),
        walletId = walletId,
        categoryId = categoryId,
        frequency = Frequency.valueOf(frequency),
        interval = intervalCount,
        startDate = LocalDate.ofEpochDay(startOn),
        endDate = endOn?.let(LocalDate::ofEpochDay),
        nextIndex = nextIndex,
        paused = paused,
    )
}.getOrNull()

/** Exposed for UI previews of the next few dates. */
fun RecurringSchedule.upcomingDates(count: Int): List<LocalDate> =
    (nextIndex until nextIndex + count).mapNotNull { RecurringMath.occurrence(this, it) }
        .filter { endDate == null || !it.isAfter(endDate) }
