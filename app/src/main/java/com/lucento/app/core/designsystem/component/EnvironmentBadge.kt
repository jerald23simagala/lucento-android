package com.lucento.app.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucento.app.BuildConfig
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing

/**
 * Marks debug builds as the TEST environment (they sync with `lucento-test`, see docs/SYNC.md), so a test session is
 * never mistaken for real data. Drawn once, over the whole app, in the system navigation-bar area: no screen has
 * content there (the dock sits above it) and the gesture handle or 3-button bar is centred, so it neither covers nor
 * moves anything. Release builds draw nothing. It shows no URL, project id or key. It is not interactive and does not
 * take touches; a screen reader announces it once as a plain description.
 */
@Composable
fun EnvironmentBadge(modifier: Modifier = Modifier) {
    if (!BuildConfig.DEBUG) return
    val colors = LucentoTheme.extendedColors
    // Exactly the navigation-bar height, so the pill is centred in that strip whatever the device's inset.
    val inset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        modifier = modifier.fillMaxWidth().height(maxOf(inset, MIN_STRIP)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(start = Spacing.gutter)
                .background(colors.warningContainer, CircleShape)
                .padding(horizontal = Spacing.sm, vertical = 1.dp)
                .semantics { contentDescription = "Test environment build" },
        ) {
            Text(
                text = "TEST",
                modifier = Modifier.clearAndSetSemantics {},
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                color = colors.warning,
            )
        }
    }
}

private val MIN_STRIP = 18.dp
