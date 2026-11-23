package com.lucento.app.domain.capture.merchant

import java.util.Locale

/**
 * Who was paid and how, read from a document. A Walmart receipt paid with American Express names Walmart as the payee
 * and American Express as the instrument; a GCash confirmation for Meralco names Meralco, paid from GCash; a PayPal
 * receipt for PG&E names PG&E, paid with PayPal.
 */
data class Parties(
    /** The business paid, from the dictionary. */
    val payee: FinancialEntity? = null,
    /** The payee's name when no dictionary entry matched but the document makes it clear ("SQ *BLUE BOTTLE" → "Blue Bottle"). */
    val payeeName: String? = null,
    /** How it was paid: a card network, e-wallet or bank named on the document. */
    val instrument: FinancialEntity? = null,
    /** The last four digits of the card or account that paid, when printed. */
    val instrumentLast4: String? = null,
    /** A processor printed in front of the merchant (Square, Toast, PayPal). */
    val processor: FinancialEntity? = null,
    /** A person paid or sent money ("Sent to Juan D."), for a confirmation with no business on it. */
    val recipient: String? = null,
    /** Countries named entities trade in, when each trades in one country only: evidence of where the document is from. */
    val countryHints: Set<String> = emptySet(),
) {
    val name: String? get() = payee?.name ?: payeeName
    val category: String? get() = payee?.category
}

object MerchantRecognizer {

    private const val HEADER_LINES = 6

    /** Lines that say who was paid: "Biller: MERALCO", "You paid $84.12 USD to PG&E", "Merchant  JOLLIBEE". */
    private val PAYEE_LABEL = Regex(
        "\\b(biller|merchant(?: name)?|paid to|pay(?:ment)? to|bills? payment(?: to| for)?|you paid|you sent|sent to|send to|payee|billed by|vendor|store name|pay bills?|pay qr)\\b",
    )

    /** Lines that say how it was paid. */
    private val TENDER = Regex(
        "\\b(tend(?:er(?:ed)?)?|paid (?:by|with|via|using|from)|payment (?:method|type|mode)|mode of payment|pay(?:ment)? via|via|card|debit|credit|chip|contactless|tap|approved|auth(?:orization)?|acct|account)\\b|\\*{3,}|x{4,}",
    )

    /** A tender line, or any line carrying an amount (a rail's name beside what it paid: "Pix  87,83"). */
    private val TENDER_OR_AMOUNT = Regex("\\d[.,]\\d{2}\\b|\\b(tend|paid|payment|pago|pagamento|zahlung|paiement)\\b")

    private val LAST4 = Regex("(?:\\*{2,}|x{2,}|•{2,}|#{2,}|ending(?: in)?|last 4(?: digits)?|card(?: no\\.?| number| #)?\\s*:?\\s*[x*•#]*)\\s*(\\d{4})(?!\\d)")

    /** Person-to-person: "Sent to JUAN D.", "You paid Jane Doe", "Express Send ... JUAN DELA CRUZ". */
    private val RECIPIENT = Regex("(?:sent to|send to|transfer(?:red)? to|you paid|you sent(?: [^a-z]*)? to|recipient(?: name)?|receiver)\\s*[:\\-]?\\s*([a-z][a-z .'*-]{1,40})")

    /** Words that start many card descriptors and say nothing about the merchant. */
    private val DESCRIPTOR_NOISE = Regex(
        "^(?:(?:pos|debit card|check ?card|card|recurring|online|purchase(?: authorized on \\d{1,2}/\\d{1,2})?(?: return)?|pos purchase|pos debit|dbt crd|" +
            "visa|mc|ach(?: debit)?|preauthorized|pre-auth|payment to|bill pay(?:ment)?|bills payment|web|mobile|purchase on)\\s*[-:*]?\\s*)+",
    )

