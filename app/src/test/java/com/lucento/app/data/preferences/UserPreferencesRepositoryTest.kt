package com.lucento.app.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.lucento.app.core.crash.CrashReportsFlag
import com.lucento.app.domain.Feature
import com.lucento.app.domain.backup.BackupSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The real repository over a real DataStore file (architecture review F14): every setter of a setting that follows the
 * account marks it for sync, and nothing else does — consents, the setup's own choices, a restore that changes nothing.
 */
class UserPreferencesRepositoryTest {

    @get:Rule val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var now = 1_000_000L
    private val repository by lazy {
        UserPreferencesRepository(
            PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "user_preferences.preferences_pb") },
            object : CrashReportsFlag.Store {
                override fun getBoolean(key: String, default: Boolean) = default
                override fun commit(edits: Map<String, Boolean>) = Unit
            },
            clock = { now },
        )
    }

    @After fun close() = scope.cancel()

    private suspend fun waiting() = repository.accountSettingsWaiting()

    private fun step(label: String, block: suspend () -> Unit) = label to block

    @Test
    fun `each setter of a setting that follows the account marks it for sync`() = runBlocking {
        val setters = listOf(
            step("theme") { repository.setThemeId(ThemeId.GROVE) },
            step("theme mode") { repository.setThemeMode(ThemeMode.DARK) },
            step("modules") { repository.setFeatureEnabled(Feature.INSIGHTS, false) },
            step("name") { repository.setDisplayName("Alex") },
            step("cents") { repository.setShowCents(false) },
            step("wallets view") { repository.setWalletsViewMode(WalletsViewMode.ROWS) },
            step("module offers") { repository.setModuleOffersHidden(true) },
            step("exchange rates") { repository.setFxRatesOnline(false) },
            step("default wallet") { repository.setDefaultWallet(3L) },
            step("budget moment") { repository.setAcknowledgedBudgetMonth(java.time.YearMonth.of(2026, 11)) },
        )
        var changes = repository.accountSettingChanges.first()
        for ((label, set) in setters) {
            set()
            val after = repository.accountSettingChanges.first()
            assertTrue("$label is marked for sync", after > changes)
            changes = after
        }
        assertTrue(waiting())
    }

    @Test
    fun `consents, AI answers and this phone's lock are never marked`() = runBlocking {
        repository.setSmartReading(true)
        repository.setOnlineVoice(true)
        repository.setAskLucentoAi(false)
        repository.setAppLock(true)
        repository.setHideAmounts(true)
        repository.setHideInRecents(true)
        repository.writeCrashReportsPreference(true)
        assertFalse(waiting())
    }

    @Test
    fun `the setup's own choices aren't marked as changes`() = runBlocking {
        repository.previewThemeMode(ThemeMode.DARK)
        repository.completeOnboarding("PHP", ThemeMode.DARK, setOf(Feature.BUDGETS))
        assertFalse(waiting())
        assertEquals(ThemeMode.DARK, repository.preferences.first().themeMode)
    }

    @Test
    fun `restoring a file of defaults marks nothing, and a Merge leaves the preferences alone`() = runBlocking {
        repository.applyBackupSettings(
            BackupSettings(onboardingComplete = true, themeMode = "SYSTEM", primaryCurrency = "USD", enabledFeatures = Feature.entries.map { it.key }),
            defaultWalletLocalId = null,
        )
        assertFalse(waiting())
        repository.clearDefaultWalletAfterMerge(defaultWalletLocalId = null)
        assertFalse(waiting())
    }

    @Test
    fun `a Merge clears only a default wallet it removed`() = runBlocking {
        repository.setDefaultWallet(5L)
        repository.clearDefaultWalletAfterMerge(defaultWalletLocalId = 5L)
        assertEquals(5L, repository.preferences.first().defaultWalletId)
        repository.clearDefaultWalletAfterMerge(defaultWalletLocalId = null)
        assertNull(repository.preferences.first().defaultWalletId)
    }

    @Test
    fun `a Pro theme in a file needs the plan`() = runBlocking {
        val file = BackupSettings(themeMode = "SYSTEM", primaryCurrency = "USD", enabledFeatures = Feature.entries.map { it.key }, themeId = "aurora")
        repository.applyBackupSettings(file, defaultWalletLocalId = null, themeAllowed = { false })
        assertEquals(ThemeId.SUNRISE, repository.preferences.first().themeId)
        repository.applyBackupSettings(file, defaultWalletLocalId = null, themeAllowed = { true })
        assertEquals(ThemeId.AURORA, repository.preferences.first().themeId)
    }

    @Test
    fun `a backup carries only the phone's own customisations, never its defaults`() = runBlocking {
        assertEquals(BackupCustomisations(null, null, null, null, null), repository.backupCustomisations())
        repository.setShowCents(false)
        repository.setThemeId(ThemeId.MONO)
        val own = repository.backupCustomisations()
        assertEquals(false, own.showCents)
        assertEquals("mono", own.themeId)
        assertNull(own.walletsView)
    }

    @Test
    fun `a restore of an account keeps its settings and the setup's, and defaults the rest`() = runBlocking {
        repository.bindAccountSettings("account-1", claiming = true)
        repository.mergeAccountSettings(
            "account-1",
            kotlinx.serialization.json.JsonObject(
                mapOf(
                    "theme_mode" to com.lucento.app.domain.settings.AccountSettingsDocument.encode(
                        com.lucento.app.domain.settings.SettingEntry(kotlinx.serialization.json.JsonPrimitive("DARK"), 100L),
                    ),
                ),
            ),
            com.lucento.app.domain.settings.AccountSetting.entries.toSet(),
            walletUuid = { null },
            walletId = { null },
        )
        repository.completeRestore("PHP", ThemeMode.SYSTEM, Feature.entries.toSet(), categoriesChosen = true)
        val p = repository.preferences.first()
        assertEquals(ThemeMode.DARK, p.themeMode)
        assertEquals(com.lucento.app.domain.FeatureSet.ALL, p.features)
        assertTrue(p.onboardingComplete)
        assertFalse(waiting())
    }
}
