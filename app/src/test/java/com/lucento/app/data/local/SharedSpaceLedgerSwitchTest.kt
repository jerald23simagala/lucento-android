package com.lucento.app.data.local

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lucento.app.data.preferences.AccountSetupStore
import com.lucento.app.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lucento Shared: the two ledger switches use the sign-out wipe's order and marker, and keep what belongs to the person.
 */
class SharedSpaceLedgerSwitchTest {

    private val onboarding = booleanPreferencesKey("onboarding_complete")
    private val generation = intPreferencesKey("setup_generation")
    private val categorySetup = booleanPreferencesKey("category_setup_done")
    private val guide = stringPreferencesKey("guide_step")
    private val defaultWallet = longPreferencesKey("default_wallet_id")
    private val name = stringPreferencesKey("display_name")
    private val smart = booleanPreferencesKey("smart_reading")

    private class Marker(var value: Boolean = false) : WipeMarker {
        override fun set() = true.also { value = true }
        override fun clear() = true.also { value = false }
        override fun isSet() = value
    }

    private class Store(val prefs: MutablePreferences, val steps: MutableList<String>) : AccountSetupStore {
        override suspend fun resetForNextAccount() {
            steps += "setup"
            UserPreferencesRepository.resetAccountSetup(prefs)
        }

        override suspend fun resetForSharedSpace() {
            steps += "space-setup"
            UserPreferencesRepository.resetForSharedSpaceLedger(prefs)
        }

        override suspend fun resetForOwnLedger() {
            steps += "own-setup"
            val keepName = prefs[stringPreferencesKey("display_name")]
            UserPreferencesRepository.resetAccountSetup(prefs)
            keepName?.let { prefs[stringPreferencesKey("display_name")] = it }
        }
    }

    private fun reset(
        marker: Marker,
        store: Store,
        steps: MutableList<String>,
        failTables: Boolean = false,
        forget: suspend () -> Unit = {},
    ) = LocalLedgerReset(
        marker = marker,
        clearBinding = { steps += "binding" },
        clearTables = { steps += "tables+seed"; if (failTables) error("killed") },
        deletePhotos = { steps += "all-photos" },
        accountSetup = store,
        clearTablesUnseeded = { steps += "tables"; if (failTables) error("killed") },
        deleteLedgerPhotos = { steps += "goal-photos" },
        forgetAccount = { steps += "forget"; forget() },
    )

    private fun cachedSpace() = com.lucento.app.data.sharedspace.InMemorySharedSpaceStore(
        com.lucento.app.data.sharedspace.SharedSpaceCache(
            snapshot = com.lucento.app.data.sharedspace.SharedSpaceSnapshot(
                role = com.lucento.app.domain.SpaceRole.OWNER,
                ownerId = "rachel",
                ownerLabel = "Rachel",
            ),
            refreshedAtMillis = 1L,
            knownNames = mapOf("rachel" to "Rachel"),
            dismissedInvitations = setOf("inv-1"),
            notice = com.lucento.app.data.sharedspace.SpaceNotice(com.lucento.app.data.sharedspace.SpaceNotice.Kind.LEFT, "Rachel"),
        ),
    )

    @Test
    fun `a wipe for another account forgets the previous account's Shared Space, before onboarding starts`() = runTest {
        // Quick account switch (test builds) and sign-out: Rachel's cached space must never show on Jerald's session.
        val steps = mutableListOf<String>()
        val space = cachedSpace()
        val store = Store(mutablePreferencesOf(onboarding to true), steps)

        reset(Marker(), store, steps, forget = { space.clearAll() }).wipe()

        assertEquals(listOf("binding", "tables+seed", "all-photos", "forget", "setup"), steps)
        assertEquals(com.lucento.app.data.sharedspace.SharedSpaceCache(), space.current())
    }

    @Test
    fun `switching ledgers for the same person keeps what the account knows`() = runTest {
        val steps = mutableListOf<String>()
        val space = cachedSpace()
        val before = space.current()
        val store = Store(mutablePreferencesOf(onboarding to true), steps)

        reset(Marker(), store, steps, forget = { space.clearAll() }).switchToSharedSpace(bind = {})
        reset(Marker(), store, steps, forget = { space.clearAll() }).returnToOwnLedger()

        assertFalse("forget" in steps)
        assertEquals(before, space.current())
    }

    @Test
    fun `an interrupted account wipe still forgets the cached space when it is finished`() = runTest {
        val steps = mutableListOf<String>()
        val space = cachedSpace()
        val marker = Marker()
        val store = Store(mutablePreferencesOf(onboarding to true), steps)
        assertTrue(runCatching { reset(marker, store, steps, failTables = true, forget = { space.clearAll() }).wipe() }.isFailure)
        assertEquals("Rachel", space.current().snapshot?.ownerLabel)

        assertTrue(reset(marker, store, steps, forget = { space.clearAll() }).resumeIfInterrupted())
        assertEquals(com.lucento.app.data.sharedspace.SharedSpaceCache(), space.current())
    }

    @Test
    fun `joining binds the space before the ledger goes, seeds nothing and keeps onboarding finished`() = runTest {
        val steps = mutableListOf<String>()
        val store = Store(mutablePreferencesOf(onboarding to true, guide to "wallets", defaultWallet to 7L, name to "Pat"), steps)
        val marker = Marker()

        reset(marker, store, steps).switchToSharedSpace(bind = { steps += "bind" })

        assertEquals(listOf("binding", "bind", "tables", "goal-photos", "space-setup"), steps)
        assertEquals(true, store.prefs[onboarding])
        assertEquals(1, store.prefs[generation]) // screens holding the old ledger start over
        assertEquals(true, store.prefs[categorySetup]) // categories are the Owner's to set up
        assertNull(store.prefs[guide])
        assertNull(store.prefs[defaultWallet])
        assertEquals("Pat", store.prefs[name])
        assertFalse(marker.isSet())
    }

    @Test
    fun `a join killed half-way is finished on the next launch as a full wipe, so onboarding sorts it out`() = runTest {
        val steps = mutableListOf<String>()
        val store = Store(mutablePreferencesOf(onboarding to true), steps)
        val marker = Marker()
        assertTrue(runCatching { reset(marker, store, steps, failTables = true).switchToSharedSpace(bind = { steps += "bind" }) }.isFailure)
        assertTrue(marker.isSet())

        steps.clear()
        assertTrue(reset(marker, store, steps).resumeIfInterrupted())
        // The binding, the space and the cached space go too: onboarding asks the server afresh (adoptMembership).
        assertEquals(listOf("binding", "tables+seed", "all-photos", "forget", "setup"), steps)
        assertEquals(false, store.prefs[onboarding])
    }

    @Test
    fun `leaving reseeds like a fresh install and goes back through onboarding, keeping the person's name`() = runTest {
        val steps = mutableListOf<String>()
        val store = Store(mutablePreferencesOf(onboarding to true, name to "Pat", smart to true), steps)
        val marker = Marker()

        reset(marker, store, steps).returnToOwnLedger()

        assertEquals(listOf("binding", "tables+seed", "goal-photos", "own-setup"), steps)
        assertEquals(false, store.prefs[onboarding])
        assertEquals("Pat", store.prefs[name])
        assertFalse(marker.isSet())
    }
}
