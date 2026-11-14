package com.lucento.app.feature.capture

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.component.LucentoBottomSheet
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.SheetHeader
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing

/**
 * Lucento listening ([VoiceCapture]): a microphone that moves with the voice, the words as they are recognised, and
 * Done. It waits for people to think before they speak (VoiceTiming), so it says so. Dismissing it is Cancel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ListeningSheet(
    state: VoiceState,
    level: () -> Float,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val stopping = state.phase == VoicePhase.STOPPING
    val transcript = state.transcript
    LucentoBottomSheet(onDismissRequest = onCancel) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter)
                .padding(bottom = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SheetHeader(
                title = if (stopping) "Finishing…" else "Listening…",
                subtitle = when {
                    stopping -> "Catching your last words."
                    transcript.isEmpty() -> "Take your time. Try “Dinner 450, parking 90”."
                    else -> "Keep going, or tap Done when you’ve finished."
                },
            )
            MicPulse(level = level, moving = !stopping)
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
            ) {
                Text(
                    transcript.ifEmpty { "Your words appear here as you speak." },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (transcript.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(Spacing.lg),
                )
            }
            Text(
                if (state.offline) {
                    "Your words are turned into text on this phone."
                } else {
                    "Online voice is on: your phone’s speech service may send your words to Google to turn them into text."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            LucentoPrimaryButton(text = "Done", onClick = onDone, modifier = Modifier.fillMaxWidth())
            LucentoTextButton(text = "Cancel", onClick = onCancel)
        }
    }
}

/** The microphone, its halo growing with the voice. Still when the phone asks for reduced motion. */
@Composable
private fun MicPulse(level: () -> Float, moving: Boolean) {
    val still = LucentoTheme.reducedMotion || !moving
    val scale by animateFloatAsState(
        targetValue = if (still) 1f else 1f + 0.4f * level(),
        animationSpec = tween(durationMillis = 120),
        label = "voice level",
    )
    Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(88.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
        )
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
        }
    }
}
