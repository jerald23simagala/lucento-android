package com.lucento.app.domain.backup

/**
 * Export, preview and restore. A file that fails to parse or validate throws [BackupFormatException]
 * before [BackupStore.replaceAll], so a bad file does not change stored data.
 */
class BackupEngine(
    private val store: BackupStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** JSON for the current snapshot. Goal photo paths are left out; the picture files stay on this device. */
    suspend fun export(appVersion: String): String {
        val snapshot = store.read().let { it.copy(goals = it.goals.map { goal -> goal.copy(photoPath = null) }) }
        return BackupCodec.encode(document(appVersion, snapshot))
    }

    fun preview(json: String): BackupPreview {
        val document = BackupCodec.decode(json)
        val dates = document.snapshot.transactions.map { it.occurredOn }
        val snapshot = document.snapshot
        return BackupPreview(
            counts = BackupCounts(
                wallets = snapshot.wallets.size,
                categories = snapshot.categories.size,
                transactions = snapshot.transactions.size,
                budgets = snapshot.budgets.size,
                goals = snapshot.goals.size,
                goalContributions = snapshot.goalContributions.size,
                recurring = snapshot.recurring.size,
                debts = snapshot.debtTerms.size,
            ),
            earliestTransaction = dates.minOrNull(),
            latestTransaction = dates.maxOrNull(),
            appVersion = document.appVersion,
            exportedAt = document.exportedAt,
            schemaVersion = document.schemaVersion,
        )
    }

    suspend fun restore(json: String, mode: RestoreMode) {
        val incoming = BackupCodec.decode(json).snapshot
        val local = store.read()
        val now = clock()
        val next = when (mode) {
            RestoreMode.MERGE -> BackupMerge.merge(local, incoming, now)
            RestoreMode.REPLACE -> BackupMerge.replace(local, incoming, now)
        }
        store.replaceAll(next)
    }

    private fun document(appVersion: String, snapshot: BackupSnapshot): BackupDocument = BackupDocument(
        schemaVersion = BackupCodec.SCHEMA_VERSION,
        appVersion = appVersion,
        exportedAt = clock(),
        snapshot = snapshot,
    )
}
