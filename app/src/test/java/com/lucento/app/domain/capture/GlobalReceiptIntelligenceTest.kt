package com.lucento.app.domain.capture

import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.WalletType
import com.lucento.app.domain.capture.ScanFixtures.amex
import com.lucento.app.domain.capture.ScanFixtures.cash
import com.lucento.app.domain.capture.ScanFixtures.chase
import com.lucento.app.domain.capture.ScanFixtures.context
import com.lucento.app.domain.capture.ScanFixtures.gcash
import com.lucento.app.domain.capture.ScanFixtures.today
import com.lucento.app.domain.capture.ScanFixtures.wallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Receipts and confirmations from the Philippines, the United States and elsewhere (scan roadmap phases 0–3, owner
 * 2026-11-22): currency from the document (never the default by assumption), total words in many languages,
 * currency-marked integers, lakh grouping, tips, refunds, bill dates, country date order, and the payee told apart from
 * the payment provider.
 */
class GlobalReceiptIntelligenceTest {

    private fun receipt(lines: List<String>, defaultWalletId: Long? = cash.id, wallets: List<com.lucento.app.domain.Wallet> = ScanFixtures.wallets) =
        ReceiptCandidates.read(lines, context(defaultWalletId = defaultWalletId, wallets = wallets))!!

    // ------------------------------------------------------------------ currency

    @Test fun aDollarOnAUsReceiptIsUsdAndGoesToTheUsdAccount() {
        val (c, _) = receipt(listOf("WALMART", "2500 4TH AVE", "SEATTLE, WA 98121", "MILK  3.49", "BREAD  2.99", "SUBTOTAL  6.48", "TAX  0.58", "TOTAL  $7.06", "VISA TEND  7.06", "11/18/26 14:02"))
        assertEquals("USD", c.mentionedCurrency)
        assertEquals(chase.id, c.walletId)
        assertFalse(CandidateFlag.CURRENCY_ASSUMED in c.flags)
        assertEquals(BigDecimal("7.06"), c.amount)
        assertEquals("Walmart", c.description)
        assertEquals(ScanFixtures.groceries.id, c.categoryId)
        assertEquals(LocalDate.parse("2026-11-18"), c.date)
    }

    @Test fun aBareDollarWithNothingElseTakesTheOnlyDollarAccountAndSaysSo() {
        val (c, _) = receipt(listOf("CORNER DELI", "SANDWICH  8.50", "TOTAL  $8.50"))
        assertEquals("USD", c.mentionedCurrency)
        assertEquals(chase.id, c.walletId)
        assertTrue(CandidateFlag.CURRENCY_ASSUMED in c.flags)
    }

    @Test fun aBareDollarWithTwoDollarCurrenciesStaysUnknown() {
        val withCad = ScanFixtures.wallets + wallet(30, "TD Canada", WalletType.BANK, currency = "CAD")
        val (c, _) = receipt(listOf("CORNER DELI", "SANDWICH  8.50", "TOTAL  $8.50"), wallets = withCad)
        assertNull(c.mentionedCurrency)
        assertEquals(cash.id, c.walletId)
    }

    @Test fun qualifiedDollarsAndCountryCuesSettleTheCurrency() {
        assertEquals("CAD", DocumentCurrency.read(listOf("TOTAL C$ 12.40"), CountryReading.UNKNOWN, emptyList()).code)
        assertEquals("SGD", DocumentCurrency.read(listOf("TOTAL S$ 12.40"), CountryReading.UNKNOWN, emptyList()).code)
        val canada = DocumentLocale.country(listOf("TIM HORTONS", "TORONTO ON M5V 2T6", "GST/HST  0.65", "TOTAL  $5.65"))
        assertEquals("CA", canada.code)
        assertEquals("CAD", DocumentCurrency.read(listOf("TOTAL  $5.65"), canada, emptyList()).code)
        val philippines = DocumentLocale.country(listOf("JOLLIBEE", "VAT REG TIN 000-123-456-000", "QUEZON CITY"))
        assertEquals("PH", philippines.code)
        // No symbol at all, but the receipt plainly says where it is from.
        assertEquals("PHP", DocumentCurrency.read(listOf("TOTAL  250.00"), philippines, emptyList()).code)
    }

    @Test fun aEuroReceiptIsNeverSavedAsPesosByDefault() {
        val (c, _) = receipt(listOf("INTERMARCHÉ", "PAIN  1,20", "TOTAL TTC  12,40 €", "CB  12,40"))
        assertEquals("EUR", c.mentionedCurrency)
        assertEquals(BigDecimal("12.40"), c.amount)
        assertEquals(cash.id, c.walletId) // no EUR account: the review blocks it until the person chooses
    }

