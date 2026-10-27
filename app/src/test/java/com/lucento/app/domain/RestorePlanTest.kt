package com.lucento.app.domain

import com.lucento.app.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Restoring an account on a new phone: whether anything came back, and the device-only preferences it implies. */
class RestorePlanTest {

    private fun wallet(id: Long, code: String, type: WalletType = WalletType.BANK) = Wallet(id, "W$id", type, Money(1_000, code), 0L)
    private val salary = Category(1, "Salary", CategoryKind.INCOME, "work")
    private val food = Category(2, "Food", CategoryKind.EXPENSE, "food")

    @Test fun freshInstallIncomeSeedsAloneAreAnEmptyAccount() {
        assertFalse(RestorePlan.hasData(emptyList(), listOf(salary)))
        assertFalse(RestorePlan.hasData(emptyList(), listOf(salary, food.copy(archived = true))))
        assertFalse(RestorePlan.hasSpendingCategories(listOf(salary)))
    }

    @Test fun aWalletOrASpendingCategoryMeansThereIsSomethingToRestore() {
        assertTrue(RestorePlan.hasData(listOf(wallet(1, "PHP")), listOf(salary)))
        assertTrue(RestorePlan.hasData(emptyList(), listOf(salary, food)))
        assertTrue(RestorePlan.hasSpendingCategories(listOf(salary, food)))
    }

    @Test fun mainCurrencyIsTheOneMostWalletsUse() {
        val wallets = listOf(wallet(1, "PHP"), wallet(2, "USD"), wallet(3, "PHP", WalletType.CREDIT_CARD))
        assertEquals("PHP", RestorePlan.primaryCurrency(wallets, fallback = "USD"))
    }

    @Test fun tiesPreferThePhonesCurrencyThenAlphabeticalNeverRowOrder() {
        val tie = listOf(wallet(1, "USD"), wallet(2, "PHP"))
        assertEquals("USD", RestorePlan.primaryCurrency(tie, fallback = "USD"))
        assertEquals("PHP", RestorePlan.primaryCurrency(tie, fallback = "EUR"))
        assertEquals("PHP", RestorePlan.primaryCurrency(tie.reversed(), fallback = "EUR"))
    }

    @Test fun noWalletsKeepsThePhonesCurrency() {
        assertEquals("JPY", RestorePlan.primaryCurrency(emptyList(), fallback = "JPY"))
    }
}
