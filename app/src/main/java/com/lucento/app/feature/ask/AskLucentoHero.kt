package com.lucento.app.feature.ask

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.component.LucentoMascot
import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing

/** The question the hero types out, as the owner asked (2026-11-03). */
const val ASK_HERO_QUESTION = "What’s my top 5 spending categories?"

/**
 * Insights' entrance to Ask Lucento: the mascot beside "Ask Lucento", and a chat box showing a question, which clears and
 * types itself out again letter by letter, over and over. It runs on an infinite transition, so tests (and reduced
 * motion) see the whole question standing still. The whole card is one button; TalkBack reads it once, not each letter.
 */
@Composable
fun AskLucentoHero(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val ext = LucentoTheme.extendedColors
    val haptics = rememberHaptics()
    val reduced = LucentoTheme.reducedMotion
    val transition = rememberInfiniteTransition(label = "ask-hero")
    // One cycle: the question held in full, a beat empty, then typed out (0 = the whole question, as at rest).
    val cycle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(TYPING_CYCLE_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "typing",
    )
    val typed = if (reduced) ASK_HERO_QUESTION.length else typedLetters(cycle, ASK_HERO_QUESTION.length)
    val cursor = transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(530), RepeatMode.Reverse),
        label = "cursor-blink",
    )
    val shape = RoundedCornerShape(24.dp)
    Surface(
        onClick = {
            haptics.tick()
            onOpen()
        },
        shape = shape,
        color = colors.primaryContainer,
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = "Ask Lucento, your money companion. For example: $ASK_HERO_QUESTION"
                onClick(label = "Open Ask Lucento") { onOpen(); true }
            },
    ) {
        Box(
            Modifier.background(
                Brush.linearGradient(listOf(colors.primaryContainer, colors.surfaceContainerLowest.copy(alpha = if (LucentoTheme.isDark) 0.12f else 0.9f))),
            ),
        ) {
            Column(Modifier.padding(Spacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LucentoMascot(size = 64.dp, mood = MascotMood.Greeting)
                    Spacer(Modifier.width(Spacing.sm))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Ask Lucento", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = colors.onSurface)
                            Spacer(Modifier.width(Spacing.xs))
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = ext.accent, modifier = Modifier.size(18.dp))
                        }
                        Text(
                            "Your money questions, answered from your records. Reports as PDFs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                // The chat box, typing.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (LucentoTheme.isDark) colors.surfaceContainerHigh else colors.surfaceContainerLowest)
                        .padding(start = Spacing.md, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            ASK_HERO_QUESTION.take(typed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Box(
                            Modifier
                                .padding(start = 1.dp)
                                .width(2.dp)
                                .height(18.dp)
                                .alpha(if (reduced) 1f else cursor.value)
                                .background(colors.primary),
                        )
                    }
                    Spacer(Modifier.width(Spacing.sm))
                    Box(Modifier.size(36.dp).clip(CircleShape).background(colors.primary), contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

private const val TYPING_CYCLE_MS = 4_200
private const val HOLD = 0.38f
private const val BLANK = 0.43f

/** Letters shown at [phase] of the typing cycle: all of them while held, none for a beat, then one by one. */
internal fun typedLetters(phase: Float, length: Int): Int = when {
    phase < HOLD -> length
    phase < BLANK -> 0
    else -> ((phase - BLANK) / (1f - BLANK) * length).toInt().coerceIn(0, length)
}
