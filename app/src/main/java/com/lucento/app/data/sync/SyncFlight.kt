package com.lucento.app.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException

/**
 * One sync at a time for the whole process, plus a file lock so a second process (or a sync that resumes after
 * this process dies) cannot run alongside it. The file lock is released by the OS if the process is killed, so it
 * cannot stick. The cursor is still saved only after a cycle finishes, so a killed cycle retries cleanly.
 *
 * The file lock is polled rather than waited on (owner, 2026-11-04): a blocking `lock()` can't be cancelled, so a lock
 * held elsewhere would have frozen the caller, its "Syncing" state and every later sync. After [LOCK_WAIT_MILLIS] the
 * caller gets [Busy] and the sync is retried later.
 */
internal object SyncFlight {
    private val mutex = Mutex()

    /** Set from [com.lucento.app.AppContainer]. Null in JVM tests, which then use only the in-process mutex. */
    var lockFile: File? = null

    /** Another process held the sync lock for longer than [LOCK_WAIT_MILLIS]. */
    class Busy : Exception("Another sync is still running")

    private const val LOCK_WAIT_MILLIS = 30_000L
    private const val LOCK_POLL_MILLIS = 200L

    suspend fun <T> exclusive(block: suspend () -> T): T = mutex.withLock {
        val file = lockFile ?: return@withLock block()
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            RandomAccessFile(file, "rw").use { raf ->
                val lock = acquire(raf)
                try {
                    block()
                } finally {
                    lock.release()
                }
            }
        }
    }

    private suspend fun acquire(raf: RandomAccessFile): FileLock {
        var waited = 0L
        while (true) {
            val lock = try {
                raf.channel.tryLock()
            } catch (e: OverlappingFileLockException) {
                null // held by this process through another channel; the mutex makes that a short wait
            }
            if (lock != null) return lock
            if (waited >= LOCK_WAIT_MILLIS) throw Busy()
            delay(LOCK_POLL_MILLIS)
            waited += LOCK_POLL_MILLIS
        }
    }
}
