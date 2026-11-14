package com.lucento.app.feature.capture

/**
 * Lucento's own listening session (owner, 2026-11-13). Google's voice window stopped listening before people had
 * started speaking, and an app can't change how long that window waits. So Lucento runs the session itself on the
 * phone's speech service: it waits for the first words, keeps listening through the pauses between items, and ends
 * after a short quiet once something was said, or when the person taps Done.
 *
 * Pure and Android-free: [VoiceCapture] feeds it the speech service's events with the time, and does what it answers.
 * A speech service ends each stretch of speech on its own (often after a second of quiet, sometimes sooner, and
 * sometimes before anyone has spoken). The session simply listens again until its own deadlines pass.
 */
data class VoiceTiming(
    /** Waiting for the first words: people think before they speak. */
    val firstWordsMs: Long = 10_000,
    /** After words, a pause this long ends the session. Long enough to think of the next item; Done ends it sooner. */
    val quietAfterWordsMs: Long = 3_500,
    /** Done was tapped mid-sentence: how long the speech service gets to hand back its last words. */
    val stopGraceMs: Long = 2_500,
    /** The longest one session lasts. */
    val maxSessionMs: Long = 60_000,
    /** A speech service that keeps giving up at once (some do within a fraction of a second): stop trying. */
    val maxRestarts: Int = 30,
    /** A short gap before listening again, so the speech service has let go of the microphone. */
    val restartDelayMs: Long = 250,
)

enum class VoicePhase { LISTENING, STOPPING, FINISHED }

/** The speech service's errors, grouped by what the session does about them. */
enum class VoiceError {
    /** Nothing was said, or nothing recognised (speech timeout, no match): listen again while there is time. */
    NO_SPEECH,
    /** The service is still busy or briefly out of step (busy, client): worth trying again. */
    RETRY,
    /** The language isn't there: not downloaded or not supported. */
    LANGUAGE,
    /** No network, a network timeout, or a server problem. */
    NETWORK,
    /** The microphone couldn't record. */
    AUDIO,
    /** A permission is missing. */
    PERMISSION,
    OTHER,
}

/** Why a session ended before any words were heard. */
enum class VoiceFailure { OFFLINE_UNAVAILABLE, NETWORK, MICROPHONE, PERMISSION, LANGUAGE, OTHER }

sealed interface VoiceEvent {
    data object SpeechStarted : VoiceEvent
    data class Partial(val text: String) : VoiceEvent
    data class Final(val text: String) : VoiceEvent
    data class Error(val error: VoiceError) : VoiceEvent
    data object DoneTapped : VoiceEvent
    /** Time passing: deadlines are checked. */
    data object Tick : VoiceEvent
}

sealed interface VoiceCommand {
    data object None : VoiceCommand
    /** Listen again after [VoiceTiming.restartDelayMs]. */
    data object ListenAgain : VoiceCommand
    /** Ask the speech service to finish the words it is hearing; they arrive as [VoiceEvent.Final]. */
    data object Stop : VoiceCommand
    /** The session is over: send [text], or nothing was heard when it is null. */
    data class Finish(val text: String?) : VoiceCommand
    /** It ended before any words were heard, because of [reason]. */
    data class Fail(val reason: VoiceFailure) : VoiceCommand
}

data class VoiceState(
    val startedAt: Long,
    /** Offline only (the default), or online voice, which the person agreed to. */
    val offline: Boolean,
    val phase: VoicePhase = VoicePhase.LISTENING,
    /** The finished stretches of speech, in order. */
    val heard: List<String> = emptyList(),
    /** What the speech service thinks it is hearing right now. */
    val partial: String = "",
    /** Speech has started and its result hasn't come yet. */
    val speaking: Boolean = false,
    /** The latest sign of words: the start of speech, a partial or a result. */
    val lastWordsAt: Long? = null,
    /** When Done asked the service to finish. */
    val stopAt: Long? = null,
    val restarts: Int = 0,
) {
    /** Everything heard so far and what is being heard now, as shown while listening. */
    val transcript: String get() = (heard + partial).filter(String::isNotBlank).joinToString(VoiceSession.SEPARATOR)

    /** Words are coming in: never cut someone off mid-sentence. */
    val active: Boolean get() = speaking || partial.isNotBlank()
}

object VoiceSession {
    /** Stretches are joined like a typed list ("lunch 250, groceries 800"), which the interpreter reads as items. */
    const val SEPARATOR = ", "

    fun start(now: Long, offline: Boolean) = VoiceState(startedAt = now, offline = offline)

