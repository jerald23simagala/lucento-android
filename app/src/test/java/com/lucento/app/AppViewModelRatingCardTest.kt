package com.lucento.app

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.lucento.app.core.crash.CrashReportsFlag
import com.lucento.app.data.preferences.UserPreferencesRepository
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The rating card after a save (tester report 2026-11-25): the ask is recorded before Google Play is asked, so Play's
 * own limit or a failure never brings it back sooner, and nothing is recorded while it isn't due.
 */
class AppViewModelRatingCardTest {

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

    private val viewModels = ViewModelStore()
    private val day = 86_400_000L
    private val installed = 1_800_000_000_000L
    private var now = installed + 10 * day
    private val recorded = MutableStateFlow(25)

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() {
        viewModels.clear()
        scope.cancel()
        Dispatchers.resetMain()
    }

    private fun viewModel(installedAt: Long? = installed): AppViewModel =
        ViewModelProvider(
            viewModels,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AppViewModel(preferences, installedAtMillis = installedAt, recordedCount = recorded, clock = { now }) as T
            },
        )[AppViewModel::class.java]

    @Test
    fun `a due card is claimed once, then waits 90 days`() = runBlocking {
        val vm = viewModel()
        assertTrue(vm.claimRatingCard(listingPublic = true))
        assertEquals(now, preferences.preferences.first().ratingCardAskedAt)

        now += day
        assertFalse("asked yesterday", vm.claimRatingCard(listingPublic = true))
        now += 89 * day
        assertTrue("90 days after the last ask", vm.claimRatingCard(listingPublic = true))
    }

    @Test
    fun `nothing is claimed or recorded before the listing is public`() = runBlocking {
        val vm = viewModel()
        assertFalse(vm.claimRatingCard(listingPublic = false))
        assertNull(preferences.preferences.first().ratingCardAskedAt)
        // The default reads the build's flag, which stays off until the launch build.
        assertEquals(com.lucento.app.core.LucentoLinks.PLAY_LISTING_PUBLIC, vm.claimRatingCard())
    }

    @Test
    fun `too few records or an unknown install time claim nothing`() = runBlocking {
        recorded.value = 19
        assertFalse(viewModel().claimRatingCard(listingPublic = true))
        assertNull(preferences.preferences.first().ratingCardAskedAt)

        recorded.value = 40
        viewModels.clear()
        assertFalse(viewModel(installedAt = null).claimRatingCard(listingPublic = true))
        assertNull(preferences.preferences.first().ratingCardAskedAt)
    }
}
