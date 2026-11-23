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
    /**
     * The document shows only a symbol several currencies share ("$", "¥", "kr") and nothing on it says which: the
     * person's only account in such a currency was taken. Shown as "check the currency"; never converted.
     */
    CURRENCY_ASSUMED,
    /** Pending at the bank: it may still change or drop off. */
    PENDING,
    /**
     * Checked against the saved ledger line by line ([StatementReconciler]); the row's [TransactionCandidate.hint] says
     * what was found, so the simpler "already saved?" check is not repeated.
     */
    RECONCILED,
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
    /**
     * Where income and transfers go when the default is a credit card the person didn't pick for this entry (owner,
     * 2026-11-18): the first cash, bank or e-wallet. Null otherwise: [defaultWalletId] then serves every type.
     */
    val moneyWalletId: Long? = null,
    /**
     * True when the person picked the account in [defaultWalletId] (a wallet pill, or the wallet the composer was opened
     * from); false when it is only the default. The person's habits never override a picked account.
     */
    val defaultWalletChosen: Boolean = false,
    /**
     * Scans only, read on this phone and never sent anywhere (Smarter reading builds its request from the fields above
     * by name): the bundled merchant dictionaries, the saved ledger (for the person's own habits and for checking a
     * statement against what is saved), and each wallet's names — its own, and its bank's from the bundled registry.
     */
    val directory: com.lucento.app.domain.capture.merchant.MerchantDirectory = com.lucento.app.domain.capture.merchant.MerchantDirectory.EMPTY,
    val history: List<com.lucento.app.domain.Transaction> = emptyList(),
    val walletLabels: Map<Long, List<String>> = emptyMap(),
) {
    /** The account a [type] goes to when the words don't name one: the default, but income and transfers never a card's. */
    fun walletFor(type: TransactionType?): Long? {
        val money = moneyWalletId?.takeIf { type != TransactionType.EXPENSE && wallets.any { it.id == moneyWalletId } }
        return money ?: wallets.firstOrNull { it.id == defaultWalletId }?.id ?: wallets.firstOrNull()?.id
    }

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
    /** Something the reader found that the person should know, shown on the row ("Possibly saved already on 3 Nov"). */
    val hint: String? = null,
)

/** Who did the interpreting, so the UI never presents local rules as something else. */
data class InterpreterEngine(val label: String, val onDevice: Boolean)

sealed interface Interpretation {
    /**
     * [note]: a line for the reply about what was read ("Read as a statement: 7 transactions…", or a message that held
     * more than is read at once, [CaptureLimits]); null when there is nothing to add.
     */
    data class Candidates(
        val items: List<TransactionCandidate>,
        val engine: InterpreterEngine,
        val note: String? = null,
        /** Statement lines already saved, so not added as rows; the person can still add any of them. */
        val held: List<HeldLine> = emptyList(),
        /** What each statement in the scan said and how it compares with the ledger ([StatementReconciler]). */
        val statements: List<StatementReport> = emptyList(),
    ) : Interpretation
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

    /**
     * A document of one or more pages — Document Scanner pages, photos, or a PDF ([ScanDocument]) — read on this phone:
     * pages joined into the documents they make up, each receipt one transaction, each statement one per line, checked
     * against the ledger. The default reads the pages' text as one scan.
     */
    suspend fun interpretDocument(document: ScanDocument, context: InterpretationContext): Interpretation =
        interpretScan(document.pages.flatMap { it.lines }, context)
}

/**
 * On-device interpretation with [BatchEntryParser] (typed and spoken messages) and, for documents, [DocumentAssembler],
 * [ReceiptCandidates], [StatementParser], [StatementCandidates] and [StatementReconciler]. Nothing leaves the phone.
 */
class LocalTransactionInterpreter : TransactionInterpreter {
    override val engine = InterpreterEngine(label = "On this phone", onDevice = true)

    override suspend fun interpretText(text: String, context: InterpretationContext): Interpretation {
        val message = BatchEntryParser.parseMessage(text, context)
        return if (message.items.isEmpty()) {
            Interpretation.NothingFound("Nothing to record was found. Include an amount, like “coffee 180”.")
        } else {
            Interpretation.Candidates(message.items, engine, CaptureLimits.note(message.leftOut, message.inputCut))
        }
    }

    override suspend fun interpretScan(lines: List<String>, context: InterpretationContext): Interpretation =
        interpretDocument(ScanDocument.ofLines(lines), context)

