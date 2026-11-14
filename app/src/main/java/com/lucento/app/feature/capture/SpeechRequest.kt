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

    /** The speech service's own window came back without text while offline; its action offers online voice. */
    const val OFFLINE_FAILED =
        "Listening stopped. If your phone said voice isn’t available, it can’t listen offline. Try online voice, or type it instead."

    /** Lucento's own listening ([VoiceSession]) found that the phone can't listen offline. */
    const val OFFLINE_UNAVAILABLE = "Your phone can’t turn speech into text offline. Try online voice, or type it instead."

    const val NOTHING_HEARD = "Didn’t catch anything. Tap the microphone to try again, or type it."

    const val TRY_ONLINE = "Try online voice"

    /** Whether a composer note should carry the [TRY_ONLINE] action. */
    fun offersOnline(notice: String): Boolean = notice == OFFLINE_FAILED || notice == OFFLINE_UNAVAILABLE

    /** The composer's note when Lucento's own listening ended before any words. */
    fun failureNotice(reason: VoiceFailure): String = when (reason) {
        VoiceFailure.OFFLINE_UNAVAILABLE -> OFFLINE_UNAVAILABLE
        VoiceFailure.NETWORK -> "Couldn’t reach your phone’s speech service. Check your connection, or type it."
        VoiceFailure.MICROPHONE -> "The microphone is busy, maybe in another app. Try again, or type it."
        VoiceFailure.PERMISSION ->
            "Listening needs the microphone. In your phone’s settings, let Lucento and the Google app use it, or type it."
        VoiceFailure.LANGUAGE -> "English speech isn’t available on this phone right now. Type it instead — it works the same way."
        VoiceFailure.OTHER -> "Listening stopped. Try again, or type it."
    }

    const val CONSENT_TITLE = "Use online voice?"

    /** One wording for the composer's prompt and the Settings switch, matching the Privacy Policy's "Voice input". */
    const val CONSENT_TEXT =
        "Your phone’s speech service — Google’s on most phones — may then send what you say to Google to turn it into " +
            "text, under Google’s own privacy policy. Lucento only receives the text and never sends your recording " +
            "anywhere. You can turn this off in Settings › Privacy."
}
