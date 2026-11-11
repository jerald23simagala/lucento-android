package com.lucento.app.domain.ask

import com.lucento.app.core.money.Money
import com.lucento.app.domain.OutsideMovement
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.ask.AskFixtures.usd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * The wallet statement (tester round 3): balance at the start, every movement grouped, balance at the end — and the
 * start plus every movement always equals the end.
 */
class WalletStatementTest {

    private val inputs = AskFixtures.inputs()
    private val november = YearMonth.of(2026, 11)

    private fun Report.section(title: String) = sections.first { it.title == title }
    private fun Report.summary(label: String): Money = section("Summary").lines.first { it.label == label }.values.single()!!
    private fun Report.reconciles() {
        val lines = section("Summary").lines.map { it.values.single()!! }
        assertEquals("start + movements = end", headline.values.single(), Money.sum(currencyCode, lines))
        assertEquals(headline.values.single(), section("Summary").total!!.values.single())
    }

    private fun row(id: Long, type: TransactionType, minor: Long, wallet: Long, date: String, note: String) =
        Transaction(id, type, usd(minor), wallet, null, LocalDate.parse(date), note, 0L)

    @Test fun aBankStatementStartsListsEveryMovementAndEnds() {
        val r = ReportBuilder.walletStatement(inputs, AskFixtures.bank.id, november)
        assertEquals(ReportKind.WALLET_STATEMENT, r.kind)
        assertEquals("Everyday bank", r.subject)
        assertEquals(AskFixtures.bank.id, r.walletId)
        assertEquals("Wallet statement · Everyday bank", r.title)
        // October 31: 4,000 + 3,000 − 250 = 6,750.
        assertEquals(usd(675_000), r.summary("Balance at the start"))
        assertEquals(usd(300_000), r.summary("Income"))
        assertEquals(usd(-150_000), r.summary("Expenses"))
        assertEquals(usd(0), r.summary("Transfers in"))
        assertEquals(usd(-50_000), r.summary("Transfers out"))
        assertEquals(usd(775_000), r.headline.values.single())
        r.reconciles()
        // Movements carry their sign; the start doesn't.
        assertFalse(r.section("Summary").lines.first().signed)
        assertTrue(r.section("Summary").lines.drop(1).all { it.signed })
        // By category, largest first, with shares; transfers by the wallet on the other side.
        assertEquals(listOf("Rent", "Groceries"), r.section("Expenses").lines.map { it.label })
        assertEquals(0.8f, r.section("Expenses").lines.first().share!!, 0.001f)
        assertEquals(listOf("Salary"), r.section("Income").lines.map { it.label })
        assertEquals(listOf("Car loan"), r.section("Transfers out").lines.map { it.label })
        assertEquals("1 transfer", r.section("Transfers out").lines.single().detail)
        // Grouped: income and expenses first, then what isn't income or spending.
        assertEquals(listOf(null, "Income and expenses", "Not income or spending"), r.sections.map { it.group }.distinct())
        // A money account can take withdrawals, so the section is there even when empty.
        assertEquals(emptyList<ReportLine>(), r.section("Withdrawals").lines)
    }

    @Test fun aLoanStatementReadsAsWhatIsOwedAndListsExternalPayments() {
        val paidByMom = row(900, TransactionType.ADJUSTMENT_IN, 45_000, AskFixtures.loan.id, "2026-11-10", OutsideMovement.EXTERNAL_PAYMENT.note("Paid by Mom"))
        val r = ReportBuilder.walletStatement(inputs.copy(transactions = inputs.transactions + paidByMom), AskFixtures.loan.id, november)
        assertEquals("Owed at the end", r.headline.label)
        assertEquals(usd(1_000_000), r.summary("Owed at the start"))
        assertEquals(usd(-50_000), r.summary("Payments from your wallets"))
        assertEquals(usd(-45_000), r.summary("External payments"))
        assertEquals(usd(905_000), r.headline.values.single())
        r.reconciles()
        assertEquals(listOf("Everyday bank"), r.section("Payments from your wallets").lines.map { it.label })
        val external = r.section("External payments").lines.single()
        assertEquals("Paid by Mom", external.label)
        assertTrue(external.detail!!, external.detail!!.startsWith("External payment · "))
        assertEquals(usd(45_000), external.values.single())
        // Nothing refunded or moved out this month: those sections aren't shown for a debt.
        assertTrue(r.sections.none { it.title == "Refunds and credits" || it.title == "Moved to your wallets" })
        assertEquals("Spending and refunds", r.section("Charges and fees").group)
    }

    @Test fun corrections_areListedApartWithTheirSign() {
        val r = ReportBuilder.walletStatement(inputs, AskFixtures.cash.id, november)
        assertEquals(usd(15_000), r.summary("Balance at the start"))
        assertEquals(usd(-10_000), r.summary("Expenses"))
        assertEquals(usd(5_000), r.summary("Balance adjustments"))
        assertEquals(usd(10_000), r.headline.values.single())
        r.reconciles()
        val correction = r.section("Balance adjustments").lines.single()
        assertEquals(usd(5_000), correction.values.single())
        assertTrue(correction.signed)
    }

    @Test fun withdrawalsListWhatTheyWereFor() {
        val out = row(901, TransactionType.ADJUSTMENT_OUT, 20_000, AskFixtures.bank.id, "2026-11-12", OutsideMovement.WITHDRAWAL.note("Moved to my investment"))
        val r = ReportBuilder.walletStatement(inputs.copy(transactions = inputs.transactions + out), AskFixtures.bank.id, november)
        assertEquals(usd(-20_000), r.summary("Withdrawals"))
        assertEquals("Moved to my investment", r.section("Withdrawals").lines.single().label)
        assertEquals(usd(755_000), r.headline.values.single())
        assertTrue("a withdrawal isn't a correction", r.sections.none { it.title == "Balance adjustments" })
        r.reconciles()
    }

    @Test fun aWalletAddedThisMonthStartsAtItsOpeningBalance() {
        val created = LocalDate.parse("2026-11-04").atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val data = inputs.copy(wallets = inputs.wallets.map { if (it.id == AskFixtures.cash.id) it.copy(createdAtEpochMillis = created) else it })
        val r = ReportBuilder.walletStatement(data, AskFixtures.cash.id, november)
        val start = r.section("Summary").lines.first()
        assertEquals(usd(15_000), start.values.single())
        assertTrue(start.detail!!, start.detail!!.startsWith("Opening balance when you added it"))
    }

    @Test fun aPastMonthEndsOnItsLastDay() {
        val r = ReportBuilder.walletStatement(inputs, AskFixtures.bank.id, YearMonth.of(2026, 10))
        assertEquals(usd(400_000), r.summary("Balance at the start"))
        assertEquals(usd(675_000), r.headline.values.single())
        r.reconciles()
    }

    @Test fun aStatementNeedsAWalletAndIsNeverOfferedToTheAiRoute() {
        assertTrue(ReportKind.WALLET_STATEMENT.needsWallet)
        assertTrue(ReportKind.entries.filter { it != ReportKind.WALLET_STATEMENT }.none { it.needsWallet })
        assertThrows(IllegalArgumentException::class.java) {
            ReportBuilder.build(ReportKind.WALLET_STATEMENT, inputs, "USD", november)
        }
        assertEquals(AskFixtures.bank.id, ReportBuilder.build(ReportKind.WALLET_STATEMENT, inputs, "USD", november, walletId = AskFixtures.bank.id).walletId)
    }
}
