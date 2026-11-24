package com.lucento.app.feature.capture

import java.util.Locale

/** Which way the microphone listens when tapped ([SpeechRequest.route]). */
enum class MicRoute {
    /** Already listening: the tap does nothing. */
    BUSY,
    /** The speech service's own window, which needs no permission in Lucento. */
    SERVICE_WINDOW,
    /** Lucento listens itself ([VoiceSession]). */
    LISTEN,
    /** Ask for the microphone first; refused, the speech service's window listens instead. */
    ASK_PERMISSION,
}

/**
 * How the composer and Ask Lucento ask the phone's speech service to listen (docs/CAPTURE.md "Speech and camera").
 *
 * The speech service (Google's on most phones) turns speech into text; Lucento only receives the text. With the
 * microphone allowed, Lucento runs the listening itself ([VoiceSession]); otherwise the service's own window listens.
 * By default Lucento asks it to stay offline. Many phones can't do that: Google's dialog then says "Voice search isn't
 * available". Online voice is the person's choice, asked after an offline attempt fails and switchable in Settings ›
 * Privacy, because the recogniser may then send the recording to Google.
 */
object SpeechRequest {

    /**
     * Lucento listens itself whenever it can, because it waits while people think. A phone without a speech service to
     * drive (or with no `<queries>` match) gets the service's own window, and so does a refused microphone.
     */
    fun route(listening: Boolean, recognitionAvailable: Boolean, microphoneAllowed: Boolean): MicRoute = when {
        listening -> MicRoute.BUSY
        !recognitionAvailable -> MicRoute.SERVICE_WINDOW
        microphoneAllowed -> MicRoute.LISTEN
        else -> MicRoute.ASK_PERMISSION
    }

    /**
     * Words that can't be sent now (the app was left mid-sentence, or an answer is still on its way) wait in the message
     * field, after anything already typed there, so nothing is lost. Capped at the field's [limit].
     */
    fun intoField(field: String, heard: String, limit: Int): String {
        val words = heard.trim()
        if (words.isEmpty()) return field
        return (if (field.isBlank()) words else "${field.trimEnd()} $words").take(limit)
    }

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

    /** The speech service's own window was closed while online. */
    const val WINDOW_STOPPED = "Listening stopped. You can type it instead."

    /** The speech service's own window came back with no words. */
    const val WINDOW_NOTHING = "Nothing was recognised. Try again, or type it."

    const val NO_RECOGNISER = "This phone has no speech recogniser available. Type it instead — it works the same way."

    const val TRY_ONLINE = "Try online voice"

    /** Whether a screen's note should carry the [TRY_ONLINE] action. */
    fun offersOnline(notice: String): Boolean = notice == OFFLINE_FAILED || notice == OFFLINE_UNAVAILABLE

    /** The note when Lucento's own listening ended before any words. */
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

    /**
     * One wording for the microphone's prompt (the composer and Ask Lucento) and the Settings switch, matching the
     * Privacy Policy's "Voice input".
     */
    const val CONSENT_TEXT =
        "Your phone’s speech service — Google’s on most phones — may then send what you say to Google to turn it into " +
            "text, under Google’s own privacy policy. Lucento only receives the text and never sends your recording " +
            "anywhere. You can turn this off in Settings › Privacy."
}
