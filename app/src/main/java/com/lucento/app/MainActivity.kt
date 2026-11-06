package com.lucento.app

import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Toast
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import com.lucento.app.core.banks.InstitutionRegistry
import com.lucento.app.core.designsystem.component.LocalInstitutions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.lucento.app.core.security.AppLockSession
import com.lucento.app.core.security.DeviceAuth
import com.lucento.app.feature.lock.LockScreen
import kotlinx.coroutines.launch
import com.lucento.app.core.designsystem.component.EnvironmentBadge
import com.lucento.app.core.designsystem.component.LaunchAnimation
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.ThemeSpec
import com.lucento.app.core.designsystem.theme.isDark
import com.lucento.app.data.preferences.ThemeId
import com.lucento.app.data.preferences.ThemeMode
import com.lucento.app.navigation.LucentoRoot

// A FragmentActivity (still a ComponentActivity) because the app lock's BiometricPrompt needs one.
class MainActivity : FragmentActivity() {

    private val appViewModel: AppViewModel by viewModels { AppViewModel.Factory }
    private val lock by lazy { (application as LucentoApplication).container.appLock }

    override fun onStart() {
        super.onStart()
        lock.onForeground(SystemClock.elapsedRealtime())
        // Opening Lucento (or coming back to it) brings in changes from other devices and sends any waiting here.
        val container = (application as LucentoApplication).container
        container.inForeground.set(true)
        container.foregroundSync.request(forced = false)
    }

    override fun onStop() {
        super.onStop()
        (application as LucentoApplication).container.inForeground.set(false)
        // A rotation is not leaving the app.
        if (!isChangingConfigurations) lock.onBackground(SystemClock.elapsedRealtime())
    }

    /** Opens the system prompt; when the phone no longer has any screen lock, the app lock turns itself off. */
    private fun requestUnlock() {
        lock.beginAuthentication()
        DeviceAuth.authenticate(this, title = "Unlock Lucento", subtitle = "Use your fingerprint, face or screen lock") { outcome ->
            when (outcome) {
                DeviceAuth.Outcome.SUCCESS -> lock.unlock()
                DeviceAuth.Outcome.CANCELLED -> lock.endAuthentication()
                DeviceAuth.Outcome.UNAVAILABLE -> {
                    // Removing the phone's screen lock already required it, so the owner has proved who they are; keeping
                    // the lock on would shut them out of their own data.
                    lock.unlock()
                    lifecycleScope.launch { (application as LucentoApplication).container.preferences.setAppLock(false) }
                    Toast.makeText(this, "App lock is off because this phone no longer has a screen lock.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * P2-10: a blank preview in recent apps. FLAG_SECURE on every version: setRecentsScreenshotEnabled (Android 13+) only
     * skips the stored snapshot, and the launcher's live tile of the last app still showed balances (device-verified).
     */
    private fun applyHideInRecents(hide: Boolean) {
        if (hide) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

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
            val themeId = ready?.preferences?.themeId ?: ThemeId.SUNRISE
            // A dark-only theme (Midnight) is dark whatever the mode, and the system bars follow it.
            val dark = ThemeSpec.of(themeId).isDark(themeMode.isDark())

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

            val hideInRecents = ready?.preferences?.hideInRecents ?: false
            LaunchedEffect(hideInRecents) { applyHideInRecents(hideInRecents) }
            val unlocked by lock.unlocked.collectAsStateWithLifecycle()
            val locked = AppLockSession.isLocked(ready?.preferences?.appLock ?: false, unlocked)

            // The rising logo plays once per launch (not on rotation) while the app loads underneath.
            var launching by rememberSaveable { mutableStateOf(true) }
            val institutions by produceState(InstitutionRegistry.EMPTY) {
                value = (application as LucentoApplication).container.institutions.registry()
            }
            // The saved theme arrives with the preferences, during the launch animation: shown at once, not faded in.
            LucentoTheme(darkTheme = dark, theme = themeId, animateChanges = ready != null && !launching) {
                Box(Modifier.fillMaxSize()) {
                    if (ready != null) {
                        // Kept composed while locked so nothing in progress is lost, but hidden from TalkBack.
                        Box(if (locked) Modifier.fillMaxSize().clearAndSetSemantics {} else Modifier.fillMaxSize()) {
                            // Hidden amounts apply on every page that shows money (owner, 2026-11-04, round 7).
                            CompositionLocalProvider(
                                LocalInstitutions provides institutions,
                                com.lucento.app.core.designsystem.component.LocalAmountsHidden provides ready.preferences.hideAmounts,
                                // Multicurrency: the main currency and the day's rates for every screen that records money.
                                com.lucento.app.feature.fx.LocalMainCurrency provides ready.preferences.primaryCurrency,
                                com.lucento.app.feature.fx.LocalFxRates provides (application as LucentoApplication).container.fxRates,
                            ) {
                                LucentoRoot(preferences = ready.preferences)
                            }
                        }
                        if (locked) LockScreen(onUnlock = ::requestUnlock)
                        // Debug builds only (draws nothing in release): marks the test environment on every screen.
                        EnvironmentBadge(Modifier.align(Alignment.BottomStart))
                    }
                    AnimatedVisibility(visible = launching || ready == null, exit = fadeOut(tween(220))) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            LaunchAnimation(onFinished = { launching = false })
                        }
                    }
                }
            }
        }
    }
}
