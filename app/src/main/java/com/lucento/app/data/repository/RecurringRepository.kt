package com.lucento.app.data.repository

import com.lucento.app.core.money.Money
import com.lucento.app.data.local.CategoryDao
import com.lucento.app.data.local.RecurringDao
import com.lucento.app.data.local.RecurringEntity
import com.lucento.app.data.local.WalletDao
import com.lucento.app.data.local.acceptsTransactions
import com.lucento.app.domain.DebtPaymentLink
import com.lucento.app.domain.Frequency
import com.lucento.app.domain.RecurringMath
import com.lucento.app.domain.RecurringSchedule
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.WalletType
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
        /** A debt payment: the card or loan paid from [walletId]. [amount] is then the part against the debt. */
        val toWalletId: Long? = null,
        /** The interest part of a debt payment (user input; zero when none). */
        val interest: Money? = null,
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
        /** A debt payment must go from a money account to a card or loan in the same currency, with interest >= 0. */
        data object InvalidDebt : SaveResult
        data object NotFound : SaveResult
    }

    sealed interface PayResult {
        /** Recorded; [transactionId] is the expense or income row (null for a debt payment, which writes several). */
        data class Recorded(val transactionId: Long?) : PayResult
        data object NotDue : PayResult
        data object NotFound : PayResult
        /** Nothing was recorded: a [TransactionRepository.SaveResult] or [TransactionRepository.TransferResult] refusal. */
        data class Rejected(val reason: Any) : PayResult
    }

    /** Thrown inside the database transaction to undo a half-recorded debt payment. */
    private class Rollback(val result: PayResult) : RuntimeException()

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
        if (wallet == null || !wallet.acceptsTransactions) return SaveResult.WalletNotFound
        if (wallet.currencyCode != input.amount.currencyCode) return SaveResult.CurrencyMismatch
        if (input.categoryId != null) {
            val category = categories.getById(input.categoryId) ?: return SaveResult.CategoryMismatch
            if (category.kind != input.type.name) return SaveResult.CategoryMismatch
        }
        val interest = input.interest ?: Money.zero(input.amount.currencyCode)
        if (input.toWalletId != null) {
            val debt = wallets.getById(input.toWalletId)
            val isDebt = debt != null && debt.acceptsTransactions &&
                runCatching { WalletType.valueOf(debt.type) }.getOrNull()?.isLiability == true
            if (!isDebt || input.type != TransactionType.EXPENSE || debt!!.id == wallet.id || debt.currencyCode != wallet.currencyCode ||
                interest.isNegative || interest.currencyCode != input.amount.currencyCode
            ) {
                return SaveResult.InvalidDebt
            }
        } else if (!interest.isZero) {
            return SaveResult.InvalidDebt
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
                    toWalletId = input.toWalletId,
                    interestMinor = interest.amountMinor,
                    clientUuid = java.util.UUID.randomUUID().toString(),
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
                toWalletId = input.toWalletId,
                interestMinor = interest.amountMinor,
            )
        )
        return SaveResult.Saved(input.id)
    }

    /**
     * Records the waiting occurrence as a real transaction dated [date] for [amount] (the scheduled
     * amount by default — bills vary), then advances the schedule. Safe to call twice.
     */
    suspend fun markPaid(id: Long, amount: Money? = null, date: LocalDate? = null, interest: Money? = null): PayResult {
        val entity = dao.getById(id) ?: return PayResult.NotFound
        val schedule = entity.toDomainOrNull() ?: return PayResult.NotFound
        val index = schedule.nextIndex
        val due = schedule.nextDue ?: return PayResult.NotDue
        if (schedule.isDebtPayment) {
            return markDebtPaid(entity, schedule, index, amount ?: schedule.amount, interest ?: schedule.interest, date ?: due)
        }
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

    /**
     * A debt payment, recorded the way docs/CALCULATIONS.md "Debt rules" counts debt: the interest as an EXPENSE on the
     * card or loan (it is spending, and raises what is owed), then a TRANSFER of principal + interest from the paying
     * wallet into it (not spending, and lowers what is owed). Net effect on the debt: minus the principal. Both rows
     * and the schedule's advance are one database transaction; each row is keyed by the occurrence, so a second tap
     * can't double it.
     */
    private suspend fun markDebtPaid(
        entity: RecurringEntity,
        schedule: RecurringSchedule,
        index: Int,
        principal: Money,
        interest: Money,
        date: LocalDate,
    ): PayResult {
        val debtId = schedule.toWalletId ?: return PayResult.NotFound
        if (principal.isNegative || interest.isNegative) return PayResult.Rejected(TransactionRepository.SaveResult.AmountNotPositive)
        val key = schedule.occurrenceKey(index)
        return try {
            runner.run {
                if (interest.amountMinor > 0) {
                    val saved = transactions.save(
                        TransactionRepository.Input(
                            id = null,
                            clientUuid = DebtPaymentLink.interestKey(key),
                            type = TransactionType.EXPENSE,
                            amount = interest,
                            walletId = debtId,
                            categoryId = schedule.categoryId,
                            date = date,
                            note = "${schedule.name} · interest",
                        )
                    )
                    if (saved !is TransactionRepository.SaveResult.Saved) throw Rollback(PayResult.Rejected(saved))
                }
                val moved = transactions.saveTransfer(
                    TransactionRepository.TransferInput(
                        group = null,
                        clientUuid = key,
                        fromWalletId = schedule.walletId,
                        toWalletId = debtId,
                        amount = principal + interest,
                        date = date,
                        note = schedule.name,
                    )
                )
                if (moved !is TransactionRepository.TransferResult.Saved) throw Rollback(PayResult.Rejected(moved))
                dao.update(entity.copy(nextIndex = maxOf(entity.nextIndex, index + 1), updatedAt = clock()))
                PayResult.Recorded(null)
            }
        } catch (e: Rollback) {
            e.result
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
        toWalletId = toWalletId,
        interest = Money(interestMinor, currencyCode),
        clientUuid = clientUuid,
    )
}.getOrNull()

/** Exposed for UI previews of the next few dates. */
fun RecurringSchedule.upcomingDates(count: Int): List<LocalDate> =
    (nextIndex until nextIndex + count).mapNotNull { RecurringMath.occurrence(this, it) }
        .filter { endDate == null || !it.isAfter(endDate) }
