package com.lucento.app.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.lucento.app.LucentoApplication
import java.util.concurrent.TimeUnit

/**
 * Runs [SyncRepository.sync] in the background (see docs/SYNC.md). Never scheduled unless the user has
 * turned cloud sync on; [SyncScheduler] is the only thing that enqueues or cancels it.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as LucentoApplication).container
        if (!container.syncPreferences.isEnabled()) return Result.success()
        return when (val result = container.syncRepository.sync()) {
            is SyncResult.Success -> Result.success(workDataOf(KEY_PUSHED to result.report.pushed, KEY_PULLED to result.report.pulled))
            // Nothing a retry could fix; Settings shows the state (and the stored error for an account mismatch).
            SyncResult.NotSignedIn, SyncResult.NotConfigured, SyncResult.AccountMismatch -> Result.success()
            is SyncResult.Retryable -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_PUSHED = "pushed"
        const val KEY_PULLED = "pulled"
        private const val MAX_ATTEMPTS = 5
    }
}

/** Enqueues/cancels [SyncWorker]; the only thing in the app that talks to WorkManager for cloud sync. */
object SyncScheduler {
    private const val PERIODIC_WORK_NAME = "lucento-cloud-sync-periodic"
    private const val MANUAL_WORK_NAME = "lucento-cloud-sync-manual"

    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Safe to call every app start: KEEP means an already-scheduled periodic sync is left alone. */
    fun ensurePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancelPeriodic(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
    }

    /**
     * "Sync now". KEEP so a second tap does not cancel the running worker and start another beside it.
     * The periodic worker uses a different unique name; [SyncRepository.sync] still admits only one cycle
     * at a time ([SyncFlight]), including after process death (the file lock is released by the OS).
     */
    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints).build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(MANUAL_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    /**
     * A sync that must run after new local rows were saved. [ExistingWorkPolicy.APPEND_OR_REPLACE] chains it
     * behind a sync that is already queued, instead of [ExistingWorkPolicy.KEEP] which would drop it and leave
     * those rows unsent.
     */
    fun enqueueAfterLocalWrite(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints).build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(MANUAL_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}
