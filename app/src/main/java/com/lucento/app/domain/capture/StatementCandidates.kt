package com.lucento.app.domain.capture

import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A statement read by [StatementParser] → one candidate per line, all in the account the statement belongs to (the
 * composer's active wallet, or the account in the statement's currency). Money out is an expense and money in is income,
 * except:
 * - money into a credit card or loan is a payment from another account: a transfer, whose source the user picks;
 * - a line naming another of the user's accounts ("Transfer to BDO Savings") is a transfer between the two;
 * - an ATM or cash withdrawal moves money into the user's cash wallet (when there is exactly one in the currency), as a
 *   typed "withdrew 2000" does.
 * Every row is marked as read from a statement, so the review's "already saved?" check is looser (posting dates lag
 * and banks' descriptions differ from the user's), and a direction the statement didn't settle is marked to check.
 */
object StatementCandidates {

    fun from(reading: StatementReading, context: InterpretationContext): List<TransactionCandidate> {
        val active = context.wallets.firstOrNull { it.id == context.defaultWalletId } ?: context.wallets.firstOrNull()
        val account = reading.currencyCode
            ?.takeIf { it != active?.currencyCode }
            ?.let { code -> context.wallets.firstOrNull { it.currencyCode == code } }
            ?: active
        val liability = account?.type?.isLiability == true
        return reading.lines.map { line ->
            val words = " " + line.description.lowercase(Locale.ROOT) + " "
            val flags = linkedSetOf(CandidateFlag.FROM_STATEMENT)
            if (line.date == null || line.dateAmbiguous) flags += CandidateFlag.DATE_UNCERTAIN
            if (line.flow == StatementFlow.UNKNOWN) flags += CandidateFlag.DIRECTION_UNCERTAIN
            val other = otherAccountNamed(words, context.wallets, account)
                ?: if (line.flow == StatementFlow.OUT && CASH_WITHDRAWAL.containsMatchIn(words)) onlyCashWallet(context.wallets, account) else null
            val base = TransactionCandidate(
                description = line.description.ifBlank { if (line.flow == StatementFlow.IN) "Money in" else "Statement line" },
                type = TransactionType.EXPENSE,
                amount = line.amount,
                walletId = account?.id,
                categoryId = null,
                date = line.date ?: context.today,
                mentionedCurrency = reading.currencyCode,
                sourceText = line.source,
                flags = flags,
            )
            when {
                // A payment into a card or loan comes from one of the user's accounts.
                line.flow == StatementFlow.IN && liability ->
                    base.copy(type = TransactionType.TRANSFER_OUT, walletId = other?.id, toWalletId = account?.id, walletFromText = other != null)
                other != null && line.flow == StatementFlow.IN ->
                    base.copy(type = TransactionType.TRANSFER_OUT, walletId = other.id, toWalletId = account?.id, walletFromText = true)
                other != null && line.flow == StatementFlow.OUT ->
                    base.copy(type = TransactionType.TRANSFER_OUT, toWalletId = other.id)
                else -> {
                    val type = if (line.flow == StatementFlow.IN) TransactionType.INCOME else TransactionType.EXPENSE
                    val category = QuickEntryParser.detectCategory(words, type, context.categories)
                    base.copy(
                        type = type,
                        categoryId = category,
                        flags = if (category == null) flags + CandidateFlag.CATEGORY_UNMATCHED else flags,
                    )
                }
            }
        }
    }

    private val CASH_WITHDRAWAL = Regex("\\batm\\b|cash\\s+withdrawal|over[- ]the[- ]counter\\s+withdrawal")

    /** The user's one cash wallet in [account]'s currency (not [account] itself); null when there are none or several. */
    private fun onlyCashWallet(wallets: List<Wallet>, account: Wallet?): Wallet? =
        account?.takeIf { it.type != com.lucento.app.domain.WalletType.CASH }?.let { a ->
            wallets.filter { it.type == com.lucento.app.domain.WalletType.CASH && it.currencyCode == a.currencyCode && it.id != a.id }.singleOrNull()
        }

    /** Another of the user's accounts, in the statement's currency, whose name the line's words contain. */
    private fun otherAccountNamed(words: String, wallets: List<Wallet>, account: Wallet?): Wallet? =
        wallets.filter { it.id != account?.id && (account == null || it.currencyCode == account.currencyCode) }
            .filter { it.name.trim().length >= 3 }
            .sortedByDescending { it.name.length }
            .firstOrNull { w -> Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(w.name.trim().lowercase(Locale.ROOT)) + "(?![\\p{L}\\p{N}])").containsMatchIn(words) }

    /** The composer's reply line for a statement: how many, which dates, what was checked, what needs a look. */
    fun note(reading: StatementReading, candidates: List<TransactionCandidate>, locale: Locale = Locale.getDefault()): String {
        val format = DateTimeFormatter.ofPattern("MMM d", locale)
        val dates = reading.lines.mapNotNull { it.date }
        val span = when {
            dates.isEmpty() -> ""
            dates.min() == dates.max() -> ", ${dates.min().format(format)}"
            else -> ", ${dates.min().format(format)} – ${dates.max().format(format)}"
        }
        val n = candidates.size
        val parts = mutableListOf("Read as a statement: $n ${if (n == 1) "transaction" else "transactions"}$span, each its own row.")
        if (reading.balanceChecked) parts += "Money in and out matches the running balance."
        val unclear = candidates.count { CandidateFlag.DIRECTION_UNCERTAIN in it.flags }
        if (unclear > 0) parts += "Check whether money came in or went out on ${if (unclear == 1) "the marked row" else "$unclear marked rows"}."
        val payments = candidates.count { it.type.isTransfer && it.walletId == null }
        if (payments > 0) parts += "Pick the account each payment came from, or remove it if you’ve recorded it already."
        if (reading.leftOut > 0) parts += "Only the first ${StatementParser.MAX_LINES} lines were read — scan the rest separately."
        return parts.joinToString(" ")
    }
}
