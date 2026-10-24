package com.lucento.app.data.repository

import com.lucento.app.core.money.Money
import com.lucento.app.data.local.GoalContributionEntity
import com.lucento.app.data.local.GoalContributionRow
import com.lucento.app.data.local.GoalDao
import com.lucento.app.data.local.GoalEntity
import com.lucento.app.domain.Goal
import com.lucento.app.domain.GoalContribution
import com.lucento.app.domain.GoalScene
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class GoalRepository(
    private val dao: GoalDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    sealed interface SaveResult {
        data class Saved(val id: Long) : SaveResult
        data object BlankName : SaveResult
        data object TargetNotPositive : SaveResult
        /** The target currency can't change once contributions exist. */
        data object CurrencyLocked : SaveResult
        data object NotFound : SaveResult
    }

    sealed interface ContributionResult {
        data object Saved : ContributionResult
        data object AmountZero : ContributionResult
        data object CurrencyMismatch : ContributionResult
        /** A withdrawal larger than what has been set aside. */
        data object ExceedsSaved : ContributionResult
        data object GoalNotFound : ContributionResult
    }

    fun observeGoals(): Flow<List<Goal>> = dao.observeActive().map { list -> list.map { it.toDomain() } }

    fun observeContributions(): Flow<List<GoalContribution>> =
        dao.observeContributions().map { list -> list.map { it.toDomain() } }

    /** [sceneKey] is the chosen card picture (`GoalScene.key`), or null for automatic; unknown keys are stored as null. */
    suspend fun save(
        id: Long?,
        name: String,
        target: Money,
        targetDate: LocalDate?,
        linkedWalletId: Long?,
        sceneKey: String? = null,
        photoPath: String? = null,
    ): SaveResult {
        val scene = GoalScene.fromKey(sceneKey)?.key
        val trimmed = name.trim().take(MAX_NAME)
        if (trimmed.isEmpty()) return SaveResult.BlankName
        if (target.amountMinor <= 0L) return SaveResult.TargetNotPositive
        val now = clock()
        if (id == null) {
            val newId = dao.insert(
                GoalEntity(
                    name = trimmed,
                    targetMinor = target.amountMinor,
                    currencyCode = target.currencyCode,
                    targetDate = targetDate?.toEpochDay(),
                    walletId = linkedWalletId,
                    createdAt = now,
                    updatedAt = now,
                    sceneKey = scene,
                    photoPath = photoPath,
                )
            )
            return SaveResult.Saved(newId)
        }
        val existing = dao.getById(id) ?: return SaveResult.NotFound
        if (existing.currencyCode != target.currencyCode) return SaveResult.CurrencyLocked
        dao.update(
            existing.copy(
                name = trimmed,
                targetMinor = target.amountMinor,
                targetDate = targetDate?.toEpochDay(),
                walletId = linkedWalletId,
                updatedAt = now,
                sceneKey = scene,
                photoPath = photoPath,
            )
        )
        return SaveResult.Saved(id)
    }

    /**
     * Earmarks [amount] for a goal (negative to take money back). Wallet balances are not changed.
     * Idempotent on [clientUuid].
     */
    suspend fun contribute(
        goalId: Long,
        amount: Money,
        date: LocalDate,
        note: String,
        clientUuid: String,
    ): ContributionResult {
        val goal = dao.getById(goalId) ?: return ContributionResult.GoalNotFound
        if (dao.findContributionByClientUuid(clientUuid) != null) return ContributionResult.Saved
        if (amount.isZero) return ContributionResult.AmountZero
        if (amount.currencyCode != goal.currencyCode) return ContributionResult.CurrencyMismatch
        val saved = Money(dao.savedMinor(goalId), goal.currencyCode)
        if (amount.isNegative && (saved + amount).isNegative) return ContributionResult.ExceedsSaved
        dao.insertContribution(
            GoalContributionEntity(
                goalId = goalId,
                amountMinor = amount.amountMinor,
                occurredOn = date.toEpochDay(),
                note = note.trim().take(TransactionRepository.MAX_NOTE),
                clientUuid = clientUuid,
                createdAt = clock(),
            )
        )
        return ContributionResult.Saved
    }

    suspend fun deleteContribution(id: Long) = dao.deleteContribution(id)

    /** Deletes a goal and its contribution history. Wallet balances are unaffected (contributions are earmarks). */
    suspend fun delete(id: Long) = dao.deleteById(id)

    /** Archiving keeps the goal and its history but removes it from the active list. */
    suspend fun archive(id: Long) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(archived = true, updatedAt = clock()))
    }

    companion object {
        const val MAX_NAME = 40
    }
}

internal fun GoalEntity.toDomain(): Goal = Goal(
    id = id,
    name = name,
    target = Money(targetMinor, currencyCode),
    targetDate = targetDate?.let(LocalDate::ofEpochDay),
    linkedWalletId = walletId,
    createdAtEpochMillis = createdAt,
    sceneKey = sceneKey,
    photoPath = photoPath,
)

internal fun GoalContributionRow.toDomain(): GoalContribution =
    GoalContribution(id, goalId, Money(amountMinor, currencyCode), LocalDate.ofEpochDay(occurredOn), note)
