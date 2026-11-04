package com.lucento.app.domain.capture

import com.lucento.app.domain.Category
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import java.math.BigDecimal
import java.time.LocalDate

/** Where a message came from. All three go through the same interpreter and the same review. RECEIPT is any scanned image. */
enum class CaptureSource { TYPED, SPOKEN, RECEIPT }

/** Something the interpreter could not settle on its own; the review row points at the field concerned. */
enum class CandidateFlag {
    /** No amount was written. The user types it; nothing is guessed. */
    AMOUNT_MISSING,
    /**
     * Several numbers could be the amount, or one number can be read two ways ("1.000": 1.00 or 1,000); the choices
     * are offered as one-tap options, none is pre-filled.
     */
    AMOUNT_AMBIGUOUS,
    /**
     * The amount may not be what was meant: a model's amount not written that way in the message, or groups joined by
     * a typed space ("250 180"). Shown with a non-blocking "check the amount" note.
     */
    AMOUNT_UNVERIFIED,
    /** No category matched the words; the row can still be saved without one. */
    CATEGORY_UNMATCHED,
    /** The date was not found (receipt) or its day/month order is unclear. */
    DATE_UNCERTAIN,
    /** A receipt with no labelled total: the largest amounts are offered, none is pre-filled. */
    TOTAL_UNCONFIRMED,
    /** A receipt whose items don't add up to the printed total. */
    ITEMS_DONT_ADD_UP,
    /** A statement line whose direction (money in or out) the statement didn't settle; read as money out. */
    DIRECTION_UNCERTAIN,
    /**
     * Read from a statement: the "already saved?" check allows a few days' difference (posting dates lag) and a different
     * description (the bank's words, not the user's).
     */
    FROM_STATEMENT,
}

/** What the interpreter needs to know about the user's setup. Nothing here leaves the device. */
data class InterpretationContext(
    val wallets: List<Wallet>,
    val categories: List<Category>,
    val today: LocalDate,
    /** Account used when the words don't name one. */
    val defaultWalletId: Long?,
    /** Type to assume when nothing in a clause says income or expense (e.g. started from a wallet's "Income"). */
    val typeHint: TransactionType? = null,
    /** The phone's decimal mark, so written amounts are read the way [com.lucento.app.core.money.MoneyParser] reads them. */
    val decimalSeparator: Char = '.',
    /** The phone groups thousands with a space ("1 000"), so a space-grouped number is one number, not two. */
    val spaceGrouping: Boolean = false,
    /** The phone writes dates day first ("03/11"); used only when a scanned statement's own dates can't settle it. */
    val dayFirst: Boolean? = null,
) {
    /** Minor-unit digits of the account amounts go to by default (2 when there is none), for reading written numbers. */
    val fractionDigits: Int
        get() = (wallets.firstOrNull { it.id == defaultWalletId } ?: wallets.firstOrNull())
            ?.let { com.lucento.app.core.money.CurrencyRules.fractionDigits(it.currencyCode) } ?: 2
}

/**
 * One proposed transaction. Every field is a suggestion shown in an editable row; nothing is saved without review.
 * [type] is EXPENSE, INCOME or TRANSFER_OUT (a transfer between two of the user's accounts, never spending).
 */
data class TransactionCandidate(
    val description: String,
    val type: TransactionType,
    /** Major units exactly as written; null when missing or ambiguous (see [flags]). */
    val amount: BigDecimal?,
    /** One-tap choices when the amount is ambiguous or a receipt total is unconfirmed, most likely first. */
    val amountOptions: List<BigDecimal> = emptyList(),
    val walletId: Long?,
    val toWalletId: Long? = null,
    val categoryId: Long?,
    val date: LocalDate,
    /** ISO code of a currency the text or receipt named; never converted. */
    val mentionedCurrency: String? = null,
    /** The words this row was read from, shown when the row is expanded. */
    val sourceText: String,
    val flags: Set<CandidateFlag> = emptySet(),
    /**
     * True when the words named the account ([walletId], the source of a transfer); false when it is only the
     * composer's active wallet, which may then be switched for this row.
     */
    val walletFromText: Boolean = false,
)

/** Who did the interpreting, so the UI never presents local rules as something else. */
data class InterpreterEngine(val label: String, val onDevice: Boolean)

sealed interface Interpretation {
    /** [note]: a line for the reply about what was read ("Read as a statement: 7 transactions…"); null for messages. */
    data class Candidates(val items: List<TransactionCandidate>, val engine: InterpreterEngine, val note: String? = null) : Interpretation
    /** Nothing that looks like a transaction; [message] says what would help. */
    data class NothingFound(val message: String) : Interpretation
}

/**
 * The boundary between capture (text, speech, receipt text) and review. The composer depends only on this.
 *
 * Lucento ships [LocalTransactionInterpreter]: deterministic and fully offline. A remote model could implement the
 * same interface later, but only behind an explicit opt-in (see docs/CAPTURE.md): the app has no INTERNET permission,
 * holds no API keys, and financial text must never leave the device without the user's consent.
 */
interface TransactionInterpreter {
    val engine: InterpreterEngine
    suspend fun interpretText(text: String, context: InterpretationContext): Interpretation

    /** A scanned image's rows: one receipt (one transaction) or a statement (one per line); see [StatementParser]. */
    suspend fun interpretScan(lines: List<String>, context: InterpretationContext): Interpretation
}

/** On-device interpretation with [BatchEntryParser], [ReceiptCandidates] and [StatementParser]. */
class LocalTransactionInterpreter : TransactionInterpreter {
    override val engine = InterpreterEngine(label = "On this phone", onDevice = true)

    override suspend fun interpretText(text: String, context: InterpretationContext): Interpretation {
        val items = BatchEntryParser.parse(text, context)
        return if (items.isEmpty()) {
            Interpretation.NothingFound("Nothing to record was found. Include an amount, like “coffee 180”.")
        } else {
            Interpretation.Candidates(items, engine)
        }
    }

    override suspend fun interpretScan(lines: List<String>, context: InterpretationContext): Interpretation {
        val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return Interpretation.NothingFound("No text was found. Try again with the whole page in view, flat and well lit.")
        if (StatementParser.classify(clean) == ScanKind.STATEMENT) {
            val account = context.wallets.firstOrNull { it.id == context.defaultWalletId } ?: context.wallets.firstOrNull()
            val reading = StatementParser.parse(
                clean, context.today,
                expectedCurrency = account?.currencyCode,
                accountIsCard = account?.type == com.lucento.app.domain.WalletType.CREDIT_CARD,
                dayFirst = context.dayFirst,
            )
            val items = StatementCandidates.from(reading, context)
            if (items.isNotEmpty()) return Interpretation.Candidates(items, engine, StatementCandidates.note(reading, items))
        }
        val (candidate, suggestion) = ReceiptCandidates.read(clean, context)
            ?: return Interpretation.NothingFound("No text was found. Try again with the whole page in view, flat and well lit.")
        val note = when {
            suggestion.totalLabel != null -> "Read as one receipt: one payment for the “${suggestion.totalLabel}” line, not each item."
            else -> "Read as one receipt: one payment, not each item."
        }
        return Interpretation.Candidates(listOf(candidate), engine, note)
    }
}
