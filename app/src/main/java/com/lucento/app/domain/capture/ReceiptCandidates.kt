package com.lucento.app.domain.capture

import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.capture.merchant.CategoryIntents
import com.lucento.app.domain.capture.merchant.EntityKind
import com.lucento.app.domain.capture.merchant.MerchantMemory
import com.lucento.app.domain.capture.merchant.MerchantRecognizer
import com.lucento.app.domain.capture.merchant.ReceiptCues
import com.lucento.app.domain.capture.merchant.WalletMatcher
import java.util.Locale

/**
 * Receipt text (from on-device OCR or a PDF) → one candidate: the business paid as the description, the total paid, the
 * printed date. A receipt is one payment, so its line items are never split into separate transactions.
 *
 * - **Who was paid** is the merchant or biller, never the payment provider: a GCash confirmation for Meralco is
 *   "Meralco", paid from the person's GCash wallet; a Walmart receipt paid with American Express is "Walmart", on their
 *   Amex card when they have one ([MerchantRecognizer], [WalletMatcher]). A transfer to a person reads "Sent to Juan D.".
 * - **The currency** is what the receipt prints; a shared symbol ("$") is settled by where the receipt is from, else by
 *   the person's only account in such a currency, marked to check ([DocumentCurrency]); never the default by assumption.
 * - **The account**: the one that paid when the receipt names it, else the person's account in the receipt's currency,
 *   else their usual account for this merchant (unless they picked one), else the active wallet.
 * - **The category**: the person's own habit for this merchant ([MerchantMemory]), else the dictionary's, else the words.
 * - A refund or return is income (a refund) instead of an expense.
 *
 * A total printed next to "Total" is pre-filled. Without one, the amounts that could be the payment are offered as
 * choices (never cash tendered, change or VAT lines) and nothing is pre-filled, because a guessed total must never be
 * saved silently. Items that don't add up to the total, a missing date and an unclear day/month order are flagged on the
 * row.
 */
object ReceiptCandidates {

    fun from(lines: List<String>, context: InterpretationContext): TransactionCandidate? = read(lines, context)?.first

    /** A confirmation that moved money between accounts rather than paying someone ("Cash in", "Transfer to BPI"). */
    private val OWN_TRANSFER = Regex("\\b(cash[- ]?in|top[- ]?up|fund\\s+transfer|transfer(red)?\\s+(to|from)|send\\s+to\\s+(bank|own)|bank\\s+transfer|instapay|pesonet)\\b")

    /** Money arriving in the confirming app's wallet from another account. */
    private val INCOMING = Regex("\\b(cash[- ]?in|top[- ]?up|add\\s+money|received\\s+from|transfer(red)?\\s+from)\\b")

