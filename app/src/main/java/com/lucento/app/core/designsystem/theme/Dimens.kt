package com.lucento.app.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** 4dp base grid. */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp

    /** Horizontal screen gutter. */
    val gutter = 20.dp

    /** Vertical rhythm between major sections of a screen. */
    val section = 28.dp

    /** Clearance under scrolling content so a floating action button never covers the last row. */
    val fabClearance = 96.dp
}

/** Fixed sizes shared by components. */
object Sizes {
    val minTouch = 48.dp
    val iconTile = 40.dp
    val iconTileLarge = 48.dp
    val icon = 20.dp
    val topBar = 64.dp
    val button = 52.dp
}

/*
 * Radius scale (dp): 8 badges · 12 inputs and icon tiles · 16 buttons, dropdowns, chips · 24 cards ·
 * 28 hero cards and sheets · full for circular controls and pills. Dense lists are never pill-shaped.
 * Lists are grouped by cards or hairlines, never both.
 */
object Radii {
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 28.dp
    /** Pill / circle. Coerced by the shape to half the shorter side. */
    val full = 999.dp
}

/** Transitional motion. Ambient loops (mascot float, torch breath) stay longer so they do not buzz. */
object Motion {
    const val fast = 100
    const val standard = 200
    const val slow = 300
}

val LucentoShapes = Shapes(
    extraSmall = RoundedCornerShape(Radii.xs),
    small = RoundedCornerShape(Radii.sm),
    medium = RoundedCornerShape(Radii.md),
    large = RoundedCornerShape(Radii.lg),
    extraLarge = RoundedCornerShape(Radii.xl),
)

/*
 * Elevation scale (navy-tinted, low opacity via [LucentoExtendedColors.shadow] — never a black shadow). Borders
 * separate most surfaces; shadows are reserved for floating elements. Night theme uses tonal surfaces instead.
 */
object Elevation {
    val xs = 1.dp // cards in the day theme (with a hairline)
    val sm = 2.dp // resting FAB
    val md = 4.dp // floating cards, menus, confirmation toast, pressed FAB
    val lg = 8.dp // dialogs and overlays
    val xl = 16.dp // major overlays, rarely
}
