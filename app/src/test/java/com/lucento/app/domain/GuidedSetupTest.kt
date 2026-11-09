package com.lucento.app.domain

import com.lucento.app.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedSetupTest {
    private val everything = FeatureSet.ALL
    private val minimal = FeatureSet(FeatureSet.ALL.enabled - setOf(Feature.DEBTS_LOANS, Feature.SAVINGS_GOALS, Feature.BUDGETS))

    @Test
    fun `the story runs wallets, debts, budgets, goal, transactions, welcome`() {
        assertEquals(GuideStep.entries.toList(), GuidedSetup.steps(everything))
        assertEquals(GuideStep.DEBTS, GuidedSetup.next(GuideStep.WALLETS, everything))
        assertEquals(null, GuidedSetup.next(GuideStep.WELCOME, everything))
    }

    @Test
    fun `switched-off modules leave the story, and a saved step for one resumes at the next`() {
        assertEquals(listOf(GuideStep.WALLETS, GuideStep.TRANSACTIONS, GuideStep.WELCOME), GuidedSetup.steps(minimal))
        assertEquals(GuideStep.TRANSACTIONS, GuidedSetup.next(GuideStep.WALLETS, minimal))
        assertEquals(GuideStep.TRANSACTIONS, GuidedSetup.resume(GuideStep.BUDGETS, minimal))
    }

    @Test
    fun `wallets need one account and budgets two amounts, or every category when there are fewer`() {
        assertFalse(GuidedSetup.canContinue(GuideStep.WALLETS, assetWallets = 0, budgetedCategories = 0, expenseCategories = 5))
        assertTrue(GuidedSetup.canContinue(GuideStep.WALLETS, assetWallets = 1, budgetedCategories = 0, expenseCategories = 5))
        assertFalse(GuidedSetup.canContinue(GuideStep.BUDGETS, assetWallets = 1, budgetedCategories = 1, expenseCategories = 5))
        assertTrue(GuidedSetup.canContinue(GuideStep.BUDGETS, assetWallets = 1, budgetedCategories = 2, expenseCategories = 5))
        assertTrue(GuidedSetup.canContinue(GuideStep.BUDGETS, assetWallets = 1, budgetedCategories = 1, expenseCategories = 1))
        assertTrue(GuidedSetup.canContinue(GuideStep.GOAL, assetWallets = 0, budgetedCategories = 0, expenseCategories = 0))
    }

    @Test
    fun `only debts, the goal and transactions can be skipped`() {
        assertEquals(setOf(GuideStep.DEBTS, GuideStep.GOAL, GuideStep.TRANSACTIONS), GuideStep.entries.filter(GuidedSetup::isSkippable).toSet())
    }

    @Test
    fun `the budget step's total is the sum of the amounts typed so far`() {
        // Blank, unclear and zero entries add nothing yet; the rest add up exactly, in minor units.
        val typed = listOf("250.00", "", "1,200.50", "abc", "0", "49.5")
        assertEquals(Money(1_500_00, "USD"), GuidedSetup.draftTotal(typed, "USD", '.'))
        assertEquals(Money(0, "USD"), GuidedSetup.draftTotal(emptyList(), "USD", '.'))
        // Currencies without minor units stay whole (JPY) and a comma separator is honoured.
        assertEquals(Money(3_500, "JPY"), GuidedSetup.draftTotal(listOf("1500", "2000"), "JPY", '.'))
        assertEquals(Money(12_75, "EUR"), GuidedSetup.draftTotal(listOf("10,25", "2,50"), "EUR", ','))
    }

    @Test
    fun `the path rail counts set-up screens from the onboarding summary, and only the pages this person has`() {
        // Everything on: summary, accounts, cards, budget, goal = 5; then transactions and the finish = 2.
        assertEquals(5, GuidedSetup.setUpScreens(everything))
        assertEquals(2 to 5, GuidedSetup.screenOf(GuideStep.WALLETS, everything))
        assertEquals(4 to 5, GuidedSetup.screenOf(GuideStep.BUDGETS, everything))
        assertEquals(5 to 5, GuidedSetup.screenOf(GuideStep.GOAL, everything))
        assertEquals(1 to 2, GuidedSetup.screenOf(GuideStep.TRANSACTIONS, everything))
        assertEquals(2 to 2, GuidedSetup.screenOf(GuideStep.WELCOME, everything))
        // No cards or loans: the budget moves up and Set up has 4 screens.
        val noDebts = FeatureSet(FeatureSet.ALL.enabled - Feature.DEBTS_LOANS)
        assertEquals(4, GuidedSetup.setUpScreens(noDebts))
        assertEquals(3 to 4, GuidedSetup.screenOf(GuideStep.BUDGETS, noDebts))
        // Only accounts left in Set up.
        assertEquals(2, GuidedSetup.setUpScreens(minimal))
        assertEquals(2 to 2, GuidedSetup.screenOf(GuideStep.WALLETS, minimal))
    }

    @Test
    fun `the starting balance adds the main-currency asset accounts only, never converting or counting debts`() {
        fun w(id: Long, type: WalletType, minor: Long, code: String = "PHP") = Wallet(id, "W$id", type, Money(minor, code), 0L)
        val wallets = listOf(
            w(1, WalletType.CASH, 2_250_00),
            w(2, WalletType.BANK, 46_000_00),
            w(3, WalletType.E_WALLET, 3_480_50),
            w(4, WalletType.CREDIT_CARD, 5_000_00), // owed: not part of a starting balance
            w(5, WalletType.BANK, 100_00, "USD"), // another currency: shown on its own, never converted
        )
        assertEquals(Money(51_730_50, "PHP"), GuidedSetup.startingBalance(wallets, "PHP"))
        assertEquals(3, GuidedSetup.startingBalanceAccounts(wallets, "PHP"))
        assertEquals(Money(0, "PHP"), GuidedSetup.startingBalance(emptyList(), "PHP"))
    }

    @Test
    fun `budget shares are whole percents that always add up to 100`() {
        assertEquals(listOf(75, 24, 1), GuidedSetup.sharePercents(listOf(10_000_00, 3_250_50, 120_00)))
        assertEquals(listOf(34, 33, 33), GuidedSetup.sharePercents(listOf(1, 1, 1)))
        assertEquals(listOf(100, 0), GuidedSetup.sharePercents(listOf(500, 0)))
        assertEquals(listOf(0, 0), GuidedSetup.sharePercents(listOf(0, 0)))
        assertEquals(100, GuidedSetup.sharePercents(listOf(1, 2, 3, 4, 5, 6, 7)).sum())
    }
}
