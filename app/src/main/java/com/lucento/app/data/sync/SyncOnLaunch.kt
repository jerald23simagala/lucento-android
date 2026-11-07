package com.lucento.app.data.sync

/**
 * Whether a cold launch should run a sync right away. Without it a second device only pulled new remote entries on
 * "Sync now" or the next periodic run (QA R6-11). Only for an account that is signed in with cloud sync on.
 */
internal fun shouldSyncOnLaunch(syncEnabled: Boolean, signedInUserId: String?, planAllows: Boolean = true): Boolean =
    syncEnabled && !signedInUserId.isNullOrEmpty() && planAllows

/**
 * Whether background, launch or upload-after-write sync may run. Never while onboarding is unfinished (P0-2): pulling an
 * account into a half-finished setup let the setup's category choices remove the account's own categories. Onboarding's
 * restore syncs directly instead, and onboarding turns sync on itself when it finishes.
 */
/**
 * [planAllows] (Lucento Pro, docs/MONETIZATION.md §3.5, §6.4): cloud sync is part of Pro, or of a Shared Space's Owner's plan
 * for its Partner ([com.lucento.app.domain.entitlement.ProGates.syncAllowedByPlan]). When the plan lapses, sync *pauses*:
 * nothing on the phone or the server is deleted, dirty rows wait in the queue, and the next allowed run resumes.
 */
internal fun backgroundSyncAllowed(syncEnabled: Boolean, onboardingComplete: Boolean, planAllows: Boolean = true): Boolean =
    syncEnabled && onboardingComplete && planAllows
