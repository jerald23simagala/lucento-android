package com.lucento.app

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lucento.app.core.money.Money
import com.lucento.app.data.preferences.ThemeId
import com.lucento.app.data.preferences.ThemeMode
import com.lucento.app.data.repository.WalletRepository
import com.lucento.app.domain.WalletType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Screens for the website that the tour doesn't take (opt-in: `-e websiteShowcase true`, skipped otherwise, so CI never
 * runs it). From the same demo month as the tour, in pesos:
 * - the Wallets gallery in every preset theme and mode (`theme-<id>-<mode>`), for the site's theme switcher;
 * - wallets in other currencies (US dollars, UAE dirhams, Japanese yen) beside the peso ones, and a dollar wallet's page,
 *   for the multicurrency section.
 *
 * Captures go through the same host-capture handshake as the tour (`-e hostCapture true` +
 * tools/screenshots/host_capture.sh) or `screencap` where the emulator allows it. The foreign wallets' rates are fixed
 * here (the test runs offline), so the converted values are the app's own arithmetic on those rates.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class WebsiteShowcaseTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val container get() = (instrumentation.targetContext.applicationContext as LucentoApplication).container
    private val arguments get() = InstrumentationRegistry.getArguments()
    private val hostCapture: Boolean get() = arguments.getString("hostCapture") == "true"

    @Test
    fun showcase() {
        assumeTrue("Opt-in: -e websiteShowcase true", arguments.getString("websiteShowcase") == "true")

        // ---------------------------------------------------------------- onboarding, in pesos
        click("Get started")
        click("Keep it on this phone only")
        click("Continue")
        waitForText("What do you want Lucento to help with?")
        click("Continue")
        waitForText("How do you pay?")
        click("Continue")
        waitForText("What do you spend on?")
        click("Review", substring = true)
        waitForText("Your spending categories")
        click("Confirm", substring = true)
        waitForText("Currency and look")
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("PHP")
        waitForText("PHP ·")
        compose.onNodeWithText("PHP ·", substring = true).performClick()
        closeKeyboard()
        click("Continue")
        waitForText("Lucento is personalized")
        click("Continue to set up")
        waitForText("Add your accounts")
        runBlocking { container.preferences.setGuideStep(null) }
        waitForText("Let’s set up your money")

        // ---------------------------------------------------------------- the demo month, on a simulated Pro plan
        val simulator = checkNotNull(container.planSimulator) { "Test builds have the plan simulator" }
        runBlocking {
            simulator.select(com.lucento.app.data.billing.SimulatedPlan.PRO_MONTHLY)
            container.entitlements.refresh()
        }
        val currency = runBlocking { container.preferences.preferences.first().primaryCurrency }
        check(currency == "PHP") { "Onboarding should have chosen pesos, got $currency" }
        runBlocking { DemoMonth.seed(container, currency, scale = 10, today = LocalDate.now()) }
        waitForText("Monthly budget")
        waitForToastsGone()

        // ---------------------------------------------------------------- the Wallets gallery in every theme
        tab("Wallets")
        waitForText("Assets")
        val looks = listOf(
            ThemeId.SUNRISE to ThemeMode.LIGHT, ThemeId.SUNRISE to ThemeMode.DARK,
            ThemeId.MIDNIGHT to ThemeMode.DARK,
            ThemeId.GROVE to ThemeMode.LIGHT, ThemeId.GROVE to ThemeMode.DARK,
            ThemeId.MONO to ThemeMode.LIGHT, ThemeId.MONO to ThemeMode.DARK,
            ThemeId.AURORA to ThemeMode.LIGHT, ThemeId.AURORA to ThemeMode.DARK,
        )
        for ((theme, mode) in looks) {
            runBlocking {
                container.preferences.setThemeId(theme)
                container.preferences.setThemeMode(mode)
            }
            compose.waitForIdle()
            Thread.sleep(600) // the theme's colour transition
            waitForToastsGone()
            shot("theme-${theme.name.lowercase()}-${mode.name.lowercase()}")
        }
        runBlocking {
            container.preferences.setThemeId(ThemeId.SUNRISE)
            container.preferences.setThemeMode(ThemeMode.LIGHT)
        }
        compose.waitForIdle()

        // ---------------------------------------------------------------- wallets in other currencies
        runBlocking {
            val wallets = container.wallets
            fun saved(r: WalletRepository.SaveResult) =
                check(r is WalletRepository.SaveResult.Saved) { "foreign wallet not saved: $r" }
            saved(wallets.create("Dollar savings", WalletType.SAVINGS, Money(1_850_00, "USD"), fxBasisRate = BigDecimal("57.80")))
            saved(wallets.create("Dubai account", WalletType.BANK, Money(4_200_00, "AED"), fxBasisRate = BigDecimal("15.74")))
            saved(wallets.create("Japan trip yen", WalletType.CASH, Money(60_000, "JPY"), fxBasisRate = BigDecimal("0.3825")))
        }
        compose.waitForIdle()
        waitForText("Dollar savings")
        waitForToastsGone()
        shot("mc-wallets-top")
        scrollToText("Japan trip yen")
        shot("mc-wallets-gallery")
        compose.onNodeWithContentDescription("Row view").performScrollTo().performClick()
        compose.waitForIdle()
        scrollToText("Dollar savings")
        shot("mc-wallets-rows")
        openRow("Dollar savings")
        waitForText("Adjust balance")
        shot("mc-wallet-usd")
        back()
        tab("Home")
        waitForText("Monthly budget")
        waitForToastsGone()
        shot("mc-home")
    }

    // ---------------------------------------------------------------- helpers (as in ScreenshotTourTest)

    private fun waitForText(text: String, substring: Boolean = true, timeout: Long = 10_000) =
        compose.waitUntilAtLeastOneExists(hasText(text, substring = substring, ignoreCase = true), timeout)

    private fun click(text: String, substring: Boolean = false) {
        waitForText(text, substring)
        val node = compose.onNodeWithText(text, substring = substring)
        runCatching { node.performScrollTo() }
        node.performClick()
        compose.waitForIdle()
    }

    private fun tab(label: String) {
        compose.onNodeWithContentDescription(label).performClick()
        compose.waitForIdle()
    }

    private fun back() {
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
    }

    private fun exists(text: String) =
        compose.onAllNodes(hasText(text, substring = true, ignoreCase = true)).fetchSemanticsNodes().isNotEmpty()

    private fun openRow(text: String) {
        scrollToText(text)
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun scrollToText(text: String) {
        val lists = compose.onAllNodes(hasScrollToNodeAction())
        val count = lists.fetchSemanticsNodes().size
        for (i in 0 until count) {
            if (runCatching { lists[i].performScrollToNode(hasText(text)) }.isSuccess) break
        }
        compose.waitForIdle()
    }

    private fun waitForToastsGone() {
        runCatching { compose.waitUntil(15_000) { !exists("Milestone:") } }
        compose.waitForIdle()
    }

    private fun closeKeyboard() {
        runCatching { androidx.test.espresso.Espresso.closeSoftKeyboard() }
        compose.waitForIdle()
        Thread.sleep(400)
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(400)
        val dir = "/data/local/tmp/lucento-shots"
        shell("mkdir -p $dir")
        if (!hostCapture) {
            shell("screencap -p $dir/$name.png")
            return
        }
        shell("rm -f $dir/$name.done")
        shell("touch $dir/$name.request")
        val deadline = android.os.SystemClock.uptimeMillis() + 30_000
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            if (shellOutput("ls $dir/$name.done").contains("$name.done")) break
            Thread.sleep(150)
        }
        shell("rm -f $dir/$name.request $dir/$name.done")
    }

    private fun shell(command: String) {
        val pfd = instrumentation.uiAutomation.executeShellCommand(command)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
    }

    private fun shellOutput(command: String): String {
        val pfd = instrumentation.uiAutomation.executeShellCommand(command)
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { String(it.readBytes()) }
    }
}
