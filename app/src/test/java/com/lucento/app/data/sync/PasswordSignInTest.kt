package com.lucento.app.data.sync

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tester report 2026-11-21: a Google-only account has no password, so Settings shouldn't offer to update one. */
class PasswordSignInTest {

    private fun metadata(vararg providers: String) = buildJsonObject {
        put("provider", providers.firstOrNull() ?: "")
        putJsonArray("providers") { providers.forEach { add(JsonPrimitive(it)) } }
    }

    @Test fun googleOnlyAccountHasNoPassword() {
        assertEquals(false, usesPasswordSignIn(metadata("google"), listOf("google")))
    }

    @Test fun emailAccountHasAPassword() {
        assertEquals(true, usesPasswordSignIn(metadata("email"), listOf("email")))
    }

    @Test fun emailAccountLaterLinkedToGoogleKeepsItsPassword() {
        assertEquals(true, usesPasswordSignIn(metadata("google", "email"), null))
        // Identities alone are enough when the metadata is missing.
        assertEquals(true, usesPasswordSignIn(null, listOf("google", "email")))
    }

    @Test fun unknownSignInMethodsKeepThePasswordSection() {
        assertNull(usesPasswordSignIn(null, null))
        assertNull(usesPasswordSignIn(buildJsonObject { put("provider", JsonPrimitive(3)) }, emptyList()))
    }
}
