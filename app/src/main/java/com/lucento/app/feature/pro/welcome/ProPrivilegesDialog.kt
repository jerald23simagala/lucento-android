package com.lucento.app.feature.pro.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.theme.Elevation
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.domain.entitlement.EntitlementState
import com.lucento.app.domain.entitlement.OnlineStatus
import com.lucento.app.domain.entitlement.PrivilegeGroup
import com.lucento.app.domain.entitlement.ProPrivilege
import com.lucento.app.domain.entitlement.ProWelcomeMode
import com.lucento.app.feature.pro.ProCopy

/**
 * The summary after the tour: congratulations (or welcome back), the plan's name, the higher limits on this phone, and
 * the online services, with where they stand with Lucento's server said first. Their allowances are listed only once the
 * server has confirmed the plan; until then they are one sentence and a plain list, so nothing reads as live early. Each
 * row is two lines (name, then "free → this plan"), so it holds at 360 dp and at large text. The button leads on (to the
 * award when there is one); Back returns to the tour ([onBack]); a tap beside the card does nothing.
 */
@Composable
internal fun ProPrivilegesDialog(
    mode: ProWelcomeMode,
    entitlements: EntitlementState,
    privileges: List<ProPrivilege>,
    onlineStatus: OnlineStatus,
    onDone: () -> Unit,
    onBack: () -> Unit,
    restored: Boolean = false,
    /** What the sections don't list but the plan includes, and that the tour can be watched again. */
    alsoYours: String? = null,
) {
    val plan = ProCopy.planStatus(entitlements, System.currentTimeMillis()).title
    val title = ProWelcomeCopy.summaryTitle(mode, restored)
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        // The same dim as the tour behind it, so the screen doesn't brighten between the moments.
        val window = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        val dim = tourScrimAlpha()
        androidx.compose.runtime.SideEffect { window?.setDimAmount(dim) }
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = surface,
            shadowElevation = Elevation.lg,
            modifier = Modifier
                .padding(horizontal = Spacing.gutter, vertical = Spacing.xl)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraLarge)
                .semantics { paneTitle = title },
        ) {
            Column {
                val scroll = rememberScrollState()
                Box(Modifier.weight(1f, fill = false)) {
                    Column(
                        Modifier
                            .verticalScroll(scroll)
                            .padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.xl),
                    ) {
                        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                        Spacer(Modifier.height(Spacing.xs))
                        Text(plan, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val limits = privileges.filter { it.group == PrivilegeGroup.LIMITS }
                        val online = privileges.filter { it.group == PrivilegeGroup.ONLINE }
                        if (limits.isNotEmpty()) {
                            SectionTitle(ProWelcomeCopy.LIMITS_TITLE)
                            PrivilegeRows(limits)
                        }
                        if (online.isNotEmpty()) {
                            SectionTitle(ProWelcomeCopy.ONLINE_TITLE)
                            val confirmed = onlineStatus == OnlineStatus.CONFIRMED || onlineStatus == OnlineStatus.NOT_IN_PLAN
                            // Where they stand comes first, so no row reads as live before it is.
                            ProWelcomeCopy.onlineStatus(onlineStatus)?.let { OnlineStatusLine(it, confirmed = onlineStatus == OnlineStatus.CONFIRMED) }
                            if (confirmed) PrivilegeRows(online) else OnlineNames(online)
                        }
                        alsoYours?.let {
                            Spacer(Modifier.height(Spacing.lg))
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(Spacing.lg))
                    }
                    // More below (beyond the bottom padding): the rows fade into the card above the button.
                    val fadeFrom = with(androidx.compose.ui.platform.LocalDensity.current) { 48.dp.toPx() }
                    if (scroll.maxValue - scroll.value > fadeFrom) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(32.dp)
                                .background(Brush.verticalGradient(listOf(Color.Transparent, surface))),
                        )
                    }
                }
                LucentoPrimaryButton(
                    text = ProWelcomeCopy.summaryButton(mode),
                    onClick = onDone,
                    modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.sm, bottom = Spacing.xl),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier
            .padding(top = Spacing.lg, bottom = Spacing.xs)
            .semantics { heading() },
    )
}

/** Each limit as two lines: its name (and what it is, where that needs saying), then "free → this plan". */
@Composable
private fun PrivilegeRows(rows: List<ProPrivilege>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        rows.forEach { row ->
            val name = ProWelcomeCopy.rowName(row.feature)
            val before = ProWelcomeCopy.limitText(row.feature, row.free)
            val after = ProWelcomeCopy.limitText(row.feature, row.plan)
            val detail = ProWelcomeCopy.rowDetail(row.feature)
            Column(
                Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        contentDescription = ProWelcomeCopy.spokenRow(name, before, after, confirmed = true) + (detail?.let { ". $it" } ?: "")
                    },
            ) {
                Text(name, style = MaterialTheme.typography.bodyMedium)
                detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        ProWelcomeCopy.freeShown(row.feature, row.free),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = Spacing.xs)
                            .size(14.dp)
                            .clearAndSetSemantics {},
                    )
                    Text(
                        after,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** The online services, while the server hasn't confirmed them: a plain list of names, no allowances. */
@Composable
private fun OnlineNames(rows: List<ProPrivilege>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        rows.forEach { row ->
            val detail = ProWelcomeCopy.rowDetail(row.feature)
            Row(verticalAlignment = Alignment.Top) {
                // A quiet dot, not a spoken bullet.
                Box(
                    Modifier
                        .padding(top = 8.dp, end = Spacing.sm)
                        .size(5.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant),
                )
                Column {
                    Text(ProWelcomeCopy.rowName(row.feature), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun OnlineStatusLine(text: String, confirmed: Boolean) {
    Row(Modifier.padding(bottom = Spacing.sm), verticalAlignment = Alignment.Top) {
        Icon(
            if (confirmed) Icons.Outlined.CloudDone else Icons.Outlined.Info,
            contentDescription = null,
            tint = if (confirmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp).size(16.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.xs),
        )
    }
}
