package com.lucento.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucento.app.core.designsystem.component.LaunchAnimation
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Motion
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

            // The rising logo plays once per launch (not on rotation) while the app loads underneath.
            var launching by rememberSaveable { mutableStateOf(true) }
            LucentoTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize()) {
                    if (ready != null) {
                        LucentoRoot(preferences = ready.preferences)
                    }
                    AnimatedVisibility(
                        visible = launching || ready == null,
                        exit = fadeOut(tween(if (LucentoTheme.reducedMotion) 0 else Motion.standard)),
                    ) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            LaunchAnimation(onFinished = { launching = false })
                        }
                    }
                }
            }
        }
    }
}
