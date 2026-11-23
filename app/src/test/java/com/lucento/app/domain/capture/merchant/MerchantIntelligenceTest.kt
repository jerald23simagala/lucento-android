package com.lucento.app.domain.capture.merchant

import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.capture.ReceiptCandidates
import com.lucento.app.domain.capture.ScanFixtures
import com.lucento.app.domain.capture.ScanFixtures.bpi
import com.lucento.app.domain.capture.ScanFixtures.cash
import com.lucento.app.domain.capture.ScanFixtures.chase
import com.lucento.app.domain.capture.ScanFixtures.context
import com.lucento.app.domain.capture.ScanFixtures.directory
import com.lucento.app.domain.capture.ScanFixtures.saved
import com.lucento.app.domain.capture.ScanFixtures.today
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Merchant and entity intelligence (scan roadmap phase 3, owner 2026-11-22): versioned dictionaries read as data, the
 * payee told apart from the payment provider and processor, and the person's own habits learned from their ledger —
 * on the phone, nothing stored.
 */
class MerchantIntelligenceTest {

    @Test fun dictionariesAreDataAndABadFileOrKindIsSkipped() {
        assertEquals(mapOf("TEST" to 3), directory.versions)
        assertNull(directory.named("Bad"))
        assertEquals(EntityKind.BILLER, directory.named("meralco")?.kind)
    }

    @Test fun theDictionaryReaderHandlesEscapesAndSkipsBadFiles() {
        val parsed = MiniJson.parse("""{"a": [1, -2.5, true, false, null], "b": "Café \"Q\" \\ \n", "c": {}}""") as Map<*, *>
        assertEquals(listOf(1L, -2.5, true, false, null), parsed["a"])
        assertEquals("Café \"Q\" \\ \n", parsed["b"])
        val directory = MerchantDirectory.parse(
            listOf(
                """{"region": "X", "version": 2, "entities": [{"name": "Café Uno", "kind": "MERCHANT", "aliases": ["cafe uno"]}]}""",
                """{"region": "Y", "entities": [""",
            ),
        )
        assertEquals(mapOf("X" to 2), directory.versions)
        assertEquals("Café Uno", directory.mentions("CAFE UNO MAKATI").single().entity.name)
    }

    @Test fun namesMatchAsWholeWordsWithAccentsAndCaseIgnored() {
        assertEquals(listOf("Walmart"), directory.mentions("WAL-MART SUPERCENTER #2516").map { it.entity.name })
        assertEquals(listOf("Intermarché"), directory.mentions("INTERMARCHE SUPER").map { it.entity.name })
        // "bpi" inside another word is not BPI.
        assertTrue(directory.mentions("SBPIX TRADING").isEmpty())
        // A common word is a weak alias: it counts at the start of a descriptor only.
        val target = MerchantRecognizer.descriptor("TARGET 00012345 MINNEAPOLIS MN", directory)
        assertEquals("Target", target.payee?.name)
        assertNull(MerchantRecognizer.descriptor("ON TARGET MARKETING SERVICES", directory).payee)
    }

    @Test fun processorsAndAppsInFrontOfTheMerchant() {
        val square = MerchantRecognizer.descriptor("SQ *BLUE BOTTLE COFFEE", directory)
        assertEquals("Square", square.processor?.name)
        assertEquals("Blue Bottle Coffee", square.name)
        val paypal = MerchantRecognizer.descriptor("PAYPAL *NETFLIX.COM 402-935-7733", directory)
        assertEquals("Netflix", paypal.payee?.name)
        assertEquals("PayPal", paypal.instrument?.name)
        val starbucks = MerchantRecognizer.descriptor("PURCHASE AUTHORIZED ON 10/05 STARBUCKS STORE 12345 SEATTLE WA", directory)
        assertEquals("Starbucks", starbucks.payee?.name)
        assertEquals("cafe", starbucks.category)
    }

    @Test fun aReceiptsMerchantIsNeverThePaymentProvider() {
        val walmart = MerchantRecognizer.receipt(listOf("WALMART", "SUPERCENTER", "TOTAL  54.32", "AMEX  ************1004", "APPROVED"), directory)
        assertEquals("Walmart", walmart.payee?.name)
        assertEquals("American Express", walmart.instrument?.name)
        assertEquals("1004", walmart.instrumentLast4)
        val gcash = MerchantRecognizer.receipt(listOf("GCash", "Bills Payment", "Biller", "MERALCO", "Total Amount Paid  2,345.67"), directory)
        assertEquals("Meralco", gcash.payee?.name)
        assertEquals("GCash", gcash.instrument?.name)
        assertEquals(setOf("PH"), gcash.countryHints)
    }

    @Test fun categoryIntentsFindThePersonsOwnCategory() {
        assertEquals(ScanFixtures.dining.id, CategoryIntents.resolve("cafe", CategoryKind.EXPENSE, ScanFixtures.categories))
        assertEquals(ScanFixtures.fuel.id, CategoryIntents.resolve("fuel", CategoryKind.EXPENSE, ScanFixtures.categories))
        assertEquals(ScanFixtures.subscriptions.id, CategoryIntents.resolve("streaming", CategoryKind.EXPENSE, ScanFixtures.categories))
        assertEquals(ScanFixtures.health.id, CategoryIntents.resolve("pharmacy", CategoryKind.EXPENSE, ScanFixtures.categories))
        assertNull(CategoryIntents.resolve("pets", CategoryKind.EXPENSE, ScanFixtures.categories))
        // Archived categories are never suggested.
        assertNull(CategoryIntents.resolve("fuel", CategoryKind.EXPENSE, listOf(ScanFixtures.fuel.copy(archived = true))))
    }

