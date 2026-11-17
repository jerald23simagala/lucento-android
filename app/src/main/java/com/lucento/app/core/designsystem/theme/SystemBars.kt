package com.lucento.app.core.designsystem.theme

import android.content.res.Configuration
import android.os.Build
import android.view.Window
import androidx.core.view.WindowCompat

/**
 * Draws Lucento behind the status and navigation bars (edge to edge) on every supported Android version, and sets the
 * bars' icons for [darkTheme].
 *
 * This replaces androidx.activity's `enableEdgeToEdge()`. That function, up to activity 1.13 at least, still calls
 * `Window.setStatusBarColor`/`setNavigationBarColor` and writes `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`, all
 * deprecated in Android 15, and Google Play Console flags them in every release that contains them. Nothing here
 * touches those APIs:
 * - Both bars are transparent through the theme (`android:statusBarColor`/`navigationBarColor` in `values/themes.xml`).
 *   Android 15+ ignores those and always draws the bars transparent for apps targeting SDK 35+.
 * - The window uses the display cutout area through the theme as well: `shortEdges` on Android 9–10 (`values-v28`) and
 *   `always` from Android 11 (`values-v30`), which is what Android 15+ enforces anyway.
 * - Here: the window lays out behind the bars before Android 15 (from 15 that's automatic), 3-button navigation gets no
 *   contrast scrim (screens pad their own content clear of the bar), and the icons turn dark on light themes.
 *
 * Safe to call again whenever the in-app theme turns light or dark.
 */
fun Window.drawEdgeToEdge(darkTheme: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        WindowCompat.setDecorFitsSystemWindows(this, false)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // Not deprecated: the status bar's counterpart is, and it is already off by default.
        isNavigationBarContrastEnforced = false
    }
    WindowCompat.getInsetsController(this, decorView).apply {
        isAppearanceLightStatusBars = !darkTheme
        isAppearanceLightNavigationBars = !darkTheme
    }
}

/** Whether the phone itself is in dark mode, for the bars' icons before the saved in-app theme has loaded. */
val Configuration.isSystemDark: Boolean
    get() = (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
