package com.lucento.app.data.sync

import com.lucento.app.data.repository.FakeBudgetDao
import com.lucento.app.data.repository.FakeCategoryDao
import com.lucento.app.data.repository.FakeGoalDao
import com.lucento.app.data.repository.FakeSyncDao
import com.lucento.app.data.repository.FakeTransactionDao
import com.lucento.app.data.repository.FakeWalletDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [SyncRepository] without a configured Supabase project (the state of a fresh checkout — see
 * docs/SYNC.md "Setup required"). Pushing network calls through a real or fake Postgrest/Auth client is out
 * of scope for a JVM test; what matters here, and what regresses easily, is that an unconfigured or
 * signed-out app never throws and never touches a DAO.
 */
class SyncRepositoryTest {

    private fun newRepository(preferences: SyncPreferencesStore): SyncRepository = SyncRepository(
        remote = null,
        wallets = FakeWalletDao(),
        categories = FakeCategoryDao(),
        transactions = FakeTransactionDao(),
        budgets = FakeBudgetDao(),
        goals = FakeGoalDao(),
        syncMeta = FakeSyncDao(),
        preferences = preferences,
    )

    @Test
    fun `an unconfigured project reports NotConfigured and reads no preference state`() = runTest {
        val repository = newRepository(preferences = FakeSyncPreferences())
        val result = repository.sync()
        assertEquals(SyncResult.NotConfigured, result)
    }
}
