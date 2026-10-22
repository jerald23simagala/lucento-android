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
 * Shapes are restrained: small radii for controls, a single larger radius for sheets and the hero.
 * Lists are not wrapped in cards; grouping comes from spacing and hairlines.
 */
val LucentoShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
