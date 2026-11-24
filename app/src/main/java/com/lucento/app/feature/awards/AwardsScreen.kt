package com.lucento.app.feature.awards

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.ProfileButton
import com.lucento.app.core.designsystem.component.screenGutter
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.domain.Achievement
import com.lucento.app.domain.BudgetMilestones
import com.lucento.app.feature.home.monthLabel
import com.lucento.app.feature.progress.ProgressViewModel
import java.time.YearMonth

/**
 * Awards (owner, 2026-11-23: "an Awards button on the Milestone page that opens the awards only page"): the medal
 * shelves and the Pro medal, every award worked out from the person's own records — nothing to keep up and nothing to
 * lose. Opened from the Awards button at the top of Milestones and from an award ceremony's "See all awards". Earned
 * medals replay their ceremony on tap, as they did on the page before.
 */
@Composable
fun AwardsScreen(
    onBack: () -> Unit,
    viewModel: ProgressViewModel = viewModel(factory = ProgressViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val current = YearMonth.from(state.today)
    var replaying by remember { mutableStateOf<Achievement?>(null) }
    var replayingPro by remember { mutableStateOf(false) }
    // Steady Month counts whole months only: say which month can be the first (critic r2, P1-3).
    val firstWhole = remember(state.inputs, state.primaryCurrency) {
        state.inputs?.let { BudgetMilestones.firstWholeMonth(it.budgets, state.primaryCurrency) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = screenGutter()),
    ) {
        LucentoTopBar(title = "Awards", onBack = onBack, actions = { ProfileButton() })
        Text(
            (if (state.loading) "" else "${state.earned.size} of ${Achievement.entries.size} earned · ") +
                "From your own records. No streaks, nothing to lose.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!state.loading) {
            Spacer(Modifier.height(Spacing.md))
            AwardCabinet(
                earned = state.earned,
                facts = state.facts,
                onReplay = { replaying = it },
                proMemberSince = state.proMemberSince,
                onReplayPro = { replayingPro = true },
                notes = buildMap {
                    if (firstWhole != null && firstWhole >= current) put(Achievement.STEADY_MONTH, "Your first whole month is ${monthLabel(firstWhole)}.")
                },
            )
        }
        Spacer(Modifier.height(Spacing.fabClearance))
    }

    replaying?.let { award ->
        AwardCeremony(award, earnedCount = state.earned.size, onDismiss = { replaying = null })
    }
    if (replayingPro) {
        ProMedalCeremony(
            onDismiss = { replayingPro = false },
            replay = true,
            memberSince = state.proMemberSince?.let { memberSinceLabel(it) },
        )
    }
}
