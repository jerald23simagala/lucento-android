package com.lucento.app.core.designsystem.component

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Lets a screen hide the navigation dock while a mode of its own needs the bottom of the screen: Scheduled’s Select
 * mode puts its actions there (design review, 2026-11-19: two stacked bars took 39 % of the screen at 200 % text).
 * The app root provides it; anywhere else it does nothing. A screen that hides the dock shows it again when it leaves.
 */
val LocalHideDock = staticCompositionLocalOf<(Boolean) -> Unit> { {} }
