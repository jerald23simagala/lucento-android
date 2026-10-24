package com.lucento.app.domain.capture

import com.lucento.app.domain.TransactionType
import java.util.Locale

/**
 * Receipt text (from on-device OCR) → one expense candidate: the shop as the description, the total paid, the printed
 * date. A receipt is one payment, so its line items are never split into separate transactions.
 *
 * A total printed next to "Total" is pre-filled. Without one, the largest amounts are offered as choices and nothing is
 * pre-filled, because a guessed total must never be saved silently. Items that don't add up to the total, a missing
 * date and an unclear day/month order are flagged on the row.
 */
object ReceiptCandidates {

    fun from(lines: List<String>, context: InterpretationContext): TransactionCandidate? {
        val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return null
        val defaultWallet = context.wallets.firstOrNull { it.id == context.defaultWalletId } ?: context.wallets.firstOrNull()
        val suggestion = ReceiptParser.parse(clean, context.today, defaultWallet?.currencyCode)

        // A receipt in another currency goes to an account in that currency when there is one; never converted.
        val walletId = suggestion.currencyCode
            ?.takeIf { it != defaultWallet?.currencyCode }
            ?.let { code -> context.wallets.firstOrNull { it.currencyCode == code }?.id }
            ?: defaultWallet?.id

        val flags = LinkedHashSet<CandidateFlag>()
        val labelled = suggestion.totalConfidence == TotalConfidence.LABELLED
        val amount = if (labelled) suggestion.total else null
        val options = if (labelled) emptyList() else suggestion.amounts.take(4)
        when (suggestion.totalConfidence) {
            TotalConfidence.LABELLED -> Unit
            TotalConfidence.LARGEST_AMOUNT -> flags += CandidateFlag.TOTAL_UNCONFIRMED
            TotalConfidence.NONE -> flags += CandidateFlag.AMOUNT_MISSING
        }
        if (labelled && ReceiptParser.checkItems(clean, suggestion.total, suggestion.fractionDigits) == ReceiptParser.ItemsCheck.DOES_NOT_ADD_UP) {
            flags += CandidateFlag.ITEMS_DONT_ADD_UP
        }
        if (suggestion.date == null || suggestion.dateAmbiguous) flags += CandidateFlag.DATE_UNCERTAIN

        // Category from the shop name and the header lines only; item names further down are too noisy.
        val header = " " + (listOfNotNull(suggestion.merchant) + clean.take(4)).joinToString(" ").lowercase(Locale.ROOT) + " "
        val categoryId = QuickEntryParser.detectCategory(header, TransactionType.EXPENSE, context.categories)
        if (categoryId == null) flags += CandidateFlag.CATEGORY_UNMATCHED

        return TransactionCandidate(
            description = suggestion.merchant ?: "Receipt",
            type = TransactionType.EXPENSE,
            amount = amount,
            amountOptions = options,
            walletId = walletId,
            categoryId = categoryId,
            date = suggestion.date ?: context.today,
            mentionedCurrency = suggestion.currencyCode,
            sourceText = clean.joinToString("\n"),
            flags = flags,
        )
    }
}
