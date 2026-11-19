package com.lucento.app.feature.capture

/**
 * Smarter reading's words (docs/CAPTURE.md), in one place. It is turned on only in Settings › Privacy & security, after
 * [CONSENT_TEXT]. Owner, 2026-11-15: the composer's own switch is gone, because Gemini makes every message wait and
 * new users expect rows at once; whoever turns it on is told first that adding transactions gets slower.
 */
object SmartReadingCopy {
    const val CONSENT_TITLE = "Send messages to Gemini?"

    /** The cost, said under the Settings switch and first in the consent. */
    const val SLOWER = "Adding transactions gets slower: each message waits a few seconds for Gemini’s answer."

    const val CONSENT_TEXT =
        "$SLOWER With this off, Lucento reads your messages on this phone and their rows appear straight away.\n\n" +
            "When you type or speak in Add transactions, Lucento sends that message, today’s date, your main currency " +
            "and the names of your categories and accounts to Lucento’s server, which asks Google’s Gemini to read it. " +
            // The free-tier disclosure every Gemini consent carries (security audit F-05), as in Settings on main.
            "Balances and your history are not sent. " + com.lucento.app.feature.legal.LegalDocuments.GEMINI_DATA_USE + " Each " +
            "account has a daily allowance; after it, reading happens on this phone. You review every row before " +
            "anything is saved, receipts are still read on this phone, and you can turn this off at any time."

    /** Lucento's reply while Gemini reads a typed or spoken message. */
    const val READING = "Gemini is reading…"

    /** Settings, when it was turned on but the plan no longer includes it (a Lifetime's year ended, a subscription lapsed). */
    const val PAUSED =
        "Smarter reading is paused: it’s part of Lucento Pro. Your messages are read on this phone until your plan includes it."

    /** The same for Pro Lifetime without Lucento Cloud, whose way back is Lucento Cloud. */
    const val PAUSED_CLOUD =
        "Smarter reading is paused: it comes with Lucento Cloud. Your messages are read on this phone until Lucento Cloud is on."

    /** Under the composer's examples: who reads the messages, so how fast rows appear, and where that is changed. */
    fun composerNote(geminiReads: Boolean): String =
        if (geminiReads) {
            "Smarter reading is on: Gemini reads each message, which takes a few seconds. You can turn it off in " +
                "Settings › Privacy & security."
        } else {
            "Your messages are read on this phone, so their rows appear straight away."
        }
}
