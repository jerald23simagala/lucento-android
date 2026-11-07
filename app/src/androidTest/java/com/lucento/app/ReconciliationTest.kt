package com.lucento.app

import android.graphics.Bitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.data.repository.TransactionRepository
import com.lucento.app.data.repository.WalletRepository
import com.lucento.app.domain.CategoryKind
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
import java.time.YearMonth
import java.util.UUID

/**
 * Financial integrity across screens (owner, 2026-11-04): the month's spending is one figure — Home's budget card
 * "Used", the Transactions page's "Expenses" for this month and the Budget page's "Spent" — on a ledger shaped like the
 * owner's November (PHP; a month-only amount overriding a recurring one; budgeted and unbudgeted spending; income) plus
 * an expense last month that must not leak into this month. Starts from a fresh install, data through the repositories.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ReconciliationTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val container get() = (instrumentation.targetContext.applicationContext as LucentoApplication).container

    private fun waitForText(text: String, substring: Boolean = true, timeout: Long = 10_000) {
        compose.waitUntil(timeout) { compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForDescription(description: String, timeout: Long = 10_000) {
        compose.waitUntil(timeout) { compose.onAllNodes(hasContentDescription(description, substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun click(text: String, substring: Boolean = false) {
        waitForText(text, substring)
        compose.onAllNodes(hasText(text, substring = substring))[0].performClick()
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(600)
        shell("mkdir -p /data/local/tmp/lucento-recon")
        shell("screencap -p /data/local/tmp/lucento-recon/$name.png")
        runCatching {
            val bitmap: Bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return@runCatching
            val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "recon").apply { mkdirs() }
            FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun shell(command: String) {
        val pfd = instrumentation.uiAutomation.executeShellCommand(command)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
    }

    @Test
    fun monthSpendingIsOneFigureAcrossHomeTransactionsAndBudget() {
        // Onboarding, as the screenshot tour walks it, keeping everything on this phone.
        click("Get started")
        // "Keep your money safe" comes first (owner, 2026-11-06).
        click("Keep it on this phone only")
        click("Continue")
        waitForText("What do you want Lucento to help with?")
        click("Continue")
        waitForText("How do you pay?")
        click("Continue")
        click("Review", substring = true)
        click("Confirm", substring = true)
        waitForText("Currency and look")
        click("Continue")
        click("Continue to set up")
        waitForText("Add your accounts")
        runBlocking { container.preferences.setGuideStep(null) }
        waitForText("Let’s set up your money")

        val today = LocalDate.now()
        val month = YearMonth.from(today)
        val php = "PHP"
        fun peso(major: Long) = Money(major * 100, php)
        val day = { d: Int -> today.withDayOfMonth(minOf(d, today.dayOfMonth)) }
        runBlocking {
            container.preferences.setPrimaryCurrency(php)
            val bank = (container.wallets.create("BPI - Personal", WalletType.BANK, peso(20_000)) as WalletRepository.SaveResult.Saved).id
            container.preferences.setDefaultWallet(bank)
            val categories = container.categories
            suspend fun expense(name: String) = checkNotNull(categories.ensure(name, CategoryKind.EXPENSE, "category"))
            val restaurants = expense("Restaurants")
            val water = expense("Water")
            val rent = expense("Rent")
            val electricity = expense("Electricity")
            val groceries = expense("Groceries")
            val salary = checkNotNull(categories.ensure("Salary", CategoryKind.INCOME, "salary"))

            // Rent: ₱6,000 from this month on, then ₱5,000 for this month only (the month-only amount wins).
            container.budgets.setAllocation(rent, php, peso(6_000))
            container.budgets.setMonthAmount(rent, php, month, peso(5_000))
            container.budgets.setAllocation(electricity, php, peso(235))

            suspend fun add(type: TransactionType, amount: Money, category: Long?, date: LocalDate, note: String) {
                val r = container.transactions.save(
                    TransactionRepository.Input(null, UUID.randomUUID().toString(), type, amount, bank, category, date, note),
                )
                check(r is TransactionRepository.SaveResult.Saved) { "seed failed: $r" }
            }
            add(TransactionType.EXPENSE, peso(250), restaurants, day(3), "Lunch")
            add(TransactionType.EXPENSE, peso(800), water, day(3), "Water bill")
            add(TransactionType.INCOME, peso(35_000), salary, day(3), "Salary")
            add(TransactionType.EXPENSE, peso(450), restaurants, day(3), "Dinner")
            add(TransactionType.EXPENSE, peso(4_600), rent, day(4), "Rent")
            add(TransactionType.EXPENSE, peso(467), electricity, day(4), "Power")
            // Last month: must never be counted in this month's figures.
            add(TransactionType.EXPENSE, peso(1_000), groceries, today.minusMonths(1).withDayOfMonth(15), "Groceries")
        }
        val spent = MoneyFormatter.format(peso(6_567))
        // Every screen is checked before failing, so one run shows all three.
        val mismatches = mutableListOf<String>()
        fun expect(what: String, check: () -> Unit) = runCatching(check).onFailure { mismatches += "$what: ${it.message}" }

        // Home: the budget card's Spent (the same word as the Budget page).
        waitForText("Monthly budget")
        compose.onNodeWithText("Monthly budget").performScrollTo()
        shot("01-home-budget")
        expect("Home Spent") { compose.onNode(hasText("Spent") and hasText(spent)).assertExists() }

        // Transactions: This month's Expenses (the page opens on this month).
        compose.onNodeWithContentDescription("Transactions").performClick()
        waitForDescription("Expenses")
        shot("02-transactions")
        expect("Transactions Expenses") { compose.onNode(hasContentDescription("Expenses −$spent")).assertExists() }

        // Budget: Spent on the hero.
        compose.onNodeWithContentDescription("Budget").performClick()
        waitForDescription("Spent")
        shot("03-budget")
        expect("Budget Spent") { compose.onNode(hasContentDescription("Spent $spent")).assertExists() }

        check(mismatches.isEmpty()) { "This month's spending should read $spent everywhere; " + mismatches.joinToString("; ") }

        // Hidden amounts (owner, 2026-11-04): one tap hides every figure on the dashboard; showing them asks for the
        // phone's lock. This emulator has none, so Lucento says so and lets the owner choose.
        compose.onNodeWithContentDescription("Home").performClick()
        waitForText("Monthly budget")
        compose.onNodeWithContentDescription("Hide amounts").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Monthly budget").performScrollTo()
        shot("04-home-hidden")
        check(compose.onAllNodes(hasText(spent, substring = true)).fetchSemanticsNodes().isEmpty()) { "Hidden amounts still show $spent" }
        // On screen "PHP••••"; TalkBack hears "Amount hidden", never the figure.
        compose.onNode(hasText("Spent") and hasContentDescription("Amount hidden")).assertExists()
        // Round 7: every page that shows money hides it too — no figure as text, none in what TalkBack reads.
        fun noFigures(page: String) {
            val leaks = compose.onAllNodes(hasText(spent, substring = true) or hasContentDescription(spent, substring = true))
                .fetchSemanticsNodes()
            check(leaks.isEmpty()) { "$page still shows $spent with amounts hidden" }
        }
        compose.onNodeWithContentDescription("Transactions").performClick()
        waitForDescription("Expenses")
        shot("04b-transactions-hidden")
        noFigures("Transactions")
        compose.onNodeWithContentDescription("Budget").performClick()
        waitForDescription("Spent")
        shot("04c-budget-hidden")
        noFigures("Budget")
        compose.onNodeWithContentDescription("Wallets").performClick()
        waitForText("BPI - Personal")
        shot("04d-wallets-hidden")
        check(compose.onAllNodes(hasText("47,433", substring = true)).fetchSemanticsNodes().isEmpty()) { "Wallets still show the balance" }
        compose.onNodeWithContentDescription("Home").performClick()
        waitForText("Monthly budget")
        compose.onNodeWithContentDescription("Show amounts", substring = true).performClick()
        waitForText("This phone has no screen lock")
        shot("05-show-no-screen-lock")
        click("Show amounts")
        waitForText(spent)
        compose.onNode(hasText("Spent") and hasText(spent)).assertExists()
    }
}