    /** The candidate and the suggestion it came from (for the reply that says which line was the total). */
    internal fun read(
        lines: List<String>,
        context: InterpretationContext,
        memory: MerchantMemory = MerchantMemory.EMPTY,
    ): Pair<TransactionCandidate, ReceiptSuggestion>? {
        val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return null
        val defaultWallet = context.wallets.firstOrNull { it.id == context.defaultWalletId } ?: context.wallets.firstOrNull()
        val parties = MerchantRecognizer.receipt(clean, context.directory)
        val country = DocumentLocale.country(clean, parties.countryHints)
        val currency = DocumentCurrency.read(clean, country, context.wallets.map { it.currencyCode })
        val suggestion = ReceiptParser.parse(
            clean, context.today,
            expectedCurrency = currency.code ?: defaultWallet?.currencyCode,
            dateOrder = DocumentLocale.dateOrder(country.code),
            phoneDayFirst = context.dayFirst,
        )
        val code = suggestion.currencyCode ?: currency.code
        val type = if (suggestion.refund) TransactionType.INCOME else TransactionType.EXPENSE
        val name = parties.name ?: suggestion.merchant

        // The account: the one that paid, the one in the receipt's currency, the usual one for this merchant, the default.
        val paidFrom = parties.instrument?.let { WalletMatcher.forEntity(it, context.wallets, context.walletLabels, code, parties.instrumentLast4) }
        val inCurrency = code?.takeIf { it != defaultWallet?.currencyCode }?.let { c -> context.wallets.firstOrNull { it.currencyCode == c } }
        val habit = memory.habit(name, type, context.directory)
        val usual = habit?.usualWallet
            ?.takeIf { !context.defaultWalletChosen }
            ?.let { id -> context.wallets.firstOrNull { it.id == id } }
            ?.takeIf { code == null || it.currencyCode == code }
        val wallet = paidFrom ?: inCurrency ?: usual ?: defaultWallet

        val flags = LinkedHashSet<CandidateFlag>()
        val labelled = suggestion.totalConfidence == TotalConfidence.LABELLED
        val amount = if (labelled) suggestion.total else null
        val options = if (labelled) emptyList() else suggestion.amounts.take(4)
        when (suggestion.totalConfidence) {
            TotalConfidence.LABELLED -> Unit
            TotalConfidence.LARGEST_AMOUNT -> flags += CandidateFlag.TOTAL_UNCONFIRMED
            TotalConfidence.NONE -> flags += CandidateFlag.AMOUNT_MISSING
        }
        if (labelled && type == TransactionType.EXPENSE &&
            ReceiptParser.checkItems(clean, suggestion.total, suggestion.fractionDigits) == ReceiptParser.ItemsCheck.DOES_NOT_ADD_UP
        ) {
            flags += CandidateFlag.ITEMS_DONT_ADD_UP
        }
        if (suggestion.date == null || suggestion.dateAmbiguous) flags += CandidateFlag.DATE_UNCERTAIN
        if (suggestion.currencyCode == null && currency.certainty == CurrencyCertainty.ACCOUNT) flags += CandidateFlag.CURRENCY_ASSUMED

        // A confirmation of money moved to another of the person's accounts is a transfer, not spending.
        val joined = clean.joinToString(" ").lowercase(Locale.ROOT)
        if (parties.payee == null && type == TransactionType.EXPENSE && OWN_TRANSFER.containsMatchIn(joined) && wallet != null) {
            val other = WalletMatcher.named(clean.joinToString(" "), context.wallets, context.walletLabels, context.directory, wallet.id, wallet.currencyCode)
            if (other != null) {
                // "Cash In from BPI" on a GCash confirmation moves money from BPI into GCash; "Transfer to BPI", the reverse.
                val incoming = INCOMING.containsMatchIn(joined)
                return TransactionCandidate(
                    description = if (incoming) "Transfer from ${other.name}" else parties.recipient?.let { "Transfer to $it" } ?: "Transfer to ${other.name}",
                    type = TransactionType.TRANSFER_OUT,
                    amount = amount,
                    amountOptions = options,
                    walletId = if (incoming) other.id else wallet.id,
                    toWalletId = if (incoming) wallet.id else other.id,
                    categoryId = null,
                    date = suggestion.date ?: context.today,
                    mentionedCurrency = code,
                    sourceText = clean.joinToString("\n"),
                    flags = flags,
                    walletFromText = paidFrom != null,
                ) to suggestion
            }
        }

        // The category: the person's habit, the dictionary's, what the receipt's own words say the business is (a tip
        // line and a server: a restaurant), then words in the shop name and header lines (item names further down are
        // too noisy; a mall in the address is where, not what).
        val kind = if (type == TransactionType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
        val header = " " + (listOfNotNull(name) + clean.take(4)).joinToString(" ").lowercase(Locale.ROOT) + " "
        val categoryId = habit?.usualCategory?.takeIf { id -> context.categories.any { it.id == id && it.kind == kind && !it.archived } }
            ?: CategoryIntents.resolve(if (type == TransactionType.INCOME) "refund" else parties.category, kind, context.categories)
            ?: (if (type == TransactionType.EXPENSE) CategoryIntents.resolve(ReceiptCues.intent(clean), kind, context.categories) else null)
            ?: QuickEntryParser.detectCategory(header, type, context.categories)
        if (categoryId == null) flags += CandidateFlag.CATEGORY_UNMATCHED

        val description = parties.name
            ?: parties.recipient?.let { "Sent to $it" }
            ?: suggestion.merchant
            ?: if (type == TransactionType.INCOME) "Refund" else "Receipt"
        return TransactionCandidate(
            description = description,
            type = type,
            amount = amount,
            amountOptions = options,
            walletId = wallet?.id,
            categoryId = categoryId,
            date = suggestion.date ?: context.today,
            mentionedCurrency = code,
            sourceText = clean.joinToString("\n"),
            flags = flags,
            walletFromText = paidFrom != null,
            // Paid from an app the person has no wallet for: say so, the row stays in the active wallet.
            hint = parties.instrument?.takeIf { paidFrom == null && it.kind == EntityKind.EWALLET }?.let { i ->
                "Paid with ${i.name} — choose that account if you track it in Lucento."
            },
        ) to suggestion
    }
}
