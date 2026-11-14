package com.lucento.app.feature.settings

/**
 * How many local changes have not reached the account yet: dirty rows plus deletions waiting to be pushed.
 * The dialog uses this before a sign-out wipe.
 */
fun unsyncedChangeCount(dirtyRows: Int, pendingTombstones: Int): Int =
    (dirtyRows.coerceAtLeast(0) + pendingTombstones.coerceAtLeast(0))

/**
 * The Cloud sync status line. "Synced just now" is only used when nothing is waiting to upload;
 * a recent sync time with a non-empty outbox still has work to send.
 */
fun syncStatusText(lastSyncedAtMillis: Long?, nowMillis: Long, pendingChanges: Int, lastError: String?): String {
    if (lastError != null) return "Last sync failed: $lastError"
    if (pendingChanges > 0) {
        return if (pendingChanges == 1) "1 change waiting to sync" else "$pendingChanges changes waiting to sync"
    }
    val last = lastSyncedAtMillis ?: return "Not synced yet"
    val minutesAgo = (nowMillis - last) / 60_000
    return when {
        minutesAgo < 1 -> "Synced just now"
        minutesAgo < 60 -> "Synced $minutesAgo min ago"
        minutesAgo < 24 * 60 -> "Synced ${minutesAgo / 60} h ago"
        else -> "Synced ${minutesAgo / (24 * 60)} d ago"
    }
}

// The sign-out dialog's own words (each case, with the count of changes it would lose) live in feature/account/SignOut.kt.
