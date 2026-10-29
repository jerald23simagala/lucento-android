package com.lucento.app.domain

/** The line under the composer chips, and the line after a message is read. */
object ComposerPrivacy {
    const val ON_DEVICE = "Your entry is read on this phone and never sent anywhere."
    const val GEMINI = "Your entry is sent to Google Gemini through Lucento's server to be read."

    fun line(geminiEnabled: Boolean): String = if (geminiEnabled) GEMINI else ON_DEVICE
}

/** Real entries: income and expenses. Transfers, adjustments and opening balances are not entries. */
fun realEntries(transactions: List<Transaction>): List<Transaction> =
    transactions.filter { it.type.isCashFlow }
