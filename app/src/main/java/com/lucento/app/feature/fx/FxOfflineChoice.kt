package com.lucento.app.feature.fx

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.domain.fx.FxSource

/**
 * No online rate for a movement's day (offline, or "Exchange rates online" off): the person picks how it's valued in the
 * main currency (owner, 2026-11-05) — the wallet's last rate (a gain or loss then counts against its average) or its
 * average rate (no gain or loss). Every row's "≈" line shows the result.
 */
@Composable
fun FxOfflineChoice(choice: FxSource, enabled: Boolean, onChoose: (FxSource) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            "No online rate for some amounts in other currencies. Value them at:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            listOf(FxSource.LAST_RATE to "Last rate", FxSource.AVERAGE to "Average rate").forEach { (source, label) ->
                FilterChip(
                    selected = choice == source,
                    onClick = { onChoose(source) },
                    enabled = enabled,
                    label = { Text(label) },
                    modifier = Modifier.heightIn(min = Sizes.minTouch).semantics { role = Role.RadioButton },
                )
            }
        }
    }
}
