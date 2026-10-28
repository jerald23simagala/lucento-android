package com.lucento.app.domain.backup

/**
 * Merge keeps every id and, where both sides have it, the row with the later timestamp.
 * An equal timestamp keeps the row already on the device. Replace keeps the file and records
 * a deletion for every local id the file does not contain, so a later sync can remove it.
 */
object BackupMerge {
    fun merge(local: BackupSnapshot, incoming: BackupSnapshot, now: Long): BackupSnapshot {
        val deletions = combine(local.deletions, incoming.deletions)
        val wallets = drop(mergeRows(local.wallets, incoming.wallets, { it.id }, { it.updatedAt }), deletions, BackupTables.WALLETS, { it.id }, { it.updatedAt })
        val categories = drop(mergeRows(local.categories, incoming.categories, { it.id }, { it.updatedAt }), deletions, BackupTables.CATEGORIES, { it.id }, { it.updatedAt })
        val transactions = drop(mergeRows(local.transactions, incoming.transactions, { it.id }, { it.updatedAt }), deletions, BackupTables.TRANSACTIONS, { it.id }, { it.updatedAt })
        val budgets = drop(mergeRows(local.budgets, incoming.budgets, { it.id }, { it.updatedAt }), deletions, BackupTables.BUDGETS, { it.id }, { it.updatedAt })
        val goals = drop(mergeRows(local.goals, incoming.goals, { it.id }, { it.updatedAt }), deletions, BackupTables.GOALS, { it.id }, { it.updatedAt })
        val contributions = drop(mergeRows(local.goalContributions, incoming.goalContributions, { it.id }, { it.createdAt }), deletions, BackupTables.GOAL_CONTRIBUTIONS, { it.id }, { it.createdAt })
        val recurring = drop(mergeRows(local.recurring, incoming.recurring, { it.id }, { it.updatedAt }), deletions, BackupTables.RECURRING, { it.id }, { it.updatedAt })
        val debts = mergeRows(local.debtTerms, incoming.debtTerms, { it.walletId }, { it.updatedAt })
        return normalize(
            BackupSnapshot(
                wallets = wallets.rows,
                categories = categories.rows,
                transactions = transactions.rows,
                budgets = budgets.rows,
                goals = goals.rows,
                goalContributions = contributions.rows,
                recurring = recurring.rows,
                debtTerms = debts,
                deletions = wallets.deletions + categories.deletions + transactions.deletions +
                    budgets.deletions + goals.deletions + contributions.deletions + recurring.deletions,
                settings = local.settings,
            ),
            now,
        )
    }

    fun replace(local: BackupSnapshot, incoming: BackupSnapshot, now: Long): BackupSnapshot {
        val removed = ArrayList<BackupDeletion>()
        fun gone(table: String, id: String, incomingIds: Set<String>) {
            if (id !in incomingIds) removed.add(BackupDeletion(table, id, now))
        }
        val incomingWallets = incoming.wallets.map { it.id }.toSet()
        val incomingCategories = incoming.categories.map { it.id }.toSet()
        val incomingTransactions = incoming.transactions.map { it.id }.toSet()
        val incomingBudgets = incoming.budgets.map { it.id }.toSet()
        val incomingGoals = incoming.goals.map { it.id }.toSet()
        val incomingContributions = incoming.goalContributions.map { it.id }.toSet()
        val incomingRecurring = incoming.recurring.map { it.id }.toSet()
        local.wallets.forEach { gone(BackupTables.WALLETS, it.id, incomingWallets) }
        local.categories.forEach { gone(BackupTables.CATEGORIES, it.id, incomingCategories) }
        local.transactions.forEach { gone(BackupTables.TRANSACTIONS, it.id, incomingTransactions) }
        local.budgets.forEach { gone(BackupTables.BUDGETS, it.id, incomingBudgets) }
        local.goals.forEach { gone(BackupTables.GOALS, it.id, incomingGoals) }
        local.goalContributions.forEach { gone(BackupTables.GOAL_CONTRIBUTIONS, it.id, incomingContributions) }
        local.recurring.forEach { gone(BackupTables.RECURRING, it.id, incomingRecurring) }
        val stillDeleted = local.deletions.filter { deletion ->
            val live = when (deletion.table) {
                BackupTables.WALLETS -> incomingWallets
                BackupTables.CATEGORIES -> incomingCategories
                BackupTables.TRANSACTIONS -> incomingTransactions
                BackupTables.BUDGETS -> incomingBudgets
                BackupTables.GOALS -> incomingGoals
                BackupTables.GOAL_CONTRIBUTIONS -> incomingContributions
                BackupTables.RECURRING -> incomingRecurring
                else -> emptySet()
            }
            deletion.id !in live
        }
        return normalize(
            incoming.copy(
                goals = incoming.goals.map { it.copy(photoPath = null) },
                deletions = combine(incoming.deletions, removed + stillDeleted),
            ),
            now,
        )
    }