    @Test fun habitsAreLearnedFromThePersonsOwnLedger() {
        val history = listOf(
            saved(TransactionType.EXPENSE, 4_500, chase, "2026-11-01", "Shell", ScanFixtures.fuel),
            saved(TransactionType.EXPENSE, 5_200, chase, "2026-11-08", "SHELL OIL 57444", ScanFixtures.fuel),
            saved(TransactionType.EXPENSE, 3_900, chase, "2026-11-15", "Shell", ScanFixtures.transport),
        )
        val memory = MerchantMemory.build(history, directory, today)
        val habit = memory.habit("Shell", TransactionType.EXPENSE, directory)!!
        assertEquals(3, habit.times)
        assertEquals(ScanFixtures.fuel.id, habit.usualCategory)
        assertEquals(chase.id, habit.usualWallet)
        // One purchase is not a habit.
        val once = MerchantMemory.build(history.take(1), directory, today)
        assertNull(once.habit("Shell", TransactionType.EXPENSE, directory)?.usualCategory)
        // Old history doesn't count.
        assertEquals(0, MerchantMemory.build(history, directory, today.plusYears(2)).size)
    }

    @Test fun aHabitSetsTheCategoryAndAccountButNeverOverridesAPickedAccount() {
        val history = (1..3).map { i -> saved(TransactionType.EXPENSE, 4_000L + i, chase, "2026-11-0$i", "Shell", ScanFixtures.fuel) }
        val lines = listOf("SHELL", "PUMP 04 REGULAR", "TOTAL  $41.20", "11/20/2026")
        val memory = MerchantMemory.build(history, directory, today)
        val (usual, _) = ReceiptCandidates.read(lines, context(defaultWalletId = cash.id, history = history), memory)!!
        assertEquals(ScanFixtures.fuel.id, usual.categoryId)
        assertEquals(chase.id, usual.walletId)
        val (picked, _) = ReceiptCandidates.read(lines, context(defaultWalletId = cash.id, history = history, chosen = true, wallets = listOf(cash, bpi)), memory)!!
        assertEquals(cash.id, picked.walletId)
    }

    @Test fun walletsAreFoundByTheirBankButNeverByAGenericWord() {
        val labels = ScanFixtures.labels
        assertEquals(bpi.id, WalletMatcher.named("CASH IN VIA BPI", ScanFixtures.wallets, labels, directory, exclude = ScanFixtures.gcash.id, currency = "PHP")?.id)
        assertNull(WalletMatcher.named("CASH BACK REWARD", ScanFixtures.wallets, labels, directory, exclude = bpi.id, currency = "PHP"))
        val bpiEntity = directory.named("BPI")!!
        assertEquals(bpi.id, WalletMatcher.forEntity(bpiEntity, ScanFixtures.wallets, labels, currency = "PHP")?.id)
    }

    /** The bundled dictionaries: they load, and the rules the engine relies on hold. */
    @Test fun theBundledDictionariesAreSound() {
        val bundled = ScanFixtures.bundledDirectory()
        assumeTrue("assets/merchants not present", bundled != null)
        bundled!!
        assertTrue(bundled.entities.size >= 500)
        assertTrue("US and PH dictionaries are present", bundled.versions.keys.containsAll(listOf("US", "PH", "GLOBAL")))
        val intents = setOf(
            "groceries", "dining", "cafe", "takeout", "convenience", "fuel", "taxi", "parking", "transport", "car", "utilities", "water", "internet",
            "phone", "heating", "housing", "pharmacy", "health", "dental", "vision", "fitness", "personal", "grooming", "pets", "baby", "education",
            "tuition", "books", "streaming", "subscriptions", "games", "entertainment", "hotel", "travel", "shopping", "home_supplies", "furniture",
            "repairs", "gift", "donation", "bank_fees", "tax", "insurance", "other",
        )
        bundled.entities.forEach { e ->
            if (e.kind.isPayee) assertTrue("${e.name}: ${e.category}", e.category == null || e.category in intents)
            assertTrue("${e.name} has aliases", e.aliases.isNotEmpty() || e.weakAliases.isNotEmpty())
        }
        // Every alias names one entity, so a name can't be read two ways.
        val owners = HashMap<String, String>()
        bundled.entities.forEach { e ->
            (e.aliases + e.weakAliases).map { MerchantDirectory.normalize(it) }.distinct().forEach { a ->
                val other = owners.put(a, e.name)
                assertTrue("alias \"$a\" belongs to both ${e.name} and $other", other == null || other == e.name)
            }
        }
        // The examples in the brief are recognised.
        listOf("Walmart", "Target", "Costco", "Kroger", "Whole Foods", "Amazon", "Walgreens", "CVS", "Home Depot", "Starbucks", "McDonald's", "Chevron", "Verizon", "Comcast", "Netflix")
            .forEach { name -> assertNotNull("US: $name", bundled.mentions(name).firstOrNull()) }
        listOf("Jollibee", "Mercury Drug", "Meralco", "Manila Water", "GCash", "Maya", "BDO", "BPI", "UnionBank")
            .forEach { name -> assertNotNull("PH: $name", bundled.mentions(name).firstOrNull()) }
        listOf("Chase", "Bank of America", "Wells Fargo", "Citibank", "Capital One", "PNC", "TD Bank", "Discover", "American Express", "Visa", "Mastercard", "PayPal", "Venmo", "Zelle", "Cash App", "Apple Pay", "Google Pay")
            .forEach { name -> assertTrue("institution or network: $name", bundled.mentions(name).isNotEmpty() || bundled.named(name) != null) }
    }
}
