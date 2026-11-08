package com.lucento.app

import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.data.repository.GoalRepository
import com.lucento.app.data.repository.RecurringRepository
import com.lucento.app.data.repository.TransactionRepository
import com.lucento.app.data.repository.WalletRepository
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.DebtRepayment
import com.lucento.app.domain.DebtTerms
import com.lucento.app.domain.Frequency
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.WalletType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.util.UUID

/**
 * End-to-end tour of the 0.5 experience on a real emulator: mandatory category onboarding, the dock and its five
 * destinations, the + Add Transaction sheet, Quick links and the Profile menu, the dashboard in its different budget states,
 * Budget, Insights, light and dark. It asserts the key behaviours and saves a screenshot of every screen for design
 * review (CI pulls them from /data/local/tmp/lucento-shots and uploads them as an artifact).
 *
 * It starts from a fresh install (as CI does). Data is created through the app's own repositories, so it follows
 * every validation rule; nothing is injected into the UI.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ScreenshotTourTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val container get() = (instrumentation.targetContext.applicationContext as LucentoApplication).container

    private fun waitForText(text: String, substring: Boolean = true, timeout: Long = 10_000) {
        try {
            // Case-insensitive: section headers are rendered in upper case.
            compose.waitUntilAtLeastOneExists(hasText(text, substring = substring, ignoreCase = true), timeout)
        } catch (e: Throwable) {
            // Make the failure self-explanatory in CI annotations: what was on screen instead.
            shot("zz-failure-waiting-for-${text.filter { it.isLetterOrDigit() }.take(30)}")
            val tree = runCatching { compose.onAllNodes(isRoot()).printToString(maxDepth = 12) }.getOrDefault("(no tree)")
            throw AssertionError("Waited for '$text'. Screen text:\n" + tree.lines().filter { "Text" in it || "ContentDescription" in it }.joinToString("\n").take(2500), e)
        }
    }

    private fun waitForDescription(description: String, timeout: Long = 10_000) =
        compose.waitUntilAtLeastOneExists(hasContentDescription(description), timeout)

    private fun click(text: String, substring: Boolean = false) {
        waitForText(text, substring)
        val node = compose.onNodeWithText(text, substring = substring)
        runCatching { node.performScrollTo() } // only when inside a scrolling container
        node.performClick()
        compose.waitForIdle()
    }

    /** Budget is a dock tab. */
    private fun openBudgets() {
        tab("Budget")
    }

    /**
     * Opens a row of a lazy list by its text: scrolls whichever list holds it into view, then runs the row's click
     * action directly, so a row that ends up under the floating dock is still opened rather than the dock.
     */
    private fun openRow(text: String) {
        val lists = compose.onAllNodes(hasScrollToNodeAction())
        val count = lists.fetchSemanticsNodes().size
        for (i in 0 until count) {
            if (runCatching { lists[i].performScrollToNode(hasText(text)) }.isSuccess) break
        }
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    /** Scrolls whichever lazy list holds [text] until it is on screen (rows off screen aren't composed yet). */
    private fun scrollToText(text: String) {
        val lists = compose.onAllNodes(hasScrollToNodeAction())
        val count = lists.fetchSemanticsNodes().size
        for (i in 0 until count) {
            if (runCatching { lists[i].performScrollToNode(hasText(text)) }.isSuccess) break
        }
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

    /** Back until [marker] is gone (the first back may only close the keyboard). */
    private fun backUntilGone(marker: String) {
        repeat(3) {
            if (!exists(marker)) return
            back()
            Thread.sleep(300)
        }
    }

    /** Full-screen capture (includes bottom sheets, which live in their own window). */
    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(400) // let the sheet / navigation transition settle
        // The shell user writes the capture to /data/local/tmp, which survives the app being uninstalled after the run
        // and needs no storage permission. A copy is also kept in the app's files for local debugging.
        shell("mkdir -p /data/local/tmp/lucento-shots")
        if (hostCapture) {
            requestHostCapture(name)
            return
        }
        shell("screencap -p /data/local/tmp/lucento-shots/$name.png")
        runCatching {
            val bitmap: Bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return@runCatching
            val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
            FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    /**
     * Opt-in (`-e hostCapture true`): some API 36.1 emulator hosts can't read pixels back inside the guest (screencap
     * and UiAutomation screenshots abort on `hasReadColorBufferDma`). The tour then asks a host script
     * (tools/screenshots/host_capture.sh) to take the frame with `adb emu screenrecord screenshot`: it leaves a request
     * marker and waits until the host answers, so the screen can't change before the capture. Off by default (CI).
     */
    private val hostCapture: Boolean
        get() = InstrumentationRegistry.getArguments().getString("hostCapture") == "true"

    /**
     * Opt-in (`-e demoCurrency PHP`): the website's screenshots show a Philippine household, so onboarding picks pesos
     * and every seeded amount is scaled ×10 (salary ₱42,000, rent ₱15,000, car loan ₱120,000). Off by default: CI keeps
     * the device's currency (US dollars on the emulator) and the original figures.
     */
    private val demoCurrency: String?
        get() = InstrumentationRegistry.getArguments().getString("demoCurrency")?.uppercase()?.takeIf { it.length == 3 }
    private val demoScale: Long get() = if (demoCurrency == "PHP") 10 else 1

    /** The composer message, in amounts that suit the demo currency. */
    private val composerMessage: String
        get() = if (demoCurrency == "PHP") "Lunch 150, groceries 1200, and paid the electricity bill"
        else "Lunch 45, groceries 120, and paid the electricity bill"

    /** Waits (up to 15 s, never failing the tour) for a milestone toast to finish, so a screenshot isn't covered. */
    private fun waitForToastsGone() {
        runCatching { compose.waitUntil(15_000) { !exists("Milestone:") } }
        compose.waitForIdle()
    }

    /** Hides the keyboard if it is up (no-op otherwise), so a screenshot shows the whole screen. */
    private fun closeKeyboard() {
        runCatching { androidx.test.espresso.Espresso.closeSoftKeyboard() }
        compose.waitForIdle()
        Thread.sleep(400) // the keyboard's own hide animation
    }

    private fun requestHostCapture(name: String) {
        val dir = "/data/local/tmp/lucento-shots"
        shell("rm -f $dir/$name.done")
        shell("touch $dir/$name.request")
        val deadline = android.os.SystemClock.uptimeMillis() + 30_000
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            // UiAutomation runs the command without a shell (no redirection); a missing file only writes to stderr.
            if (shellOutput("ls $dir/$name.done").contains("$name.done")) break
            Thread.sleep(150)
        }
        shell("rm -f $dir/$name.request $dir/$name.done")
    }

    private fun shell(command: String) {
        // Reading the output to the end waits for the command to finish; the stream closes the descriptor.
        val pfd = instrumentation.uiAutomation.executeShellCommand(command)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
    }

    private fun shellOutput(command: String): String {
        val pfd = instrumentation.uiAutomation.executeShellCommand(command)
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { String(it.readBytes()) }
    }

    @Test
    fun tour() {
        // ---------------------------------------------------------------- onboarding
        // Onboarding: keep your money safe (account or this phone), focus, how you pay, categories, currency, summary.
        waitForText("Get started")
        shot("01-onboarding-welcome")
        click("Get started")
        // "Keep your money safe" comes right after Get started (owner, 2026-11-06). The tour stays offline: no account
        // is connected, everything stays on this phone. (Without sync in the build, this phone is the only choice.)
        waitForText("Keep it on this phone only")
        shot("01a-onboarding-backups")
        click("Keep it on this phone only")
        click("Continue")
        waitForText("What do you want Lucento to help with?")
        shot("01b-onboarding-focus")
        click("Continue") // tracking spending and budgeting are always included
        waitForText("How do you pay?")
        click("Continue")
        waitForText("What do you spend on?")
        shot("02-onboarding-categories")
        click("Review", substring = true)
        waitForText("Your spending categories")
        shot("03-onboarding-review")
        click("Confirm", substring = true)
        waitForText("Currency and look")
        demoCurrency?.let { code ->
            compose.onAllNodes(hasSetTextAction())[0].performTextInput(code)
            waitForText("$code ·")
            compose.onNodeWithText("$code ·", substring = true).performClick()
            closeKeyboard()
        }
        shot("03b-onboarding-currency")
        click("Continue")
        waitForText("Lucento is personalized")
        shot("04-onboarding-confirm")
        click("Continue to set up")

        // ---------------------------------------------------------------- guided setup (first page), then Home
        waitForText("Add your accounts")
        shot("04b-guide-accounts")
        // The budget step: category rows with an amount field each, and the total that is always their sum.
        runBlocking { container.preferences.setGuideStep(com.lucento.app.domain.GuideStep.BUDGETS) }
        waitForText("Set your monthly budget")
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("250")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("120.50")
        waitForText("The sum of these amounts")
        shot("04c-guide-budgets")
        // The tour seeds its own data below, so it ends the guide here the way "Go to Home" does.
        runBlocking { container.preferences.setGuideStep(null) }

        // ---------------------------------------------------------------- empty dashboard
        waitForText("Let’s set up your money")
        shot("05-home-first-run")

        // ---------------------------------------------------------------- seed realistic data through the repositories
        // Lucento Pro: the tour shows every module, so it runs on a simulated Pro plan (test builds only); the free plan's
        // locks, the plan-ended notice and the Pro page are captured at the end.
        val simulator = checkNotNull(container.planSimulator) { "Test builds have the plan simulator" }
        runBlocking {
            simulator.select(com.lucento.app.data.billing.SimulatedPlan.PRO_MONTHLY)
            container.entitlements.refresh()
        }
        val today = LocalDate.now()
        val currency = runBlocking { container.preferences.preferences.first().primaryCurrency }
        fun money(major: Long, cents: Long = 0) = Money((major * 100 + cents) * demoScale, currency)
        // The demo month (wallets, three months of salary and spending, budgets, goals, loan payments, schedules) is
        // shared with WebsiteShowcaseTest, so the website's screens and the tour show the same figures.
        runBlocking { DemoMonth.seed(container, currency, demoScale, today) }

        // ---------------------------------------------------------------- dashboard: within budget (happy sun)
        val total = MoneyFormatter.format(money(2_590))
        waitForText("Monthly budget")
        waitForText(total) // the pace card's budget figure is the sum of the category amounts
        // Bills due counts every scheduled bill, whatever its category; Safe to spend always closes the card (0 in red
        // once the bills take what's left).
        waitForText("Bills due")
        waitForText("Safe to spend")
        waitForToastsGone()
        shot("06-home-within-budget")
        // The bell lists scheduled payments due this week (the seeded gym membership is overdue).
        compose.onNode(hasContentDescription("Upcoming payments", substring = true)).performClick()
        waitForText("See all scheduled")
        shot("06b-home-bell")
        // A card's payment in the bell opens the transfer that pays it, filled in: from the everyday account with the
        // most money (the card has never been paid), to the card, for the amount due (owner, round 8).
        compose.onNode(hasText("Visa card payment") and hasAnyAncestor(isPopup())).performClick()
        waitForText("Amount due by")
        waitForText("Change the amount for a partial payment")
        shot("06c-bell-pays-card")
        back()
        compose.waitForIdle()
        runCatching { compose.onNodeWithText("Nothing is recorded until you mark", substring = true).performScrollTo() }
        shot("07-home-lower-cards")

        // ---------------------------------------------------------------- the composer: several transactions in one message
        compose.onNodeWithContentDescription("Add transaction").performClick()
        waitForText("Tell me about your transaction")
        shot("08-composer-empty")
        compose.onNode(hasSetTextAction()).performTextInput(composerMessage)
        compose.onNodeWithContentDescription("Send").performClick()
        // The save bar is always in view (one compact line while the keyboard is up); one of the three rows needs a fix
        // before anything can be saved.
        waitForText("1 transaction is incomplete")
        shot("09-composer-rows")
        // The same review with the keyboard down: every row, and the one that needs a look (website screenshot).
        closeKeyboard()
        waitForToastsGone()
        shot("09b-composer-review")
        compose.onNodeWithContentDescription("Back").performClick()
        waitForText("Discard 3 unsaved transactions?")
        shot("10-composer-discard")
        click("Discard")
        compose.waitForIdle()
        waitForDescription("Add transaction")

        // ---------------------------------------------------------------- tabs
        tab("Transactions")
        waitForText("Transactions")
        shot("11-transactions")
        // A left swipe reveals Edit and Delete; a tap opens the read-only View page, whose Edit pill opens the editor.
        // Search narrows the history to the row, so it sits near the top: scrolled to the bottom edge of the list on a
        // short screen it would lie under the floating dock, which would take the swipe.
        compose.onNodeWithContentDescription("Search transactions").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Weekly")
        compose.waitForIdle()
        waitForText("Weekly shop")
        compose.onNodeWithText("Weekly shop").performTouchInput { swipeLeft() }
        waitForText("Delete")
        shot("11a-transaction-actions")
        compose.onNodeWithText("Weekly shop").performClick() // closes the open row
        compose.waitForIdle()
        click("Weekly shop")
        waitForDescription("Edit")
        shot("11b-transaction-view")
        compose.onNodeWithContentDescription("Edit").performClick()
        waitForText("Edit transaction")
        shot("11c-transaction-edit")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        tab("Home")
        // The dock is Home · Transactions · + · Budget · Wallets; Insights opens from Home's Quick links.
        tab("Wallets")
        waitForText("Assets")
        shot("12-wallets")
        // New wallet: the card being built is the wallet page's own card, live (the Add button is in the list's header,
        // so this runs while the list is still at the top).
        compose.onNodeWithContentDescription("Add wallet").performClick()
        waitForText("Build it below and watch it take shape.")
        shot("12g-new-wallet")
        backUntilGone("Build it below and watch it take shape.")
        // Cards (the gallery) are the default view (owner, 2026-11-04); the rows view is one tap away and is kept for the
        // account pages below.
        compose.onNodeWithContentDescription("Row view").performScrollTo().performClick()
        compose.waitForIdle()
        shot("12b-wallets-rows")
        // Account pages: an instalment loan (Principal, Paid, Remaining and its instalments), a credit card (limit,
        // available, amount due) and its editor's card details, and an everyday account (Transfer and Adjust balance).
        openRow("Car loan")
        waitForText("3 of 24 installments paid")
        shot("12c-wallet-loan")
        back()
        openRow("Visa card")
        waitForText("Amount due")
        shot("12d-wallet-card")
        compose.onNodeWithContentDescription("Edit account").performClick()
        waitForText("Card details")
        runCatching { compose.onNodeWithText("Statement day").performScrollTo() }
        shot("12e-card-editor")
        backUntilGone("Card details")
        back()
        openRow("Everyday bank")
        waitForText("Adjust balance")
        shot("12f-wallet-bank")
        back()
        tab("Home")
        compose.onNodeWithContentDescription("Insights").performScrollTo().performClick()
        waitForText("Income and expenses")
        shot("13-insights-trends")
        // Ask Lucento: preset questions and reports are worked out on the phone (debug builds have no AI proxy).
        compose.onNodeWithContentDescription("Ask Lucento", substring = true).performClick()
        waitForText("Ask me about your money")
        shot("13a-ask-lucento")
        click("What are my top 5 spending categories?")
        waitForText("Calculated on this phone")
        shot("13b-ask-answer")
        scrollToText("Open report")
        click("Open report")
        waitForText("Save as PDF")
        shot("13c-ask-report")
        backUntilGone("Save as PDF")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        waitForText("Income and expenses")
        runCatching { compose.onNodeWithText("Savings goals aren’t added here", substring = true).performScrollTo() }
        shot("14-insights-net-worth")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        openBudgets()
        waitForText("Remaining of $total")
        shot("15-budget")
        compose.onNodeWithContentDescription("Manage categories").performClick()
        compose.waitForIdle()
        waitForText("Active", substring = true)
        shot("16-manage-categories")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()

        // ---------------------------------------------------------------- Quick links and Profile menu
        tab("Home")
        compose.onNodeWithContentDescription("Debts and loans").performScrollTo()
        shot("17-quick-links")
        // Goals (owner, 2026-11-04 redesign): hero with the overall ring, goals in progress, then the new-goal form with
        // icon suggestions, onboarding-style fields, "Kept in" wallets and the square icon picker.
        compose.onNodeWithContentDescription("Savings goals").performScrollTo().performClick()
        waitForText("In progress")
        shot("17a-goals")
        compose.onNodeWithContentDescription("Add goal").performClick()
        waitForText("New savings goal")
        shot("17b-goal-new")
        scrollToText("Add photo")
        shot("17c-goal-icons")
        backUntilGone("New savings goal")
        back()
        tab("Home")
        // Scroll to the tile itself: after scrolling to Debts, the row above can sit under the pinned header.
        compose.onNodeWithContentDescription("Scheduled transactions").performScrollTo().performClick()
        // The agenda's summary (Transactions' tiles, transfers apart) says what the range holds, overdue included.
        waitForText("overdue included", substring = true)
        shot("18-recurring")
        // The agenda: overdue first, then this month's dates; then each plan once under All schedules.
        scrollToText("Fibre internet")
        shot("18b-recurring-agenda")
        scrollToText("All schedules")
        click("All schedules")
        scrollToText("Car loan instalment")
        waitForText("Car loan instalment")
        shot("18c-recurring-schedules")
        // The top bar scrolled away with the list: leave with the system back.
        back()
        tab("Home")
        waitForText("Monthly budget")
        // The Home header is pinned, so the profile button is always on screen.
        compose.onNodeWithContentDescription("Profile and settings").performClick()
        compose.waitForIdle()
        waitForText("Account and app")
        shot("19-profile-menu")

        // ---------------------------------------------------------------- dark theme
        click("Dark")
        backUntilGone("Account and app")
        waitForText("Monthly budget")
        runCatching { compose.onNodeWithContentDescription("Profile and settings").performScrollTo() }
        waitForToastsGone()
        shot("20-home-dark")
        openBudgets()
        shot("21-budget-dark")
        tab("Home")
        // The composer's review in dark, keyboard down (the website's Night theme pairs it with 09b).
        compose.onNodeWithContentDescription("Add transaction").performClick()
        waitForText("Tell me about your transaction")
        compose.onNode(hasSetTextAction()).performTextInput(composerMessage)
        compose.onNodeWithContentDescription("Send").performClick()
        waitForText("1 transaction is incomplete")
        closeKeyboard()
        waitForToastsGone()
        shot("21b-composer-review-dark")
        compose.onNodeWithContentDescription("Back").performClick()
        waitForText("Discard 3 unsaved transactions?")
        click("Discard")
        compose.waitForIdle()
        waitForDescription("Add transaction")

        // ---------------------------------------------------------------- over in one category: it leads "To watch"
        runBlocking {
            val categories = container.categories.observeCategories().first()
            val restaurants = categories.first { it.name == "Restaurants" }.id
            val bank = container.wallets.observeWallets().first().first { it.name == "Everyday bank" }.id
            container.transactions.save(
                TransactionRepository.Input(null, UUID.randomUUID().toString(), TransactionType.EXPENSE, money(260), bank, restaurants, today, "Celebration dinner"),
            )
        }
        waitForText("Monthly budget")
        // Restaurants ($200 budgeted) is now well over, so it is the first category the budget card asks to watch.
        compose.waitUntilAtLeastOneExists(
            hasContentDescription("Restaurants,", substring = true) and hasContentDescription("of its budget used", substring = true),
            10_000,
        )
        runCatching { compose.onNodeWithContentDescription("Profile and settings").performScrollTo() }
        shot("22-home-budget-almost-used-dark")

        // ---------------------------------------------------------------- hidden amounts on every page (round 7)
        // Hide on the dashboard hides money everywhere it's shown: no figure as text, none in what TalkBack reads.
        compose.onNodeWithContentDescription("Hide amounts").performClick()
        compose.waitForIdle()
        val figures = listOf(money(4_200), money(212, 40), money(1_500), money(5_000), money(3_250)).map { MoneyFormatter.format(it) }
        fun noFigures(page: String) {
            compose.waitForIdle()
            figures.forEach { figure ->
                val leaks = compose.onAllNodes(hasText(figure, substring = true) or hasContentDescription(figure, substring = true), useUnmergedTree = true)
                    .fetchSemanticsNodes()
                check(leaks.isEmpty()) { "$page shows $figure with amounts hidden" }
            }
        }
        waitForText("Show")
        noFigures("Home")
        shot("23-hidden-home")
        tab("Transactions")
        scrollToText("Weekly shop")
        noFigures("Transactions")
        shot("23b-hidden-transactions")
        openRow("Weekly shop")
        waitForDescription("Edit")
        noFigures("Transaction view")
        shot("23c-hidden-transaction-view")
        back()
        openBudgets()
        noFigures("Budget")
        shot("23d-hidden-budget")
        tab("Wallets")
        waitForText("Assets")
        noFigures("Wallets")
        openRow("Everyday bank")
        noFigures("Wallet")
        shot("23e-hidden-wallet")
        back()
        tab("Home")
        compose.onNodeWithContentDescription("Insights").performScrollTo().performClick()
        compose.waitForIdle()
        noFigures("Insights")
        shot("23f-hidden-insights")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Savings goals").performScrollTo().performClick()
        waitForText("In progress")
        noFigures("Goals")
        shot("23g-hidden-goals")
        back()
        compose.onNodeWithContentDescription("Debts and loans").performScrollTo().performClick()
        compose.waitForIdle()
        noFigures("Debts")
        shot("23h-hidden-debts")
        back()
        compose.onNodeWithContentDescription("Scheduled transactions").performScrollTo().performClick()
        waitForText("overdue included", substring = true)
        noFigures("Scheduled")
        shot("23i-hidden-scheduled")

        // ---------------------------------------------------------------- Lucento Pro: the plan ends; the free plan's locks
        back()
        runBlocking {
            simulator.select(com.lucento.app.data.billing.SimulatedPlan.FREE)
            container.entitlements.refresh()
        }
        // Told once, with what changed and that nothing was removed.
        waitForText("Your Lucento Pro has ended")
        shot("24a-plan-ended")
        click("OK")
        tab("Home")
        waitForText("Monthly budget")
        compose.onNodeWithContentDescription("Profile and settings").performClick()
        compose.waitForIdle()
        click("Lucento Pro")
        waitForText("What each plan includes")
        shot("24b-pro-page-free")
        runCatching { compose.onNodeWithText("Pro Lifetime").performScrollTo() }
        shot("24c-pro-page-plans")
        runCatching { compose.onNodeWithText("Restore purchases").performScrollTo() }
        shot("24d-pro-page-purchases")
        // The top bar scrolled away with the page: leave with the system back.
        back()
        // Back lands on Home (the Pro page opened from its profile menu); the dock returns after the page's exit animation.
        waitForText("Monthly budget")
        // Insights opens on the free plan's 3 months; a locked period explains itself only when picked.
        compose.onNodeWithContentDescription("Insights").performScrollTo().performClick()
        waitForText("6 months · Pro")
        shot("24e-insights-free")
        click("6 months · Pro")
        waitForText("Trends over 6 and 12 months are part of Lucento Pro")
        shot("24f-insights-locked-period")

        // ---------------------------------------------------------------- Lucento Pro: buying (Phase 4), with the test store
        // Test builds fake Google Play's purchase flow: nothing is charged and every price says "test". The plan that
        // results comes through the same entitlement manager a real Play purchase does.
        back()
        waitForText("Monthly budget")
        simulator.setTestStore(true)
        compose.onNodeWithContentDescription("Profile and settings").performClick()
        compose.waitForIdle()
        click("Lucento Pro")
        waitForText("Start 7-day free trial")
        runCatching { compose.onNodeWithText("Start 7-day free trial").performScrollTo() }
        shot("24g-pro-page-buy")
        compose.onNodeWithText("Buy Lifetime").performScrollTo().performClick()
        waitForText("Pro Lifetime is yours")
        runCatching { compose.onNodeWithText("Pro Lifetime is yours", substring = true).performScrollTo() }
        shot("24h-bought-lifetime")
        simulator.setTestStore(false)
    }
}
