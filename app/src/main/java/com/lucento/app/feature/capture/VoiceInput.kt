package com.lucento.app.feature.capture

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import com.lucento.app.core.designsystem.component.ConfirmDialog
import com.lucento.app.core.designsystem.component.Haptics
import com.lucento.app.core.designsystem.component.rememberHaptics
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** What a screen does with what [VoiceInput] hears; read fresh each time, so it always sees the screen's latest state. */
internal class VoiceCallbacks(
    val onlineAllowed: suspend () -> Boolean,
    val allowOnline: () -> Unit,
    val notice: (String?) -> Unit,
    val heard: (String) -> Unit,
    val left: (String) -> Unit,
)

/**
 * The microphone beside a message field: the composer and Ask Lucento (docs/CAPTURE.md "Speech and camera").
 *
 * Lucento listens itself ([VoiceCapture]) when the phone has a speech service and the microphone is allowed: it waits
 * while people think before they speak. Otherwise the speech service's own window, which needs no permission here.
 * Both stay offline unless the person agreed to online voice ([SpeechRequest]); after an offline attempt fails, the
 * screen's note offers it ([offerOnline]). Create it with [rememberVoiceInput] and show [VoiceInputSheets].
 */
@Stable
class VoiceInput internal constructor(
    context: Context,
    private val scope: CoroutineScope,
    private val haptics: Haptics,
    /** Something to try saying, in the listening sheet and the speech service's window. */
    internal val example: String,
    private val callbacks: State<VoiceCallbacks>,
    // Saveable: the speech service's window can answer after a rotation, and the online-voice question can be open.
    private val listenedOffline: MutableState<Boolean>,
    /** The mode the microphone was asked for: the online-voice consent may still be being saved when the answer comes. */
    private val micForOnline: MutableState<Boolean>,
    private val askingOnlineState: MutableState<Boolean>,
) {
    private val appContext = context.applicationContext
    internal val capture = VoiceCapture(appContext) { onEnd(it) }
    internal var window: ActivityResultLauncher<Intent>? = null
    internal var microphone: ActivityResultLauncher<String>? = null

    /** The online-voice question ([SpeechRequest.CONSENT_TEXT]) is open. */
    internal val askingOnline: Boolean get() = askingOnlineState.value

    /** The microphone was tapped: listen, online only if the person agreed to online voice. */
    fun listen() {
        haptics.tick()
        scope.launch { listenWith(online = callbacks.value.onlineAllowed()) }
    }

    /** A note's [SpeechRequest.TRY_ONLINE]: ask first; agreeing listens again straight away, online. */
    fun offerOnline() {
        askingOnlineState.value = true
    }

    internal fun acceptOnline() {
        askingOnlineState.value = false
        val screen = callbacks.value
        screen.notice(null)
        screen.allowOnline()
        haptics.tick()
        // They tapped the mic to speak: listen again straight away, online this time.
        listenWith(online = true)
    }

    internal fun declineOnline() {
        askingOnlineState.value = false
    }

    /** Leaving the app or the screen stops listening; words already heard go back to the screen's field, unsent. */
    internal fun stop() {
        capture.cancel()?.let { callbacks.value.left(it) }
    }

    private fun listenWith(online: Boolean) {
        val route = SpeechRequest.route(
            listening = capture.listening,
            recognitionAvailable = SpeechRecognizer.isRecognitionAvailable(appContext),
            microphoneAllowed = ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
        when (route) {
            MicRoute.BUSY -> Unit
            MicRoute.SERVICE_WINDOW -> openWindow(online)
            MicRoute.LISTEN -> startListening(online)
            MicRoute.ASK_PERMISSION -> {
                micForOnline.value = online
                val ask = microphone
                if (ask != null) ask.launch(Manifest.permission.RECORD_AUDIO) else openWindow(online)
            }
        }
    }

    /** The microphone permission's answer. Refused: the speech service's own window still works without it. */
    internal fun onMicrophoneAnswer(granted: Boolean) {
        if (granted) startListening(micForOnline.value) else openWindow(micForOnline.value)
    }

    private fun startListening(online: Boolean) {
        listenedOffline.value = !online
        callbacks.value.notice(null)
        capture.start(online, SpeechRequest.languageTag(Locale.getDefault()))
    }

    private fun openWindow(online: Boolean) {
        listenedOffline.value = !online
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // Lucento reads English: the phone's own English, or US English.
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, SpeechRequest.languageTag(Locale.getDefault()))
            // True means offline only: phones without an offline pack then can't listen at all.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, !online)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say something like “$example”")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        val launcher = window
        if (launcher == null) {
            callbacks.value.notice(SpeechRequest.NO_RECOGNISER)
            return
        }
        try {
            launcher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            callbacks.value.notice(SpeechRequest.NO_RECOGNISER)
        }
    }

    /** The speech service's own window answered. */
    internal fun onWindowResult(result: ActivityResult) {
        val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        val screen = callbacks.value
        when {
            // Cancelled and "Voice search isn't available" both come back here; offline, offer online voice.
            result.resultCode != Activity.RESULT_OK ->
                screen.notice(if (listenedOffline.value) SpeechRequest.OFFLINE_FAILED else SpeechRequest.WINDOW_STOPPED)
            heard.isNullOrBlank() -> screen.notice(SpeechRequest.WINDOW_NOTHING)
            else -> {
                screen.notice(null)
                screen.heard(heard)
            }
        }
    }

    /** Lucento's own listening ended. */
    private fun onEnd(command: VoiceCommand) {
        val screen = callbacks.value
        when (command) {
            is VoiceCommand.Finish -> {
                val heard = command.text
                if (heard == null) {
                    screen.notice(SpeechRequest.NOTHING_HEARD)
                } else {
                    screen.notice(null)
                    haptics.tick()
                    screen.heard(heard)
                }
            }
            is VoiceCommand.Fail -> screen.notice(SpeechRequest.failureNotice(command.reason))
            else -> Unit
        }
    }
}

