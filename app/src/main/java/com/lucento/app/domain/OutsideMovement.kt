package com.lucento.app.domain

/**
 * Money that moves between one of the person's accounts and the world outside Lucento without being income, spending
 * or a transfer between tracked wallets (tester round 3):
 *
 * - [WITHDRAWAL]: money taken out of a cash, bank, e-wallet or savings account that isn't spending and doesn't go to
 *   another tracked wallet (handed to an account Lucento doesn't follow, moved to an untracked investment).
 * - [EXTERNAL_PAYMENT]: a card or loan paid from outside the person's wallets — a family member settling it as a gift.
 *   What is owed drops, no wallet pays, and it isn't income.
 *
 * Each is one balance adjustment row ([type]), so budgets, Insights and net flow never count it, noted
 * "<label> · <what for>". The reason is required: a statement lists every one with what it was for.
 */
enum class OutsideMovement(val label: String, val type: TransactionType) {
    WITHDRAWAL("Withdrawal", TransactionType.ADJUSTMENT_OUT),
    EXTERNAL_PAYMENT("External payment", TransactionType.ADJUSTMENT_IN),
    ;

    /** Withdrawals: accounts that hold money. External payments: cards and loans. Never receivables or valued assets. */
    fun allowedFor(walletType: WalletType): Boolean = when (this) {
        WITHDRAWAL -> !walletType.isLiability && walletType.acceptsTransactions && walletType != WalletType.RECEIVABLE
        EXTERNAL_PAYMENT -> walletType.isLiability
    }

    /** The stored note, "Withdrawal · Moved to my investment"; [reason] must not be blank (see [reasonValid]). */
    fun note(reason: String): String = "$label · ${reason.trim().take(MAX_REASON)}"

    companion object {
        /** The longest reason kept, so the note stays well within the synced note limit (500). */
        const val MAX_REASON = 200

        fun reasonValid(reason: String): Boolean = reason.isNotBlank()

        /** Which outside movement [transaction] records, or null for anything else (a balance correction included). */
        fun of(transaction: Transaction): OutsideMovement? = entries.firstOrNull { kind ->
            transaction.type == kind.type &&
                (transaction.note == kind.label || transaction.note.startsWith("${kind.label} · "))
        }

        /** What it was for: the note without its label; empty when none was given (rows from before it was required). */
        fun reasonOf(transaction: Transaction): String {
            val kind = of(transaction) ?: return ""
            return transaction.note.removePrefix(kind.label).removePrefix(" · ").trim()
        }
    }
}
