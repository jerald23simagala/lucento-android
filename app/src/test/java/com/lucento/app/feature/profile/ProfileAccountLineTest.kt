package com.lucento.app.feature.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileAccountLineTest {
    @Test fun signedInShowsTheEmail() = assertEquals("Signed in as qa@example.com", profileAccountLine("qa@example.com"))
    @Test fun signedInWithoutEmailNeverSaysNoAccount() = assertTrue(!profileAccountLine("").contains("No account"))
    @Test fun signedOutKeepsTheLocalOnlyLine() = assertTrue(profileAccountLine(null).startsWith("No account needed"))
}
