package com.lucento.app.feature.pro.welcome

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.component.SpotlightKeys
import com.lucento.app.core.designsystem.component.SpotlightRegistry
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.domain.entitlement.EntitlementState
import com.lucento.app.domain.entitlement.ProTourStop
import com.lucento.app.domain.entitlement.ProWelcome
import com.lucento.app.domain.entitlement.ProWelcomeMode
import com.lucento.app.domain.entitlement.ProWelcomeRun
import com.lucento.app.feature.awards.AwardMedal
import com.lucento.app.feature.awards.MedalAspect
import com.lucento.app.feature.awards.ProMedalArt
import com.lucento.app.feature.awards.ProMedalCeremony
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/** Where Lucento Pro's welcome is: the curtain, a tour stop, the summary, the medal, the badge's flight. */
enum class ProWelcomePhase { CURTAIN, TOUR, SUMMARY, MEDAL, FLIGHT, DONE }

/**
 * The welcome's progress, saved across rotation (the run itself lives in AppViewModel). [coversScreen]: the curtain, a
 * tour stop or the flight is drawn over the app, which is then hidden from TalkBack. [ringHeld]: the Pro ring waits for
 * the badge to land before it appears.
 */
@Stable
class ProWelcomeState internal constructor(val run: ProWelcomeRun?, phase: String, index: Int, landed: Boolean) {
    var phase by mutableStateOf(ProWelcomePhase.valueOf(phase))
        internal set
    var index by mutableIntStateOf(index)
        internal set
    var landed by mutableStateOf(landed)
        internal set

    val coversScreen: Boolean
        get() = run != null && (phase == ProWelcomePhase.CURTAIN || phase == ProWelcomePhase.TOUR || phase == ProWelcomePhase.FLIGHT)

    val ringHeld: Boolean get() = run != null && run.mode.flight && !landed && phase != ProWelcomePhase.DONE
}

@Composable
fun rememberProWelcomeState(run: ProWelcomeRun?): ProWelcomeState {
    val key = run?.let { "${it.mode.name}|${it.planKeys.joinToString(",")}" }
    val start = if (run?.mode?.tour == false) ProWelcomePhase.MEDAL else ProWelcomePhase.CURTAIN
    var phase by rememberSaveable(key) { mutableStateOf(start.name) }
    var index by rememberSaveable(key) { mutableIntStateOf(0) }
    var landed by rememberSaveable(key) { mutableStateOf(false) }
    val state = remember(key) { ProWelcomeState(run, phase, index, landed) }
    // Mirrors the live state into the saved values (read back after a rotation).
    LaunchedEffect(state) {
        snapshotFlow { Triple(state.phase, state.index, state.landed) }.collect { (p, i, l) ->
            phase = p.name
            index = i
            landed = l
        }
    }
    return state
}

/**
 * Lucento Pro's welcome (owner, 2026-11-12), drawn over the whole app: a curtain, a tour of the real screens Pro opened
 * (each stop navigates there and lights the control up), the summary of higher limits and online services, the Lucento
 * Pro medal, and its flight to the profile picture. [paused] while the app lock is showing: nothing is drawn (dialogs
 * would sit above the lock) and the curtain's timer waits.
 */
