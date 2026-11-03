package com.lucento.app.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.domain.SetupStage

/**
 * Where a new user is in getting started: three named stages (Personalize · Set up · Get started) instead of a count of
 * screens. Finished stages are full, the current one fills as its screens go by, later ones are empty. Read by
 * TalkBack as one line ("Stage 1 of 3, Personalize").
 */
@Composable
fun StageIndicator(stage: SetupStage, progress: Float, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val current by animateFloatAsState(progress.coerceIn(0f, 1f), tween(320), label = "stage-progress")
    Row(
        modifier
            .padding(top = Spacing.sm, bottom = Spacing.lg)
            .clearAndSetSemantics { contentDescription = "Stage ${stage.ordinal + 1} of ${SetupStage.entries.size}, ${stage.label}" },
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SetupStage.entries.forEach { s ->
            val fill = when {
                s.ordinal < stage.ordinal -> 1f
                s == stage -> current
                else -> 0f
            }
            Column(Modifier.weight(1f)) {
                Text(
                    s.label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (s == stage) FontWeight.Bold else FontWeight.Medium),
                    color = if (s.ordinal <= stage.ordinal) colors.onSurface else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Spacing.xxs + 2.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.outlineVariant),
                ) {
                    Box(Modifier.fillMaxWidth(fill).fillMaxHeight().background(colors.primary))
                }
            }
        }
    }
}
