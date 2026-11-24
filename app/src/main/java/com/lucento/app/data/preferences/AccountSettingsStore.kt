package com.lucento.app.data.preferences

import com.lucento.app.domain.settings.AccountSetting
import com.lucento.app.domain.settings.AccountSettingsMerge
import kotlinx.serialization.json.JsonObject

/**
 * The settings that follow the account, as cloud sync sees them (docs/SYNC.md "Settings"). An interface so the sync engine
 * runs against an in-memory copy in JVM tests; [UserPreferencesRepository] is the app's.
 */
interface AccountSettingsStore {
    /**
     * At the start of every sync cycle, before anything is read: the settings here belong to [account]. [claiming]: this
     * phone is taking the account for the first time since it was installed or signed out. Then the same person signing
     * back in keeps their settings and the changes still waiting; anyone else joins (their account's settings are used,
     * and what another account left here is never sent).
     */
    suspend fun bindAccountSettings(account: String, claiming: Boolean)

    /**
     * Merges the account's [document] (null: it has none yet) into this phone's settings, one setting at a time and in one
     * edit — a change saved meanwhile lands before it (and is weighed) or after it (and stays waiting) — and returns what to
     * send back. [account]: whose document it is. [settings]: what this phone may send and take. [walletUuid] and
     * [walletId] translate the default wallet between this phone's ids and the stable ones the document uses.
     * [lockedThemes]: theme keys this phone's plan may not pick (Lucento Pro); the account's choice of one isn't applied.
     * It has no default, so no caller can leave the plan out (Pro-gate review 2026-11-23, F1).
     */
    suspend fun mergeAccountSettings(
        account: String,
        document: JsonObject?,
        settings: Set<AccountSetting>,
        walletUuid: (Long) -> String?,
        walletId: (String) -> Long?,
        lockedThemes: Set<String>,
    ): AccountSettingsMerge.Result

    /** The account accepted [upload]: what it carried counts as synced, unless it changed here in the meantime. */
    suspend fun markAccountSettingsSent(upload: AccountSettingsMerge.Upload)
}
