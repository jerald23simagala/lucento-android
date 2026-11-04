package com.lucento.app.data.media

import com.lucento.app.data.sync.accountPhotoUrl
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The profile photo's only network use: Google's own HTTPS image host, and only the account's picture URL. */
class ProfilePhotosTest {

    private val google = "https://lh3.googleusercontent.com/a/ACg8ocJ-abc123=s96-c"

    @Test fun onlyGooglesHttpsImageHostIsAccepted() {
        assertTrue(ProfilePhotos.isGooglePhotoUrl(google))
        assertTrue(ProfilePhotos.isGooglePhotoUrl("https://googleusercontent.com/photo.jpg"))
        assertFalse(ProfilePhotos.isGooglePhotoUrl("http://lh3.googleusercontent.com/a/abc=s96-c"))
        assertFalse(ProfilePhotos.isGooglePhotoUrl("https://evilgoogleusercontent.com/a.jpg"))
        assertFalse(ProfilePhotos.isGooglePhotoUrl("https://googleusercontent.com.example.com/a.jpg"))
        assertFalse(ProfilePhotos.isGooglePhotoUrl("https://example.com/a.jpg"))
        assertFalse(ProfilePhotos.isGooglePhotoUrl("file:///sdcard/a.jpg"))
        assertFalse(ProfilePhotos.isGooglePhotoUrl(""))
        assertFalse(ProfilePhotos.isGooglePhotoUrl(null))
    }

    @Test fun googlesSmallDefaultSizeIsReplacedSoTheAvatarIsSharp() {
        assertEquals("https://lh3.googleusercontent.com/a/ACg8ocJ-abc123=s${ProfilePhotos.EDGE_PX}-c", ProfilePhotos.sizedGoogleUrl(google))
        assertEquals("https://lh3.googleusercontent.com/a/x=s${ProfilePhotos.EDGE_PX}-c", ProfilePhotos.sizedGoogleUrl("https://lh3.googleusercontent.com/a/x=s96"))
        val plain = "https://lh3.googleusercontent.com/a/x"
        assertEquals(plain, ProfilePhotos.sizedGoogleUrl(plain))
    }

    @Test fun thePictureComesFromTheSessionMetadataOnlyWhenItIsGoogles() {
        assertEquals(google, accountPhotoUrl(buildJsonObject { put("avatar_url", google) }))
        assertEquals(google, accountPhotoUrl(buildJsonObject { put("picture", google) }))
        // An email account has none; anything not on Google's image host is ignored.
        assertNull(accountPhotoUrl(buildJsonObject { put("full_name", "Alex") }))
        assertNull(accountPhotoUrl(buildJsonObject { put("avatar_url", "https://example.com/me.jpg") }))
        assertNull(accountPhotoUrl(buildJsonObject { put("avatar_url", JsonPrimitive(42)) }))
        assertNull(accountPhotoUrl(null))
    }

    @Test fun largePhotosAreDecodedDownsampledButNeverBelowTheAvatarSize() {
        assertEquals(1, ProfilePhotos.sampleSizeFor(400, 300))
        assertEquals(8, ProfilePhotos.sampleSizeFor(6000, 4000))
        assertTrue(4000 / ProfilePhotos.sampleSizeFor(6000, 4000) >= ProfilePhotos.EDGE_PX)
    }
}
