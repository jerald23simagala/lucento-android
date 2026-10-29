package com.lucento.app.data.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalWriteUploadsTest {
    private fun run(block: suspend (MutableSharedFlow<Int>, () -> Int, suspend (Long) -> Unit) -> Unit) = runTest {
        val pending = MutableSharedFlow<Int>(replay = 0, extraBufferCapacity = 16)
        val out = mutableListOf<Unit>()
        val job = launch { uploadRequests(pending, settleMillis = 1_000).toList(out) }
        runCurrent()
        block(pending, { out.size }) { advanceTimeBy(it); runCurrent() }
        job.cancel()
    }

    @Test fun anInsertSchedulesAnUpload() = run { pending, count, wait ->
        pending.emit(0); pending.emit(1); wait(1_100)
        assertEquals(1, count())
    }

    @Test fun aBurstOfWritesSchedulesOneUpload() = run { pending, count, wait ->
        pending.emit(0); pending.emit(1); wait(200); pending.emit(2); wait(200); pending.emit(3); wait(1_100)
        assertEquals(1, count())
    }

    @Test fun theOutboxDrainingDoesNotScheduleAnother() = run { pending, count, wait ->
        pending.emit(2); wait(1_100); pending.emit(0); wait(1_100)
        assertEquals(1, count())
    }

    @Test fun nothingPendingSchedulesNothing() = run { pending, count, wait ->
        pending.emit(0); wait(2_000)
        assertEquals(0, count())
    }
}
