package com.lucento.app

import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
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
        shell("screencap -p /data/local/tmp/lucento-shots/$name.png")
        runCatching {
            val bitmap: Bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return@runCatching
            val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
            FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun shell(command: String) {
        // Reading the output to the end waits for the command to finish; the stream closes the descriptor.
        val pfd = instrumentation.uiAutomation.executeShellCommand(command)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
    }

    @Test
    fun tour() {
        // ---------------------------------------------------------------- onboarding
        // Part B onboarding: workspace choice, focus, how you pay, categories, currency, backups, summary.
        waitForText("Start fresh")
        shot("01-onboarding-welcome")
        click("Start fresh")
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
        shot("03b-onboarding-currency")
        click("Continue")
        // The tour stays offline: no account is connected, everything stays on this phone. (With sync configured the
        // account path is preselected and Continue appears only once another path is chosen.)
        waitForText("Keep it on this phone only")
        shot("04a-onboarding-backups")
        click("Keep it on this phone only")
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
        val today = LocalDate.now()
        val currency = runBlocking { container.preferences.preferences.first().primaryCurrency }
        fun money(major: Long, cents: Long = 0) = Money(major * 100 + cents, currency)
        val day = { d: Int -> today.withDayOfMonth(minOf(d, today.dayOfMonth)) }
        runBlocking {
            // Part B onboarding switches on only what the focus step chose; the tour visits every module.
            com.lucento.app.domain.Feature.entries.forEach { container.preferences.setFeatureEnabled(it, true) }
            val wallets = container.wallets
            fun id(r: WalletRepository.SaveResult) = (r as WalletRepository.SaveResult.Saved).id
            val bank = id(wallets.create("Everyday bank", WalletType.BANK, money(3_250)))
            val cash = id(wallets.create("Cash", WalletType.CASH, money(1_200)))
            val savings = id(wallets.create("Savings", WalletType.SAVINGS, money(8_400)))
            // A card with a limit and billing days: its statement closed ten days ago and its payment is due in five, so
            // Home lists the payment and the card shows its credit limit, available credit and amount due.
            id(
                wallets.create(
                    "Visa card", WalletType.CREDIT_CARD, money(640),
                    DebtTerms(
                        interestRateBps = 2400,
                        creditLimit = money(5_000),
                        statementDay = today.minusDays(10).dayOfMonth,
                        dueDay = today.plusDays(5).dayOfMonth,
                    ),
                ),
            )
            // An instalment loan with three repayments recorded in earlier months (3 of 24 paid).
            val loan = id(
                wallets.create(
                    "Car loan", WalletType.LOAN, money(12_000),
                    DebtTerms(interestRateBps = 2400, repayment = DebtRepayment.MONTHLY, termMonths = 24, installment = money(634, 44)),
                ),
            )
            // The composer's wallet bar starts on the default, so it opens without asking which wallet to use.
            container.preferences.setDefaultWallet(bank)

            val categories = container.categories.observeCategories().first()
            fun cat(name: String) = categories.first { it.name == name && !it.archived }.id
            val salary = categories.first { it.kind == CategoryKind.INCOME && it.name == "Salary" }.id
            val tx = container.transactions
            suspend fun add(type: TransactionType, amount: Money, wallet: Long, category: Long?, date: LocalDate, note: String) {
                val r = tx.save(TransactionRepository.Input(null, UUID.randomUUID().toString(), type, amount, wallet, category, date, note))
                check(r is TransactionRepository.SaveResult.Saved) { "seed failed: $r" }
            }
            for (monthsBack in 2L downTo 1L) {
                val d = today.minusMonths(monthsBack).withDayOfMonth(10)
                add(TransactionType.INCOME, money(4_200), bank, salary, d, "Salary")
                add(TransactionType.EXPENSE, money(1_500), bank, cat("Rent"), d, "Rent")
                add(TransactionType.EXPENSE, money(380 + monthsBack * 40), bank, cat("Groceries"), d, "Groceries")
                add(TransactionType.EXPENSE, money(210), cash, cat("Restaurants"), d, "Dinners")
            }
            add(TransactionType.INCOME, money(4_200), bank, salary, day(1), "Salary")
            add(TransactionType.EXPENSE, money(1_500), bank, cat("Rent"), day(2), "Rent")
            add(TransactionType.EXPENSE, money(212, 40), bank, cat("Groceries"), day(4), "Weekly shop")
            add(TransactionType.EXPENSE, money(107, 60), bank, cat("Groceries"), day(9), "Market")
            add(TransactionType.EXPENSE, money(145), cash, cat("Restaurants"), day(6), "Dinner out")
            add(TransactionType.EXPENSE, money(96), bank, cat("Electricity"), day(5), "Power bill")
            add(TransactionType.EXPENSE, money(60), bank, cat("Internet"), day(3), "Fibre")
            add(TransactionType.EXPENSE, money(48), cash, cat("Public transportation"), day(7), "Transit card")
            add(TransactionType.EXPENSE, money(15, 99), bank, cat("Streaming services"), day(8), "Streaming")
            add(TransactionType.EXPENSE, money(89), bank, cat("Clothing and shoes"), day(8), "Shoes")
            add(TransactionType.EXPENSE, money(24), cash, null, day(8), "Uncategorised")

            val budgets = container.budgets
            listOf(
                "Rent" to money(1_500), "Groceries" to money(450), "Restaurants" to money(200), "Electricity" to money(120),
                "Internet" to money(60), "Public transportation" to money(80), "Streaming services" to money(20),
                "Clothing and shoes" to money(100), "Personal care" to money(60),
            ).forEach { (name, amount) -> budgets.setAllocation(cat(name), currency, amount) }

            val goals = container.goals
            val emergency = (goals.save(null, "Emergency fund", money(5_000), null, savings) as GoalRepository.SaveResult.Saved).id
            val trip = (goals.save(null, "Japan trip", money(3_000), today.plusMonths(7), savings) as GoalRepository.SaveResult.Saved).id
            goals.contribute(emergency, money(2_100), today.minusMonths(2), "", UUID.randomUUID().toString())
            goals.contribute(trip, money(1_650), today.minusMonths(1), "", UUID.randomUUID().toString())

            (1..3).forEach { n ->
                container.transactions.saveTransfer(
                    TransactionRepository.TransferInput(
                        group = null, clientUuid = UUID.randomUUID().toString(), fromWalletId = bank, toWalletId = loan,
                        amount = money(400), date = today.minusMonths(n.toLong()).withDayOfMonth(1), note = "",
                    ),
                )
            }

            val recurring = container.recurring
            fun schedule(name: String, amount: Money, start: LocalDate, category: Long?) = RecurringRepository.Input(
                null, name, TransactionType.EXPENSE, amount, bank, category, Frequency.MONTHLY, 1, start, null,
            )
            // Every scheduled bill has a category now (owner, 2026-11-03); the internet bill is overdue.
            recurring.save(schedule("Fibre internet", money(29, 99), today.minusDays(3), cat("Internet")))
            recurring.save(schedule("Phone plan", money(35), today.plusDays(5), cat("Mobile phone")))
            recurring.save(schedule("Rent", money(1_500), today.plusDays(12), cat("Rent")))
            // A loan instalment: a transfer from the bank to the loan, shown in neutral ink and never in Bills due.
            recurring.save(
                RecurringRepository.Input(
                    null, "Car loan instalment", TransactionType.EXPENSE, money(634, 44), bank, null, Frequency.MONTHLY, 1,
                    today.plusDays(8), null, toWalletId = loan,
                ),
            )
        }

        // ---------------------------------------------------------------- dashboard: within budget (happy sun)
        val total = MoneyFormatter.format(money(2_590))
        waitForText("Monthly budget")
        waitForText(total) // the pace card's budget figure is the sum of the category amounts
        // Bills due counts every scheduled bill, whatever its category; Safe to spend always closes the card (0 in red
        // once the bills take what's left).
        waitForText("Bills due")
        waitForText("Safe to spend")
        shot("06-home-within-budget")
        // The bell lists scheduled payments due this week (the seeded gym membership is overdue).
        compose.onNode(hasContentDescription("Upcoming payments", substring = true)).performClick()
        waitForText("See all scheduled")
        shot("06b-home-bell")
        back()
        runCatching { compose.onNodeWithText("Nothing is recorded until you mark", substring = true).performScrollTo() }
        shot("07-home-lower-cards")

        // ---------------------------------------------------------------- the composer: several transactions in one message
        compose.onNodeWithContentDescription("Add transaction").performClick()
        waitForText("Tell me about your transaction")
        shot("08-composer-empty")
        compose.onNode(hasSetTextAction()).performTextInput("Lunch 45, groceries 120, and paid the electricity bill")
        compose.onNodeWithContentDescription("Send").performClick()
        // The save bar is always in view (one compact line while the keyboard is up); one of the three rows needs a fix
        // before anything can be saved.
        waitForText("1 transaction is incomplete")
        shot("09-composer-rows")
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
        shot("20-home-dark")
        openBudgets()
        shot("21-budget-dark")
        tab("Home")

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
    }
}