    @Test fun aCurrencyMarkedIntegerIsMoneyOnAReceipt() {
        assertEquals(listOf(BigDecimal("348")), ReceiptParser.amountsIn("コーヒー ¥348", 0))
        assertEquals(listOf(BigDecimal("15000")), ReceiptParser.amountsIn("아메리카노 ₩15000", 0))
        assertEquals(listOf(BigDecimal("348")), ReceiptParser.amountsIn("コーヒー 348円", 0))
        // A bare integer is still a count or code.
        assertTrue(ReceiptParser.amountsIn("ITEMS 348", 2).isEmpty())
    }

    @Test fun aJapaneseReceiptReadsItsTotalWord() {
        val lines = listOf("ローソン 渋谷店", "2026年11月20日 18:31", "おにぎり  ¥160", "お茶  ¥188", "合計  ¥348", "お預り  ¥500", "お釣り  ¥152")
        val s = ReceiptParser.parse(lines, today, expectedCurrency = "JPY")
        assertEquals(TotalConfidence.LABELLED, s.totalConfidence)
        assertEquals(BigDecimal("348"), s.total)
        assertEquals(LocalDate.parse("2026-11-20"), s.date)
        assertEquals("JPY", DocumentCurrency.read(lines, DocumentLocale.country(lines), emptyList()).code)
    }

    @Test fun indianLakhGroupingIsOneNumber() {
        assertEquals(listOf(BigDecimal("123456.00")), ReceiptParser.amountsIn("TOTAL ₹1,23,456.00", 2))
        assertEquals(BigDecimal("123456.00"), ReceiptParser.parse(listOf("RELIANCE DIGITAL", "CGST 9%  9,415.32", "TOTAL  ₹1,23,456.00"), today).total)
    }

    // ------------------------------------------------------------------ total words in other languages

    @Test fun totalWordsInManyLanguages() {
        fun total(vararg lines: String) = ReceiptParser.parse(lines.toList(), today, expectedCurrency = "EUR").let { it.totalConfidence to it.total }
        assertEquals(TotalConfidence.LABELLED to BigDecimal("23.45"), total("REWE", "Brot  2,49", "Zwischensumme  21,30", "MwSt 7%  2,15", "SUMME EUR  23,45", "Gegeben BAR  50,00", "Rückgeld  26,55"))
        assertEquals(TotalConfidence.LABELLED to BigDecimal("45.60"), total("CARREFOUR", "TVA 20%  7,60", "TOTAL TTC  45,60", "ESPECES  50,00", "RENDU MONNAIE  4,40"))
        assertEquals(TotalConfidence.LABELLED to BigDecimal("12.30"), total("MERCADONA", "IVA 10%  1,12", "TOTAL A PAGAR  12,30", "EFECTIVO  20,00", "CAMBIO  7,70"))
        assertEquals(TotalConfidence.LABELLED to BigDecimal("9.80"), total("ESSELUNGA", "TOTALE COMPLESSIVO  9,80", "CONTANTI  10,00"))
        assertEquals(TotalConfidence.LABELLED to BigDecimal("17.25"), total("ALBERT HEIJN", "BTW 9%  1,42", "TE BETALEN  17,25"))
        val rupiah = ReceiptParser.parse(listOf("INDOMARET", "TOTAL BAYAR  Rp 45.000", "TUNAI  Rp 50.000", "KEMBALIAN  Rp 5.000"), today)
        assertEquals(TotalConfidence.LABELLED, rupiah.totalConfidence)
        assertEquals(0, BigDecimal("45000").compareTo(rupiah.total))
    }

    // ------------------------------------------------------------------ tips and refunds

    @Test fun aRestaurantTotalAfterTheTipIsWhatWasPaid() {
        val lines = listOf("THE HABIT BURGER GRILL", "IRVINE, CA 92618", "2 BURGERS  18.00", "FRIES  4.50", "SUBTOTAL  22.50", "TAX  1.74", "TIP  4.00", "TOTAL  28.24")
        val (c, s) = receipt(lines)
        assertEquals(BigDecimal("28.24"), s.total)
        assertFalse("items, tax and tip add up", CandidateFlag.ITEMS_DONT_ADD_UP in c.flags)
    }

    @Test fun aBlankTipLineNeverTakesTheSuggestedTip() {
        val lines = listOf("CHEESECAKE FACTORY", "SUBTOTAL  45.00", "TAX  3.60", "AMOUNT DUE  48.60", "TIP: ________", "TOTAL: ________", "18% tip: 8.75", "20% tip: 9.72")
        assertEquals(BigDecimal("48.60"), ReceiptParser.parse(lines, today).total)
    }

    @Test fun aRefundIsMoneyBackNotSpending() {
        val (c, s) = receipt(listOf("TARGET", "MINNEAPOLIS, MN 55403", "RETURN  BATH TOWEL  -19.99", "REFUND TOTAL  -19.99", "REFUND TO VISA  19.99"))
        assertTrue(s.refund)
        assertEquals(TransactionType.INCOME, c.type)
        assertEquals(ScanFixtures.refunds.id, c.categoryId)
        assertEquals(BigDecimal("19.99"), c.amount)
    }

