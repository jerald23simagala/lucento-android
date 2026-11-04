package com.lucento.app.data.sync

import com.lucento.app.data.repository.FakeBudgetDao
import com.lucento.app.data.repository.FakeCategoryDao
import com.lucento.app.data.repository.FakeGoalDao
import com.lucento.app.data.repository.FakeSyncDao
import com.lucento.app.data.repository.FakeTransactionDao
import com.lucento.app.data.repository.FakeWalletDao
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner, 2026-11-04: "the sync continues spinning but no update". A cycle that never ends must not leave "Syncing" on,
 * nor block the next sync: it stops at the cap, is stored as a retryable failure, and the next sync runs.
 */
class SyncNeverHangsTest {

    private fun repository(remote: SyncRemote, preferences: FakeSyncPreferences) = SyncRepository(
        remote = remote,
        wallets = FakeWalletDao(),
        categories = FakeCategoryDao(),
        transactions = FakeTransactionDao(),
        budgets = FakeBudgetDao(),
        goals = FakeGoalDao(),
        syncMeta = FakeSyncDao(),
        preferences = preferences,
    )

    @Test fun `a cycle that never ends is stopped, reported and frees the next sync`() = runTest {
        val server = FakeSyncServer()
        var hang = true
        val remote = object : SyncRemote by server.remote("user-1") {
            override suspend fun currentUserId(): String? {
                if (hang) awaitCancellation()
                return "user-1"
            }
        }
        val preferences = FakeSyncPreferences()
        val repo = repository(remote, preferences)

        val result = repo.sync()

        assertTrue(result is SyncResult.Retryable)
        assertEquals("Sync took too long and stopped", (result as SyncResult.Retryable).message)
        assertFalse("never left spinning", repo.running.value)

        // The lock is free: the next sync runs to the end.
        hang = false
        assertTrue(repo.sync() is SyncResult.Success)
        assertFalse(repo.running.value)
    }

    @Test fun `waiting for the stored sign-in is reported in words`() {
        assertEquals("Couldn't confirm your sign-in", safeFailureDescription(AuthNotReady()))
        assertEquals("Another sync is still running", safeFailureDescription(SyncFlight.Busy()))
    }

    @Test fun `foreground requests during a sync collapse into one more run, and opening the app skips a fresh sync`() = runTest {
        var runs = 0
        var upToDate = false
        val foreground = ForegroundSync(
            scope = this,
            allowed = { true },
            upToDate = { upToDate },
            sync = {
                runs += 1
                delay(1_000)
            },
        )
        foreground.request(forced = true)
        delay(10)
        // Three changes saved while the first sync runs: one more sync after it, not three.
        repeat(3) { foreground.request(forced = true) }
        delay(5_000)
        assertEquals(2, runs)

        // Reopening the app right after a complete sync doesn't sync again; a change to send always does.
        upToDate = true
        foreground.request(forced = false)
        delay(5_000)
        assertEquals(2, runs)
        foreground.request(forced = true)
        delay(5_000)
        assertEquals(3, runs)
        coroutineContext.cancelChildren()
    }

    @Test fun `nothing runs while sync is off or onboarding is unfinished`() = runTest {
        var runs = 0
        val foreground = ForegroundSync(scope = this, allowed = { false }, upToDate = { false }, sync = { runs += 1 })
        foreground.request(forced = true)
        delay(1_000)
        assertEquals(0, runs)
        coroutineContext.cancelChildren()
    }
}

