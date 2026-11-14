package com.lucento.app.feature.capture

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/**
 * Runs a [VoiceSession] on the phone's speech service through [SpeechRecognizer] (docs/CAPTURE.md). Main thread only.
 *
 * Lucento holds RECORD_AUDIO for this alone: the speech service listens and hands back text. Audio buffers the service
 * may offer are ignored, so Lucento never keeps or sends a recording. Each stretch of listening gets a fresh recogniser
 * tagged with a generation, so a late callback from an earlier stretch can never disturb the current one.
 */
class VoiceCapture(
    context: Context,
    private val timing: VoiceTiming = VoiceTiming(),
    private val onEnd: (VoiceCommand) -> Unit,
) {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var request: Intent? = null
    private var generation = 0

    /** The session while it runs (shown by [ListeningSheet]); null when nothing is listening. */
    var state by mutableStateOf<VoiceState?>(null)
        private set

    /** How loud the voice is right now, from 0 to 1, for the microphone's pulse. */
    var level by mutableFloatStateOf(0f)
        private set

    val listening: Boolean get() = state != null

    fun start(online: Boolean, language: String) {
        release()
        request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            // True means offline only; online voice is the person's choice (SpeechRequest).
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, !online)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // Hints that a thinking pause isn't the end. Speech services may ignore them; VoiceTiming is what counts.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2_000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1_500L)
        }
        state = VoiceSession.start(SystemClock.elapsedRealtime(), offline = !online)
        listenNow()
        // listenNow may already have ended the session (no permission): then there is nothing to time.
        if (state != null) main.postDelayed(tick, TICK_MS)
    }

    /** The person finished speaking. */
    fun done() = dispatch(VoiceEvent.DoneTapped)

    /** Stops without sending anything; returns what had been heard, so it can go back into the message field. */
    fun cancel(): String? {
        val heard = state?.transcript?.takeIf { it.isNotBlank() }
        release()
        return heard
    }

    /** Lets go of the speech service and the microphone. Safe to call at any time. */
    fun release() {
        main.removeCallbacksAndMessages(null)
        generation++
        recognizer?.let { runCatching { it.destroy() } }
        recognizer = null
        state = null
        level = 0f
    }

    private fun dispatch(event: VoiceEvent) {
        val current = state ?: return
        val (next, command) = VoiceSession.reduce(current, event, SystemClock.elapsedRealtime(), timing)
        state = next
        when (command) {
            VoiceCommand.None -> Unit
            VoiceCommand.ListenAgain -> {
                level = 0f
                main.postDelayed(again, timing.restartDelayMs)
            }
            VoiceCommand.Stop -> recognizer?.let { runCatching { it.stopListening() } }
            is VoiceCommand.Finish, is VoiceCommand.Fail -> {
                release()
                onEnd(command)
            }
        }
    }

    private val again = Runnable { if (state?.phase == VoicePhase.LISTENING) listenNow() }

    private val tick = object : Runnable {
        override fun run() {
            dispatch(VoiceEvent.Tick)
            if (state != null) main.postDelayed(this, TICK_MS)
        }
    }

    private fun listenNow() {
        val intent = request ?: return
        // Checked each time: the permission can be taken away in the phone's settings while Lucento is open.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            dispatch(VoiceEvent.Error(VoiceError.PERMISSION))
            return
        }
        recognizer?.let { runCatching { it.destroy() } }
        val stretch = ++generation
        val service = SpeechRecognizer.createSpeechRecognizer(context)
        service.setRecognitionListener(Listener(stretch))
        recognizer = service
        service.startListening(intent)
    }

    private inner class Listener(private val stretch: Int) : RecognitionListener {
        private val current get() = stretch == generation

        private fun send(event: VoiceEvent) {
            if (current) dispatch(event)
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = send(VoiceEvent.SpeechStarted)
        override fun onRmsChanged(rmsdB: Float) {
            // Speech services report roughly -2 dB (quiet) to 10 dB (loud).
            if (current) level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }
        override fun onBufferReceived(buffer: ByteArray?) = Unit // never kept
        override fun onEndOfSpeech() = Unit
        override fun onError(error: Int) = send(VoiceEvent.Error(errorFor(error)))
        override fun onResults(results: Bundle?) = send(VoiceEvent.Final(results.firstText()))
        override fun onPartialResults(partialResults: Bundle?) = send(VoiceEvent.Partial(partialResults.firstText()))
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private companion object {
        const val TICK_MS = 200L

        fun Bundle?.firstText(): String =
            this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

        /** The newer codes (Android 12+) are plain numbers, so they are safe to compare on older phones too. */
        @SuppressLint("InlinedApi")
        fun errorFor(code: Int): VoiceError = when (code) {
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_NO_MATCH -> VoiceError.NO_SPEECH
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> VoiceError.RETRY
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> VoiceError.LANGUAGE
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> VoiceError.NETWORK
            SpeechRecognizer.ERROR_AUDIO -> VoiceError.AUDIO
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceError.PERMISSION
            else -> VoiceError.OTHER
        }
    }
}
