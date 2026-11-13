package com.lucento.app.feature.pro.welcome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Savings
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.domain.entitlement.ProTourStop

/** One line of the collection: a card's short name and what it gained ("No limit") or until when ("Until Nov 12, 2027"). */
internal data class CollectionItem(val stop: ProTourStop, val name: String, val tag: String?)

/**
 * The collection, after the last card or a skip: the plan's own words first (what is owned, and until when the online
 * services are included; the critic's round 2 wanted the terms in view, not below the list), then every card the deck
 * held. The button goes on to the award ([onDone]); the system Back key leaves the welcome (the host's).
 */
@Composable
internal fun ProCollection(
    eyebrow: String,
    title: String,
    plan: String,
    planDetail: String,
    /** Where the online services stand, while the server hasn't confirmed them (signed out, confirming). */
    status: String?,
    items: List<CollectionItem>,
    button: String,
    onDone: () -> Unit,
) {
    WelcomeStage(
        eyebrow = eyebrow,
        title = title,
        sub = null,
        pane = title,
        middle = {
            val scroll = rememberScrollState()
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                // A surface, not a painted box: its words take the theme's ink (the welcome sits outside the app's
                // surfaces, so a bare title fell back to black, 1.56:1 on dark navy: critic round 3).
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                ) {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        Text(
                            plan,
                            // Even lines at large text ("Pro Lifetime · you own it" left "it" alone on a line).
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, lineBreak = LineBreak.Heading),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(planDetail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        status?.let {
                            Spacer(Modifier.height(Spacing.xxs))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items.forEach { CollectionRow(it) }
                Spacer(Modifier.height(Spacing.lg))
            }
            if (scroll.canScrollForward) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.scrim.copy(alpha = WelcomeDim)))),
                )
            }
        },
        buttons = {
            LucentoPrimaryButton(text = button, onClick = onDone, modifier = Modifier.weight(1f))
        },
    )
}

/**
 * A card in the collection. The tag sits at the row's end while the name and the tag fit on one line, and moves under
 * the name when they don't (a date beside a long name on a narrow phone, or large text), so a name is never squeezed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollectionRow(item: CollectionItem) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
    ) {
        Row(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(item.stop.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(Spacing.md))
            FlowRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(end = Spacing.sm).align(Alignment.CenterVertically),
                )
                item.tag?.let { Tag(it, Modifier.align(Alignment.CenterVertically)) }
            }
        }
    }
}

@Composable
private fun Tag(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

/** Each card's icon in the collection (the same symbols the app uses for these features). */
internal val ProTourStop.icon: ImageVector
    get() = when (this) {
        ProTourStop.WALLETS -> Icons.Outlined.AccountBalanceWallet
        ProTourStop.NET_WORTH -> Icons.Outlined.Savings
        ProTourStop.SMART_READING -> Icons.Outlined.AutoAwesome
        ProTourStop.GOALS -> Icons.Outlined.Flag
        ProTourStop.INSIGHTS_PERIODS -> Icons.Outlined.Insights
        ProTourStop.STATEMENTS -> Icons.Outlined.PictureAsPdf
        ProTourStop.AI_ANSWERS -> Icons.Outlined.Forum
        ProTourStop.SYNC -> Icons.Outlined.Devices
        ProTourStop.SHARED -> Icons.Outlined.Group
        ProTourStop.THEMES -> Icons.Outlined.Palette
    }