@Composable
fun ProWelcomeHost(
    state: ProWelcomeState,
    stops: List<ProTourStop>,
    entitlements: EntitlementState,
    signedIn: Boolean,
    aiAnswersOn: Boolean,
    /** False in a build without cloud sync: the online services can't be used here. */
    syncAvailable: Boolean,
    /** Whether wallets and goals exist, so the words name the control that is really there. */
    hasWallets: Boolean = true,
    hasGoals: Boolean = true,
    /** Whether Smarter reading is already on (its stop says so instead of where to turn it on). */
    smartReadingOn: Boolean = false,
    paused: Boolean,
    registry: SpotlightRegistry,
    /** Navigates to [ProTourStop]'s screen (nothing when already there). */
    onShowStop: (ProTourStop) -> Unit,
    /** Back to Home, where the profile picture waits for the badge. */
    onGoHome: () -> Unit,
    /** The run began: its plans are stored as welcomed (once, so it never loops). */
    onStarted: (ProWelcomeRun) -> Unit,
    /** The medal is on screen: its date is stored (kept for good). */
    onMedalPresented: () -> Unit,
    /** The badge reached the profile picture: the ring pulses. */
    onLanded: () -> Unit,
    onFinished: () -> Unit,
) {
    val run = state.run ?: return
    val finished by rememberUpdatedState(onFinished)
    val haptics = rememberHaptics()
    val reduced = LucentoTheme.reducedMotion

    LaunchedEffect(run) { onStarted(run) }

    fun finish() {
        registry.active = null
        state.phase = ProWelcomePhase.DONE
        finished()
    }

    fun afterSummary() {
        state.phase = when {
            run.mode.medal -> ProWelcomePhase.MEDAL
            run.mode.flight -> ProWelcomePhase.FLIGHT
            else -> ProWelcomePhase.DONE
        }
        if (state.phase == ProWelcomePhase.DONE) finish()
    }

    fun skipTour() {
        registry.active = null
        state.phase = ProWelcomePhase.SUMMARY
    }

    // Back (or a tap outside) on the summary or the award: the welcome ends there. A first award is still given, and
    // the badge simply appears on the profile picture; nothing pushes the person into the next moment.
    fun leave() {
        if (run.mode.medal && state.phase != ProWelcomePhase.MEDAL) onMedalPresented()
        if (run.mode.flight) {
            state.landed = true
            onLanded()
        }
        finish()
    }

    if (paused || state.phase == ProWelcomePhase.DONE) return
    val now = System.currentTimeMillis()
    val capabilities = entitlements.capabilities(now)

    when (state.phase) {
        ProWelcomePhase.CURTAIN -> {
            if (stops.isEmpty()) {
                LaunchedEffect(Unit) { state.phase = ProWelcomePhase.SUMMARY }
                return
            }
            BackHandler { skipTour() }
            ProTourCurtain(
                title = ProWelcomeCopy.curtainTitle(run.mode, run.restored),
                body = if (run.mode == ProWelcomeMode.REPLAY) ProWelcomeCopy.replayBody(stops.size) else ProWelcomeCopy.curtainBody(stops.size, run.restored),
                celebrate = run.mode != ProWelcomeMode.REPLAY,
                onShowMe = {
                    state.index = 0
                    state.phase = ProWelcomePhase.TOUR
                },
                onSkip = ::skipTour,
            )
            // On its own after a moment (the owner wanted the welcome to start with the screens themselves), but never
            // while TalkBack is on, and stretched to the person's "time to take action" setting otherwise (WCAG 2.2.1).
            val context = LocalContext.current
            val touchExploration = remember(context) {
                context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)?.isTouchExplorationEnabled == true
            }
            val a11y = LocalAccessibilityManager.current
            if (!touchExploration) {
                LaunchedEffect(Unit) {
                    delay(a11y?.calculateRecommendedTimeoutMillis(CurtainMillis, containsIcons = true, containsText = true, containsControls = true) ?: CurtainMillis)
                    if (state.phase == ProWelcomePhase.CURTAIN) {
                        state.index = 0
                        state.phase = ProWelcomePhase.TOUR
                    }
                }
            }
        }
        ProWelcomePhase.TOUR -> {
            val index = state.index.coerceIn(0, (stops.size - 1).coerceAtLeast(0))
            val stop = stops.getOrNull(index)
            if (stop == null) {
                LaunchedEffect(Unit) { skipTour() }
                return
            }
            LaunchedEffect(stop) {
                onShowStop(stop)
                registry.active = stop.spotlightKey
            }
            // One stop back, like the card's Back; from the first stop, back to the curtain (whose Back goes on to the
            // summary, whose Back leaves): pressing Back always leads out.
            BackHandler { if (index > 0) state.index = index - 1 else state.phase = ProWelcomePhase.CURTAIN }
            ProTourStopOverlay(
                stop = stop,
                index = index,
                total = stops.size,
                copy = ProWelcomeCopy.stop(
                    stop, capabilities, signedIn, aiAnswersOn,
                    ProWelcome.onlineStatus(entitlements, now, signedIn, syncAvailable), hasWallets, hasGoals, smartReadingOn,
                ),
                registry = registry,
                // The primary button gives its own confirm haptic.
                onNext = { if (index + 1 < stops.size) state.index = index + 1 else skipTour() },
                onBack = if (index > 0) ({
                    haptics.tick()
                    state.index = index - 1
                }) else null,
                onSkip = ::skipTour,
                lastStop = index == stops.size - 1,
            )
        }
        ProWelcomePhase.SUMMARY -> ProPrivilegesDialog(
            mode = run.mode,
            restored = run.restored,
            entitlements = entitlements,
            privileges = ProWelcome.privileges(capabilities),
            onlineStatus = ProWelcome.onlineStatus(entitlements, now, signedIn, syncAvailable),
            onDone = ::afterSummary,
            alsoYours = ProWelcomeCopy.alsoYours(capabilities, replay = run.mode == ProWelcomeMode.REPLAY),
            // Back leaves the welcome (the award is still given and the badge appears); a tap outside does nothing.
            onBack = ::leave,
        )
        ProWelcomePhase.MEDAL -> {
            LaunchedEffect(Unit) { onMedalPresented() }
            ProMedalCeremony(
                onDismiss = { if (run.mode.flight) state.phase = ProWelcomePhase.FLIGHT else finish() },
                onClose = ::leave,
                // The same dim as the tour, so the screen doesn't brighten between the moments.
                dim = tourScrimAlpha(),
            )
        }
        ProWelcomePhase.FLIGHT -> BadgeFlight(
            registry = registry,
            reduced = reduced,
            onGoHome = onGoHome,
            onLanded = {
                state.landed = true
                haptics.confirm()
                onLanded()
            },
            onDone = ::finish,
        )
        ProWelcomePhase.DONE -> Unit
    }
}

