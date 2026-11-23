package com.lucento.app.feature.ask

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lucento.app.LucentoApplication
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.data.ai.AskLucentoClient
import com.lucento.app.data.billing.SimulatedPlan
import com.lucento.app.domain.ask.LocalAnswers
import com.lucento.app.feature.legal.LegalDocuments
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Ask Lucento's AI answers are on by default and confirmed once (owner, 2026-11-17). On the real screen, with a signed-in
 * account and a proxy that can't be reached: the first question shows what would be sent and nothing is requested until
 * "Ask Gemini"; "Keep answers on this phone" sends nothing and turns AI answers off.
 *
 * Optional instrumentation arguments for a visual check: `askAiShots=true` saves screenshots to the app's external files
 * (`ask-ai-shots/`), `askAiFontScale=2` renders at 200 % text.
 */
@RunWith(AndroidJUnit4::class)
class AskAiConfirmTest {

    @get:Rule
    val compose = createComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val container get() = (instrumentation.targetContext.applicationContext as LucentoApplication).container
    private val arguments get() = InstrumentationRegistry.getArguments()

    /** Each time the client asks for the session's token: the first step of any request to Lucento's proxy. */
    private val tokenRequests = AtomicInteger(0)

    /** The simulated plan before this test (other tests in the same run choose their own, e.g. a Lifetime). */
    private var planBefore: SimulatedPlan? = null

    @Before fun startFromTheDefault() = runBlocking {
        planBefore = simulator().current.value
        container.preferences.forgetAccountConsents()
    }

    @After fun leaveTheDefault() = runBlocking {
        container.preferences.forgetAccountConsents()
        simulator().select(planBefore)
        container.entitlements.refresh()
        Unit
    }

    @Test fun the_first_question_waits_for_the_person_and_goes_once_confirmed() {
        usePlan(SimulatedPlan.FREE)
        open()
        askUntilTheCardShows(LocalAnswers.adviceSuggestions.first())
        compose.onNodeWithText(LegalDocuments.GEMINI_DATA_USE).assertExists()
        // Free: AI answers are available, 10 a month (not "a day", and no Smarter reading).
        compose.onNodeWithText(AskAiCopy.allowance(pro = false), substring = true).assertExists()
        assertEquals("nothing is requested before the person confirms", 0, tokenRequests.get())
        shot("1-first-question-card")

        compose.onNodeWithText(AskAiCopy.CONFIRM_SEND).performScrollTo().performClick()
        compose.waitUntil(TIMEOUT) { tokenRequests.get() > 0 }
        // No token in this test, so nothing reaches the network: the reply says the sign-in has expired.
        waitForText("Your sign-in has expired")
        compose.onAllNodesWithText(AskAiCopy.CONFIRM_TITLE).assertCountEquals(0)
        val prefs = runBlocking { container.preferences.preferences.first() }
        assertTrue(prefs.askLucentoAi)
        assertTrue(prefs.askAiConfirmed)
        shot("2-after-ask-gemini")
    }

    @Test fun keeping_answers_on_the_phone_sends_nothing_and_turns_ai_answers_off() {
        // A Lifetime in its included year: the same card, with Pro's allowance.
        usePlan(SimulatedPlan.LIFETIME)
        open()
        askUntilTheCardShows(LocalAnswers.adviceSuggestions.first())
        compose.onNodeWithText(AskAiCopy.allowance(pro = true), substring = true).assertExists()
        compose.onNodeWithText(AskAiCopy.CONFIRM_KEEP).performScrollTo().performClick()
        waitForText("Nothing was sent")
        // The top bar's Gemini switch is always on screen (the status line scrolls away at large text).
        compose.waitUntil(TIMEOUT) { runCatching { geminiSwitch().assertIsOff() }.isSuccess }
        assertEquals(0, tokenRequests.get())
        val prefs = runBlocking { container.preferences.preferences.first() }
        assertFalse(prefs.askLucentoAi)
        assertFalse(prefs.askAiConfirmed)
        shot("3-kept-on-this-phone")
    }

    private fun open() {
        val viewModel = AskLucentoViewModel(
            container.transactions, container.wallets, container.categories, container.budgets, container.recurring,
            container.goals, container.preferences,
            signedInEmail = flowOf("tester@example.com"),
            // Never a token, so even after "Ask Gemini" nothing goes over the network.
            client = AskLucentoClient(accessToken = { tokenRequests.incrementAndGet(); null }, endpoint = "https://ask.invalid/api/ask"),
            pdf = container.reportPdf,
            entitlements = container.entitlements.state,
        )
        val fontScale = arguments.getString("askAiFontScale")?.toFloatOrNull()
        compose.setContent {
            LucentoTheme(darkTheme = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale ?: density.fontScale)) {
                    AskLucentoScreen(onBack = {}, voiceEnabled = false, viewModel = viewModel)
                }
            }
        }
        waitForText("AI answers on · Gemini")
        geminiSwitch().assertIsOn()
    }

    private fun geminiSwitch() = compose.onNode(hasText("Gemini") and isToggleable())

    private fun simulator() = checkNotNull(container.planSimulator) { "Test builds have the plan simulator" }

    /** The plan the card's allowance follows, through the test builds' plan simulator. */
    private fun usePlan(plan: SimulatedPlan) = runBlocking {
        simulator().select(plan)
        container.entitlements.refresh()
        Unit
    }

    /** Taps the suggestion until the card shows: a tap while the records are still loading asks nothing. */
    private fun askUntilTheCardShows(question: String) {
        val deadline = System.currentTimeMillis() + TIMEOUT
        while (System.currentTimeMillis() < deadline) {
            compose.waitForIdle()
            if (compose.onAllNodesWithText(AskAiCopy.CONFIRM_TITLE).fetchSemanticsNodes().isNotEmpty()) return
            runCatching { compose.onAllNodesWithText(question)[0].performScrollTo().performClick() }
            Thread.sleep(300)
        }
        throw AssertionError("The first-question card never appeared")
    }

    private fun waitForText(text: String) {
        compose.waitUntil(TIMEOUT) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun shot(name: String) {
        if (arguments.getString("askAiShots") != "true") return
        compose.waitForIdle()
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "ask-ai-shots").apply { mkdirs() }
        val scale = arguments.getString("askAiFontScale") ?: "1"
        File(dir, "$name-x$scale.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private companion object {
        const val TIMEOUT = 15_000L
    }
}