    fun reduce(s: VoiceState, event: VoiceEvent, now: Long, t: VoiceTiming = VoiceTiming()): Pair<VoiceState, VoiceCommand> {
        if (s.phase == VoicePhase.FINISHED) return s to VoiceCommand.None
        return when (event) {
            VoiceEvent.SpeechStarted -> s.copy(speaking = true, lastWordsAt = now) to VoiceCommand.None
            is VoiceEvent.Partial -> {
                val text = event.text.trim()
                s.copy(partial = text, lastWordsAt = if (text.isEmpty()) s.lastWordsAt else now) to VoiceCommand.None
            }
            is VoiceEvent.Final -> {
                val text = event.text.trim()
                val next = s.copy(
                    heard = if (text.isEmpty()) s.heard else s.heard + text,
                    partial = "",
                    speaking = false,
                    lastWordsAt = if (text.isEmpty()) s.lastWordsAt else now,
                )
                if (s.phase == VoicePhase.STOPPING) finish(next) else listenAgainOrFinish(next, now, t)
            }
            is VoiceEvent.Error -> onError(s, event.error, now, t)
            VoiceEvent.DoneTapped -> when {
                // A second tap doesn't wait for the service any longer.
                s.phase == VoicePhase.STOPPING -> finish(s, withPartial = true)
                s.active -> s.copy(phase = VoicePhase.STOPPING, stopAt = now) to VoiceCommand.Stop
                else -> finish(s)
            }
            VoiceEvent.Tick -> onTick(s, now, t)
        }
    }

    private fun onError(s: VoiceState, error: VoiceError, now: Long, t: VoiceTiming): Pair<VoiceState, VoiceCommand> {
        // Done was tapped: whatever the service says now, the session ends with what the person saw.
        if (s.phase == VoicePhase.STOPPING) return finish(s, withPartial = true)
        val quiet = s.copy(speaking = false, partial = "")
        return when (error) {
            VoiceError.NO_SPEECH, VoiceError.RETRY -> listenAgainOrFinish(quiet, now, t)
            // Anything else ends the session, but never loses words already heard.
            else -> if (quiet.heard.isNotEmpty()) finish(quiet) else fail(quiet, failureFor(error, s.offline))
        }
    }

    private fun onTick(s: VoiceState, now: Long, t: VoiceTiming): Pair<VoiceState, VoiceCommand> = when (s.phase) {
        VoicePhase.STOPPING ->
            if (now - (s.stopAt ?: now) >= t.stopGraceMs) finish(s, withPartial = true) else s to VoiceCommand.None
        VoicePhase.LISTENING -> when {
            now - s.startedAt >= t.maxSessionMs -> if (s.active) stop(s, now) else finish(s)
            // Speech "started" but nothing new for a long while (noise, or a stuck service): ask for what it has.
            s.active -> if (now - (s.lastWordsAt ?: s.startedAt) >= t.firstWordsMs) stop(s, now) else s to VoiceCommand.None
            s.heard.isEmpty() -> if (now - s.startedAt >= t.firstWordsMs) finish(s) else s to VoiceCommand.None
            else -> if (now - (s.lastWordsAt ?: s.startedAt) >= t.quietAfterWordsMs) finish(s) else s to VoiceCommand.None
        }
        VoicePhase.FINISHED -> s to VoiceCommand.None
    }

    /** The service ended a stretch: listen again while the person may still speak, or end the session. */
    private fun listenAgainOrFinish(s: VoiceState, now: Long, t: VoiceTiming): Pair<VoiceState, VoiceCommand> {
        val deadline = if (s.heard.isEmpty()) s.startedAt + t.firstWordsMs else (s.lastWordsAt ?: s.startedAt) + t.quietAfterWordsMs
        val canListen = now < deadline && now - s.startedAt < t.maxSessionMs && s.restarts < t.maxRestarts
        return if (canListen) s.copy(restarts = s.restarts + 1) to VoiceCommand.ListenAgain else finish(s)
    }

    private fun stop(s: VoiceState, now: Long) = s.copy(phase = VoicePhase.STOPPING, stopAt = now) to VoiceCommand.Stop

    private fun finish(s: VoiceState, withPartial: Boolean = false): Pair<VoiceState, VoiceCommand> {
        val pieces = (if (withPartial) s.heard + s.partial else s.heard).map(String::trim).filter(String::isNotEmpty)
        val text = pieces.joinToString(SEPARATOR).takeIf { it.isNotEmpty() }
        return s.copy(phase = VoicePhase.FINISHED, heard = pieces, partial = "", speaking = false) to VoiceCommand.Finish(text)
    }

    private fun fail(s: VoiceState, reason: VoiceFailure) = s.copy(phase = VoicePhase.FINISHED) to VoiceCommand.Fail(reason)

    /**
     * Offline only, a missing language pack shows up as a language error on newer phones and as a network or other
     * error on older speech services. Either way, online voice is the way out.
     */
    fun failureFor(error: VoiceError, offline: Boolean): VoiceFailure = when (error) {
        VoiceError.AUDIO -> VoiceFailure.MICROPHONE
        VoiceError.PERMISSION -> VoiceFailure.PERMISSION
        VoiceError.LANGUAGE -> if (offline) VoiceFailure.OFFLINE_UNAVAILABLE else VoiceFailure.LANGUAGE
        VoiceError.NETWORK -> if (offline) VoiceFailure.OFFLINE_UNAVAILABLE else VoiceFailure.NETWORK
        VoiceError.OTHER, VoiceError.NO_SPEECH, VoiceError.RETRY ->
            if (offline) VoiceFailure.OFFLINE_UNAVAILABLE else VoiceFailure.OTHER
    }
}
