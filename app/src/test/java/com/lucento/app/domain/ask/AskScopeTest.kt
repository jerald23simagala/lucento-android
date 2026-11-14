package com.lucento.app.domain.ask

import com.lucento.app.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Owner test round 2: off-topic questions stay on the phone; purchase questions get a phone answer when Gemini can't. */
class AskScopeTest {

    @Test
    fun `questions plainly not about money are off topic`() {
        assertEquals(AskTopic.OFF_TOPIC, AskScope.topic("Give me vba code to pritn pdf in excel"))
        assertEquals(AskTopic.OFF_TOPIC, AskScope.topic("Write me a poem about the sea"))
        assertEquals(AskTopic.OFF_TOPIC, AskScope.topic("What's a good adobo recipe?"))
    }

    @Test
    fun `a money word keeps a question on topic`() {
        assertEquals(AskTopic.MONEY, AskScope.topic("Can you make an Excel formula for my budget?"))
        assertEquals(AskTopic.MONEY, AskScope.topic("Export my transactions to excel"))
        assertEquals(AskTopic.MONEY, AskScope.topic("Which debt should I pay off first?"))
        // Nothing marks it as off topic either way: Gemini (when on) decides and steers back to money.
        assertEquals(AskTopic.MONEY, AskScope.topic("Hello, who are you?"))
    }

    @Test
    fun `purchase questions are recognised, typos included`() {
        assertEquals(AskTopic.PURCHASE, AskScope.topic("Should I but iPhone duo"))
        assertEquals(AskTopic.PURCHASE, AskScope.topic("Should I buy a new laptop?"))
        assertEquals(AskTopic.PURCHASE, AskScope.topic("Can I afford a trip to Japan?"))
        assertEquals(AskTopic.PURCHASE, AskScope.topic("Can I afford to spend 5000 on clothes?"))
        // Borrowing isn't a purchase: the debts report or Gemini answers it.
        assertEquals(AskTopic.MONEY, AskScope.topic("Should I get a loan?"))
        assertEquals(AskTopic.MONEY, AskScope.topic("Should I get a credit card?"))
    }

    @Test
    fun `a price is read only when it is marked as one`() {
        assertNull(AskScope.price("Should I buy the iPhone 17 Pro?", "PHP"))
        assertEquals(Money(7_500_000, "PHP"), AskScope.price("Should I buy the iPhone 17 for 75000?", "PHP"))
        assertEquals(Money(4_500_000, "PHP"), AskScope.price("Can I afford a 45,000 laptop", "PHP"))
        assertEquals(Money(4_500_000, "PHP"), AskScope.price("Can I afford a 45k laptop", "PHP"))
        assertEquals(Money(4_500_000, "PHP"), AskScope.price("Can I afford a ₱45000 laptop", "PHP"))
        assertEquals(Money(4_500_000, "PHP"), AskScope.price("Can I afford a PHP 45000 laptop", "PHP"))
        assertEquals(Money(4_500_000, "PHP"), AskScope.price("Can I afford a laptop, php 45000", "PHP"))
        assertEquals(Money(1_299_99, "USD"), AskScope.price("Should I buy a TV at 1299.99?", "USD"))
        assertEquals(Money(150_000, "JPY"), AskScope.price("Can I afford ¥150,000 headphones", "JPY"))
    }

    @Test
    fun `a price in another currency is never converted`() {
        assertNull(AskScope.price("Should I buy it for USD 1000?", "PHP"))
        assertNull(AskScope.price("Should I buy it for $1000?", "PHP"))
    }

    private fun snapshot(vararg figures: Pair<String, Long>) = AskSnapshot(
        "PHP",
        LocalDate.of(2026, 11, 9),
        figures.map { (id, minor) -> SnapshotFigure(id, id, "", amount = Money(minor, "PHP")) },
    )

    private fun text(parts: List<CheckedAnswer.Part>) = parts.joinToString("") { it.text }

    private val steady = arrayOf(
        "safe" to 1_000_000L, // 10,000 safe to spend
        "on_hand" to 25_000_000L, // 250,000
        "savings" to 5_000_000L, // 50,000
        "avg_out" to 4_000_000L, // 40,000 a month
        "avg_net" to 1_000_000L, // 10,000 kept a month
        "nw" to 30_000_000L,
    )

    @Test
    fun `within safe to spend it fits this month`() {
        val answer = text(PurchaseCheck.answer(snapshot(*steady), Money(800_000, "PHP")))
        assertTrue(answer, answer.startsWith("Yes, it fits."))
    }

    @Test
    fun `above safe to spend but with a three month cushion left it can come from savings`() {
        // 300,000 on hand and in savings; 150,000 leaves 150,000 = 3.75 months of 40,000.
        val answer = text(PurchaseCheck.answer(snapshot(*steady), Money(15_000_000, "PHP")))
        assertTrue(answer, answer.startsWith("You can, but from savings"))
        assertTrue(answer, answer.contains("3.7 months"))
    }

    @Test
    fun `eating into the cushion says wait, with how long saving takes`() {
        // 250,000 leaves 50,000; the cushion is 120,000, so 70,000 short: 7 months at 10,000.
        val answer = text(PurchaseCheck.answer(snapshot(*steady), Money(25_000_000, "PHP")))
        assertTrue(answer, answer.startsWith("I’d wait."))
        assertTrue(answer, answer.contains("in about 7 months"))
    }

    @Test
    fun `more than every balance together is not yet`() {
        val answer = text(PurchaseCheck.answer(snapshot(*steady), Money(40_000_000, "PHP")))
        assertTrue(answer, answer.startsWith("Not yet."))
    }

    @Test
    fun `without a price it lays out the figures and asks for one`() {
        val parts = PurchaseCheck.answer(snapshot(*steady, "nw_owed" to -2_000_000L), null)
        val answer = text(parts)
        assertTrue(answer, answer.contains("Safe to spend"))
        assertTrue(answer, answer.contains("7.5 months"))
        assertTrue(answer, answer.contains("Owed on cards and loans"))
        assertTrue(answer, answer.contains("Tell me the price in PHP"))
        // Figures are marked so the screen shows them in bold, as in every answer.
        assertTrue(parts.any { it.isFigure })
    }

    @Test
    fun `without balances it says what's needed instead of guessing`() {
        val answer = text(PurchaseCheck.answer(snapshot("safe" to 0L), Money(5_000_000, "PHP")))
        assertTrue(answer, answer.contains("add your wallets"))
        assertFalse(answer.contains("Yes"))
    }
}
