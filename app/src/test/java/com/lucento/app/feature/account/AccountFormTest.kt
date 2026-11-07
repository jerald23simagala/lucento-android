package com.lucento.app.feature.account

import com.lucento.app.data.sync.AuthRepository
import com.lucento.app.data.sync.consentMetadata
import com.lucento.app.data.sync.emailCheckFor
import com.lucento.app.feature.legal.LegalDocuments
import com.lucento.app.feature.settings.EmailMode
import com.lucento.app.feature.settings.emailFormProblem
import com.lucento.app.feature.settings.emailProblem
import com.lucento.app.feature.settings.passwordProblem
import io.github.jan.supabase.auth.exception.AuthErrorCode
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** The email account form and what a new account records (owner, 2026-11-06). */
class AccountFormTest {

    @Test
    fun `the email field asks for a real address`() {
        assertNotNull(emailProblem(""))
        assertNotNull(emailProblem("ana"))
        assertNotNull(emailProblem("ana@example"))
        assertNull(emailProblem(" ana@example.com "))
    }

    @Test
    fun `a new password needs eight characters, signing in only needs one`() {
        assertNotNull(passwordProblem("", EmailMode.CREATE))
        assertNotNull(passwordProblem("short12", EmailMode.CREATE))
        assertNull(passwordProblem("long-enough", EmailMode.CREATE))
        assertNull(passwordProblem("short12", EmailMode.SIGN_IN))
        assertNotNull(passwordProblem("", EmailMode.SIGN_IN))
        assertNull(emailFormProblem("ana@example.com", "long-enough", EmailMode.CREATE))
    }

    @Test
    fun `a refused sign-in tells waiting apart from a wrong password and from no answer`() {
        assertEquals(AuthRepository.EmailCheck.PENDING, emailCheckFor(AuthErrorCode.EmailNotConfirmed))
        assertEquals(AuthRepository.EmailCheck.WRONG_PASSWORD, emailCheckFor(AuthErrorCode.InvalidCredentials))
        assertEquals(AuthRepository.EmailCheck.UNREACHABLE, emailCheckFor(AuthErrorCode.OverRequestRateLimit))
        assertEquals(AuthRepository.EmailCheck.UNREACHABLE, emailCheckFor(null))
    }

    @Test
    fun `an email the server couldn't send says so, instead of a generic failure`() {
        // lucento-test, 2026-11-06: Resend refused the sender domain; Supabase answered unexpected_failure.
        val message = com.lucento.app.data.sync.emailFailureMessage(AuthErrorCode.UnexpectedFailure, debug = false, "Error sending confirmation email")
        assertTrue(message, message.startsWith("We couldn’t send the verification email"))
        assertEquals(
            "Couldn’t connect your account. Try again.",
            com.lucento.app.data.sync.emailFailureMessage(AuthErrorCode.UnexpectedFailure, debug = false, "Database error"),
        )
    }

    @Test
    fun `a new account records which terms it agreed to, and when`() {
        val at = Instant.parse("2026-11-06T08:00:00Z")
        val data = consentMetadata(LegalDocuments.VERSION, at)
        assertEquals(LegalDocuments.VERSION, data["terms_version"]?.jsonPrimitive?.content)
        assertEquals("2026-11-06T08:00:00Z", data["terms_accepted_at"]?.jsonPrimitive?.content)
        assertEquals("only these two fields", 2, data.size)
    }

    @Test
    fun `both documents are complete enough to agree to`() {
        listOf(LegalDocuments.terms, LegalDocuments.privacy).forEach { doc ->
            assertTrue(doc.title, doc.intro.isNotBlank())
            assertTrue(doc.title, doc.sections.size >= 5)
            assertTrue(doc.title, doc.sections.all { it.title.isNotBlank() && it.items.isNotEmpty() && it.items.all(String::isNotBlank) })
        }
        assertTrue("the version is a date", Regex("""\d{4}-\d{2}-\d{2}""").matches(LegalDocuments.VERSION))
    }
}
