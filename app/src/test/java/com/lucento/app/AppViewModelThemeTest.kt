package com.lucento.app

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.lucento.app.core.crash.CrashReportsFlag
import com.lucento.app.data.preferences.ThemeId
import com.lucento.app.data.preferences.UserPreferencesRepository
import com.lucento.app.domain.entitlement.Capability
import com.lucento.app.domain.entitlement.Entitlement
import com.lucento.app.domain.entitlement.EntitlementSource
import com.lucento.app.domain.entitlement.EntitlementState
import com.lucento.app.domain.entitlement.EntitlementStatus
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
import org.junit.After
import org.junit.Assert.assertEquals
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

    /** Owns this test's ViewModels, so their coroutines end with the test. */
    private val viewModels = ViewModelStore()
    private var now = 1_800_000_000_000L

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() {
        viewModels.clear()
        scope.cancel()
        Dispatchers.resetMain()
    }

    private fun viewModel(plan: EntitlementState): AppViewModel =
        ViewModelProvider(
            viewModels,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AppViewModel(preferences, entitlements = MutableStateFlow(plan), clock = { now }) as T
            },
        )[AppViewModel::class.java]

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

    private suspend fun themeBecomes(theme: ThemeId) = withTimeout(10_000) { preferences.preferences.first { it.themeId == theme } }

    @Test
    fun `a Pro theme is picked while the plan holds`() = runBlocking {
        val vm = viewModel(proUntil(now + 3_600_000L))
        vm.setThemeId(ThemeId.AURORA)
        themeBecomes(ThemeId.AURORA)
        Unit
    }

    @Test
    fun `a plan that ends while Lucento stays open unlocks no Pro theme`() = runBlocking {
        val vm = viewModel(proUntil(now + 60_000L))
        assertEquals(emptySet<ThemeId>(), vm.lockedThemes.value)
        now += 120_000L
        // No new plan has arrived, so the list worked out earlier still offers the Pro themes...
        assertEquals(emptySet<ThemeId>(), vm.lockedThemes.value)
        // ...but the tap checks the plan as it is now.
        vm.setThemeId(ThemeId.AURORA)
        // An edit made after it lands after anything the tap asked for (DataStore applies edits in order).
        withTimeout(10_000) { preferences.setShowCents(false) }
        assertEquals(ThemeId.SUNRISE, preferences.preferences.first().themeId)
        // A free theme is still picked.
        vm.setThemeId(ThemeId.MIDNIGHT)
        themeBecomes(ThemeId.MIDNIGHT)
        Unit
    }
}
