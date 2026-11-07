package com.lucento.app.data.repository

import com.lucento.app.core.money.Money
import com.lucento.app.data.local.GoalContributionEntity
import com.lucento.app.data.local.GoalContributionRow
import com.lucento.app.data.local.GoalDao
import com.lucento.app.data.local.GoalEntity
import com.lucento.app.domain.Goal
import com.lucento.app.domain.GoalContribution
import com.lucento.app.domain.GoalIcon
import com.lucento.app.domain.entitlement.Access
import com.lucento.app.domain.entitlement.Limit
import com.lucento.app.domain.entitlement.ProFeature
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID

class GoalRepository(
    private val dao: GoalDao,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Lucento Shared: false on a Partner's phone, where goals are the Owner's to change ([StructureAccess]). */
    private val access: StructureAccess = StructureAccess.Always,
    /** Lucento Pro: whether the plan allows one more active goal ([EntitlementGate]). Archived goals never count. */
    private val gate: EntitlementGate = EntitlementGate.Permissive,
) {

    sealed interface SaveResult {
        data class Saved(val id: Long) : SaveResult
        /** A Partner's phone: goals are changed by the Shared Space's Owner. Nothing was written. */
        data object NotPermitted : SaveResult
        data object BlankName : SaveResult
        data object TargetNotPositive : SaveResult
        /** The target currency can't change once contributions exist. */
        data object CurrencyLocked : SaveResult
        data object NotFound : SaveResult
        /** Lucento Pro: the plan allows [limit] active goals and [used] exist. Nothing was written; existing goals are untouched. */
        data class LimitReached(val used: Int, val limit: Limit) : SaveResult
    }

    sealed interface RestoreResult {
        data object Restored : RestoreResult
        data object NotPermitted : RestoreResult
        data object NotFound : RestoreResult
        /** Lucento Pro: un-archiving would exceed the plan's active goals; the goal stays archived, history intact. */
        data class LimitReached(val used: Int, val limit: Limit) : RestoreResult
    }

    sealed interface ContributionResult {
        data object Saved : ContributionResult
        /** A Partner's phone: goals are changed by the Shared Space's Owner. Nothing was written. */
        data object NotPermitted : ContributionResult
        data object AmountZero : ContributionResult
        data object CurrencyMismatch : ContributionResult
        /** A withdrawal larger than what has been set aside. */
        data object ExceedsSaved : ContributionResult
        /** The goal is gone, or archived (restore it to add or take out money). */
        data object GoalNotFound : ContributionResult
    }

    sealed interface ArchiveResult {
        data object Archived : ArchiveResult
        /** A Partner's phone: goals are changed by the Shared Space's Owner. Nothing changed. */
        data object NotPermitted : ArchiveResult
        /** Only a goal that has reached its target can be archived. */
        data object NotReached : ArchiveResult
        data object NotFound : ArchiveResult
    }

    fun observeGoals(): Flow<List<Goal>> = dao.observeActive().map { list -> list.map { it.toDomain() } }

    /** Archived goals, most recently archived first. They keep their history but count nowhere else. */
    fun observeArchivedGoals(): Flow<List<Goal>> = dao.observeArchived().map { list -> list.map { it.toDomain() } }

    fun observeContributions(): Flow<List<GoalContribution>> =
        dao.observeContributions().map { list -> list.map { it.toDomain() } }

    /** [sceneKey] is the chosen icon (`GoalIcon.key`, or an older `GoalScene.key`), or null for automatic; unknown keys are stored as null. */
    suspend fun save(
        id: Long?,
        name: String,
        target: Money,
        targetDate: LocalDate?,
        linkedWalletId: Long?,
        sceneKey: String? = null,
        photoPath: String? = null,
    ): SaveResult {
        if (!access.canManageStructure()) return SaveResult.NotPermitted
        val scene = GoalIcon.normalizeKey(sceneKey)
        val trimmed = name.trim().take(MAX_NAME)
        if (trimmed.isEmpty()) return SaveResult.BlankName
        if (target.amountMinor <= 0L) return SaveResult.TargetNotPositive
        val now = clock()
        if (id == null) {
            activeGoalsGate()?.let { return SaveResult.LimitReached(it.used, it.limit) }
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
                    clientUuid = UUID.randomUUID().toString(),
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
        if (!access.canManageStructure()) return ContributionResult.NotPermitted
        val goal = dao.getById(goalId)?.takeUnless { it.archived } ?: return ContributionResult.GoalNotFound
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

    suspend fun deleteContribution(id: Long) {
        if (access.canManageStructure()) dao.deleteContribution(id)
    }

    /** Deletes a goal and its contribution history. Wallet balances are unaffected (contributions are earmarks). */
    suspend fun delete(id: Long) {
        if (access.canManageStructure()) dao.deleteById(id)
    }

    /**
     * Archives a goal that has reached its target: it keeps its history (and syncs) but leaves the active list, the
     * Goals totals and Home's savings card. Archiving an archived goal again is a no-op.
     */
    suspend fun archive(id: Long): ArchiveResult {
        if (!access.canManageStructure()) return ArchiveResult.NotPermitted
        val existing = dao.getById(id) ?: return ArchiveResult.NotFound
        if (dao.savedMinor(id) < existing.targetMinor) return ArchiveResult.NotReached
        if (!existing.archived) dao.update(existing.copy(archived = true, updatedAt = clock()))
        return ArchiveResult.Archived
    }

    /** Brings an archived goal back to the active list, history intact. Counts against the plan's active goals. */
    suspend fun restore(id: Long): RestoreResult {
        if (!access.canManageStructure()) return RestoreResult.NotPermitted
        val existing = dao.getById(id) ?: return RestoreResult.NotFound
        if (!existing.archived) return RestoreResult.Restored
        activeGoalsGate()?.let { return RestoreResult.LimitReached(it.used, it.limit) }
        dao.update(existing.copy(archived = false, updatedAt = clock()))
        return RestoreResult.Restored
    }

    /** Lucento Pro (docs/MONETIZATION.md §3.3): one more active goal, or the limit that stops it. */
    private suspend fun activeGoalsGate(): Access.LimitReached? {
        val used = dao.observeActive().first().size
        return when (val access = gate.check(ProFeature.SAVINGS_GOALS, used)) {
            Access.Allowed -> null
            is Access.LimitReached -> access
            // Goals are a count on every plan, never locked outright; an unexpected lock reads as the count being full.
            is Access.Locked -> Access.LimitReached(access.feature, used, Limit.None, Limit.None)
        }
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
