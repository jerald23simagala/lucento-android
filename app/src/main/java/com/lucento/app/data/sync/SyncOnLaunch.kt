package com.lucento.app.data.sync

/**
 * Whether a cold launch should run a sync right away. Without it a second device only pulled new remote entries on
 * "Sync now" or the next periodic run (QA R6-11). Only for an account that is signed in with cloud sync on.
 */
internal fun shouldSyncOnLaunch(syncEnabled: Boolean, signedInUserId: String?): Boolean =
    syncEnabled && !signedInUserId.isNullOrEmpty()

/**
 * Whether background, launch or upload-after-write sync may run. Never while onboarding is unfinished (P0-2): pulling an
 * account into a half-finished setup let the setup's category choices remove the account's own categories. Onboarding's
 * restore syncs directly instead, and onboarding turns sync on itself when it finishes.
 */
internal fun backgroundSyncAllowed(syncEnabled: Boolean, onboardingComplete: Boolean): Boolean =
    syncEnabled && onboardingComplete
