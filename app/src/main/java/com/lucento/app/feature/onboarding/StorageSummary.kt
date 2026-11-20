package com.lucento.app.feature.onboarding

/**
 * The onboarding summary's line about where the person's records live. Signed in doesn't mean synced: cloud sync is part
 * of Lucento Pro (or of a Shared Space Owner's plan, docs/MONETIZATION.md §3.5), so a Free account keeps everything on
 * this phone, with uploads waiting for the plan (owner, 2026-11-19: the summary said "Synced with your account" anyway).
 *
 * [syncIncluded]: whether the signed-in account's plan includes cloud sync; null until that is known. [cloud] picks the
 * row's icon.
 */
internal data class StorageSummary(val title: String, val detail: String, val cloud: Boolean)

internal fun storageSummary(signedInEmail: String?, syncIncluded: Boolean?): StorageSummary {
    val who = signedInEmail?.ifEmpty { null }
    return when {
        signedInEmail == null -> StorageSummary("Kept on this phone", "Export a backup any time in Settings", cloud = false)
        syncIncluded == true -> StorageSummary("Synced with your account", who ?: "Connected", cloud = true)
        syncIncluded == false -> StorageSummary(
            "Kept on this phone",
            (if (who != null) "Signed in as $who. " else "Signed in. ") + "Cloud sync is part of Lucento Pro.",
            cloud = false,
        )
        else -> StorageSummary("Signed in", who ?: "Connected", cloud = false)
    }
}
