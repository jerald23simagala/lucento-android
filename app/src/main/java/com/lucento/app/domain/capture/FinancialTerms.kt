package com.lucento.app.domain.capture

/** What a statement line is, beyond money in or out. */
enum class LineKind {
    PURCHASE,
    /** The cardholder paying the card: money into a card account, out of a bank account. */
    PAYMENT_TO_CARD,
    BILL_PAYMENT,
    TRANSFER,
    DEPOSIT,
    PAYROLL,
    ATM,
    CHECK,
    FEE,
    /** Interest a card or loan charged. */
    INTEREST_CHARGE,
    /** Interest a savings or bank account earned. */
    INTEREST_EARNED,
    /** A refund, return, statement credit or cash-back reward. */
    REFUND,
    /** A charge reversed, a returned payment or a chargeback. */
    REVERSAL,
    CASH_ADVANCE,
    /** Money added to an e-wallet ("Cash In via BPI", "Top up"). */
    CASH_IN,
    /** Money taken out of an e-wallet to cash or a bank. */
    CASH_OUT,
    OTHER,
}

/** Where a line stands at the bank. */
enum class LineStatus {
    POSTED,
    /** Shown but not settled; it may still change or drop off. */
    PENDING,
    /** Failed, declined or cancelled: not a movement of money. */
    DECLINED,
}

/**
 * The financial meaning of a statement line's words, in the context of the account (docs/SCAN.md "Statements"): a
 * "payment" into a card is the cardholder paying it, but a payment on a bank statement is money out; "credit" is money
 * in on a bank statement and a refund or payment on a card's; "interest" is a charge on a card and income on savings.
 * English, Filipino usage, Spanish, Portuguese, French, German, Italian, Dutch and Indonesian terms are known.
 */
object FinancialTerms {

    data class Meaning(
        val kind: LineKind,
        /** The direction the words alone imply for this account, or null when they don't say. */
        val flow: StatementFlow?,
        val status: LineStatus,
    )

