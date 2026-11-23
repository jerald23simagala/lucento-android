package com.lucento.app.feature.settings

import com.lucento.app.data.sync.AccountProbe

/** What Settings does before this phone's first sync with a signed-in account (P0-2, scenario C). */
enum class FirstSync {
    /** Sync now: the phone is already bound to this account, or one of the two sides is empty. */
    SYNC,
    /** Both this phone and the account hold records: ask before joining them (both are kept). */
    ASK_MERGE,
    /** The account couldn't be checked: upload nothing and drop the new session. */
    DISCONNECT,
}

/**
 * The single rule, used by sign-in and by "Sync now", so a phone with records of its own never uploads them into an
 * account that has data without the user agreeing. [alreadyBound]: this phone has synced with the account before.
 */
internal fun firstSyncDecision(alreadyBound: Boolean, probe: AccountProbe?, localHasData: Boolean): FirstSync = when {
    alreadyBound -> FirstSync.SYNC
    probe is AccountProbe.Unavailable -> FirstSync.DISCONNECT
    probe is AccountProbe.HasData && localHasData -> FirstSync.ASK_MERGE
    else -> FirstSync.SYNC
}

/** What Settings' "Sync now" does. */
enum class SyncNowStep {
    /** The plan doesn't include cloud sync here: say so and sync nothing. */
    PLAN_LOCKED,
    /** This phone never synced with the signed-in account: the first-sync rule ([firstSyncDecision]) comes first. */
    FIRST_SYNC_CHECK,
    SYNC,
}

/**
 * The plan comes first. The screen shows the plan's wall instead of calling Sync now, and the rule holds here too, so no
 * path syncs without the plan: a sync would also download the account's records, which is restore, a Pro feature
 * (docs/MONETIZATION.md §2 principle 5; Pro-gate review 2026-11-23, PE-4). [planAllows]: ProGates.syncAllowedByPlan,
 * a Partner's phone on the Owner's plan included.
 */
internal fun syncNowStep(planAllows: Boolean, neverSyncedHere: Boolean, signedIn: Boolean): SyncNowStep = when {
    !planAllows -> SyncNowStep.PLAN_LOCKED
    neverSyncedHere && signedIn -> SyncNowStep.FIRST_SYNC_CHECK
    else -> SyncNowStep.SYNC
}

/**
 * After an account switch wiped the previous ledger, Settings syncs the new account only if this phone is still set up.
 * The wipe sends a phone back to onboarding (F-7), and onboarding then checks the account and restores or sets it up.
 */
internal fun syncAfterAccountWipe(onboardingComplete: Boolean): Boolean = onboardingComplete
