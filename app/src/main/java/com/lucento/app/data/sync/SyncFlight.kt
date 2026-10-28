package com.lucento.app.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/**
 * One sync at a time for the whole process, plus a file lock so a second process (or a sync that resumes after
 * this process dies) cannot run alongside it. The file lock is released by the OS if the process is killed, so it
 * cannot stick. The cursor is still saved only after a cycle finishes, so a killed cycle retries cleanly.
 */
internal object SyncFlight {
    private val mutex = Mutex()

    /** Set from [com.lucento.app.AppContainer]. Null in JVM tests, which then use only the in-process mutex. */
    var lockFile: File? = null

    suspend fun <T> exclusive(block: suspend () -> T): T = mutex.withLock {
        val file = lockFile ?: return@withLock block()
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            RandomAccessFile(file, "rw").use { raf ->
                val lock = raf.channel.lock()
                try {
                    block()
                } finally {
                    lock.release()
                }
            }
        }
    }
}
