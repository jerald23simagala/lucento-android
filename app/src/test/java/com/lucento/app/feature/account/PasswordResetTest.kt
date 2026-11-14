package com.lucento.app.feature.account

import com.lucento.app.data.sync.AuthRepository.ResendResult
import com.lucento.app.data.sync.resetFailureMessage
import com.lucento.app.data.sync.resetSendResult
import com.lucento.app.feature.settings.emailProblem
import com.lucento.app.feature.settings.PasswordResetViewModel
import io.github.jan.supabase.auth.exception.AuthErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Forgot password (owner test 2026-11-09): the emailed code and what a refusal says. */
class PasswordResetTest {
    @Test
    fun `a code is 6 to 10 digits, spaces around it allowed`() {
        assertTrue(PasswordResetViewModel.codeLooksRight("123456"))
        assertTrue(PasswordResetViewModel.codeLooksRight(" 12345678 "))
        assertFalse(PasswordResetViewModel.codeLooksRight("12345"))
        assertFalse(PasswordResetViewModel.codeLooksRight("12345a"))
        assertFalse(PasswordResetViewModel.codeLooksRight("12345678901"))
    }

    @Test
    fun `a wrong or old code, a reused password and too many tries each get their own sentence`() {
        val expired = resetFailureMessage(AuthErrorCode.OtpExpired, debug = false, detail = "")
        val same = resetFailureMessage(AuthErrorCode.SamePassword, debug = false, detail = "")
        val limited = resetFailureMessage(AuthErrorCode.OverRequestRateLimit, debug = false, detail = "")
        assertTrue(expired.contains("code"))
        assertTrue(same.contains("current password"))
        assertTrue(limited.contains("Wait"))
        assertEquals(3, setOf(expired, same, limited).size)
    }

    @Test
    fun `release builds never show the server's detail`() {
        assertFalse(resetFailureMessage(null, debug = false, detail = "secret detail").contains("secret detail"))
    }

    @Test
    fun `a refused address says check it, not check your connection (owner test round 2)`() {
        assertEquals(ResendResult.INVALID_EMAIL, resetSendResult(AuthErrorCode.ValidationFailed, "Unable to validate email address: invalid format"))
        assertEquals(ResendResult.TOO_SOON, resetSendResult(AuthErrorCode.OverEmailSendRateLimit, ""))
        assertEquals(ResendResult.FAILED, resetSendResult(AuthErrorCode.UnexpectedFailure, "Error sending recovery email"))
    }

    @Test
    fun `addresses Supabase refuses are caught on the phone first`() {
        assertNotNull(emailProblem("name@gmail..com"))
        assertNotNull(emailProblem("name@.gmail.com"))
        assertNotNull(emailProblem("name@gmail.com."))
        assertNull(emailProblem("first.last@mail.example.co.uk"))
    }
}
