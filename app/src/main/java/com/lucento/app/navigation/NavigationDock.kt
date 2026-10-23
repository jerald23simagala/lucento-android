package com.lucento.app.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.LucentoTheme

/** One destination in the dock. */
data class DockItem(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

/**
 * Lucento's persistent navigation dock: the five primary destinations and a "More" menu button.
 *
 * The selected destination shows its label under a tinted indicator; the others are icon-only (each still has its
 * label as the accessibility name), which keeps the dock calm and legible on narrow phones. Every item is a
 * selectable tab with a 48dp+ touch target. "More" opens a menu rather than a destination, so it is never selected.
 */
@Composable
fun NavigationDock(
    items: List<DockItem>,
    selectedKey: String?,
    onSelect: (DockItem) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val container = if (LucentoTheme.isDark) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerLowest
    Surface(modifier = modifier.fillMaxWidth(), color = container) {
        Column(Modifier.navigationBarsPadding()) {
            Hairline()
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .padding(horizontal = 4.dp)
                    .selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    DockTab(
                        item = item,
                        selected = item.key == selectedKey,
                        onClick = {
                            if (item.key != selectedKey) haptics.tick()
                            onSelect(item)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                Box(
                    Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                Box(
                    Modifier
                        .width(56.dp)
                        .fillMaxHeight()
                        .clip(MaterialTheme.shapes.medium)
                        .selectable(selected = false, role = Role.Button, onClick = {
                            haptics.tick()
                            onMore()
                        })
                        .semantics { contentDescription = "More: goals, recurring payments, debts" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.MoreHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DockTab(item: DockItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val indicator by animateColorAsState(
        if (selected) colors.primaryContainer else colors.primaryContainer.copy(alpha = 0f),
        tween(180),
        label = "dock-indicator",
    )
    val tint by animateColorAsState(
        if (selected) colors.primary else colors.onSurfaceVariant,
        tween(180),
        label = "dock-tint",
    )
    // Not clipped, so a long selected label ("Transactions") can use a little of its neighbours' empty label space.
    Column(
        modifier
            .fillMaxHeight()
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = item.label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .width(52.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(indicator),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (selected) item.selectedIcon else item.icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }
        if (selected) {
            Text(
                item.label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.primary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
                // Measured at its natural width and centred, so it may extend slightly past a narrow tab.
                modifier = Modifier.padding(top = 4.dp).wrapContentWidth(unbounded = true),
            )
        }
    }
}
