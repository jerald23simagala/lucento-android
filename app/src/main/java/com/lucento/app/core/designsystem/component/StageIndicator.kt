package com.lucento.app.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.domain.SetupProgress
import com.lucento.app.domain.SetupStage
import com.lucento.app.domain.StageStatus

/** What each stage is about, drawn in its circle until the stage is done: shaping Lucento, accounts, then using it. */
val SetupStage.icon: ImageVector
    get() = when (this) {
        SetupStage.PERSONALIZE -> Icons.Outlined.Tune
        SetupStage.SET_UP -> Icons.Outlined.AccountBalanceWallet
        SetupStage.GET_STARTED -> Icons.Outlined.AutoAwesome
    }

private val MarkSize = 26.dp
private val MarkIcon = 15.dp
private val TrackHeight = 3.dp
private val ChipStart = 4.dp
private val ChipGap = 6.dp
private val ChipEnd = 10.dp
private val MinConnector = 14.dp

/**
 * Where a new user is in getting started, as one connected stepper: Personalize · Set up · Get started, each with its own
 * icon. Done stages are solid blue with a white check (the icon turns into the check), the current one sits on a
 * pale-sky pill with its icon on blue, later ones are muted grey. The line between two stages fills as the person moves
 * through the first. Stages come from the flow's state ([SetupProgress]), so going back never un-completes one.
 *
 * Labels sit beside their circles when all three fit, under them when they don't (narrow phones, large text), and give
 * way to one caption under the circles when even that is too tight. TalkBack hears one sentence for the whole stepper.
 */
@Composable
fun StageIndicator(progress: SetupProgress, modifier: Modifier = Modifier) {
    val labelStyle = MaterialTheme.typography.labelLarge
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm, bottom = Spacing.lg)
            .clearAndSetSemantics { contentDescription = progress.description },
    ) {
        // Measured bold (the widest a label gets), so the layout never changes as the active stage moves.
        val labels = SetupStage.entries.map { stage ->
            with(density) { measurer.measure(stage.label, labelStyle.copy(fontWeight = FontWeight.Bold)).size.width.toDp() }
        }
        val inline = labels.fold(0.dp) { sum, w -> sum + w } + (ChipStart + MarkSize + ChipGap + ChipEnd) * 3 + MinConnector * 2
        val column = maxWidth / SetupStage.entries.size
        when {
            maxWidth >= inline -> InlineStepper(progress)
            labels.all { it <= column - Spacing.xs } -> StackedStepper(progress, showLabels = true)
            else -> Column {
                StackedStepper(progress, showLabels = false)
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "${progress.current.label} · step ${progress.current.ordinal + 1} of ${SetupStage.entries.size}",
                    style = labelStyle.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Circle and label side by side for each stage, joined by equal connecting lines (the reference layout). */
@Composable
private fun InlineStepper(progress: SetupProgress) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SetupStage.entries.forEach { stage ->
            StageChip(stage, progress.status(stage), progress.isDone(stage))
            if (stage != SetupStage.entries.last()) {
                Connector(progress.connectorFill(stage), Modifier.weight(1f).padding(horizontal = Spacing.xxs))
            }
        }
    }
}

/**
 * Three equal columns, each a circle on the line with its label beneath; the line between two circles is drawn as the
 * right half of one column and the left half of the next, filled in order.
 */
@Composable
private fun StackedStepper(progress: SetupProgress, showLabels: Boolean) {
    val stages = SetupStage.entries
    Row(Modifier.fillMaxWidth()) {
        stages.forEachIndexed { index, stage ->
            val status = progress.status(stage)
            val before = stages.getOrNull(index - 1)?.let { progress.connectorFill(it) }
            val after = if (index < stages.lastIndex) progress.connectorFill(stage) else null
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth().height(MarkSize + 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // A line's first half fills before its second, so the two halves read as one line filling left to right.
                    if (before != null) Connector(((before * 2f) - 1f).coerceIn(0f, 1f), Modifier.weight(1f), round = false)
                    else Spacer(Modifier.weight(1f))
                    StageMark(stage, status, progress.isDone(stage))
                    if (after != null) Connector((after * 2f).coerceIn(0f, 1f), Modifier.weight(1f), round = false)
                    else Spacer(Modifier.weight(1f))
                }
                if (showLabels) {
                    Spacer(Modifier.height(Spacing.xxs))
                    StageLabel(stage, status, Modifier.padding(horizontal = Spacing.xxs))
                }
            }
        }
    }
}

