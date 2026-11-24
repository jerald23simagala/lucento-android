package com.lucento.app.domain.fx

import com.lucento.app.core.money.Money
import com.lucento.app.domain.Frequency
import com.lucento.app.domain.MoneyOnHandCalculator
import com.lucento.app.domain.RecurringSchedule
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * QA, 2026-11-17: a foreign wallet counts in Safe to spend at its value, so its bills must be set aside at the same rate
 * (`FxTranslation.valueSchedules`); before, the wallet counted and its bills didn't.
 */
class ValuedSchedulesTest {

    private val today = LocalDate.parse("2026-11-16")
    private val pesoBank = Wallet(1, "Bank", WalletType.BANK, Money(1_000_000, "PHP"), 0L)
    private val yenCash = Wallet(
        2, "Yen", WalletType.CASH, Money(50_000, "JPY"), 0L,
        openingBalance = Money(50_000, "JPY"), fxBasisRate = BigDecimal("0.38"),
    )
    private val rent = RecurringSchedule(
        7, "Rent", TransactionType.EXPENSE, Money(10_000, "JPY"), 2L, 11L, Frequency.MONTHLY, 1, LocalDate.parse("2026-11-28"), null, 0, false,
    )
    private val internet = RecurringSchedule(
        8, "Internet", TransactionType.EXPENSE, Money(150_000, "PHP"), 1L, 11L, Frequency.MONTHLY, 1, LocalDate.parse("2026-11-20"), null, 0, false,
    )

    @Test fun aForeignWalletsScheduleIsValuedAtItsRateAndTheRestAreLeftAlone() {
        val ledger = FxTranslation.translate("PHP", listOf(pesoBank, yenCash), emptyList())
        val (valuedRent, valuedInternet) = FxTranslation.valueSchedules(ledger, listOf(rent, internet))
        assertEquals(Money(380_000, "PHP"), valuedRent.amount) // ¥10,000 at 0.38
        assertEquals(Money(0, "PHP"), valuedRent.interest)
        assertEquals(internet, valuedInternet)
        // A wallet with no rate stays in its own currency, and so do its schedules.
        val plain = FxTranslation.translate("PHP", listOf(pesoBank, yenCash.copy(fxBasisRate = null)), emptyList())
        assertEquals(rent, FxTranslation.valueSchedules(plain, listOf(rent)).single())
    }

    @Test fun safeToSpendSetsAForeignBillAsideWithTheMoneyItWillLeave() {
        val ledger = FxTranslation.translate("PHP", listOf(pesoBank, yenCash), emptyList())
        val valued = MoneyOnHandCalculator.compute("PHP", today, ledger.wallets, ledger.transactions, FxTranslation.valueSchedules(ledger, listOf(rent)))!!
        assertEquals(Money(380_000, "PHP"), valued.deducted)
        // Unvalued, the yen wallet's value counted while its bill was left out.
        val unvalued = MoneyOnHandCalculator.compute("PHP", today, ledger.wallets, ledger.transactions, listOf(rent))!!
        assertEquals(Money(0, "PHP"), unvalued.deducted)
    }
}