    private fun re(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

    private val DECLINED = re("\\b(declined|failed|unsuccessful|rejected|cancell?ed|voided|denied|abgelehnt|rechazad[ao]|refus[ée]e?|gagal|ditolak)\\b")
    private val PENDING = re("\\b(pending|processing|on\\s+hold|authori[sz]ation\\s+hold|memo\\s+post(ed)?|vorgemerkt|pendiente|en\\s+attente|in\\s+sospeso|pendente|tertunda)\\b")

    private val REVERSAL = re("\\b(revers(al|ed)|returned\\s+(item|payment|pymt|check|cheque|ach|deposit)|chargeback|charge[- ]back|storno|estorno|contrapartida|r[üu]cklastschrift|annulation|annullamento)\\b")
    private val RETURNED_PAYMENT = re("\\breturned\\s+(payment|pymt|check|cheque)\\b|\\bpayment\\s+returned\\b|\\br(ü|ue)ckbuchung\\b|\\br(ü|ue)cklastschrift\\b")
    /** A direct debit collected by a biller: "Lastschrift", "PRLV SEPA", "Adeudo", "Recibo", "Addebito", "Incasso". */
    private val DIRECT_DEBIT = re("\\b(lastschrift|prlv|pr[ée]l[èe]vement|adeudo|recibo|addebito|incasso|domiciliaci[oó]n|direct\\s+debit)\\b")
    /** An entry the bank marks as a credit to the account, in words. */
    private val CREDIT_ENTRY = re("\\b(gutschrift|abono|ingreso|accredito|bijschrijving|remise\\s+ch[eè]que|transfer\\s+masuk|vir(ement)?\\s+(inst(antan[ée])?\\s+)?(sepa\\s+)?re[çc]u)\\b")
    /** An entry the bank marks as a debit, in words. */
    private val DEBIT_ENTRY = re("\\b(cargo|afschrijving|transfer\\s+keluar|belastung|vir(ement)?\\s+(emis|sepa\\s+emis|permanent))\\b")
    private val STANDING_ORDER = re("\\b(dauerauftrag|standing\\s+order|ordre\\s+permanent|orden\\s+permanente)\\b")
    private val REFUND = re(
        "\\b(refund(ed)?|merchandise\\s+credit|purchase\\s+return|return(ed)?\\s+(item|purchase|merchandise)|credit\\s+voucher|reembolso|devoluci[oó]n|" +
            "rembours\\w*|r[üu]ckerstattung|erstattung|rimborso|restitui[cç][aã]o|terugbetaling|pengembalian(\\s+dana)?)\\b|^\\s*return\\b",
    )
    private val REWARD = re("\\b(statement\\s+credit|cash\\s*-?\\s*back\\s+(reward|bonus|credit|redemption)|rewards?\\s+(credit|redemption)|rebate|cashback\\s+credit|bonus\\s+credit)\\b")
    private val INTEREST_CHARGE = re("\\b(interest\\s+charged?|finance\\s+charge|purchase\\s+interest|cash\\s+advance\\s+interest|interest\\s+on\\s+(purchases|cash|balance)|periodic\\s+interest|sollzinsen|intereses\\s+deudores)\\b")
    private val INTEREST_EARNED = re("\\b(interest\\s+(earned|paid|credit(ed)?|income|payment)|int(erest)?\\s+pd|credit\\s+interest|habenzinsen|intereses\\s+(acreedores|ganados|a\\s+favor)|int[ée]r[êe]ts\\s+cr[ée]diteurs|bunga|jasa\\s+giro)\\b")
    private val INTEREST = re("\\b(interest|zinsen|intereses|int[ée]r[êe]ts|interessi|rente)\\b")
    private val FEE = re(
        "\\b(fees?|service\\s+charge|maintenance\\s+(fee|charge)|monthly\\s+service|overdraft|od\\s+fee|nsf|insufficient\\s+funds|late\\s+(payment\\s+)?(fee|charge)|annual\\s+(fee|membership)|" +
            "membership\\s+fee|foreign\\s+(transaction|exchange|currency)\\s+(fee|charge)|fx\\s+fee|atm\\s+(fee|surcharge)|wire\\s+fee|penalty|commission|" +
            "comisi[oó]n|cuota|frais|cotisation|geb[üu]hr(en)?|entgelt|kontof[üu]hrung|commissione|canone|spese|kosten|biaya(\\s+admin)?|tarifa|taxa\\s+de)\\b",
    )
    private val FEE_WORD = re("\\b(fees?|service\\s+charge|geb[üu]hr(en)?|entgelt|frais|comisi[oó]n|commission|commissione|biaya\\s+admin|cuota\\s+de\\s+manejo)\\b")
    private val FEE_UNDONE = re("\\b(revers\\w*|refund\\w*|waiv\\w*|rebate|r[üu]ckerstattung|reembolso|rembours\\w*|storno)\\b")
    private val CASH_ADVANCE = re("\\b(cash\\s+advance|cash\\s+adv|bargeldbezug\\s+kreditkarte)\\b")
    private val ATM = re(
        "\\b(atm|cash\\s+withdrawal|withdrawal\\s+atm|over[- ]the[- ]counter\\s+withdrawal|otc\\s+withdrawal|geldautomat|bargeldauszahlung|bargeld|" +
            "retiro\\s+(en\\s+)?(cajero|efectivo)|retrait\\s+(dab|gab|esp[eè]ces)|prelievo|geldopname|tarik\\s+tunai|penarikan\\s+tunai)\\b",
    )
    private val CHECK_DEPOSIT = re("\\b(mobile|remote|check|cheque)\\s+deposit\\b|\\bdeposited\\s+(check|cheque)\\b")
    private val CHECK = re("\\b(check|cheque|chk|chq)\\s*(#|no\\.?|number|num)?\\s*\\d{2,}|\\bchecks?\\s+paid\\b")
    private val PAYROLL = re("\\b(payroll|salary|direct\\s+dep(osit)?|dir\\s+dep|paycheck|wages|sweldo|gehalt|lohn|salaire|n[oó]mina|stipendio|sal[aá]rio|gaji|loon)\\b")
    private val DEPOSIT = re("\\b(deposit(ed)?|cash\\s+deposit|einzahlung|bareinzahlung|dep[oó]sito|versement|versamento|storting|setoran)\\b")
    private val CARD_PAYMENT_IN = re(
        "\\b(payment|pymt|pmt|autopay|auto[- ]?pay|auto\\s+payment|thank\\s+you|online\\s+payment|mobile\\s+payment|e-?payment|payment\\s+received|" +
            "pago|abono\\s+tarjeta|paiement|zahlung(seingang)?|pagamento|betaling|pembayaran)\\b",
    )
    private val CARD_PAYMENT_OUT = re(
        "\\b(credit\\s+card|card)\\s+(payment|pymt|pmt|autopay)|\\b(cc|crd|card)\\s+(pmt|pymt|autopay|epay)\\b|\\bpayment\\s+to\\s+(\\w+\\s+){0,3}(credit\\s+)?card\\b|" +
            "\\b(amex|american\\s+express|discover|capital\\s+one|citi\\s*card|chase\\s+(credit\\s+)?(card|crd)|barclaycard|synchrony|applecard|apple\\s+card)\\b.*\\b(payment|pymt|pmt|autopay|epay|e-?payment)\\b",
    )
    private val BILL_PAYMENT = re("\\b(bill\\s*pay(ment)?|bills\\s+payment|billpay|utility\\s+payment|pay\\s+bills|pago\\s+de\\s+servicios|bayar\\s+tagihan)\\b")
    private val CASH_IN = re("\\b(cash[- ]?in|top[- ]?up|add\\s+money|load\\s+wallet|reload|add\\s+funds|recarga)\\b")
    private val CASH_OUT = re("\\bcash[- ]?out\\b")
    private val TRANSFER = re(
        "\\b(transfer|xfer|trf|tfr|instapay|pesonet|zelle|venmo|wire|ach|online\\s+transfer|e-?transfer|interac|send\\s+money|sent\\s+money|express\\s+send|" +
            "fund\\s+transfer|internal\\s+transfer|to\\s+savings|from\\s+savings|to\\s+checking|from\\s+checking|[üu]berweisung|dauerauftrag|transferencia|" +
            "virement|bonifico|giroconto|overboeking|overschrijving|upi|imps|neft|rtgs|sepa|paynow|promptpay|fast\\s+transfer|duitnow|bi-?fast)\\b",
    )
    private val TRANSFER_IN = re("\\b(from|received|receive|incoming|credit|cr|inward|in\\s+from|eingang|recibida|re[çc]u|ricevuto|ontvangen|masuk|dari)\\b")
    private val TRANSFER_OUT = re("\\b(to|sent|send|outgoing|debit|dr|outward|ausgang|enviada|[ée]mis|inviato|verzonden|keluar|ke)\\b")
    private val PURCHASE = re(
        "\\b(purchase[sd]?|pos|debit\\s+card|card\\s+purchase|point\\s+of\\s+sale|buy|bought|pay\\s+qr|qr\\s+(payment|pay)|online\\s+shopping|compra|achat|" +
            "paiement\\s+cb|carte|kauf|kartenzahlung|acquisto|aankoop|pembelian|belanja)\\b",
    )

    fun meaning(text: String, card: Boolean): Meaning {
        val status = when {
            DECLINED.containsMatchIn(text) -> LineStatus.DECLINED
            PENDING.containsMatchIn(text) -> LineStatus.PENDING
            else -> LineStatus.POSTED
        }
        val feeWord = FEE_WORD.containsMatchIn(text)
        val (kind, flow) = when {
            // A fee given back ("MONTHLY FEE REVERSAL", "ATM fee refund") is money in; any other line naming a fee is one.
            feeWord && FEE_UNDONE.containsMatchIn(text) -> LineKind.REFUND to StatementFlow.IN
            feeWord -> LineKind.FEE to StatementFlow.OUT
            // A card payment that bounced puts the debt back; any other reversal could go either way.
            RETURNED_PAYMENT.containsMatchIn(text) -> LineKind.REVERSAL to (if (card) StatementFlow.OUT else StatementFlow.IN)
            REVERSAL.containsMatchIn(text) -> LineKind.REVERSAL to null
            REWARD.containsMatchIn(text) || REFUND.containsMatchIn(text) -> LineKind.REFUND to StatementFlow.IN
            INTEREST_CHARGE.containsMatchIn(text) -> LineKind.INTEREST_CHARGE to StatementFlow.OUT
            INTEREST_EARNED.containsMatchIn(text) -> LineKind.INTEREST_EARNED to StatementFlow.IN
            INTEREST.containsMatchIn(text) && !FEE.containsMatchIn(text) ->
                if (card) LineKind.INTEREST_CHARGE to StatementFlow.OUT else LineKind.INTEREST_EARNED to StatementFlow.IN
            CASH_ADVANCE.containsMatchIn(text) -> LineKind.CASH_ADVANCE to StatementFlow.OUT
            FEE.containsMatchIn(text) -> LineKind.FEE to StatementFlow.OUT
            ATM.containsMatchIn(text) -> LineKind.ATM to StatementFlow.OUT
            CHECK_DEPOSIT.containsMatchIn(text) -> LineKind.DEPOSIT to StatementFlow.IN
            CHECK.containsMatchIn(text) -> LineKind.CHECK to StatementFlow.OUT
            PAYROLL.containsMatchIn(text) -> LineKind.PAYROLL to StatementFlow.IN
            card && CARD_PAYMENT_IN.containsMatchIn(text) && !Regex("\\bpayment\\s+to\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) ->
                LineKind.PAYMENT_TO_CARD to StatementFlow.IN
            !card && CARD_PAYMENT_OUT.containsMatchIn(text) -> LineKind.PAYMENT_TO_CARD to StatementFlow.OUT
            CASH_IN.containsMatchIn(text) -> LineKind.CASH_IN to StatementFlow.IN
            CASH_OUT.containsMatchIn(text) -> LineKind.CASH_OUT to StatementFlow.OUT
            STANDING_ORDER.containsMatchIn(text) -> LineKind.TRANSFER to StatementFlow.OUT
            DIRECT_DEBIT.containsMatchIn(text) -> LineKind.BILL_PAYMENT to StatementFlow.OUT
            CREDIT_ENTRY.containsMatchIn(text) -> LineKind.DEPOSIT to StatementFlow.IN
            DEBIT_ENTRY.containsMatchIn(text) -> LineKind.OTHER to StatementFlow.OUT
            BILL_PAYMENT.containsMatchIn(text) -> LineKind.BILL_PAYMENT to StatementFlow.OUT
            TRANSFER.containsMatchIn(text) -> {
                val inWords = TRANSFER_IN.containsMatchIn(text)
                val outWords = TRANSFER_OUT.containsMatchIn(text)
                LineKind.TRANSFER to when {
                    inWords && !outWords -> StatementFlow.IN
                    outWords && !inWords -> StatementFlow.OUT
                    else -> null
                }
            }
            DEPOSIT.containsMatchIn(text) -> LineKind.DEPOSIT to StatementFlow.IN
            PURCHASE.containsMatchIn(text) -> LineKind.PURCHASE to StatementFlow.OUT
            else -> LineKind.OTHER to null
        }
        return Meaning(kind, flow, status)
    }
}
