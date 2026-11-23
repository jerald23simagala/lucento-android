package com.lucento.app.domain.capture

import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import com.lucento.app.domain.capture.merchant.CategoryIntents
import com.lucento.app.domain.capture.merchant.EntityKind
import com.lucento.app.domain.capture.merchant.MerchantMemory
import com.lucento.app.domain.capture.merchant.MerchantRecognizer
import com.lucento.app.domain.capture.merchant.WalletMatcher
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A statement read by [StatementParser] → one candidate per line, all in the account the statement belongs to (the
 * person's wallet at the bank or app it comes from, else the composer's active wallet, else the account in the
 * statement's currency). Money out is an expense and money in is income, except:
 * - money into a credit card or loan is a payment from another account: a transfer, whose source the user picks — but a
 *   refund or credit on the card is income (a refund), never a payment (scan audit 2026-11-21);
 * - a line naming another of the user's accounts ("Transfer to BDO Savings", "Cash in via BPI") is a transfer between
 *   the two; money added to an e-wallet is a transfer in, whose source the user picks when the line doesn't say;
 * - an ATM or cash withdrawal (and a card's cash advance) moves money into the user's cash wallet (when there is exactly
 *   one in the currency), as a typed "withdrew 2000" does.
 * The description is the merchant when one is recognised ("Starbucks" for "PURCHASE AUTHORIZED ON 10/05 STARBUCKS STORE
 * 12345 SEATTLE WA"); the bank's words stay in the row's source text. The category is the person's own habit for that
 * merchant, else the dictionary's, else the words'; fees, interest, payroll and refunds take theirs. Every row is marked
 * as read from a statement, so the review's "already saved?" check is looser (posting dates lag and banks' descriptions
 * differ from the user's), and a direction the statement didn't settle is marked to check.
 */
object StatementCandidates {

    /** "Account ending 1234", "Card ending in 1004", "Account No. ****1234", "XXXX-XXXX-XXXX-1234". */
    private val LAST4 = Regex(
        "(?:ending(?:\\s+in)?|account\\s*(?:no\\.?|number|#)?|card\\s*(?:no\\.?|number|#)?|acct\\.?\\s*(?:no\\.?)?)\\s*[:#]?\\s*(?:[x*•#\\d]{2,}[\\s-]?){0,4}?[x*•#]{2,}[\\s-]?(\\d{4})\\b|" +
            "ending(?:\\s+in)?\\s*[:#]?\\s*(\\d{4})\\b|(?:\\*{2,}|x{4}|•{2,})[\\s-]?(\\d{4})\\b",
        RegexOption.IGNORE_CASE,
    )

    /**
     * The account a statement belongs to, and whether the document named it: the person's wallet at the bank, card
     * issuer or app named in the statement's heading (its last four digits pick between several cards), else the account
     * in the statement's currency when that differs from the active wallet's, else the active wallet.
     */
    fun account(reading: StatementReading, lines: List<String>, context: InterpretationContext): Pair<Wallet?, Boolean> {
        val active = context.wallets.firstOrNull { it.id == context.defaultWalletId } ?: context.wallets.firstOrNull()
        // The heading: the lines above the first amount. A bank named in a transaction ("Cash In via BPI") is a
        // counterpart, not the statement's own bank.
        val header = lines.take(12).takeWhile { line -> ReceiptParser.amountsIn(line, 2).isEmpty() && !SIGNED_AMOUNT.containsMatchIn(line) }
        val country = DocumentLocale.country(lines)
        val currency = reading.currencyCode ?: DocumentCurrency.read(lines, country, context.wallets.map { it.currencyCode }).code
        val last4 = header.firstNotNullOfOrNull { line -> LAST4.find(line)?.let { m -> m.groupValues.drop(1).firstOrNull { it.isNotEmpty() } } }
        val type = if (reading.looksLikeCard) WalletType.CREDIT_CARD else null
        val issuers = header.flatMap { line ->
            context.directory.mentions(line).filter { m ->
                (m.entity.kind == EntityKind.BANK || m.entity.kind == EntityKind.EWALLET || (m.entity.kind == EntityKind.CARD_NETWORK && m.entity.issuesCards)) &&
                    (!m.weak || m.start == 0)
            }
        }.map { it.entity }.distinct()
        val named = issuers.firstNotNullOfOrNull { entity ->
            WalletMatcher.forEntity(entity, context.wallets, context.walletLabels, currency, last4, type)
                ?.takeIf { w -> type == null || w.type == type }
        }
        if (named != null) return named to (named.id != active?.id)
        val byCurrency = currency?.takeIf { it != active?.currencyCode }?.let { code -> context.wallets.firstOrNull { it.currencyCode == code } }
        return (byCurrency ?: active) to false
    }

    /** "+₱2,000", "-500": an app history's amounts, which a plain amount check (decimals only) may miss. */
    private val SIGNED_AMOUNT = Regex("(?<![\\p{L}\\p{N}])[-+−]\\s*\\p{Sc}?\\s*\\d")

    /** The active wallet, or the account in the statement's currency when that differs (used when nothing else is known). */
    private fun defaultAccount(reading: StatementReading, context: InterpretationContext): Wallet? {
        val active = context.wallets.firstOrNull { it.id == context.defaultWalletId } ?: context.wallets.firstOrNull()
        return reading.currencyCode
            ?.takeIf { it != active?.currencyCode }
            ?.let { code -> context.wallets.firstOrNull { it.currencyCode == code } }
            ?: active
    }

    fun from(
        reading: StatementReading,
        context: InterpretationContext,
        memory: MerchantMemory = MerchantMemory.EMPTY,
        account: Wallet? = defaultAccount(reading, context),
        accountFromDocument: Boolean = false,
        /** The statement's currency when it prints only a shared symbol or none ([DocumentCurrency]); never a guess. */
        documentCurrency: CurrencyReading? = null,
    ): List<TransactionCandidate> {
        val liability = account?.type?.isLiability == true
        val currency = reading.currencyCode ?: documentCurrency?.takeIf { it.certainty == CurrencyCertainty.COUNTRY }?.code
        val assumed = reading.currencyCode == null && documentCurrency?.certainty == CurrencyCertainty.ACCOUNT && account?.currencyCode == documentCurrency.code
        return reading.lines.map { line ->
            val words = " " + line.description.lowercase(Locale.ROOT) + " "
            val flags = linkedSetOf(CandidateFlag.FROM_STATEMENT)
            if (line.date == null || line.dateAmbiguous) flags += CandidateFlag.DATE_UNCERTAIN
            if (line.flow == StatementFlow.UNKNOWN) flags += CandidateFlag.DIRECTION_UNCERTAIN
            if (line.status == LineStatus.PENDING) flags += CandidateFlag.PENDING
            if (assumed) flags += CandidateFlag.CURRENCY_ASSUMED
            val parties = MerchantRecognizer.descriptor(line.description, context.directory)
            val other = WalletMatcher.named(line.description, context.wallets, context.walletLabels, context.directory, account?.id, account?.currencyCode)
                ?: legacyNamed(words, context.wallets, account)
            val cashOut = line.flow == StatementFlow.OUT &&
                (line.kind == LineKind.ATM || line.kind == LineKind.CASH_ADVANCE || CASH_WITHDRAWAL.containsMatchIn(words))
            val cash = if (cashOut) onlyCashWallet(context.wallets, account) else null
            val payee = parties.name?.takeIf {
                line.kind == LineKind.PURCHASE || line.kind == LineKind.OTHER || line.kind == LineKind.BILL_PAYMENT || line.kind == LineKind.REFUND
            }
            val base = TransactionCandidate(
                description = (payee ?: line.description).ifBlank { if (line.flow == StatementFlow.IN) "Money in" else "Statement line" },
                type = TransactionType.EXPENSE,
                amount = line.amount,
                walletId = account?.id,
                categoryId = null,
                date = line.date ?: context.today,
                mentionedCurrency = currency,
                sourceText = line.source,
                flags = flags,
                walletFromText = accountFromDocument,
            )

            fun transferIn(from: Wallet?) = base.copy(type = TransactionType.TRANSFER_OUT, walletId = from?.id, toWalletId = account?.id, walletFromText = from != null)
            fun transferOut(to: Wallet) = base.copy(type = TransactionType.TRANSFER_OUT, toWalletId = to.id)
            fun categorized(type: TransactionType, intent: String?): TransactionCandidate {
                val kind = if (type == TransactionType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
                val habit = memory.habit(payee ?: line.description, type, context.directory)
                val category = habit?.usualCategory?.takeIf { id -> context.categories.any { it.id == id && it.kind == kind && !it.archived } }
                    ?: CategoryIntents.resolve(intent ?: parties.category?.takeIf { type == TransactionType.EXPENSE }, kind, context.categories)
                    ?: QuickEntryParser.detectCategory(words, type, context.categories)
                return base.copy(
                    type = type,
                    categoryId = category,
                    flags = if (category == null) flags + CandidateFlag.CATEGORY_UNMATCHED else flags,
                )
            }

            when {
                // A refund, credit or cash-back reward is income, on a card as anywhere (never a payment to the card).
                line.flow == StatementFlow.IN && line.kind == LineKind.REFUND -> categorized(TransactionType.INCOME, "refund")
                // Money into a card or loan is a payment from one of the user's accounts.
                line.flow == StatementFlow.IN && liability && line.kind != LineKind.INTEREST_EARNED -> transferIn(other)
                cash != null -> transferOut(cash)
                // Money added to an e-wallet comes from one of the user's accounts; they pick it when the line doesn't say.
                line.flow == StatementFlow.IN && line.kind == LineKind.CASH_IN -> transferIn(other)
                other != null && line.flow == StatementFlow.IN -> transferIn(other)
                other != null && line.flow == StatementFlow.OUT -> transferOut(other)
                line.flow == StatementFlow.IN -> categorized(
                    TransactionType.INCOME,
                    when (line.kind) {
                        LineKind.PAYROLL -> "work"
                        LineKind.INTEREST_EARNED -> "investments"
                        else -> null
                    },
                )
                else -> categorized(
                    TransactionType.EXPENSE,
                    when (line.kind) {
                        LineKind.FEE -> "bank_fees"
                        LineKind.INTEREST_CHARGE -> "interest"
                        else -> null
                    },
                )
            }
        }
    }

    private val CASH_WITHDRAWAL = Regex("\\batm\\b|cash\\s+withdrawal|over[- ]the[- ]counter\\s+withdrawal")

    /** The user's one cash wallet in [account]'s currency (not [account] itself); null when there are none or several. */
    private fun onlyCashWallet(wallets: List<Wallet>, account: Wallet?): Wallet? =
        account?.takeIf { it.type != WalletType.CASH }?.let { a ->
            wallets.filter { it.type == WalletType.CASH && it.currencyCode == a.currencyCode && it.id != a.id }.singleOrNull()
        }

    /**
     * Another of the user's accounts, in the statement's currency, whose name the line's words contain — the rule from
     * before the dictionaries, kept for names [WalletMatcher] leaves alone (a wallet named only "Savings"). A wallet named
     * "Cash" is never matched by "cash in", "cash back" or "cash advance".
     */
    private fun legacyNamed(words: String, wallets: List<Wallet>, account: Wallet?): Wallet? {
        val text = words.replace(Regex("\\bcash[- ]?(in|out|back|advance|withdrawal)\\b"), " ")
        return wallets.filter { it.id != account?.id && (account == null || it.currencyCode == account.currencyCode) }
            .filter { it.name.trim().length >= 3 }
            .sortedByDescending { it.name.length }
            .firstOrNull { w -> Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(w.name.trim().lowercase(Locale.ROOT)) + "(?![\\p{L}\\p{N}])").containsMatchIn(text) }
    }

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
        if (reading.declined > 0) parts += "${reading.declined} declined ${if (reading.declined == 1) "line wasn’t" else "lines weren’t"} read: no money moved."
        if (reading.leftOut > 0) parts += "Only the first ${StatementParser.MAX_LINES} lines were read — scan the rest separately."
        return parts.joinToString(" ")
    }
}
