package com.lucento.app.domain.backup

import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.DebtRepayment
import com.lucento.app.domain.Frequency
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.WalletType
import java.time.LocalDate

/**
 * A Lucento backup after it has been parsed and checked.
 * [exportedAt] is epoch milliseconds. Amounts elsewhere in the snapshot are integer minor units.
 */
data class BackupDocument(
    val schemaVersion: Int,
    val appVersion: String,
    val exportedAt: Long,
    val snapshot: BackupSnapshot,
)

/**
 * Everything a backup restores. [BackupGoal.photoPath] is filled only when reading this device;
 * the JSON file never carries it. Sync bookkeeping (dirty, synced_at, base_revision) is not part of
 * the snapshot: a restore marks rows dirty itself.
 */
data class BackupSnapshot(
    val wallets: List<BackupWallet> = emptyList(),
    val categories: List<BackupCategory> = emptyList(),
    val transactions: List<BackupTransaction> = emptyList(),
    val budgets: List<BackupBudget> = emptyList(),
    val goals: List<BackupGoal> = emptyList(),
    val goalContributions: List<BackupGoalContribution> = emptyList(),
    val recurring: List<BackupRecurring> = emptyList(),
    val debtTerms: List<BackupDebtTerms> = emptyList(),
    val deletions: List<BackupDeletion> = emptyList(),
    val settings: BackupSettings = BackupSettings(),
)

data class BackupWallet(
    val id: String,
    val name: String,
    val type: WalletType,
    val currency: String,
    val openingBalanceMinor: Long,
    val sortOrder: Int,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

data class BackupCategory(
    val id: String,
    val name: String,
    val kind: CategoryKind,
    val iconKey: String,
    val sortOrder: Int,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

data class BackupTransaction(
    val id: String,
    val type: TransactionType,
    val amountMinor: Long,
    val currency: String,
    val walletId: String,
    val categoryId: String?,
    val occurredOn: LocalDate,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    val transferGroup: String?,
)

data class BackupBudget(
    val id: String,
    val categoryId: String?,
    val currency: String,
    val limitMinor: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

data class BackupGoal(
    val id: String,
    val name: String,
    val targetMinor: Long,
    val currency: String,
    val targetDate: LocalDate?,
    val walletId: String?,
    val archived: Boolean,
    val sceneKey: String?,
    val createdAt: Long,
    val updatedAt: Long,
    /** This device's copy of a goal photo. Omitted from the file; a restore from JSON leaves it null. */
    val photoPath: String? = null,
)

data class BackupGoalContribution(
    val id: String,
    val goalId: String,
    val amountMinor: Long,
    val occurredOn: LocalDate,
    val note: String,
    val createdAt: Long,
)

data class BackupRecurring(
    val id: String,
    val name: String,
    val type: TransactionType,
    val amountMinor: Long,
    val currency: String,
    val walletId: String,
    val categoryId: String?,
    val frequency: Frequency,
    val intervalCount: Int,
    val startOn: LocalDate,
    val endOn: LocalDate?,
    val nextIndex: Int,
    val paused: Boolean,
    val toWalletId: String?,
    val interestMinor: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

data class BackupDebtTerms(
    val walletId: String,
    val interestRateBps: Int?,
    val repayment: DebtRepayment,
    val termMonths: Int?,
    val installmentMinor: Long?,
    val updatedAt: Long,
)

/** A row that was deleted, so a later sync can tell the server. [table] is a [BackupTables] name. */
data class BackupDeletion(
    val table: String,
    val id: String,
    val deletedAt: Long,
)

/**
 * Device preferences that belong in a backup. Crash reports, the one-time local-data notice, sync
 * cursors, account ids and sessions are not here.
 */
data class BackupSettings(
    val onboardingComplete: Boolean = false,
    val themeMode: String = "SYSTEM",
    val primaryCurrency: String = "USD",
    val enabledFeatures: List<String> = emptyList(),
    val acknowledgedBudgetMonth: String? = null,
    val displayName: String? = null,
    val categorySetupDone: Boolean = false,
    val defaultWalletId: String? = null,
    val guideStep: String? = null,
    val smartReading: Boolean = false,
)

enum class RestoreMode { MERGE, REPLACE }

data class BackupCounts(
    val wallets: Int,
    val categories: Int,
    val transactions: Int,
    val budgets: Int,
    val goals: Int,
    val goalContributions: Int,
    val recurring: Int,
    val debts: Int,
)

data class BackupPreview(
    val counts: BackupCounts,
    val earliestTransaction: LocalDate?,
    val latestTransaction: LocalDate?,
    val appVersion: String,
    val exportedAt: Long,
    val schemaVersion: Int,
) {
    /** Counts and the transaction date span, for the restore confirmation. */
    fun summary(): String = buildString {
        appendLine("${counts.wallets} ${noun(counts.wallets, "wallet", "wallets")}")
        appendLine("${counts.categories} ${noun(counts.categories, "category", "categories")}")
        appendLine("${counts.transactions} ${noun(counts.transactions, "transaction", "transactions")}")
        appendLine("${counts.budgets} ${noun(counts.budgets, "budget", "budgets")}")
        appendLine("${counts.goals} ${noun(counts.goals, "goal", "goals")}")
        appendLine("${counts.goalContributions} ${noun(counts.goalContributions, "goal contribution", "goal contributions")}")
        appendLine("${counts.recurring} scheduled")
        appendLine("${counts.debts} ${noun(counts.debts, "debt", "debts")}")
        val from = earliestTransaction
        val to = latestTransaction
        if (from != null && to != null) {
            append("Transactions from $from to $to.")
        } else {
            append("No transactions in this file.")
        }
    }

    private fun noun(count: Int, one: String, many: String): String = if (count == 1) one else many
}

object BackupTables {
    const val WALLETS = "wallets"
    const val CATEGORIES = "categories"
    const val TRANSACTIONS = "transactions"
    const val BUDGETS = "budgets"
    const val GOALS = "goals"
    const val GOAL_CONTRIBUTIONS = "goal_contributions"
    const val RECURRING = "recurring"

    val ALL = listOf(WALLETS, CATEGORIES, TRANSACTIONS, BUDGETS, GOALS, GOAL_CONTRIBUTIONS, RECURRING)
}

/**
 * How a restored row is marked so the next sync uploads it, including when the user is signed out
 * (the flag simply waits). [baseRevision] stays null so an old file is not treated as a causal
 * successor of whatever the server currently has; the later [updatedAt] wins that conflict.
 */
object RestoredSync {
    const val DIRTY = true
    val syncedAt: Long? = null
    val baseRevision: Long? = null
}

/** Reads and writes the snapshot. [replaceAll] is atomic: a failure leaves the previous snapshot in place. */
interface BackupStore {
    suspend fun read(): BackupSnapshot

    /**
     * Replaces the stored snapshot with [snapshot]. Every live row is marked with [RestoredSync].
     * Throws before the new snapshot becomes visible if the write cannot finish.
     */
    suspend fun replaceAll(snapshot: BackupSnapshot)
}