/** The current stage sits on a pale-sky pill; the others keep the same padding, so nothing shifts as it moves. */
@Composable
private fun StageChip(stage: SetupStage, status: StageStatus, done: Boolean) {
    val pill by animateColorAsState(
        if (status == StageStatus.ACTIVE) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        stageMotion(),
        label = "stage-pill",
    )
    Row(
        Modifier
            .background(pill, RoundedCornerShape(50))
            .padding(start = ChipStart, end = ChipEnd, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StageMark(stage, status, done)
        Spacer(Modifier.width(ChipGap))
        StageLabel(stage, status)
    }
}

@Composable
private fun StageLabel(stage: SetupStage, status: StageStatus, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val ink by animateColorAsState(if (status == StageStatus.UPCOMING) colors.onSurfaceVariant else colors.onSurface, stageMotion(), label = "stage-ink")
    Text(
        stage.label,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (status == StageStatus.ACTIVE) FontWeight.Bold else FontWeight.Medium),
        color = ink,
        maxLines = 1,
        softWrap = false,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

/**
 * A stage's circle: solid blue for done and current stages, pale sky for one reached earlier, grey ahead. Its icon turns
 * into a check once the stage is done; the current one carries a soft halo.
 */
@Composable
private fun StageMark(stage: SetupStage, status: StageStatus, done: Boolean) {
    val colors = MaterialTheme.colorScheme
    val reducedMotion = LucentoTheme.reducedMotion
    val container by animateColorAsState(
        when (status) {
            StageStatus.COMPLETED, StageStatus.ACTIVE -> colors.primary
            StageStatus.REACHED -> colors.primaryContainer
            StageStatus.UPCOMING -> colors.surfaceContainerHigh
        },
        stageMotion(),
        label = "stage-mark",
    )
    val content by animateColorAsState(
        when (status) {
            StageStatus.COMPLETED, StageStatus.ACTIVE -> colors.onPrimary
            StageStatus.REACHED -> colors.primary
            StageStatus.UPCOMING -> colors.onSurfaceVariant
        },
        stageMotion(),
        label = "stage-glyph",
    )
    val halo by animateFloatAsState(
        if (status == StageStatus.ACTIVE) 1f else 0f,
        if (reducedMotion) tween(0) else tween(300, easing = FastOutSlowInEasing),
        label = "stage-halo",
    )
    val haloColor = colors.primary
    Box(
        Modifier
            .size(MarkSize)
            .drawBehind {
                if (halo > 0f) drawCircle(haloColor.copy(alpha = 0.18f * halo), radius = size.minDimension / 2f + 3.dp.toPx() * halo)
            }
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = done,
            transitionSpec = {
                if (reducedMotion) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                else (fadeIn(tween(280)) + scaleIn(tween(280), initialScale = 0.5f)) togetherWith fadeOut(tween(140))
            },
            label = "stage-check",
        ) { isDone ->
            Icon(
                if (isDone) Icons.Rounded.Check else stage.icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(MarkIcon),
            )
        }
    }
}

/** The line between two stages: a 3dp grey track that fills blue as the person moves through the first stage. */
@Composable
private fun Connector(fill: Float, modifier: Modifier, round: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    val animated by animateFloatAsState(
        fill.coerceIn(0f, 1f),
        if (LucentoTheme.reducedMotion) tween(0) else tween(320, easing = FastOutSlowInEasing),
        label = "stage-line",
    )
    val shape = if (round) RoundedCornerShape(50) else RoundedCornerShape(0.dp)
    Box(modifier.height(TrackHeight).clip(shape).background(colors.outlineVariant)) {
        Box(Modifier.fillMaxWidth(animated).fillMaxHeight().background(colors.primary))
    }
}

@Composable
private fun stageMotion() = if (LucentoTheme.reducedMotion) tween<Color>(0) else tween(300)
