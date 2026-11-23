package com.lucento.app

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.lucento.app.core.crash.CrashReportsFlag
import com.lucento.app.data.preferences.ThemeId
import com.lucento.app.data.preferences.UserPreferencesRepository
import com.lucento.app.domain.entitlement.Capability
import com.lucento.app.domain.entitlement.Entitlement
import com.lucento.app.domain.entitlement.EntitlementSource
import com.lucento.app.domain.entitlement.EntitlementState
import com.lucento.app.domain.entitlement.EntitlementStatus
import com.lucento.app.domain.entitlement.ProGates
import com.lucento.app.domain.entitlement.Verifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The theme picker checks the plan at the tap, not only when the plan last changed (Pro-gate review 2026-11-23, PE-3). */
class AppViewModelThemeTest {

    @get:Rule val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val preferences by lazy {
        UserPreferencesRepository(
            PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "user_preferences.preferences_pb") },
            object : CrashReportsFlag.Store {
                override fun getBoolean(key: String, default: Boolean) = default
                override fun commit(edits: Map<String, Boolean>) = Unit
            },
        )
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() {
        // The DataStore first, while the test's Main dispatcher still runs the ViewModel collectors its closing wakes.
        scope.cancel()
        Dispatchers.resetMain()
    }

    /** Lucento Pro (on this phone) until [endsAt], the offline grace included. */
    private fun proUntil(endsAt: Long): EntitlementState {
        val expires = endsAt - Entitlement.OFFLINE_GRACE_MILLIS
        return EntitlementState(
            listOf(
                Entitlement(
                    source = EntitlementSource.GRANT, status = EntitlementStatus.ACTIVE, grants = setOf(Capability.LOCAL_PRO),
                    startsAtMillis = expires - 86_400_000L, expiresAtMillis = expires, productId = null,
                    verifiedAtMillis = expires - 86_400_000L, verifiedBy = Verifier.SERVER,
                ),
            ),
        )
    }

    private suspend fun themeBecomes(theme: ThemeId) = withTimeout(5_000) { preferences.preferences.first { it.themeId == theme } }

    @Test
    fun `a Pro theme is picked while the plan holds`() = runBlocking {
        val vm = AppViewModel(preferences, entitlements = MutableStateFlow(proUntil(System.currentTimeMillis() + 3_600_000L)))
        vm.setThemeId(ThemeId.AURORA)
        themeBecomes(ThemeId.AURORA)
        Unit
    }

    @Test
    fun `a plan that ends while Lucento stays open unlocks no Pro theme`() = runBlocking {
        val plan = proUntil(System.currentTimeMillis() + 3_000)
        val vm = AppViewModel(preferences, entitlements = MutableStateFlow(plan))
        assertTrue(ProGates.themeAllowed(ThemeId.AURORA.name, plan, System.currentTimeMillis()))
        assertEquals(emptySet<ThemeId>(), vm.lockedThemes.value)
        Thread.sleep(3_500)
        // No new plan has arrived, so the list worked out earlier still offers the Pro themes...
        assertEquals(emptySet<ThemeId>(), vm.lockedThemes.value)
        // ...but the tap checks the plan as it is now: nothing is written (a write would land within milliseconds).
        vm.setThemeId(ThemeId.AURORA)
        assertNull(withTimeoutOrNull(1_500) { preferences.preferences.first { it.themeId == ThemeId.AURORA } })
        assertEquals(ThemeId.SUNRISE, preferences.preferences.first().themeId)
        // A free theme is still picked.
        vm.setThemeId(ThemeId.MIDNIGHT)
        themeBecomes(ThemeId.MIDNIGHT)
        Unit
    }
}