    override suspend fun interpretDocument(document: ScanDocument, context: InterpretationContext): Interpretation {
        val parts = DocumentAssembler.assemble(document, context.today)
        if (parts.isEmpty()) return Interpretation.NothingFound(NO_TEXT)
        val memory = com.lucento.app.domain.capture.merchant.MerchantMemory.build(context.history, context.directory, context.today)
        val items = ArrayList<TransactionCandidate>()
        val held = ArrayList<HeldLine>()
        val reports = ArrayList<StatementReport>()
        val notes = ArrayList<String>()
        var receipts = 0
        var overlap = 0
        parts.forEach { part ->
            overlap += part.overlapRemoved
            val statement = if (part.kind == ScanKind.STATEMENT) readStatement(part, context, memory) else null
            if (statement != null) {
                items += statement.rows
                held += statement.held
                reports += statement.report
                notes += statement.note
                return@forEach
            }
            val (candidate, suggestion) = ReceiptCandidates.read(part.lines, context, memory) ?: return@forEach
            items += candidate
            receipts++
            notes += when {
                suggestion.refund && suggestion.totalLabel != null -> "Read as a refund: money back for the “${suggestion.totalLabel}” line."
                suggestion.refund -> "Read as a refund: money back, one row."
                suggestion.totalLabel != null -> "Read as one receipt: one payment for the “${suggestion.totalLabel}” line, not each item."
                else -> "Read as one receipt: one payment, not each item."
            }
        }
        if (items.isEmpty() && held.isEmpty()) return Interpretation.NothingFound(NO_TEXT)
        val note = buildList {
            if (parts.size == 1) {
                addAll(notes)
            } else {
                val pieces = buildList {
                    if (receipts > 0) add(if (receipts == 1) "1 receipt" else "$receipts receipts")
                    reports.forEach { r -> add("a statement (${r.lines} ${if (r.lines == 1) "transaction" else "transactions"})") }
                }
                add("Read ${pieces.joinToString(" and ")}: each payment is its own row.")
            }
            if (overlap > 0) add("${if (overlap == 1) "1 line that appeared" else "$overlap lines that appeared"} on two photos ${if (overlap == 1) "was" else "were"} read once.")
        }.joinToString(" ").ifBlank { null }
        return Interpretation.Candidates(items, engine, note, held, reports)
    }

    private class StatementResult(val rows: List<TransactionCandidate>, val held: List<HeldLine>, val report: StatementReport, val note: String)

    /**
     * A statement part → rows checked against the ledger. It is read once for the active wallet, then again if its
     * heading names another of the person's accounts of a different kind or currency (a card's lines read differently).
     */
    private fun readStatement(
        part: DocumentAssembler.Part,
        context: InterpretationContext,
        memory: com.lucento.app.domain.capture.merchant.MerchantMemory,
    ): StatementResult? {
        val lines = part.lines
        val order = DocumentLocale.dateOrder(DocumentLocale.country(lines).code)
        fun parse(account: com.lucento.app.domain.Wallet?) = StatementParser.parseRows(
            part.rows, context.today,
            expectedCurrency = account?.currencyCode,
            accountIsCard = account?.type == com.lucento.app.domain.WalletType.CREDIT_CARD,
            dayFirst = context.dayFirst,
            dateOrder = order,
        )
        val active = context.wallets.firstOrNull { it.id == context.defaultWalletId } ?: context.wallets.firstOrNull()
        var reading = parse(active)
        val (account, named) = StatementCandidates.account(reading, lines, context)
        if (account != null && account.id != active?.id &&
            (account.type.isLiability != active?.type?.isLiability || account.currencyCode != active?.currencyCode)
        ) {
            reading = parse(account)
        }
        val documentCurrency = DocumentCurrency.read(lines, DocumentLocale.country(lines), context.wallets.map { it.currencyCode })
        val candidates = StatementCandidates.from(reading, context, memory, account, named, documentCurrency)
        if (candidates.isEmpty()) return null
        val result = StatementReconciler.reconcile(candidates, reading, account, context, part.pages, part.overlapRemoved)
        return StatementResult(result.rows, result.held, result.report, StatementCandidates.note(reading, candidates))
    }

    private companion object {
        const val NO_TEXT = "No text was found. Try again with the whole page in view, flat and well lit."
    }
}
