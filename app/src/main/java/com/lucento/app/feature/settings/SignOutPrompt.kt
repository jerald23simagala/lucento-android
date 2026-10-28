package com.lucento.app.feature.settings

/**
 * How many local changes have not reached the account yet: dirty rows plus deletions waiting to be pushed.
 * The dialog uses this before a sign-out wipe.
 */
fun unsyncedChangeCount(dirtyRows: Int, pendingTombstones: Int): Int =
    (dirtyRows.coerceAtLeast(0) + pendingTombstones.coerceAtLeast(0))

/** Plain sentence for a sign-out that would delete unsynced work. Null when nothing would be lost. */
fun signOutUnsyncedMessage(count: Int): String? = when {
    count <= 0 -> null
    count == 1 -> "1 change hasn’t synced and will be deleted from this phone."
    else -> "$count changes haven’t synced and will be deleted from this phone."
}
