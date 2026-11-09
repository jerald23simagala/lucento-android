package com.lucento.app.feature.capture

/**
 * Smarter reading's consent (docs/CAPTURE.md), in one place: Settings › Privacy and the composer's switch ask the same
 * question with the same words, so turning it on from either says exactly what is sent and where.
 */
object SmartReadingCopy {
    const val CONSENT_TITLE = "Send messages to Gemini?"

    const val CONSENT_TEXT =
        "When you type or speak in the composer, Lucento sends that message, today’s date, your main currency " +
            "and the names of your categories and accounts to Lucento’s server, which asks Google’s Gemini to read it. " +
            "Balances and your history are not sent. Google processes the text under the Gemini API terms. Each " +
            "account has a daily allowance; after it, reading happens on this phone. You review every row before " +
            "anything is saved, receipts are still read on this phone, and you can turn this off at any time."

    /** The composer strip's status line, beside the switch. */
    fun status(on: Boolean): String = if (on) "Messages are read by Gemini" else "Messages are read on this phone"
}
