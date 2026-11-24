package com.lucento.app.domain.ask

/**
 * Where a question typed or spoken in Ask Lucento goes (docs/ASK.md). AI answers are on by default for a signed-in
 * account and confirmed once (owner, 2026-11-17): until the person confirms, nothing leaves the phone.
 */
enum class AskAiRoute {
    /** Worked out on this phone, or a note on what the phone can do: off-topic, no AI in this build or session, or off. */
    PHONE,

    /** AI answers are on but not yet confirmed: say what would be sent and wait for the person. Nothing is sent. */
    CONFIRM_FIRST,

    /** Sent to Gemini through Lucento's proxy. */
    GEMINI;

    companion object {
        /**
         * [available]: this build has the proxy and the person is signed in. [on]: AI answers are on (the default).
         * [confirmed]: the person agreed to what is sent. [offTopic]: plainly not about money, answered on the phone.
         */
        fun of(available: Boolean, on: Boolean, confirmed: Boolean, offTopic: Boolean): AskAiRoute = when {
            offTopic || !available || !on -> PHONE
            !confirmed -> CONFIRM_FIRST
            else -> GEMINI
        }
    }
}
