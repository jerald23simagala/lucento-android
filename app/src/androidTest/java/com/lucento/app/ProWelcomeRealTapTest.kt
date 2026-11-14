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
 * Lucento Pro's welcome over the real app, pressed with a [Finger]: down, a pixel or two of drift, up, as a person taps.
 * `performClick` never moves, and that hid a bug which left the old tour's buttons dead on real phones (owner's report,
 * 2026-11-12, Galaxy S8). The deck that replaced the tour is pressed the same way here; `ProRevealDeckFingerTest` presses
 * it on its own, with a layer beneath that counts any touch that leaks through.
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ProWelcomeRealTapTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val container get() = (instrumentation.targetContext.applicationContext as LucentoApplication).container

    private val cardOne = hasText("Add every account you use", substring = true)
    private val cardTwo = hasText("Count everything you have", substring = true)
    private val collection = hasText("Congratulations, you’re Pro", substring = true) or hasText("Welcome back to Pro", substring = true)

    @Test
    fun the_welcome_answers_a_finger() {
        onboardAndSeed()
        // A Lifetime that Google Play already lists: the welcome starts at once, as on the owner's phone.
        val simulator = checkNotNull(container.planSimulator) { "Test builds have the plan simulator" }
        runBlocking {
            container.preferences.setProMemberSince(null)
            simulator.select(com.lucento.app.data.billing.SimulatedPlan.LIFETIME)
            container.entitlements.refresh()
        }

        // The deck opens and the first card turns over (the plain deck here: a plan the simulator lists counts as one
        // restored on this phone; ProRevealDeckFingerTest opens the band).
        tap(hasText("Open your Pro deck") or hasText("Reveal your first perk"))
        await(cardOne, "The first card never turned over.", timeout = 15_000)
        Thread.sleep(700)
        tap(hasText("Next"))
        await(cardTwo, "Next didn't bring the second card.")
        Thread.sleep(700)
        tap(hasText("Previous"))
        await(cardOne, "Previous didn't bring the first card back.")
        Thread.sleep(700)

        // A finger on the dim (the top corner, clear of every button) moves nothing. ProRevealDeckFingerTest counts what
        // leaks beneath; here the deck must simply stay where it is, and the welcome must not have left Home.
        val origin = IntArray(2)
        compose.runOnUiThread { compose.activity.window.decorView.getLocationOnScreen(origin) }
        val density = instrumentation.targetContext.resources.displayMetrics.density
        Finger.tapAt(origin[0] + 12 * density, origin[1] + 56 * density)
        compose.waitForIdle()
        Thread.sleep(500)
        await(cardOne, "A tap on the dim moved the deck.")

        tap(hasText("Skip"))
        await(collection, "Skip didn't lead to the collection.")

        // Back on the collection ends the welcome (the award is still given).
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        val gone = SystemClock.uptimeMillis() + 10_000
        while (compose.onAllNodes(collection).fetchSemanticsNodes().isNotEmpty()) {
            if (SystemClock.uptimeMillis() > gone) throw AssertionError("Back didn't close the collection.\n" + screenReport())
            Thread.sleep(200)
        }
        compose.onNode(hasText("Home") and isSelectable()).assertIsSelected()
        compose.onNode(hasText("Wallets") and isSelectable()).assertIsNotSelected()
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