    /**
     * Drops rows whose parent was removed, matching the database's foreign keys, and records a
     * deletion for each dropped row so sync does not bring it back.
     */
    internal fun normalize(snapshot: BackupSnapshot, now: Long): BackupSnapshot {
        val walletIds = snapshot.wallets.map { it.id }.toSet()
        val categoryIds = snapshot.categories.map { it.id }.toSet()
        val goalIds = snapshot.goals.map { it.id }.toSet()
        val deletions = snapshot.deletions.toMutableList()
        fun add(table: String, id: String) {
            if (deletions.none { it.table == table && it.id == id }) deletions.add(BackupDeletion(table, id, now))
        }
        val transactions = snapshot.transactions.mapNotNull { row ->
            when {
                row.walletId !in walletIds -> {
                    add(BackupTables.TRANSACTIONS, row.id)
                    null
                }
                row.categoryId != null && row.categoryId !in categoryIds -> row.copy(categoryId = null)
                else -> row
            }
        }
        val budgets = snapshot.budgets.mapNotNull { row ->
            if (row.categoryId != null && row.categoryId !in categoryIds) {
                add(BackupTables.BUDGETS, row.id)
                null
            } else {
                row
            }
        }
        val goals = snapshot.goals.map { row ->
            if (row.walletId != null && row.walletId !in walletIds) row.copy(walletId = null) else row
        }
        val contributions = snapshot.goalContributions.mapNotNull { row ->
            if (row.goalId !in goalIds) {
                add(BackupTables.GOAL_CONTRIBUTIONS, row.id)
                null
            } else {
                row
            }
        }
        val recurring = snapshot.recurring.mapNotNull { row ->
            val categoryGone = row.categoryId != null && row.categoryId !in categoryIds
            val toGone = row.toWalletId != null && row.toWalletId !in walletIds
            when {
                row.walletId !in walletIds || toGone -> {
                    add(BackupTables.RECURRING, row.id)
                    null
                }
                categoryGone -> row.copy(categoryId = null)
                else -> row
            }
        }
        val debts = snapshot.debtTerms.filter { debt ->
            val wallet = snapshot.wallets.firstOrNull { it.id == debt.walletId }
            wallet != null && wallet.type.isLiability
        }
        val settings = snapshot.settings.let { settings ->
            if (settings.defaultWalletId != null && settings.defaultWalletId !in walletIds) {
                settings.copy(defaultWalletId = null)
            } else {
                settings
            }
        }
        return snapshot.copy(
            transactions = transactions,
            budgets = budgets,
            goals = goals,
            goalContributions = contributions,
            recurring = recurring,
            debtTerms = debts,
            deletions = deletions,
            settings = settings,
        ).ordered()
    }
}

private data class Kept<T>(val rows: List<T>, val deletions: List<BackupDeletion>)

private fun <T> mergeRows(local: List<T>, incoming: List<T>, id: (T) -> String, time: (T) -> Long): List<T> {
    val localById = local.associateBy(id)
    val seen = HashSet<String>()
    val merged = ArrayList<T>(local.size + incoming.size)
    for (row in incoming) {
        val key = id(row)
        val previous = localById[key]
        merged.add(if (previous == null || time(row) > time(previous)) row else previous)
        seen.add(key)
    }
    for (row in local) {
        if (id(row) !in seen) merged.add(row)
    }
    return merged
}

/** A row stays when its timestamp is at least the deletion's. The survivor's opposite record is dropped. */
private fun <T> drop(
    rows: List<T>,
    deletions: List<BackupDeletion>,
    table: String,
    id: (T) -> String,
    time: (T) -> Long,
): Kept<T> {
    val relevant = deletions.filter { it.table == table }.associateBy { it.id }
    val kept = rows.filter { row ->
        val deletion = relevant[id(row)]
        deletion == null || time(row) >= deletion.deletedAt
    }
    val keptIds = kept.map(id).toSet()
    val keptDeletions = deletions.filter { it.table == table && it.id !in keptIds }
    return Kept(kept, keptDeletions)
}

private fun combine(first: List<BackupDeletion>, second: List<BackupDeletion>): List<BackupDeletion> {
    val byKey = LinkedHashMap<String, BackupDeletion>()
    for (deletion in first + second) {
        val key = deletion.table + ":" + deletion.id
        val existing = byKey[key]
        if (existing == null || deletion.deletedAt > existing.deletedAt) byKey[key] = deletion
    }
    return byKey.values.toList()
}

private fun BackupSnapshot.ordered(): BackupSnapshot = copy(
    wallets = wallets.sortedBy { it.id },
    categories = categories.sortedBy { it.id },
    transactions = transactions.sortedWith(compareBy({ it.occurredOn }, { it.id })),
    budgets = budgets.sortedBy { it.id },
    goals = goals.sortedBy { it.id },
    goalContributions = goalContributions.sortedWith(compareBy({ it.occurredOn }, { it.id })),
    recurring = recurring.sortedBy { it.id },
    debtTerms = debtTerms.sortedBy { it.walletId },
    deletions = deletions.sortedWith(compareBy({ it.table }, { it.id })),
)
