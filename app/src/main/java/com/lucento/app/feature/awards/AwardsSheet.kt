package com.lucento.app.feature.awards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.component.LucentoBottomSheet
import com.lucento.app.core.designsystem.component.SheetHeader
import com.lucento.app.core.designsystem.component.StatusTones
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.domain.Achievement
import com.lucento.app.domain.Achievements
import com.lucento.app.domain.ProgressFacts

/**
 * The award cabinet: every award as a medal on a shelf, two to a shelf. Earned medals are struck gold and replay their
 * ceremony on tap ([onReplay]); the rest wait as outlines with how to earn them, and a count where the award takes
 * several steps. Worked out from the user's records — nothing to keep up and nothing to lose.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AwardsSheet(
    earned: Set<Achievement>,
    facts: ProgressFacts?,
    onDismiss: () -> Unit,
    onReplay: (Achievement) -> Unit,
) {
    LucentoBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = Spacing.gutter)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding() // the last shelf's words clear the navigation bar
                .padding(bottom = Spacing.lg),
        ) {
            SheetHeader(
                title = "Awards",
                subtitle = "${earned.size} of ${Achievement.entries.size} earned · from your own records. No streaks, nothing to lose.",
                icon = Icons.Filled.EmojiEvents,
                tone = StatusTones.warning,
                modifier = Modifier.padding(bottom = Spacing.md),
            )
            Achievement.entries.chunked(2).forEach { shelf ->
                Shelf(shelf, earned, facts, onReplay)
                Spacer(Modifier.height(Spacing.lg))
            }
        }
    }
}

private val CabinetMedal = 96.dp

@Composable
private fun Shelf(awards: List<Achievement>, earned: Set<Achievement>, facts: ProgressFacts?, onReplay: (Achievement) -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        // The shelf the medals stand on, just under the discs.
        Box(
            Modifier
                .padding(top = CabinetMedal * MedalAspect - 5.dp)
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            awards.forEach { a ->
                AwardCell(a, a in earned, facts?.let { Achievements.progress(a, it) }, onReplay, Modifier.weight(1f))
            }
            if (awards.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun AwardCell(
    award: Achievement,
    earned: Boolean,
    progress: com.lucento.app.domain.AwardProgress?,
    onReplay: (Achievement) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tap = if (earned) {
        Modifier.clickable(role = Role.Button, onClickLabel = "Replay the award") { onReplay(award) }
    } else {
        Modifier.semantics(mergeDescendants = true) {}
    }
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .then(tap)
            .heightIn(min = 48.dp)
            .padding(bottom = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AwardMedal(
            achievement = award,
            earned = earned,
            size = CabinetMedal,
            progress = progress?.takeIf { it.isCounted }?.fraction,
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            award.title,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Spacing.xs),
        )
        Spacer(Modifier.height(Spacing.xxs))
        if (earned) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text("Earned", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Text(
                if (progress?.isCounted == true) "${progress.current} of ${progress.target}" + if (award == Achievement.GETTING_CONSISTENT) " days" else "" else "Not yet",
                style = MaterialTheme.typography.labelMedium,
                color = if (progress?.isCounted == true && progress.current > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            if (earned) award.description else award.howTo,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Spacing.xs),
        )
    }
}
