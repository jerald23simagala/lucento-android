package com.lucento.app

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lucento.app.data.preferences.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Captures Lucento Pro's welcome for design review (CI doesn't run it): the closed deck, every card, the collection, the
 * award and the ring, as PNGs in /data/local/tmp/lucento-reveal. Pull them with `adb pull`. `-e revealDark true` takes
 * them in the dark theme; the text size, the screen width (`wm size` / `wm density`) and reduced motion (animator scale
 * 0) are whatever the device has, and each name says them ("w360-dark-text200-…").
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ProRevealCaptureTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val container get() = (instrumentation.targetContext.applicationContext as LucentoApplication).container

    @Test
    fun capture_the_welcome() {
        val dark = InstrumentationRegistry.getArguments().getString("revealDark") == "true"
        // `-e revealRestored true`: a Lifetime the phone finds already bought (a new phone, a reinstall): "Welcome back"
        // and the plain deck, without the band.
        val restored = InstrumentationRegistry.getArguments().getString("revealRestored") == "true"
        val configuration = instrumentation.targetContext.resources.configuration
        val reduced = android.provider.Settings.Global.getFloat(
            instrumentation.targetContext.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
        val tag = "w${configuration.screenWidthDp}-" + (if (dark) "dark" else "light") + "-text" +
            kotlin.math.round(configuration.fontScale * 100).toInt() + (if (restored) "-restored" else "") + (if (reduced) "-reduced" else "")
        shell("mkdir -p /data/local/tmp/lucento-reveal")
        onboardAndSeed()
        if (dark) runBlocking { container.preferences.setThemeMode(ThemeMode.DARK) }
        // A Lifetime bought now, through the test store (as ScreenshotTourTest does): a first purchase, so the deck comes
        // in its band. A plan the simulator merely lists counts as one restored on this phone, with the plain deck.
        val simulator = checkNotNull(container.planSimulator) { "Test builds have the plan simulator" }
        runBlocking { container.preferences.setProMemberSince(null) }
        val open = if (restored) "Reveal your first perk" else "Open your Pro deck"
        if (restored) {
            runBlocking {
                simulator.select(com.lucento.app.data.billing.SimulatedPlan.LIFETIME)
                container.entitlements.refresh()
            }
        } else {
            simulator.setTestStore(true)
            compose.onNodeWithContentDescription("Profile and settings").performClick()
            compose.waitForIdle()
            click("Lucento Pro")
            waitForText("Buy Lifetime", 15_000)
            compose.onNodeWithText("Buy Lifetime").performScrollTo().performClick()
        }
        waitForText(open, 20_000)
        // The deck can be in the tree a moment before it reaches the screen (round 3's dark shot caught the Pro page).
        shot("$tag-00-deck", settle = 1_500)
        click(open)
        var card = 1
        while (card < 20) {
            Thread.sleep(1_000)
            shot("$tag-%02d-card".format(card))
            if (compose.onAllNodesWithText("See your collection").fetchSemanticsNodes().isNotEmpty()) {
                click("See your collection")
                break
            }
            click("Next")
            card++
        }
        waitForText("Your Pro collection", 10_000)
        Thread.sleep(1_200)
        shot("$tag-20-collection")
        click("Collect your award")
        waitForText("Membership award", 10_000)
        Thread.sleep(1_500)
        shot("$tag-21-award")
        click("See your Pro badge")
        // The badge's flight runs on the test's clock, which moves only while the test waits on Compose (a plain sleep
        // would freeze it): wait for the welcome to end (the app is reachable again) and the ring to be said.
        compose.waitUntilAtLeastOneExists(androidx.compose.ui.test.hasContentDescription("Profile and settings"), 15_000)
        compose.waitUntilAtLeastOneExists(
            androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Lucento Pro"),
            10_000,
        )
        shot("$tag-22-ring")
        // The Lucento Pro page offers the cards again (kept apart: a miss here doesn't cost the shots before it).
        runCatching {
            compose.waitUntilExactlyOneExists(androidx.compose.ui.test.hasContentDescription("Profile and settings"), 10_000)
            compose.onNodeWithContentDescription("Profile and settings").performClick()
            compose.waitForIdle()
            click("Lucento Pro")
            waitForText("See your Pro cards", 15_000)
            runCatching { compose.onNodeWithText("See your Pro cards").performScrollTo() }
            shot("$tag-23-pro-page")
        }
        simulator.setTestStore(false)
    }

    private fun shot(name: String, settle: Long = 300) {
        compose.waitForIdle()
        Thread.sleep(settle)
        shell("screencap -p /data/local/tmp/lucento-reveal/$name.png")
    }

    private fun shell(command: String): String {
        val pfd = instrumentation.uiAutomation.executeShellCommand(command)
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { String(it.readBytes()) }
    }

    private fun waitForText(text: String, timeout: Long = 10_000) =
        compose.waitUntilAtLeastOneExists(hasText(text, substring = true, ignoreCase = true), timeout)

    private fun click(text: String, substring: Boolean = false) {
        waitForText(text)
        val node = compose.onNodeWithText(text, substring = substring)
        runCatching { node.performScrollTo() }
        node.performClick()
        compose.waitForIdle()
    }

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
