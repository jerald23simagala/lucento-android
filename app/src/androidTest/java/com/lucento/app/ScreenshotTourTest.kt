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

    /** Budgets lives in Home's Quick links. */
    private fun openBudgets() {
        tab("Home")
        compose.onNodeWithContentDescription("Budgets").performScrollTo().performClick()
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
        waitForText("Get started")
        shot("01-onboarding-welcome")
        click("Get started")
        waitForText("main focus")
        click("Skip") // optional questions only: it leads to the mandatory categories step
        waitForText("What do you spend on?")
        shot("02-onboarding-categories")
        click("Review", substring = true)
        waitForText("Your spending categories")
        shot("03-onboarding-review")
        click("Confirm", substring = true)
        waitForText("Currency and look")
        click("Continue")
        waitForText("Back up and sync?")
        shot("04a-onboarding-sync")
        // The tour stays offline: no account is connected. A build without a sync key (CI) shows only Continue here;
        // one with sync offers "Not now — keep it on this phone".
        if (compose.onAllNodes(hasText("Not now", substring = true)).fetchSemanticsNodes().isNotEmpty()) {
            click("Not now", substring = true)
        } else {
            click("Continue")
        }
        waitForText("You’re all set")
        shot("04-onboarding-confirm")
        click("Start using Lucento")

        // ---------------------------------------------------------------- guided setup (first page), then Home
        waitForText("Add your accounts")
        shot("04b-guide-accounts")
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
            val wallets = container.wallets
            fun id(r: WalletRepository.SaveResult) = (r as WalletRepository.SaveResult.Saved).id
            val bank = id(wallets.create("Everyday bank", WalletType.BANK, money(3_250)))
            val cash = id(wallets.create("Cash", WalletType.CASH, money(1_200)))
            val savings = id(wallets.create("Savings", WalletType.SAVINGS, money(8_400)))
            id(wallets.create("Visa card", WalletType.CREDIT_CARD, money(640)))
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

            val recurring = container.recurring
            fun schedule(name: String, amount: Money, start: LocalDate, category: Long?) = RecurringRepository.Input(
                null, name, TransactionType.EXPENSE, amount, bank, category, Frequency.MONTHLY, 1, start, null,
            )
            recurring.save(schedule("Gym membership", money(29, 99), today.minusDays(3), null))
            recurring.save(schedule("Phone plan", money(35), today.plusDays(5), cat("Mobile phone")))
            recurring.save(schedule("Rent", money(1_500), today.plusDays(12), cat("Rent")))
        }

        // ---------------------------------------------------------------- dashboard: within budget (happy sun)
        val total = MoneyFormatter.format(money(2_590))
        waitForText("Monthly budget")
        waitForText(total) // the pace card's budget figure is the sum of the category amounts
        waitForText("days left") // the compact pace card: bars are self-labelled, the stats end with days left
        shot("06-home-within-budget")
        runCatching { compose.onNodeWithText("Nothing is recorded until you mark", substring = true).performScrollTo() }
        shot("07-home-lower-cards")

        // ---------------------------------------------------------------- the composer: several transactions in one message
        compose.onNodeWithContentDescription("Add transaction").performClick()
        waitForText("Describe what happened")
        shot("08-composer-empty")
        compose.onNode(hasSetTextAction()).performTextInput("Lunch 45, groceries 120, and paid the electricity bill")
        compose.onNodeWithContentDescription("Send").performClick()
        waitForText("Found 3 transactions")
        waitForText("1 needs a look")
        shot("09-composer-rows")
        waitForText("Fix 1 highlighted to save")
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
        // The history is a lazy list: rows below the fold aren't composed yet, so scroll the list itself to the row.
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Weekly shop"))
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
        // The dock is Home · Transactions · + · Insights · Wallets; Budgets opens from Home's Quick links.
        tab("Wallets")
        waitForText("Assets")
        shot("12-wallets")
        tab("Insights")
        waitForText("Income and expenses")
        shot("13-insights-trends")
        runCatching { compose.onNodeWithText("Savings goals aren’t added here", substring = true).performScrollTo() }
        shot("14-insights-net-worth")
        openBudgets()
        waitForText("left of $total")
        shot("15-budget")
        compose.onNodeWithContentDescription("Manage categories").performClick()
        compose.waitForIdle()
        waitForText("Active", substring = true)
        shot("16-manage-categories")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()

        // ---------------------------------------------------------------- Quick links and Profile menu
        tab("Home")
        compose.onNodeWithContentDescription("Debts and loans").performScrollTo()
        shot("17-quick-links")
        compose.onNodeWithContentDescription("Scheduled transactions").performClick()
        waitForText("Gym membership")
        shot("18-recurring")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
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
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        tab("Home")

        // ---------------------------------------------------------------- almost used: Lucento turns supportively concerned
        runBlocking {
            val categories = container.categories.observeCategories().first()
            val restaurants = categories.first { it.name == "Restaurants" }.id
            val bank = container.wallets.observeWallets().first().first { it.name == "Everyday bank" }.id
            container.transactions.save(
                TransactionRepository.Input(null, UUID.randomUUID().toString(), TransactionType.EXPENSE, money(260), bank, restaurants, today, "Celebration dinner"),
            )
        }
        waitForText("Monthly budget")
        compose.waitUntilAtLeastOneExists(hasContentDescription("gently concerned", substring = true), 10_000)
        runCatching { compose.onNodeWithContentDescription("Profile and settings").performScrollTo() }
        shot("22-home-budget-almost-used-dark")
    }
}
