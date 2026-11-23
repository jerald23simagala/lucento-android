package com.lucento.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lucento.app.core.money.Money
import com.lucento.app.data.preferences.ThemeMode
import com.lucento.app.data.repository.CategoryRepository
import com.lucento.app.data.repository.RecurringRepository
import com.lucento.app.data.repository.WalletRepository
import com.lucento.app.data.repository.TransactionRepository
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.Frequency
import com.lucento.app.domain.MonthCheckIn
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.WalletType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * Captures the monthly check-in, the plan page, Progress, a future month on Budget and Follow Lucento for design review
 * (CI doesn't run it). The card waits until a budget is two weeks old, so it runs in two steps with the device clock set
 * by the person running it (`settings put global auto_time 0; cmd alarm set-time <ms>`):
 * 1. [seed] on a fresh install, on a day at least two weeks before the one to capture (e.g. 10 November): onboarding,
 *    the demo month, a yearly bill due next month, one category over its budget, the trip goal topped up to its target and a
 *    small loan paid off (so two of the new awards show their earned art).
 * 2. [capture] on a day inside the check-in's window (e.g. 29 November, then 2 December). `-e captureDark true` takes the
 *    dark theme, `-e capturePro true` a Pro plan (the year view unlocked). Shots land in /data/local/tmp/lucento-monthclose.
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class MonthCloseCaptureTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val container get() = (instrumentation.targetContext.applicationContext as LucentoApplication).container
    private val args get() = InstrumentationRegistry.getArguments()

    @Test
    fun seed() {
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
            // Only the awards the demo month earns are recorded (quietly, no ceremony), so the rest stay outlines, as most
            // people will see Steady Month, Goal Reached and Loan Cleared at first (design review 2026-11-23).
            container.preferences.recordAwardsQuietly(
                setOf(
                    com.lucento.app.domain.Achievement.MONEY_KEEPER,
                    com.lucento.app.domain.Achievement.BUDGET_BUILDER,
                    com.lucento.app.domain.Achievement.FIRST_STEP,
                    com.lucento.app.domain.Achievement.GETTING_CONSISTENT,
                    com.lucento.app.domain.Achievement.GOAL_SETTER,
                    com.lucento.app.domain.Achievement.GOAL_REACHED,
                    com.lucento.app.domain.Achievement.LOAN_CLEARED,
                ),
            )
            val currency = container.preferences.preferences.first().primaryCurrency
            val today = LocalDate.now()
            DemoMonth.seed(container, currency, 1, today)
            fun money(major: Long) = Money(major * 100, currency)
            val bank = container.wallets.observeWallets().first().first { it.name == "Everyday bank" }.id
            // A yearly bill that falls in next month: the plan page names it.
            val insurance = container.categories.observeCategories().first().firstOrNull { it.name == "Insurance" && !it.archived }?.id
                ?: (container.categories.create("Insurance", CategoryKind.EXPENSE) as CategoryRepository.SaveResult.Saved).id
            container.recurring.save(
                RecurringRepository.Input(
                    null, "Car insurance", TransactionType.EXPENSE, money(640), bank, insurance, Frequency.YEARLY, 1,
                    YearMonth.from(today).plusMonths(1).atDay(12), null,
                ),
            )
            // Restaurants over its ₱200 budget this month: the plan page names that too.
            val restaurants = container.categories.observeCategories().first().first { it.name == "Restaurants" && !it.archived }.id
            val saved = container.transactions.save(
                TransactionRepository.Input(null, UUID.randomUUID().toString(), TransactionType.EXPENSE, money(90), bank, restaurants, today, "Birthday dinner"),
            )
            check(saved is TransactionRepository.SaveResult.Saved)
            // A long category name with an amount, for the large-text checks (28 characters).
            val school = (container.categories.create("Children’s school activities", CategoryKind.EXPENSE) as CategoryRepository.SaveResult.Saved).id
            container.budgets.setAllocation(school, currency, money(150))
            // A goal saved up to its target and a small loan paid off, so Goal Reached and Loan Cleared are earned. Free
            // allows two goals and two cards or loans, and the demo month has both: the trip goal is topped up, and the
            // card (nothing uses it) makes room for the loan.
            val trip = container.goals.observeGoals().first().first { it.name == "Japan trip" }
            container.goals.contribute(trip.id, money(1_350), today.minusDays(3), "", UUID.randomUUID().toString())
            val visa = container.wallets.observeWallets().first().first { it.name == "Visa card" }.id
            check(container.wallets.delete(visa) == WalletRepository.DeleteResult.Deleted)
            val phone = (container.wallets.create("Phone loan", WalletType.LOAN, money(300)) as WalletRepository.SaveResult.Saved).id
            container.transactions.saveTransfer(
                TransactionRepository.TransferInput(
                    group = null, clientUuid = UUID.randomUUID().toString(), fromWalletId = bank, toWalletId = phone,
                    amount = money(300), date = today.minusDays(2), note = "",
                ),
            )
        }
        waitForText("Monthly budget", 15_000)
    }

    @Test
    fun capture() {
        val dark = args.getString("captureDark") == "true"
        val pro = args.getString("capturePro") == "true"
        // `-e homeOnly true`: the card and the plan page only. Opening a recap marks it seen, so runs that must still show
        // the card's recap (another text size, another theme) go first with this.
        val homeOnly = args.getString("homeOnly") == "true"
        // `-e finish true`: also closes the card and undoes it, saves a hint's amount, and taps Done (the month is then
        // planned, so later runs on the same days show only the recap, or no card).
        val finish = args.getString("finish") == "true"
        val today = LocalDate.now()
        val configuration = instrumentation.targetContext.resources.configuration
        val tag = "$today-w${configuration.screenWidthDp}-" + (if (dark) "dark" else "light") + "-text" +
            kotlin.math.round(configuration.fontScale * 100).toInt() + if (pro) "-pro" else "-free"
        shell("mkdir -p /data/local/tmp/lucento-monthclose")
        runBlocking { container.preferences.setThemeMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) }
        // The simulator keeps the last plan between runs: every run says which one it shows.
        val simulator = checkNotNull(container.planSimulator) { "Test builds have the plan simulator" }
        runBlocking {
            if (pro) DemoMonth.skipProWelcome(container, com.lucento.app.data.billing.SimulatedPlan.PRO_MONTHLY)
            simulator.select(if (pro) com.lucento.app.data.billing.SimulatedPlan.PRO_MONTHLY else com.lucento.app.data.billing.SimulatedPlan.FREE)
            container.entitlements.refresh()
        }
        waitForText("Monthly budget", 15_000)
        val plan = MonthCheckIn.planMonth(today)
        val recap = MonthCheckIn.recapMonth(today)
        fun name(month: YearMonth) = com.lucento.app.feature.home.monthLabel(month)

        if (plan != null) {
            waitForText("Plan ${name(plan)}", 10_000)
            shot("$tag-01-home-card")
            if (finish) {
                // Its close button, then Undo: the card comes back.
                compose.onNodeWithContentDescription("Not this month").performClick()
                waitForText("We won’t ask about")
                shot("$tag-01b-card-hidden-undo")
                click("Undo")
                waitForText("Plan ${name(plan)}")
            }
            compose.onNode(hasText("Plan ${name(plan)}") and hasClickAction()).performClick()
            compose.waitForIdle()
            // The plan page is a lazy list: its top, then most of a screen at a time to its end.
            waitForText("What’s different about", 10_000)
            shot("$tag-02-plan-top")
            // Most of a screen at a time until the page's end is in view, so every row is seen at every text size.
            for (step in 'a'..'p') {
                if (isShown("Each change is saved")) break
                scrollOn()
                shot("$tag-03$step-plan-scroll")
            }
            scrollOn(-100f) // back to the top (scrolling stops there)
            // A bill's hint opens its category with the bill's amount already in (design review 2026-11-23).
            lazyScrollTo("Worth a look")
            runCatching {
                compose.onAllNodesWithText("Car insurance:", substring = true)[0].performClick()
                waitForText("Apply to")
                shot("$tag-05-plan-amount")
                backUntilGone("Apply to")
            }
            if (finish) {
                // Last month's overspending, saved for this month only: the row then says where its amount comes from.
                runCatching {
                    lazyScrollTo("Worth a look")
                    compose.onAllNodesWithText("Restaurants:", substring = true)[0].performClick()
                    waitForText("Apply to")
                    shot("$tag-05b-plan-amount-over")
                    click("Save amount")
                    compose.waitUntilDoesNotExist(hasText("Apply to"), 5_000)
                    // The hint now says what December has, and offers a change instead of a new figure.
                    lazyScrollTo("Worth a look")
                    shot("$tag-05c-plan-after-save")
                    // Opened again, it shows the amount saved, never that amount plus the overspend.
                    compose.onAllNodesWithText("Restaurants:", substring = true)[0].performClick()
                    waitForText("Apply to")
                    shot("$tag-05d-hint-reopened")
                    backUntilGone("Apply to")
                    // The row it changed, under Budgeted: "Only December · usually …".
                    lazyScrollTo("usually")
                    shot("$tag-05e-edited-row")
                }
            }
            runCatching {
                lazyScrollTo("Add something for")
                click("Add something for ${name(plan)}")
                waitForText("Your categories")
                shot("$tag-06-add-something")
                // A budgeted category takes the extra: "Personal care becomes … (… planned + …)".
                compose.onNode(hasText("Personal care") and hasText("planned", substring = true) and hasClickAction())
                    .performScrollTo()
                    .performClick()
                waitForText("Extra for")
                shot("$tag-06d-extra-sheet")
                compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("200")
                compose.waitForIdle()
                shot("$tag-06e-extra-typed")
                backUntilGone("Extra for")
            }
            if (finish) {
                // Done planning, at the page's end: back on Home with the total, and the card's recap (if any) left.
                lazyScrollTo("Done planning")
                compose.onNode(hasText("Done planning ${name(plan)}") and hasClickAction()).performClick()
                waitForText("is planned")
                shot("$tag-06b-done-message")
                // The message's 4 seconds pass (the test's clock moves only when told to); the card shows what is left.
                compose.mainClock.advanceTimeBy(5_000)
                compose.waitForIdle()
                shot("$tag-06c-home-after-done")
            } else {
                backUntilShown("Monthly budget")
            }
        }
        if (homeOnly) return
        if (recap != null && compose.onAllNodesWithText("See ${name(recap)}’s recap").fetchSemanticsNodes().isNotEmpty()) {
            click("See ${name(recap)}’s recap")
        } else {
            compose.onNodeWithContentDescription("Progress").performScrollTo().performClick()
        }
        waitForText("How your months are going")
        shot("$tag-07-progress-recap")
        // Most of a screen at a time to the page's end (Pro's Membership shelf comes after the awards).
        for (step in 'a'..'p') {
            if (isShown(if (pro) "Yours to keep" else "Paid a loan down to zero")) break
            scrollOn()
            shot("$tag-08$step-progress-scroll")
        }
        back()

        // Budget, one month ahead: what is planned, nothing spent yet.
        compose.onNodeWithContentDescription("Budget").performClick()
        compose.waitUntilAtLeastOneExists(androidx.compose.ui.test.hasContentDescription("Next month"), 10_000)
        if (today.dayOfMonth <= MonthCheckIn.LAST_DAY) {
            // In a month's first days its plan page is on the Budget tab too.
            lazyScrollTo("Plan ${name(YearMonth.from(today))}")
            scrollOn(0.3f) // clear of the navigation dock
            shot("$tag-11a-budget-this-month")
            runCatching {
                compose.onAllNodes(hasScrollToNodeAction()).onFirst()
                    .performScrollToNode(androidx.compose.ui.test.hasContentDescription("Next month"))
                compose.waitForIdle()
            }
        }
        compose.onNodeWithContentDescription("Next month").performClick()
        compose.waitForIdle()
        shot("$tag-11-budget-next-month")
        compose.onNodeWithContentDescription("Previous month").performClick()

        compose.onNodeWithContentDescription("Profile and settings").performClick()
        compose.waitForIdle()
        click("Follow Lucento")
        waitForText("Tips, updates and short videos")
        shot("$tag-12-follow")
        back()
    }

    /**
     * Scrolls the screen's main page (its tallest scrolling container) by [fraction] of its height, with no fling, so
     * consecutive shots overlap and nothing is skipped. A large negative fraction goes back to the top.
     */
    private fun scrollOn(fraction: Float = 0.7f) {
        runCatching {
            val page = compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().maxByOrNull { it.size.height } ?: return
            compose.onNode(SemanticsMatcher("the page") { it.id == page.id })
                .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, page.size.height * fraction) }
            compose.waitForIdle()
        }
    }

    /** Whether [text] is on screen now (composed and in view). */
    private fun isShown(text: String): Boolean =
        runCatching { compose.onAllNodes(hasText(text, substring = true)).onFirst().assertIsDisplayed() }.isSuccess

    /** Scrolls the screen's lazy list to the item holding [text] (a lazy list composes only what is in view). */
    private fun lazyScrollTo(text: String) {
        runCatching {
            compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(text, substring = true))
            compose.waitForIdle()
        }
    }

    /** Back, one press at a time (a keyboard can take the first), until [marker] has left the screen. At most 3. */
    private fun backUntilGone(marker: String) {
        repeat(3) {
            if (compose.onAllNodes(hasText(marker, substring = true)).fetchSemanticsNodes().isEmpty()) return
            back()
        }
    }

    /** Back until [marker] is on screen (a lazy list composes only what is in view, so wait for what comes back). */
    private fun backUntilShown(marker: String) {
        repeat(3) {
            back()
            // A screen coming back can take a moment: wait for it before pressing again, or the next press leaves the app.
            val back = runCatching {
                compose.waitUntilAtLeastOneExists(hasText(marker, substring = true, ignoreCase = true), 2_500)
            }.isSuccess
            if (back) return
        }
        waitForText(marker)
    }

    private fun back() {
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        Thread.sleep(400)
        compose.waitForIdle()
    }

    private fun shot(name: String, settle: Long = 700) {
        compose.waitForIdle()
        Thread.sleep(settle)
        shell("screencap -p /data/local/tmp/lucento-monthclose/$name.png")
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
}
