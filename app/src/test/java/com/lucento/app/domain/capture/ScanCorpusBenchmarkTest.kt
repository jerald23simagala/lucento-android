package com.lucento.app.domain.capture

import com.lucento.app.core.money.Money
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import com.lucento.app.domain.capture.merchant.MerchantDirectory
import com.lucento.app.domain.capture.merchant.MerchantRecognizer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

/**
 * Lucento Scan's benchmark (docs/SCAN.md "Benchmark"): an independent synthetic corpus in `src/test/resources/scan-corpus`
 * — written without looking at the parser, with ground truth for each document — read end to end by the on-device
 * interpreter, scored by document type and by country. `dev` is the set rules may be tuned on; `holdout` is only
 * measured. Results are written to `build/reports/scan-benchmark/` and printed.
 *
 * These are OCR-like text documents, not photos: real photos (glare, creases, fading, misreads) score lower. The targets
 * asserted are the owner's acceptance criteria (2026-11-22) on what the text corpus can measure.
 */
class ScanCorpusBenchmarkTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test fun devSet() = run("dev", enforce = true)

    @Test fun holdoutSet() = run("holdout", enforce = true)

    // ------------------------------------------------------------------ the persona reading the documents

    /** Every category intent as a category, so a category is scored exactly. */
    private val intents = listOf(
        "groceries", "dining", "cafe", "takeout", "convenience", "fuel", "taxi", "parking", "transport", "car", "utilities", "water", "internet",
        "phone", "housing", "pharmacy", "health", "dental", "vision", "fitness", "personal", "pets", "baby", "education", "books", "streaming",
        "subscriptions", "games", "entertainment", "hotel", "travel", "shopping", "home_supplies", "furniture", "gift", "donation", "bank_fees",
        "tax", "insurance", "other",
    )
    private val categories: List<Category> = intents.mapIndexed { i, key -> Category(100L + i, key.replace('_', ' '), CategoryKind.EXPENSE, key) } +
        listOf("work", "refund", "investments", "business", "income").mapIndexed { i, key -> Category(200L + i, key, CategoryKind.INCOME, key) }

    /** A Filipino with a US dollar account (the app's two priority markets in one person). */
    private val wallets = listOf(
        wallet(1, "Cash", WalletType.CASH, "PHP"),
        wallet(2, "BPI Savings", WalletType.BANK, "PHP", "ph_bpi"),
        wallet(3, "GCash", WalletType.E_WALLET, "PHP", "ph_gcash"),
        wallet(4, "BDO Card", WalletType.CREDIT_CARD, "PHP", "ph_bdo"),
        wallet(10, "US Checking", WalletType.BANK, "USD"),
    )
    private val labels = mapOf(2L to listOf("BPI"), 3L to listOf("GCash"), 4L to listOf("BDO"))

    private fun wallet(id: Long, name: String, type: WalletType, code: String, institution: String? = null) =
        Wallet(id, name, type, Money(0, code), 0, institutionId = institution)

    private val directory: MerchantDirectory by lazy { ScanFixtures.bundledDirectory() ?: MerchantDirectory.EMPTY }

    // ------------------------------------------------------------------ the corpus

    private class Doc(
        val id: String,
        val country: String,
        val type: String,
        val source: String,
        val noise: Boolean,
        val today: LocalDate,
        val pages: List<List<String>>,
        val expected: List<JsonObject>,
    )

    private fun load(set: String): List<Doc> {
        val url = javaClass.classLoader?.getResource("scan-corpus/$set") ?: return emptyList()
        val dir = File(url.toURI())
        return dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().sortedBy { it.name }.map { f ->
            val o = json.parseToJsonElement(f.readText()).jsonObject
            Doc(
                id = o.str("id") ?: f.nameWithoutExtension,
                country = o.str("country") ?: "??",
                type = o.str("type") ?: "?",
                source = o.str("source") ?: "PHOTO",
                noise = o["noise"]?.jsonPrimitive?.booleanOrNull ?: false,
                today = o.str("today")?.let(LocalDate::parse) ?: LocalDate.parse("2026-11-22"),
                pages = o["pages"]!!.jsonArray.map { p -> p.jsonArray.map { it.jsonPrimitive.content } },
                expected = o["expected"]!!.jsonObject["documents"]!!.jsonArray.map { it.jsonObject },
            )
        }
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.dec(key: String): BigDecimal? = str(key)?.let { runCatching { BigDecimal(it) }.getOrNull() }

    // ------------------------------------------------------------------ scoring

    /** A count of hits out of tries, for one measure. */
    private class Score {
        var hit = 0
        var of = 0
        fun add(ok: Boolean) {
            of++
            if (ok) hit++
        }
        val rate: Double get() = if (of == 0) Double.NaN else hit.toDouble() / of
        override fun toString() = if (of == 0) "—" else "$hit/$of (${"%.0f".format(Locale.ROOT, rate * 100)} %)"
    }

    private class Card {
        val docs = Score()
        val kind = Score()
        val total = Score()
        val totalOrTap = Score()
        val merchant = Score()
        val currency = Score()
        val currencyWrong = Score()
        val date = Score()
        val dateSilentlyWrong = Score()
        val ambiguousFlagged = Score()
        val category = Score()
        val instrument = Score()
        val receiptDirection = Score()
        val recall = Score()
        val precision = Score()
        val direction = Score()
        val lineMerchant = Score()
        val facts = Score()
        val silentDuplicates = Score()
    }

    private fun categoryKey(id: Long?) = categories.firstOrNull { it.id == id }?.iconKey

    private fun norm(s: String) = MerchantDirectory.normalize(s).replace(Regex("[^\\p{L}\\p{N}]"), "")

    /** The same name, or two names the dictionaries give to one business ("E-mart" and "이마트"). */
    private fun nameMatches(read: String, expected: String): Boolean {
        val a = norm(read)
        val b = norm(expected)
        if (a.isNotEmpty() && b.isNotEmpty() && (a.contains(b) || b.contains(a))) return true
        val entity = directory.named(read) ?: return false
        return directory.mentions(expected).any { it.entity == entity }
    }

    private fun run(set: String, enforce: Boolean) {
        val docs = load(set)
        assumeTrue("scan-corpus/$set not present", docs.isNotEmpty())
        val overall = Card()
        val byType = sortedMapOf<String, Card>()
        val byCountry = sortedMapOf<String, Card>()
        val failures = ArrayList<String>()
        val interpreter = LocalTransactionInterpreter()

        docs.forEach { doc ->
            val cards = listOf(overall, byType.getOrPut(doc.type) { Card() }, byCountry.getOrPut(doc.country) { Card() })
            val origin = if (doc.source == "PDF_TEXT") PageOrigin.PDF_TEXT else PageOrigin.PHOTO
            val document = ScanDocument(doc.pages.map { ScanPage.ofLines(it, origin) })
            val defaultWallet = if (doc.country == "US") 10L else 1L
            val context = InterpretationContext(
                wallets, categories, doc.today, defaultWallet, directory = directory, walletLabels = labels, dayFirst = null,
            )
            val parts = DocumentAssembler.assemble(document, doc.today)
            val result = runBlocking { interpreter.interpretDocument(document, context) }
            val items = (result as? Interpretation.Candidates)?.items.orEmpty()
            val reports = (result as? Interpretation.Candidates)?.statements.orEmpty()
            cards.forEach { it.docs.add(true) }

            doc.expected.forEachIndexed { index, exp ->
                val kind = exp.str("kind") ?: "RECEIPT"
                val readKind = parts.getOrNull(index)?.kind?.name ?: parts.firstOrNull()?.kind?.name
                cards.forEach { it.kind.add(readKind == kind) }
                if (readKind != kind) failures += "${doc.id}: read as $readKind, is $kind"
                val evidence = exp.str("currencyEvidence")
                val currency = exp.str("currency")
                if (kind == "RECEIPT") {
                    val item = if (doc.expected.size == 1) items.firstOrNull() else items.getOrNull(index)
                    val total = exp.dec("total")
                    if (total != null) {
                        val exact = item?.amount?.compareTo(total) == 0
                        cards.forEach { it.total.add(exact) }
                        cards.forEach { it.totalOrTap.add(exact || item?.amountOptions?.any { o -> o.compareTo(total) == 0 } == true) }
                        if (!exact) failures += "${doc.id}: total ${item?.amount} (options ${item?.amountOptions}), is $total"
                    }
                    exp.str("merchant")?.let { m ->
                        val ok = item != null && nameMatches(item.description, m)
                        cards.forEach { it.merchant.add(ok) }
                        if (!ok) failures += "${doc.id}: merchant '${item?.description}', is '$m'"
                    }
                    if (currency != null && (evidence == "EXPLICIT" || evidence == "CONTEXT")) {
                        val ok = item?.mentionedCurrency == currency
                        cards.forEach { it.currency.add(ok) }
                        if (!ok) failures += "${doc.id}: currency ${item?.mentionedCurrency}, is $currency ($evidence)"
                    }
                    if (currency != null) cards.forEach { it.currencyWrong.add(item?.mentionedCurrency != null && item.mentionedCurrency != currency) }
                    val date = exp.str("date")?.let(LocalDate::parse)
                    val ambiguous = exp["dateAmbiguous"]?.jsonPrimitive?.booleanOrNull == true
                    if (date != null && item != null) {
                        if (ambiguous) {
                            cards.forEach { it.ambiguousFlagged.add(CandidateFlag.DATE_UNCERTAIN in item.flags) }
                        } else {
                            cards.forEach { it.date.add(item.date == date) }
                            if (item.date != date) failures += "${doc.id}: date ${item.date}, is $date"
                        }
                        cards.forEach { it.dateSilentlyWrong.add(item.date != date && CandidateFlag.DATE_UNCERTAIN !in item.flags) }
                    }
                    exp.str("direction")?.let { d ->
                        val read = when (item?.type) {
                            TransactionType.INCOME -> "IN"
                            null -> null
                            else -> "OUT"
                        }
                        cards.forEach { it.receiptDirection.add(read == d) }
                    }
                    exp.str("category")?.takeIf { it in intents && it != "other" }?.let { c ->
                        val ok = categoryKey(item?.categoryId) == c
                        cards.forEach { it.category.add(ok) }
                        if (!ok) failures += "${doc.id}: category ${categoryKey(item?.categoryId)}, is $c"
                    }
                    exp.str("paymentInstrument")?.takeIf { !it.equals("Cash", ignoreCase = true) }?.let { p ->
                        val read = MerchantRecognizer.receipt(doc.pages.flatten(), directory).instrument?.name
                        val ok = read != null && nameMatches(read, p)
                        cards.forEach { it.instrument.add(ok) }
                        if (!ok) failures += "${doc.id}: paid with $read, is $p"
                    }
                } else {
                    val expectedTx = exp["transactions"]?.jsonArray?.map { it.jsonObject }.orEmpty()
                    val report = reports.getOrNull(index) ?: reports.firstOrNull()
                    val account = report?.accountId
                    val unused = items.toMutableList()
                    var matched = 0
                    expectedTx.forEach { t ->
                        val amount = t.dec("amount") ?: return@forEach
                        val date = t.str("date")?.let(LocalDate::parse)
                        val hit = unused.firstOrNull { c -> c.amount?.compareTo(amount) == 0 && (date == null || c.date == date) }
                            ?: unused.firstOrNull { c -> c.amount?.compareTo(amount) == 0 }
                        cards.forEach { it.recall.add(hit != null) }
                        if (hit == null) {
                            failures += "${doc.id}: line ${t.str("date")} ${t.str("description")} $amount not read"
                            return@forEach
                        }
                        unused.remove(hit)
                        matched++
                        val effect = when {
                            hit.type == TransactionType.INCOME -> "IN"
                            hit.type == TransactionType.EXPENSE -> "OUT"
                            hit.toWalletId != null && hit.toWalletId == account -> "IN"
                            hit.walletId != null && hit.walletId == account -> "OUT"
                            hit.toWalletId == account -> "IN"
                            else -> "OUT"
                        }
                        val ok = effect == t.str("direction")
                        cards.forEach { it.direction.add(ok) }
                        if (!ok) failures += "${doc.id}: ${t.str("description")} $amount read $effect (${hit.type}), is ${t.str("direction")}"
                        t.str("merchant")?.let { m -> cards.forEach { it.lineMerchant.add(nameMatches(hit.description, m)) } }
                    }
                    cards.forEach { c -> repeat(items.size) { i -> c.precision.add(i < matched) } }
                    if (items.size > matched) {
                        failures += "${doc.id}: ${items.size} rows for ${expectedTx.size} lines; extra: " +
                            unused.take(4).joinToString { "${it.date} ${it.description.take(24)} ${it.amount}" }
                    }
                    // Lines read twice with nothing on the row to say so.
                    unused.forEach { extra ->
                        val twin = extra.amount != null && items.any { it !== extra && it.amount?.compareTo(extra.amount) == 0 && it.date == extra.date }
                        cards.forEach { it.silentDuplicates.add(twin && extra.hint == null) }
                        if (twin && extra.hint == null) failures += "${doc.id}: read twice without a mark: ${extra.date} ${extra.description} ${extra.amount}"
                    }
                    if (currency != null && (evidence == "EXPLICIT" || evidence == "CONTEXT")) {
                        val read = items.firstNotNullOfOrNull { it.mentionedCurrency } ?: wallets.firstOrNull { it.id == account }?.currencyCode
                        cards.forEach { it.currency.add(read == currency) }
                        if (read != currency) failures += "${doc.id}: statement currency $read, is $currency ($evidence)"
                    }
                    val s = report?.summary
                    listOf(
                        "openingBalance" to s?.opening, "closingBalance" to s?.closing, "minimumPayment" to s?.minimumDue,
                        "creditLimit" to s?.creditLimit, "availableCredit" to s?.availableCredit,
                    ).forEach { (key, read) ->
                        exp.dec(key)?.let { v ->
                            val ok = read?.compareTo(v) == 0 || read?.abs()?.compareTo(v.abs()) == 0
                            cards.forEach { it.facts.add(ok) }
                            if (!ok) failures += "${doc.id}: $key read $read, is $v"
                        }
                    }
                    exp.str("paymentDueDate")?.let { d ->
                        val ok = s?.dueDate == LocalDate.parse(d)
                        cards.forEach { it.facts.add(ok) }
                        if (!ok) failures += "${doc.id}: paymentDueDate read ${s?.dueDate}, is $d"
                    }
                }
            }
        }

        // Two receipts photographed side by side, made from the corpus's own single receipts.
        val split = Score()
        val singles = docs.filter { d ->
            d.pages.size == 1 && d.expected.size == 1 && d.expected.single().str("kind") == "RECEIPT" && d.expected.single().dec("total") != null && !d.noise &&
                d.pages.single().size in 6..40
        }
        singles.chunked(2).filter { it.size == 2 }.forEach { (a, b) ->
            val fragments = fragmentsOf(a.pages.single(), 0) + fragmentsOf(b.pages.single(), 2600)
            val result = runBlocking {
                interpreter.interpretDocument(
                    ScanDocument(listOf(ScanPage.ofFragments(fragments, PageOrigin.PHOTO))),
                    InterpretationContext(wallets, categories, maxOf(a.today, b.today), 1L, directory = directory, walletLabels = labels),
                )
            }
            val amounts = (result as? Interpretation.Candidates)?.items?.mapNotNull { it.amount }.orEmpty()
            val ok = amounts.size == 2 && listOf(a, b).all { d -> amounts.any { it.compareTo(d.expected.single().dec("total")!!) == 0 } }
            split.add(ok)
            if (!ok) failures += "side by side ${a.id} + ${b.id}: read ${amounts}"
        }

        val report = render(set, docs.size, overall, byType, byCountry, split, failures)
        val out = File("build/reports/scan-benchmark").apply { mkdirs() }
        File(out, "$set.md").writeText(report)
        println(report)

        if (enforce) {
            // The owner's targets (2026-11-22), on what a text corpus can measure.
            assertTrue("statement recall ${overall.recall}", overall.recall.of == 0 || overall.recall.rate >= 0.95)
            assertTrue("currency on unambiguous documents ${overall.currency}", overall.currency.rate >= 0.95)
            assertTrue("statement direction ${overall.direction}", overall.direction.of == 0 || overall.direction.rate >= 0.95)
            assertEquals("lines read twice without a mark", 0, overall.silentDuplicates.hit)
        }
    }

    /** A receipt's printed lines as OCR fragments in a column starting at [left]: labels left, amounts right. */
    private fun fragmentsOf(lines: List<String>, left: Int): List<TextFragment> = lines.flatMapIndexed { i, line ->
        val y = 100 + i * 44
        // A receipt about 1,300 px wide: the first cell on the left, the amount right-aligned, any others between.
        val cells = line.split(Regex("\\s{2,}"))
        cells.mapIndexed { k, cell ->
            val width = cell.length * 14
            val x = when {
                k == 0 -> left
                k == cells.lastIndex -> left + 1300 - width
                else -> left + 700 + (k - 1) * 180
            }
            TextFragment(cell, x, y, x + width, y + 30)
        }
    }

    private fun render(
        set: String,
        n: Int,
        overall: Card,
        byType: Map<String, Card>,
        byCountry: Map<String, Card>,
        split: Score,
        failures: List<String>,
    ): String = buildString {
        appendLine("# Lucento Scan benchmark — $set ($n documents)")
        appendLine()
        appendLine("Dictionaries: ${if (directory.entities.isEmpty()) "none" else "${directory.entities.size} entities (${directory.versions.entries.joinToString { "${it.key} v${it.value}" }})"}")
        appendLine()
        appendLine("| Measure | Result |")
        appendLine("|---|---|")
        appendLine("| Receipt vs statement told apart | ${overall.kind} |")
        appendLine("| Receipt total pre-filled correctly | ${overall.total} |")
        appendLine("| Receipt total pre-filled or one tap away | ${overall.totalOrTap} |")
        appendLine("| Merchant (receipts) | ${overall.merchant} |")
        appendLine("| Currency, unambiguous documents | ${overall.currency} |")
        appendLine("| Currency read but wrong (all documents) | ${overall.currencyWrong} |")
        appendLine("| Receipt date (unambiguous) | ${overall.date} |")
        appendLine("| Ambiguous dates marked to check | ${overall.ambiguousFlagged} |")
        appendLine("| Dates wrong without a mark | ${overall.dateSilentlyWrong} |")
        appendLine("| Category | ${overall.category} |")
        appendLine("| Payment instrument (receipts) | ${overall.instrument} |")
        appendLine("| Receipt direction (paid vs refund) | ${overall.receiptDirection} |")
        appendLine("| Statement lines found (recall) | ${overall.recall} |")
        appendLine("| Statement rows that are real lines (precision) | ${overall.precision} |")
        appendLine("| Statement direction | ${overall.direction} |")
        appendLine("| Statement line merchant | ${overall.lineMerchant} |")
        appendLine("| Statement facts (balances, minimum, limit, due date) | ${overall.facts} |")
        appendLine("| Lines read twice without a mark | ${overall.silentDuplicates} |")
        appendLine("| Two receipts in one photo, both read | $split |")
        fun table(title: String, cards: Map<String, Card>) {
            appendLine()
            appendLine("## By $title")
            appendLine()
            appendLine("| $title | Docs | Kind | Total | Merchant | Currency | Date | Category | Recall | Direction |")
            appendLine("|---|---|---|---|---|---|---|---|---|---|")
            cards.forEach { (k, c) ->
                appendLine("| $k | ${c.docs.of} | ${c.kind} | ${c.total} | ${c.merchant} | ${c.currency} | ${c.date} | ${c.category} | ${c.recall} | ${c.direction} |")
            }
        }
        table("type", byType)
        table("country", byCountry)
        appendLine()
        appendLine("## Misses (${failures.size})")
        appendLine()
        failures.forEach { appendLine("- $it") }
    }
}