/**
 * A screen's microphone ([VoiceInput]). [onHeard] gets the words when listening ends with some; [onNotice] the one-line
 * note to show otherwise (null clears it; [SpeechRequest.offersOnline] says when it carries "Try online voice");
 * [onLeft] the words heard so far when the person leaves mid-sentence. Online voice is read from and saved through
 * [onlineAllowed] and [onAllowOnline], the same preference everywhere.
 */
@Composable
fun rememberVoiceInput(
    example: String,
    onlineAllowed: suspend () -> Boolean,
    onAllowOnline: () -> Unit,
    onNotice: (String?) -> Unit,
    onHeard: (String) -> Unit,
    onLeft: (String) -> Unit,
): VoiceInput {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val callbacks = rememberUpdatedState(VoiceCallbacks(onlineAllowed, onAllowOnline, onNotice, onHeard, onLeft))
    val listenedOffline = rememberSaveable { mutableStateOf(false) }
    val micForOnline = rememberSaveable { mutableStateOf(false) }
    val askingOnline = rememberSaveable { mutableStateOf(false) }
    val voice = remember {
        VoiceInput(context, scope, haptics, example, callbacks, listenedOffline, micForOnline, askingOnline)
    }
    // Set while composing, not in an effect: after the activity is recreated (a rotation while the microphone question
    // was open), a launcher hands over the waiting answer as it registers, and a refused microphone then opens the
    // speech service's window straight away. The launchers are remembered, so this sets the same objects every time.
    voice.window = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { voice.onWindowResult(it) }
    voice.microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { voice.onMicrophoneAnswer(it) }
    LifecycleStartEffect(voice) {
        onStopOrDispose { voice.stop() }
    }
    return voice
}

/** What [voice] shows over the screen: the question before online voice, and Lucento listening. */
@Composable
fun VoiceInputSheets(voice: VoiceInput) {
    if (voice.askingOnline) {
        ConfirmDialog(
            title = SpeechRequest.CONSENT_TITLE,
            text = SpeechRequest.CONSENT_TEXT,
            confirmLabel = "Use online voice",
            dismissLabel = "Not now",
            onConfirm = voice::acceptOnline,
            onDismiss = voice::declineOnline,
        )
    }
    voice.capture.state?.let { session ->
        ListeningSheet(
            state = session,
            example = voice.example,
            level = { voice.capture.level },
            onDone = voice.capture::done,
            onCancel = { voice.capture.cancel() },
        )
    }
}
