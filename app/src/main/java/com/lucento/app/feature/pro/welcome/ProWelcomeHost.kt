package com.lucento.app.feature.pro.welcome

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
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
import com.lucento.app.feature.pro.ProCopy
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/** Where Lucento Pro's welcome is: the deck of cards, the collection, the medal, the badge's flight. */
enum class ProWelcomePhase { REVEAL, COLLECTION, MEDAL, FLIGHT, DONE }

/**
 * The welcome's progress, saved across rotation (the run itself lives in AppViewModel). [index]: the card face up on top,
 * -1 while the deck is closed. [coversScreen]: the deck, the collection or the flight is drawn over the app, which is
 * then hidden from TalkBack. [ringHeld]: the Pro ring waits for the badge to land before it appears.
 */
@Stable
class ProWelcomeState internal constructor(val run: ProWelcomeRun?, phase: String, index: Int, landed: Boolean) {
    var phase by mutableStateOf(ProWelcomePhase.entries.firstOrNull { it.name == phase } ?: ProWelcomePhase.REVEAL)
        internal set
    var index by mutableIntStateOf(index)
        internal set
    var landed by mutableStateOf(landed)
        internal set

    val coversScreen: Boolean
        get() = run != null && (phase == ProWelcomePhase.REVEAL || phase == ProWelcomePhase.COLLECTION || phase == ProWelcomePhase.FLIGHT)

    val ringHeld: Boolean get() = run != null && run.mode.flight && !landed && phase != ProWelcomePhase.DONE
}

@Composable
fun rememberProWelcomeState(run: ProWelcomeRun?): ProWelcomeState {
    val key = run?.let { "${it.mode.name}|${it.planKeys.joinToString(",")}" }
    val start = if (run?.mode?.reveal == false) ProWelcomePhase.MEDAL else ProWelcomePhase.REVEAL
    var phase by rememberSaveable(key) { mutableStateOf(start.name) }
    var index by rememberSaveable(key) { mutableIntStateOf(-1) }
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
 * Lucento Pro's welcome (owner, 2026-11-12), drawn over the whole app: a deck of cards, one per thing Pro opened, turned
 * over one at a time; the collection of them all with the plan's terms; the Lucento Pro medal; and its flight to the
 * profile picture. Nothing in it moves through the app's screens (the tour it replaced did, and its buttons failed on
 * real phones: KNOWN_ISSUES PW-1). [paused] while the app lock is showing: nothing is drawn (dialogs would sit above the
 * lock). The system Back key always leads out: from the cards to the collection, and from there out of the welcome
 * (the award is still given and the badge appears).
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
    /** Whether Smarter reading is already on (its card says so instead of where to turn it on). */
    smartReadingOn: Boolean = false,
    paused: Boolean,
    registry: SpotlightRegistry,
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
        state.phase = ProWelcomePhase.DONE
        finished()
    }

    fun toCollection() {
        state.phase = ProWelcomePhase.COLLECTION
    }

    fun afterCollection() {
        state.phase = when {
            run.mode.medal -> ProWelcomePhase.MEDAL
            run.mode.flight -> ProWelcomePhase.FLIGHT
            else -> ProWelcomePhase.DONE
        }
        if (state.phase == ProWelcomePhase.DONE) finish()
    }

    // Back on the collection or the award: the welcome ends there. A first award is still given, and the badge simply
    // appears on the profile picture; nothing pushes the person into the next moment.
    fun leave() {
        if (run.mode.medal && state.phase != ProWelcomePhase.MEDAL) onMedalPresented()
        if (run.mode.flight) {
            state.landed = true
            onLanded()
        }
        finish()
    }

    if (paused || state.phase == ProWelcomePhase.DONE) return
    // Light status and navigation bar icons on the dim, in every theme, until the badge flies over the app again
    // (critic round 3: the light theme's dark icons sat on the dim, then turned white for the award).
    LightSystemBars(state.phase != ProWelcomePhase.FLIGHT)
    val now = System.currentTimeMillis()
    val capabilities = entitlements.capabilities(now)
    val online = ProWelcome.onlineStatus(entitlements, now, signedIn, syncAvailable)
    val term = ProCopy.serviceTerm(entitlements, now)
    val cards = remember(stops, capabilities, signedIn, aiAnswersOn, online, hasWallets, hasGoals, smartReadingOn, term) {
        stops.map { stop ->
            RevealCard(stop, ProWelcomeCopy.stop(stop, capabilities, signedIn, aiAnswersOn, online, hasWallets, hasGoals, smartReadingOn, term))
        }
    }

    when (state.phase) {
        ProWelcomePhase.REVEAL -> {
            if (cards.isEmpty()) {
                LaunchedEffect(Unit) { toCollection() }
                return
            }
            BackHandler { toCollection() }
            ProRevealDeck(
                cards = cards,
                title = ProWelcomeCopy.revealTitle(run.mode, run.restored),
                body = ProWelcomeCopy.revealBody(cards.size),
                // The band is for a first purchase; a welcome back or a replay opens on the plain deck.
                unwrap = run.mode == ProWelcomeMode.FIRST && !run.restored,
                index = state.index,
                onIndex = { state.index = it },
                onCollection = ::toCollection,
            )
        }
        ProWelcomePhase.COLLECTION -> {
            BackHandler { leave() }
            val plan = ProCopy.planStatus(entitlements, now)
            ProCollection(
                eyebrow = ProWelcomeCopy.collectionEyebrow(cards.size),
                // At large text the short title, so no word breaks across lines (critic round 3: "Congratula- / tions").
                title = ProWelcomeCopy.collectionTitle(run.mode, run.restored, compact = LocalDensity.current.fontScale >= 1.3f),
                plan = plan.title,
                planDetail = plan.detail,
                status = ProWelcomeCopy.onlineStatus(online),
                items = cards.map { CollectionItem(it.stop, it.copy.short, ProWelcomeCopy.collectionTag(it.stop, it.copy, term)) },
                button = ProWelcomeCopy.collectionButton(run.mode),
                onDone = ::afterCollection,
            )
        }
        ProWelcomePhase.MEDAL -> {
            LaunchedEffect(Unit) { onMedalPresented() }
            ProMedalCeremony(
                onDismiss = { if (run.mode.flight) state.phase = ProWelcomePhase.FLIGHT else finish() },
                onClose = ::leave,
                // The same dim as the cards, so the screen doesn't brighten between the moments.
                dim = WelcomeDim,
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

/**
 * While [on], the status and navigation bars draw light icons (the welcome's dim is dark in every theme); when the
 * welcome moves on, they go back to what the app had.
 */
@Composable
private fun LightSystemBars(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(on, view) {
        val window = view.context.findActivity()?.window
        if (!on || window == null || view.isInEditMode) return@DisposableEffect onDispose {}
        val bars = WindowCompat.getInsetsController(window, view)
        val status = bars.isAppearanceLightStatusBars
        val navigation = bars.isAppearanceLightNavigationBars
        bars.isAppearanceLightStatusBars = false
        bars.isAppearanceLightNavigationBars = false
        onDispose {
            bars.isAppearanceLightStatusBars = status
            bars.isAppearanceLightNavigationBars = navigation
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