private const val CurtainMillis = 2_600L

/** The control each stop lights up (see [SpotlightKeys]). */
val ProTourStop.spotlightKey: String
    get() = when (this) {
        ProTourStop.WALLETS -> SpotlightKeys.WALLETS_ADD
        ProTourStop.NET_WORTH -> SpotlightKeys.NET_WORTH
        ProTourStop.SMART_READING -> SpotlightKeys.COMPOSER
        ProTourStop.GOALS -> SpotlightKeys.GOALS_ADD
        ProTourStop.INSIGHTS_PERIODS -> SpotlightKeys.INSIGHTS_PERIOD
        ProTourStop.STATEMENTS -> SpotlightKeys.REPORTS
        ProTourStop.AI_ANSWERS -> SpotlightKeys.AI_STATUS
        ProTourStop.SYNC -> SpotlightKeys.SYNC
        ProTourStop.SHARED -> SpotlightKeys.SHARED
        ProTourStop.THEMES -> SpotlightKeys.PROFILE
    }

/**
 * The badge's flight: the medal, small now, rises from where the ceremony stood and arcs into the profile picture, where
 * the Pro ring appears with a pulse and a confirm haptic. Home is shown first if the picture isn't on screen; if it
 * still can't be found, or under reduced motion, the ring simply appears.
 */
@Composable
private fun BadgeFlight(
    registry: SpotlightRegistry,
    reduced: Boolean,
    onGoHome: () -> Unit,
    onLanded: () -> Unit,
    onDone: () -> Unit,
) {
    val density = LocalDensity.current
    var origin by remember { mutableStateOf<Offset?>(null) }
    var size by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    val progress = remember { Animatable(0f) }
    var target by remember { mutableStateOf<Rect?>(null) }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                origin = it.boundsInWindow().topLeft
                size = it.boundsInWindow().size
            },
    ) {
        val to = target
        val from = origin
        if (to != null && from != null && progress.value < 1f && size != androidx.compose.ui.geometry.Size.Zero) {
            val start = Offset(size.width / 2f, size.height * 0.36f)
            val end = Offset(to.center.x - from.x, to.center.y - from.y)
            // A gentle arc: up and across, then down into the picture.
            val control = Offset(end.x, start.y - with(density) { 40.dp.toPx() })
            val t = progress.value
            val u = 1f - t
            val point = start * (u * u) + control * (2 * u * t) + end * (t * t)
            val medal = with(density) { (64.dp + (18.dp - 64.dp) * t).toPx() }
            AwardMedal(
                art = ProMedalArt,
                earned = true,
                size = with(density) { medal.toDp() },
                modifier = Modifier
                    .offset { IntOffset((point.x - medal / 2f).roundToInt(), (point.y - medal * MedalAspect / 2f).roundToInt()) }
                    .graphicsLayer { alpha = if (t > 0.85f) (1f - t) / 0.15f else 1f },
            )
        }
    }
    LaunchedEffect(Unit) {
        if (registry.boundsOf(SpotlightKeys.PROFILE) == null) onGoHome()
        val found = withTimeoutOrNull(1_800) {
            snapshotFlow { registry.boundsOf(SpotlightKeys.PROFILE) }.first { it != null }
        }
        // Let the screen settle under it before it flies.
        delay(if (reduced) 0 else 250)
        target = registry.boundsOf(SpotlightKeys.PROFILE) ?: found
        if (!reduced && target != null) {
            progress.animateTo(1f, tween(950, easing = FastOutSlowInEasing))
        }
        onLanded()
        delay(if (reduced) 0 else 700)
        onDone()
    }
}
