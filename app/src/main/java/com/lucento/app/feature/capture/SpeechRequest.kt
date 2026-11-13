package com.lucento.app.feature.capture

import java.util.Locale

/**
 * How the composer asks the phone's speech service to listen (docs/CAPTURE.md "Speech and camera").
 *
 * Lucento holds no microphone permission: the system recogniser (Google's on most phones) listens and hands back text.
 * By default Lucento asks it to stay offline. Many phones can't do that: Google's dialog then says "Voice search isn't
 * available". Online voice is the person's choice, asked after an offline attempt fails and switchable in Settings ›
 * Privacy, because the recogniser may then send the recording to Google.
 */
object SpeechRequest {

    /**
     * The language to ask for. The interpreter reads English, so: the phone's own English when it is set to English (its
     * offline pack is usually that one), otherwise US English. Always language and region, as speech services name
     * their languages, never a bare "en".
     */
    fun languageTag(locale: Locale): String =
        if (locale.language == "en" && locale.country.isNotEmpty()) "en-${locale.country}" else "en-US"

    /** Shown when an offline attempt comes back without text; its action offers online voice. */
    const val OFFLINE_FAILED =
        "Listening stopped. If your phone said voice isn’t available, it can’t listen offline. Try online voice, or type it instead."

    const val TRY_ONLINE = "Try online voice"

    const val CONSENT_TITLE = "Use online voice?"

    /** One wording for the composer's prompt and the Settings switch, matching the Privacy Policy's "Voice input". */
    const val CONSENT_TEXT =
        "Your phone’s speech service — Google’s on most phones — may then send what you say to Google to turn it into " +
            "text, under Google’s own privacy policy. Lucento only receives the text and never sends your recording " +
            "anywhere. You can turn this off in Settings › Privacy."
}
