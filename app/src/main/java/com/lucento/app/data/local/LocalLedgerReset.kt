package com.lucento.app.data.local

import com.lucento.app.data.media.GoalPhotos
import com.lucento.app.data.sync.SyncPreferences

/**
 * Removes this phone's financial data and the sync binding so the next account starts empty.
 * Income categories are seeded again, the same way a fresh install does. Theme and other device
 * preferences are left alone.
 */
class LocalLedgerReset(
    private val database: LucentoDatabase,
    private val syncPreferences: SyncPreferences,
    private val goalPhotos: GoalPhotos,
) {
    suspend fun wipe() {
        database.clearAllTables()
        CategorySeed.insertFreshInstall(database.openHelper.writableDatabase, System.currentTimeMillis())
        goalPhotos.deleteAll()
        syncPreferences.clearBinding()
    }
}