    @Test fun aReturnsPolicyFooterIsNotARefund() {
        assertFalse(ReceiptParser.parse(listOf("TARGET", "TOTAL  25.00", "Returns accepted within 90 days with receipt"), today).refund)
    }

    // ------------------------------------------------------------------ dates

    @Test fun aBillGivesItsBillDateNotItsPeriodOrDueDate() {
        val lines = listOf("MERALCO", "Billing Period  Oct 15, 2026 - Nov 14, 2026", "Bill Date  Nov 15, 2026", "Due Date  Nov 29, 2026", "Total Amount Due  2,345.67")
        assertEquals(LocalDate.parse("2026-11-15") to false, ReceiptParser.findDate(lines, today))
    }

    @Test fun theCountrySettlesAnAmbiguousNumericDate() {
        val us = listOf("SAFEWAY", "SEATTLE, WA 98101", "03/04/2026 10:15", "TOTAL  12.00")
        assertEquals(LocalDate.parse("2026-03-04") to false, ReceiptParser.findDate(us, today, DocumentLocale.dateOrder(DocumentLocale.country(us).code)))
        val uk = listOf("TESCO", "LONDON SW1A 1AA", "03/04/2026 10:15", "TOTAL  £12.00")
        assertEquals(LocalDate.parse("2026-04-03") to false, ReceiptParser.findDate(uk, today, DocumentLocale.dateOrder(DocumentLocale.country(uk).code)))
        // Nothing says: offered, and marked as unclear.
        assertTrue(ReceiptParser.findDate(listOf("SHOP", "03/04/2026", "TOTAL  12.00"), today).second)
    }

    @Test fun monthNamesInOtherLanguages() {
        assertEquals(LocalDate.parse("2026-11-21"), ReceiptParser.findDate(listOf("Datum: 21. November 2026"), today).first)
        assertEquals(LocalDate.parse("2026-11-21"), ReceiptParser.findDate(listOf("Fecha: 21 de noviembre de 2026"), today).first)
        assertEquals(LocalDate.parse("2026-02-03"), ReceiptParser.findDate(listOf("le 3 févr. 2026"), today).first)
        assertEquals(LocalDate.parse("2026-10-21"), ReceiptParser.findDate(listOf("21 Okt 2026"), today).first)
        assertEquals(LocalDate.parse("2026-11-21"), ReceiptParser.findDate(listOf("2026년 11월 21일"), today).first)
    }

    // ------------------------------------------------------------------ who was paid, and how

    @Test fun aWalmartReceiptPaidWithAmexIsWalmartOnTheAmexCard() {
        val (c, _) = receipt(listOf("WALMART", "SUPERCENTER #2516", "DALLAS, TX 75201", "TV  299.00", "TOTAL  319.93", "AMEX  ************1004", "APPROVED"), defaultWalletId = chase.id)
        assertEquals("Walmart", c.description)
        assertEquals(amex.id, c.walletId)
        assertTrue(c.walletFromText)
    }

    @Test fun aGcashBillsPaymentIsTheBillerPaidFromGcash() {
        val (c, _) = receipt(
            listOf("GCash", "Bills Payment", "Biller  MERALCO", "Account No.  1234567890", "Amount  2,345.67", "Total Amount Paid  2,345.67", "Ref No. 1001 234 567890", "Nov 21, 2026 10:15 AM"),
        )
        assertEquals("Meralco", c.description)
        assertEquals(gcash.id, c.walletId)
        assertEquals(ScanFixtures.utilities.id, c.categoryId)
        assertEquals(BigDecimal("2345.67"), c.amount)
        assertEquals(LocalDate.parse("2026-11-21"), c.date)
    }

    @Test fun aPaypalPaymentToAUtilityIsTheUtility() {
        val (c, _) = receipt(listOf("PayPal", "You paid $84.12 USD to PG&E", "Transaction date  Nov 18, 2026", "Total  $84.12 USD"))
        assertEquals("PG&E", c.description)
        assertEquals("USD", c.mentionedCurrency)
        assertEquals(ScanFixtures.utilities.id, c.categoryId)
    }

    @Test fun aTransferToAPersonSaysWhoItWentTo() {
        val (c, _) = receipt(listOf("GCash", "Express Send", "Sent to  JUAN DELA CRUZ", "+63 917 *** 4567", "Amount  500.00", "Total Amount Sent  500.00", "Nov 20, 2026 9:12 PM"))
        assertEquals("Sent to Juan Dela Cruz", c.description)
        assertEquals(gcash.id, c.walletId)
        assertEquals(TransactionType.EXPENSE, c.type)
    }

    @Test fun aCashInConfirmationIsATransferBetweenOwnAccounts() {
        val (c, _) = receipt(listOf("GCash", "Cash In", "From  BPI", "Amount  1,000.00", "Total  1,000.00", "Nov 19, 2026"))
        assertEquals(TransactionType.TRANSFER_OUT, c.type)
        assertEquals("money leaves BPI", ScanFixtures.bpi.id, c.walletId)
        assertEquals("and arrives in GCash", gcash.id, c.toWalletId)
    }
}
