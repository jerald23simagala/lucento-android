package com.lucento.app.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.FontScaleCaps
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Motion
import com.lucento.app.core.designsystem.theme.Radii
import com.lucento.app.core.designsystem.theme.WithFontScaleCap

/** One destination in the dock. */
data class DockItem(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

/**
 * Lucento's persistent navigation dock: the primary destinations either side of the central **+ Add transaction**
 * button (Home · Transactions · + · Insights · Wallets).
 *
 * The dock floats with rounded corners; the + is a raised brand-blue circle that breaks its top edge, so transaction
 * entry reads as the one central action rather than another destination. Every tab shows its label (the selected one
 * in brand blue under a tinted indicator), is a selectable tab, and has a 48dp+ touch target.
 */
@Composable
fun NavigationDock(
    items: List<DockItem>,
    selectedKey: String?,
    onSelect: (DockItem) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val container = if (LucentoTheme.isDark) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerLowest
    val shape = RoundedCornerShape(Radii.lg)
    // Destinations split evenly around the + (the first half on the left).
    val leading = items.take((items.size + 1) / 2)
    val trailing = items.drop(leading.size)
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AddRise)
                .shadow(
                    elevation = if (LucentoTheme.isDark) 0.dp else 6.dp,
                    shape = shape,
                    ambientColor = LucentoTheme.extendedColors.shadow.copy(alpha = 0.10f),
                    spotColor = LucentoTheme.extendedColors.shadow.copy(alpha = 0.16f),
                ),
            shape = shape,
            color = container,
            border = BorderStroke(1.dp, LucentoTheme.extendedColors.borderDefault),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 4.dp)
                    .selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                fun select(item: DockItem) {
                    if (item.key != selectedKey) haptics.tick()
                    onSelect(item)
                }
                leading.forEach { item ->
                    DockTab(item, selected = item.key == selectedKey, onClick = { select(item) }, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.width(AddSize + 16.dp))
                trailing.forEach { item ->
                    DockTab(item, selected = item.key == selectedKey, onClick = { select(item) }, modifier = Modifier.weight(1f))
                }
            }
        }
        AddButton(onClick = onAdd, modifier = Modifier.align(Alignment.TopCenter))
    }
}

private val AddSize = 60.dp
/** How far the + rises above the dock's top edge. */
private val AddRise = 14.dp

/** The raised central + with a soft brand-tinted shadow. */
@Composable
private fun AddButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (primary, onPrimary) = com.lucento.app.core.designsystem.component.addActionColors()
    Box(
        modifier
            .size(AddSize)
            .shadow(elevation = 10.dp, shape = CircleShape, ambientColor = primary.copy(alpha = 0.35f), spotColor = primary.copy(alpha = 0.6f))
            .clip(CircleShape)
            .background(primary)
            .selectable(selected = false, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Add transaction" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = onPrimary, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun DockTab(item: DockItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val indicator by animateColorAsState(
        if (selected) colors.primaryContainer else colors.primaryContainer.copy(alpha = 0f),
        tween(if (LucentoTheme.reducedMotion) 0 else Motion.standard),
        label = "dock-indicator",
    )
    val tint by animateColorAsState(
        if (selected) colors.primary else colors.onSurfaceVariant,
        tween(if (LucentoTheme.reducedMotion) 0 else Motion.standard),
        label = "dock-tint",
    )
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
                .width(48.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(Radii.full))
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
        DockLabel(item.label, tint)
    }
}

/**
 * Dock labels stay inside their tab. Type is capped at 1.3× (so 2.0× system type does not overflow the 64dp bar)
 * and then shrinks to the tab width, so neighbours never overlap and the word is not clipped.
 */
@Composable
private fun DockLabel(text: String, color: Color) {
    WithFontScaleCap(FontScaleCaps.Navigation) {
        val style = MaterialTheme.typography.labelSmall
        var scale by remember(text) { mutableFloatStateOf(1f) }
        var ready by remember(text) { mutableStateOf(false) }
        val scaled = if (scale == 1f) {
            style
        } else {
            style.copy(fontSize = style.fontSize * scale, lineHeight = style.lineHeight * scale)
        }
        Text(
            text,
            style = scaled,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                .drawWithContent { if (ready) drawContent() },
            onTextLayout = { result ->
                if (result.hasVisualOverflow && scale > 0.55f) {
                    scale = (scale - 0.05f).coerceAtLeast(0.55f)
                } else {
                    ready = true
                }
            },
        )
    }
}
