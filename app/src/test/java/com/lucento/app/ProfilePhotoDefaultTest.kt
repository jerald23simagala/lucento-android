package com.lucento.app

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lucento.app.data.preferences.ProfilePhotoChoice
import com.lucento.app.data.preferences.UserPreferencesRepository
import com.lucento.app.data.sync.OFFLINE_SIGN_IN_MESSAGE
import com.lucento.app.data.sync.offlineFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner, 2026-11-05: the profile photo disappeared on every sign-in (the account wipe removes it). Now the Google picture
 * is the default and comes back by itself; a picture the person picked, or a removal, is respected.
 * Also: signing in (or switching account) offline says so before anything else is tried.
 */
class ProfilePhotoDefaultTest {

    private val url = "https://lh3.googleusercontent.com/a/photo=s96-c"

    @Test
    fun `a Google account with no photo on the phone gets its picture by default`() {
        assertTrue(shouldFetchGooglePhoto(url, ProfilePhotoChoice.GOOGLE, version = null, alreadyTried = null))
    }

    @Test
    fun `the person's own picture or a removal is never replaced`() {
        assertFalse(shouldFetchGooglePhoto(url, ProfilePhotoChoice.OWN, version = null, alreadyTried = null))
        assertFalse(shouldFetchGooglePhoto(url, ProfilePhotoChoice.NONE, version = null, alreadyTried = null))
        // A photo already on the phone stays.
        assertFalse(shouldFetchGooglePhoto(url, ProfilePhotoChoice.GOOGLE, version = 1L, alreadyTried = null))
    }

    @Test
    fun `no Google account, or a fetch already tried in this launch, fetches nothing`() {
        assertFalse(shouldFetchGooglePhoto(null, ProfilePhotoChoice.GOOGLE, version = null, alreadyTried = null))
        assertFalse(shouldFetchGooglePhoto(url, ProfilePhotoChoice.GOOGLE, version = null, alreadyTried = url))
        // Another account's picture is a new URL: fetched.
        assertTrue(shouldFetchGooglePhoto("$url-other", ProfilePhotoChoice.GOOGLE, version = null, alreadyTried = url))
    }

    @Test
    fun `the photo choice belongs to the account and goes back to Google when the account changes`() {
        val choice = stringPreferencesKey("profile_photo_choice")
        val prefs = mutablePreferencesOf(choice to ProfilePhotoChoice.NONE.key)
        UserPreferencesRepository.resetAccountSetup(prefs)
        assertNull(prefs[choice])
        assertEquals(ProfilePhotoChoice.GOOGLE, ProfilePhotoChoice.fromKey(prefs[choice]))
    }

    @Test
    fun `signing in offline says so first`() {
        assertEquals(OFFLINE_SIGN_IN_MESSAGE, offlineFailure { false }?.message)
        assertNull(offlineFailure { true })
    }
}
