package com.lucento.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.isDark
import com.lucento.app.data.preferences.ThemeMode
import com.lucento.app.navigation.LucentoRoot

class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels { AppViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Hold the system splash only until preferences are read (typically a few ms).
        splash.setKeepOnScreenCondition { appViewModel.uiState.value is AppUiState.Loading }
        enableEdgeToEdge()

        setContent {
            val state by appViewModel.uiState.collectAsStateWithLifecycle()
            val ready = state as? AppUiState.Ready
            val themeMode = ready?.preferences?.themeMode ?: ThemeMode.SYSTEM
            val dark = themeMode.isDark()

            // Keep status / navigation bar icons legible when the in-app theme overrides the system.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (dark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                )
                onDispose {}
            }

            LucentoTheme(darkTheme = dark) {
                if (ready != null) {
                    LucentoRoot(preferences = ready.preferences)
                }
            }
        }
    }
}