    fun receipt(lines: List<String>, directory: MerchantDirectory): Parties {
        if (directory.entities.isEmpty() || lines.isEmpty()) return Parties()
        val normalized = lines.map { MerchantDirectory.normalize(it) }
        val all = normalized.mapIndexed { i, line -> i to directory.mentions(line) }
        fun usable(i: Int, m: EntityMention) = !m.weak || (i < HEADER_LINES && m.start == 0)

        // Who was paid: a labelled payee first, then a name heading the receipt, then any name in it.
        val labelled = all.firstNotNullOfOrNull { (i, ms) ->
            val line = normalized[i]
            if (!PAYEE_LABEL.containsMatchIn(line)) return@firstNotNullOfOrNull null
            ms.firstOrNull { it.entity.kind.isPayee && !it.weak }
                ?: all.getOrNull(i + 1)?.second?.firstOrNull { it.entity.kind.isPayee && usable(i + 1, it) && line.trimEnd().endsWithLabel() }
        }
        val header = all.take(HEADER_LINES).firstNotNullOfOrNull { (i, ms) -> ms.firstOrNull { it.entity.kind.isPayee && usable(i, it) } }
        // A name further down is used only when the top of the receipt names nobody: on a shop's own receipt, a brand in
        // the item lines ("APPLE IPHONE CASE" at Interdiscount) is what was bought, not who was paid.
        // A confirmation headed by the app or bank that issued it ("Maya", "GCash") still looks for the payee below.
        val headed = com.lucento.app.domain.capture.ReceiptParser.findMerchant(lines.map { it.trim() }.filter { it.isNotEmpty() })
            ?.let { top -> directory.mentions(top).none { it.entity.kind.isInstrument } } == true
        val anywhere = if (headed) null else all.flatMap { (_, ms) -> ms.filter { it.entity.kind.isPayee && !it.weak } }
            .groupingBy { it.entity }.eachCount().maxByOrNull { it.value }?.key
        val payee = labelled?.entity ?: header?.entity ?: anywhere

        // How it was paid: the app that issued a confirmation ("GCash", "PayPal" heading it), else an instrument on a
        // tender line — a payment rail too ("Pix  87,83", "InstaPay") — else any instrument named.
        val instruments = all.flatMap { (i, ms) ->
            ms.filter { m -> usable(i, m) && (m.entity.kind.isInstrument || (m.entity.kind == EntityKind.PROCESSOR && TENDER_OR_AMOUNT.containsMatchIn(normalized[i]))) }
                .map { i to it }
        }
        val issuer = instruments.firstOrNull { (i, m) -> i < HEADER_LINES && m.entity.kind == EntityKind.EWALLET }
        val onTender = instruments.firstOrNull { (i, _) -> TENDER.containsMatchIn(normalized[i]) }
            ?: instruments.firstOrNull { (_, m) -> m.entity.kind == EntityKind.PROCESSOR }
        val instrument = (issuer ?: onTender ?: instruments.firstOrNull())?.second?.entity
        val last4 = (onTender?.first?.let { LAST4.find(normalized[it])?.groupValues?.get(1) })
            ?: normalized.firstNotNullOfOrNull { line -> line.takeIf { TENDER.containsMatchIn(it) }?.let { LAST4.find(it)?.groupValues?.get(1) } }

        val recipient = if (payee == null) normalized.firstNotNullOfOrNull { line -> RECIPIENT.find(line)?.groupValues?.get(1) }?.let(::tidyName) else null
        return Parties(
            payee = payee,
            instrument = instrument,
            instrumentLast4 = last4,
            recipient = recipient?.takeIf { r -> instrument == null || !r.equals(instrument.name, ignoreCase = true) },
            countryHints = listOfNotNull(payee, instrument).mapNotNull { it.countries.singleOrNull() }.toSet(),
        )
    }

    /** A line that is only a label ("Biller", "Merchant:"), its value on the next line. */
    private fun String.endsWithLabel(): Boolean = PAYEE_LABEL.find(this)?.let { m -> this.substring(m.range.last + 1).trim(' ', ':', '-').isEmpty() } ?: false

    /** One statement line's descriptor: "PURCHASE AUTHORIZED ON 10/05 STARBUCKS STORE 12345 SEATTLE WA", "SQ *BLUE BOTTLE". */
    fun descriptor(text: String, directory: MerchantDirectory): Parties {
        if (directory.entities.isEmpty() || text.isBlank()) return Parties()
        // A whole merchant name opening the descriptor wins over a processor prefix it happens to start with
        // ("SP SERVICES" is Singapore's utility, not Shopify's "SP " prefix).
        val opening = DESCRIPTOR_NOISE.replace(MerchantDirectory.normalize(text), "")
        val named = directory.mentions(opening).firstOrNull { it.start == 0 && it.entity.kind.isPayee }
        val processor = if (named != null) null else directory.processor(text)
        val focus = (processor?.second ?: MerchantDirectory.normalize(text)).let { DESCRIPTOR_NOISE.replace(it, "") }
        val mentions = directory.mentions(focus)
        val payee = mentions.firstOrNull { it.entity.kind.isPayee && (!it.weak || it.start == 0) }?.entity
        val whole = directory.mentions(text)
        val instrument = whole.firstOrNull { it.entity.kind.isInstrument && !it.weak && it.entity != processor?.first }?.entity
            ?: processor?.first?.takeIf { it.kind == EntityKind.EWALLET }
        val payeeName = if (payee == null && processor != null) tidyName(cleanDescriptor(processor.second)) else null
        return Parties(
            payee = payee,
            payeeName = payeeName?.takeIf { it.length >= 2 },
            instrument = instrument,
            processor = processor?.first,
            countryHints = listOfNotNull(payee, instrument).mapNotNull { it.countries.singleOrNull() }.toSet(),
        )
    }

    /** A descriptor's merchant words: store numbers, references, phone numbers and a trailing "CITY ST" removed. */
    fun cleanDescriptor(text: String): String =
        text.replace(Regex("\\b\\d{3,}[-\\d]*\\b|#\\s*\\d+|\\*\\w*\\d\\w*"), " ")
            .replace(Regex("\\s+[a-z .]+\\s+(?:al|ak|az|ar|ca|co|ct|de|dc|fl|ga|hi|id|il|in|ia|ks|ky|la|me|md|ma|mi|mn|ms|mo|mt|ne|nv|nh|nj|nm|ny|nc|nd|oh|ok|or|pa|ri|sc|sd|tn|tx|ut|vt|va|wa|wv|wi|wy)$"), " ")
            .replace(Regex("\\s+(?:us|usa|ph|phl)$"), " ")
            .replace(Regex("[*_]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', '.', ',')

    /** "juan dela cruz" → "Juan Dela Cruz"; at most five words. */
    fun tidyName(text: String): String =
        text.trim().split(' ').filter { it.isNotBlank() }.take(5)
            .joinToString(" ") { w -> w.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) } }
            .trim(' ', '-', '*')
}
