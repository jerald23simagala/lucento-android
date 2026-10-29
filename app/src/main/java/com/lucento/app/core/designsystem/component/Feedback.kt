package com.lucento.app.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.theme.Elevation
import com.lucento.app.core.designsystem.theme.Motion
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import kotlinx.coroutines.delay

/**
 * Empty state with an optional illustration animation. The illustration plays once and holds its finished
 * frame; under reduced motion it is shown complete and still. On short screens (landscape, split screen,
 * large font) the illustration is dropped so the explanation and actions stay visible.
 */
@Composable
fun LucentoEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    animation: LucentoAnimation? = null,
    mascot: MascotMood? = if (animation == null) MascotMood.Curious else null,
    actions: @Composable (() -> Unit)? = null,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val showArt = maxWidth >= 280.dp && maxHeight >= 420.dp && (animation != null || mascot != null)
        EmptyState(
            title = title,
            body = body,
            illustration = if (showArt) {
                {
                    if (animation != null) {
                        LucentoLottieAnimation(animation, Modifier.size(width = 200.dp, height = 166.dp))
                    } else if (mascot != null) {
                        LucentoMascot(size = 104.dp, mood = mascot)
                    }
                }
            } else {
                null
            },
            action = if (actions != null) {
                {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) { actions() }
                }
            } else {
                null
            },
        )
    }
}

/** A short, honest confirmation shown after a write has been committed. */
@Immutable
data class Confirmation(
    val message: String,
    val kind: Kind = Kind.Saved,
    /** Distinguishes two identical messages in a row so both are shown. */
    val id: Long = System.nanoTime(),
) {
    enum class Kind { Saved, Milestone }
}

/**
 * Overlay that plays the "saved" check (or the calm milestone animation) with a one-line message, then
 * dismisses itself. Call [ConfirmationHost] once near the root and pass it the confirmation produced **after**
 * the database write succeeded — never before, never for a proposal or a cancelled edit.
 *
 * It does not take focus or block touches, it announces itself to screen readers, and with reduced motion
 * the finished check is shown still for the same duration, so a save never looks unfinished.
 */
@Composable
fun ConfirmationHost(
    confirmation: Confirmation?,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismiss by rememberUpdatedState(onDismissed)
    var visible by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf<Confirmation?>(null) }
    LaunchedEffect(confirmation?.id) {
        val c = confirmation ?: return@LaunchedEffect
        shown = c
        visible = true
        delay(if (c.kind == Confirmation.Kind.Milestone) 2_400L else 1_300L)
        visible = false
        delay(250L)
        dismiss()
    }
    val reduced = LucentoTheme.reducedMotion
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = visible,
            enter = if (reduced) fadeIn(tween(0)) else fadeIn(tween(Motion.fast)) + scaleIn(tween(Motion.standard), initialScale = 0.92f),
            exit = if (reduced) fadeOut(tween(0)) else fadeOut(tween(Motion.standard)) + scaleOut(tween(Motion.standard), targetScale = 0.96f),
        ) {
            val c = shown ?: return@AnimatedVisibility
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shadowElevation = Elevation.md,
                modifier = Modifier
                    .widthIn(min = 184.dp, max = 280.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Column(
                    Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val animation = if (c.kind == Confirmation.Kind.Milestone) LucentoAnimation.Milestone else LucentoAnimation.Saved
                    LucentoLottieAnimation(animation, Modifier.size(if (c.kind == Confirmation.Kind.Milestone) 112.dp else 72.dp))
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        c.message,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                }
            }
        }
    }
}

/**
 * Small inline indicator for genuine background work (reading a receipt, exporting). Uses the brand dots;
 * under reduced motion it becomes a still row of dots next to [label], which always says what is happening.
 */
@Composable
fun LucentoWorkingIndicator(label: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = Spacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
        LucentoLottieAnimation(LucentoAnimation.Loading, Modifier.size(width = 72.dp, height = 24.dp))
        Spacer(Modifier.height(Spacing.sm))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

/** Standard size for an empty-state illustration; pass to [EmptyState]'s `illustration`. */
@Composable
fun EmptyIllustration(animation: LucentoAnimation) {
    LucentoLottieAnimation(
        animation = animation,
        modifier = Modifier.size(width = 200.dp, height = 166.dp),
        fallback = { LucentoMascot(size = 96.dp, animate = false, mood = MascotMood.Curious) },
    )
}
