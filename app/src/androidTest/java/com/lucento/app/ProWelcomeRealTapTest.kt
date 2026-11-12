package com.lucento.app

import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Lucento Pro's tour over the real app, pressed with a [Finger]: down, a pixel or two of drift, up, as a person taps.
 * `performClick` never moves, and that hid a bug which left the welcome's buttons dead on real phones (owner's report,
 * 2026-11-12, Galaxy S8: Next did nothing, nothing else could be tapped, only Back worked). The curtain's own buttons are
 * pressed in `ProTourCurtainFingerTest`, away from the curtain's timer.
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ProWelcomeRealTapTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val container get() = (instrumentation.targetContext.applicationContext as LucentoApplication).container

    private val stopOne = hasText("Add every account you use", substring = true)
    private val stopTwo = hasText("Count everything you have", substring = true)
    private val summary = hasText("Congratulations, you’re Pro", substring = true) or hasText("Welcome back to Pro", substring = true)

    @Test
    fun the_tour_answers_a_finger() {
        onboardAndSeed()
        // Where the dock's Home tab is (the app is hidden from accessibility while the tour covers it, so it is found now).
        val home = point(hasText("Home") and isSelectable())
        // A Lifetime that Google Play already lists: the welcome starts at once, as on the owner's phone.
        val simulator = checkNotNull(container.planSimulator) { "Test builds have the plan simulator" }
        runBlocking {
            container.preferences.setProMemberSince(null)
            simulator.select(com.lucento.app.data.billing.SimulatedPlan.LIFETIME)
            container.entitlements.refresh()
        }

        // The curtain moves on to the first stop by itself.
        await(stopOne, "The tour's first stop never came.", timeout = 20_000)
        Thread.sleep(1_500) // the card arrives and the control it points at is lit, as a person would wait

        // The card's buttons answer a finger: the owner's report was that Next did nothing.
        tap(hasText("Next"))
        await(stopTwo, "Next didn't move the tour on.")
        Thread.sleep(800)
        tap(hasText("Back"))
        await(stopOne, "Back didn't go back a stop.")
        Thread.sleep(800)

        // A finger on the dimmed page changes nothing beneath it. Checked once the welcome is over: the dock stays on
        // Wallets, where the first stop took it, rather than going Home.
        Finger.tapAt(home.first, home.second)
        compose.waitForIdle()
        Thread.sleep(500)
        await(stopOne, "A tap on the dimmed dock moved the tour.")
        tap(hasText("End tour"))
        await(summary, "End tour didn't end the tour.")

        // Back on the summary ends the welcome (the award is still given).
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        val gone = SystemClock.uptimeMillis() + 10_000
        while (compose.onAllNodes(summary).fetchSemanticsNodes().isNotEmpty()) {
            if (SystemClock.uptimeMillis() > gone) throw AssertionError("Back didn't close the summary.\n" + screenReport())
            Thread.sleep(200)
        }
        compose.onNode(hasText("Wallets") and isSelectable()).assertIsSelected()
        compose.onNode(hasText("Home") and isSelectable()).assertIsNotSelected()
    }

    private fun point(matcher: SemanticsMatcher): Pair<Float, Float> = try {
        Finger.centreOf(compose, matcher)
    } catch (e: Throwable) {
        throw AssertionError("Nothing to tap: ${matcher.description}\n" + screenReport(), e)
    }

    private fun tap(matcher: SemanticsMatcher) {
        val (x, y) = point(matcher)
        Finger.tapAt(x, y)
        compose.waitForIdle()
    }

    private fun await(matcher: SemanticsMatcher, failure: String, timeout: Long = 8_000) {
        try {
            compose.waitUntilAtLeastOneExists(matcher, timeout)
        } catch (e: Throwable) {
            throw AssertionError(failure + "\n" + screenReport(), e)
        }
    }

    private fun waitForText(text: String, substring: Boolean = true) =
        await(hasText(text, substring = substring, ignoreCase = true), "Waited for '$text'.", timeout = 10_000)

    /** Moves through screens that aren't under test (onboarding) without a finger. */
    private fun click(text: String, substring: Boolean = false) {
        waitForText(text, substring)
        val node = compose.onNodeWithText(text, substring = substring)
        runCatching { node.performScrollTo() }
        node.performClick()
        compose.waitForIdle()
    }

    /** What is on screen: the app's texts and every window the system shows, top first. */
    private fun screenReport(): String {
        val tree = runCatching { compose.onAllNodes(isRoot()).printToString(maxDepth = 14) }.getOrDefault("(no tree)")
        val texts = tree.lines().filter { "Text" in it || "ContentDescription" in it || "Selected" in it }.joinToString("\n").take(3000)
        val automation = instrumentation.uiAutomation
        val windows = runCatching {
            automation.serviceInfo = automation.serviceInfo.apply {
                flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
            automation.windows.joinToString("\n") { w ->
                val r = android.graphics.Rect().also { w.getBoundsInScreen(it) }
                "  window type=${w.type} layer=${w.layer} focused=${w.isFocused} title=${w.title} bounds=$r"
            }
        }.getOrElse { "(windows unavailable: $it)" }
        return "Screen text:\n$texts\nWindows:\n$windows"
    }

    /** Onboarding as in ScreenshotTourTest (no account: everything stays on this phone), every award seen, a real month. */
    private fun onboardAndSeed() {
        waitForText("Get started")
        click("Get started")
        waitForText("Keep it on this phone only")
        click("Keep it on this phone only")
        click("Continue")
        waitForText("What should Lucento help with?")
        click("Continue")
        waitForText("How do you pay?")
        click("Continue")
        waitForText("What do you spend on?")
        click("Review", substring = true)
        waitForText("Rename, reorder or add more")
        click("Confirm", substring = true)
        waitForText("Currency and look")
        click("Continue")
        waitForText("Lucento is personalized")
        click("Continue to set up")
        waitForText("Add your accounts")
        runBlocking { container.preferences.setGuideStep(null) }
        waitForText("Let’s set up your money")
        runBlocking {
            container.preferences.acknowledgeAchievements(com.lucento.app.domain.Achievement.entries.toSet())
            val currency = container.preferences.preferences.first().primaryCurrency
            DemoMonth.seed(container, currency, 1, LocalDate.now())
        }
    }
}
