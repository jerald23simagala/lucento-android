package com.lucento.app.data.sync

/**
 * Whether a cold launch should run a sync right away. Without it a second device only pulled new remote entries on
 * "Sync now" or the next periodic run (QA R6-11). Only for an account that is signed in with cloud sync on.
 */
internal fun shouldSyncOnLaunch(syncEnabled: Boolean, signedInUserId: String?): Boolean =
    syncEnabled && !signedInUserId.isNullOrEmpty()
