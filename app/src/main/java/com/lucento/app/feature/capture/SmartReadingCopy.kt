package com.lucento.app.feature.capture

import com.lucento.app.domain.entitlement.PlanCatalog

/**
 * Smarter reading's words (docs/CAPTURE.md), in one place. It is turned on only in Settings › Privacy & security, after
 * [CONSENT_TEXT]. Owner, 2026-11-15: the composer's own switch is gone, because Gemini makes every message wait and
 * new users expect rows at once; whoever turns it on is told first that adding transactions gets slower.
 * Owner, 2026-11-23: Gemini is for Ask Lucento, not for adding transactions. The composer no longer mentions Smarter
 * reading, and Settings says first that each message uses the AI allowance Ask Lucento uses ([ALLOWANCE]).
 */
object SmartReadingCopy {
    const val CONSENT_TITLE = "Send messages to Gemini?"

    /** The caveat (owner, 2026-11-23): Smarter reading spends the same monthly AI questions as Ask Lucento. */
    const val ALLOWANCE =
        "Each message uses one of your ${PlanCatalog.PRO_AI_QUESTIONS_PER_MONTH} AI questions a month, the same allowance as Ask Lucento."

    /** The other cost, said with [ALLOWANCE] under the Settings switch and first in the consent. */
    const val SLOWER = "Adding transactions gets slower: each message waits a few seconds for Gemini’s answer."

    /** Under the Settings switch: both costs, on or off. */
    const val NOTE = "$ALLOWANCE $SLOWER"

    const val CONSENT_TEXT =
        "$ALLOWANCE $SLOWER With this off, Lucento reads your messages on this phone and their rows appear straight away.\n\n" +
            "When you type or speak in Add transactions, Lucento sends that message, today’s date, your main currency " +
            "and the names of your categories and accounts to Lucento’s server, which asks Google’s Gemini to read it. " +
            // What Google does with it, which every Gemini consent carries (security audit F-05).
            "Balances and your history are not sent. " + com.lucento.app.feature.legal.LegalDocuments.GEMINI_DATA_USE + " When " +
            "this month’s AI questions are used up, reading happens on this phone. You review every row before " +
            "anything is saved, receipts are still read on this phone, and you can turn this off at any time."

    /** Lucento's reply while Gemini reads a typed or spoken message. */
    const val READING = "Gemini is reading…"

    /** Settings, when it was turned on but the plan no longer includes it (a Lifetime's year ended, a subscription lapsed). */
    const val PAUSED =
        "Smarter reading is paused: it’s part of Lucento Pro. Your messages are read on this phone until your plan includes it."

    /** The same for Pro Lifetime without Lucento Cloud, whose way back is Lucento Cloud. */
    const val PAUSED_CLOUD =
        "Smarter reading is paused: it comes with Lucento Cloud. Your messages are read on this phone until Lucento Cloud is on."
}
