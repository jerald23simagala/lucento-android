package com.lucento.app.feature.ask

import com.lucento.app.domain.entitlement.PlanCatalog

/**
 * What Lucento says about Ask Lucento's AI answers in more than one place: Ask's first-question card and consent sheet,
 * Settings' dialog and the sign-in hint. AI answers are on by default for a signed-in account, and the person confirms
 * once before anything is sent (owner, 2026-11-17). The allowance follows the plan: per day with Lucento Pro, per month on
 * the free plan ([com.lucento.app.domain.entitlement.ProGates.aiAllowancePerDay]; the server counts).
 */
object AskAiCopy {
    /** The plan's AI allowance in one sentence. [perDay]: Pro, Lifetime's included year or the Cloud add-on. */
    fun allowance(perDay: Boolean): String =
        if (perDay) {
            "Your plan includes ${PlanCatalog.PRO_AI_QUESTIONS_PER_DAY} AI questions a day, shared with Smarter reading."
        } else {
            "The free plan includes ${PlanCatalog.FREE_AI_QUESTIONS_PER_MONTH} AI questions a month."
        }

    /** What is left of the allowance, as the server counted it. */
    fun remaining(left: Int, perDay: Boolean): String = if (perDay) "$left left today" else "$left left this month"

    const val CONFIRM_TITLE = "Your first AI question"
    const val CONFIRM_BODY =
        "To answer, Lucento sends this question with your figures and your transactions from the last 60 days to " +
            "Lucento’s server, which asks Google’s Gemini. Receipts, photos and older transactions stay on this phone."
    const val CONFIRM_SEND = "Ask Gemini"
    const val CONFIRM_KEEP = "Keep answers on this phone"
    const val WHATS_SENT = "What’s sent"

    /** After "Keep answers on this phone", when the phone can't answer the question itself. */
    const val DECLINED =
        "Nothing was sent. On this phone I can prepare your net worth, cash flow, budget, debts and top categories — " +
            "try one of those below. You can turn AI answers back on with the Gemini switch above."

    /** After "Keep answers on this phone", under an answer the phone worked out. */
    const val DECLINED_NOTE = "Nothing was sent: this was worked out on this phone."

    /** Signed out (or no account yet) in a build with AI answers: what they need, and what the free plan includes. */
    fun signInHint(perDay: Boolean): String =
        "On this phone I can prepare your net worth, cash flow, budget, debts and top categories — try one of those " +
            "below. To ask anything else with AI, sign in to your Lucento account in Settings › Account & sync" +
            if (perDay) "." else ": the free plan includes ${PlanCatalog.FREE_AI_QUESTIONS_PER_MONTH} AI questions a month."
}
