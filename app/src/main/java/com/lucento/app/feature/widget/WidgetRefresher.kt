package com.lucento.app.feature.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentCallbacks
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lucento.app.LucentoApplication
import com.lucento.app.data.preferences.ThemeId
import com.lucento.app.data.preferences.ThemeMode
import com.lucento.app.data.preferences.UserPreferences
import com.lucento.app.domain.FeatureSet
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Keeps the widgets current (docs/WIDGETS.md): after every change to the tables they read (a save, an edit, a delete,
 * a sync pull — a Shared Space partner's entries too), after a change to the preferences they show, and just after
 * midnight ([WidgetMidnightWorker]), when "days left" and "a day" turn over. Room's change signal is used, so nothing is
 * read until a widget actually redraws, and a redraw with no widget placed costs nothing.
 */
object WidgetRefresher {
    private const val DEBOUNCE_MS = 750L

    /** Counts the reasons to redraw. A widget whose session is still open reloads on each one ([frames]). */
    private val generation = MutableStateFlow(0L)

    @OptIn(FlowPreview::class)
    fun start(app: LucentoApplication) {
        val container = app.container
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            merge(
                container.ledgerChanges(),
                container.preferences.preferences.map { WidgetPreferences(it) }.distinctUntilChanged().drop(1).map { },
            ).debounce(DEBOUNCE_MS).collect { refresh(app) }
        }
        // A widget's layout is chosen when it redraws: after the phone's text size or dark mode changes, the launcher
        // can't pick a new one on its own. Redraw whenever Lucento starts, and on such a change while it runs.
        scope.launch { refresh(app) }
        app.registerComponentCallbacks(object : ComponentCallbacks {
            private var last = widgetConfiguration(app.resources.configuration)

            override fun onConfigurationChanged(newConfig: Configuration) {
                val now = widgetConfiguration(newConfig)
                if (now != last) {
                    last = now
                    scope.launch { refresh(app) }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = Unit
        })
        if (hasSafeToSpendWidgets(app)) WidgetMidnightWorker.schedule(app, ExistingWorkPolicy.KEEP)
    }

    /** What in the phone's configuration changes a widget's layout or colours: text size and dark mode. */
    private fun widgetConfiguration(config: Configuration): Pair<Float, Int> =
        config.fontScale to (config.uiMode and Configuration.UI_MODE_NIGHT_MASK)

    /** Redraws every placed widget: one whose session has closed opens a new one and reads afresh; an open one reloads. */
    suspend fun refresh(context: Context) {
        generation.update { it + 1 }
        runCatching { SafeToSpendWidget().updateAll(context) }
        runCatching { QuickAddWidget().updateAll(context) }
    }

    /**
     * A widget's frames for one Glance session: the first as it opens, then a fresh one on every [refresh] while it
     * stays open. Glance keeps a session (and its composition) open for a while after each update, and an update
     * meanwhile recomposes only what reads Glance's own state — a widget that loaded once would go on showing old
     * figures, or the layout for the old text size.
     */
    internal suspend fun <T> frames(context: Context, load: suspend () -> T): Pair<WidgetFrame<T>, Flow<WidgetFrame<T>>> {
        val opened = generation.value
        suspend fun frame(): WidgetFrame<T> {
            val config = context.resources.configuration
            return WidgetFrame(load(), config.fontScale, config.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
        }
        return frame() to generation.filter { it != opened }.map { frame() }
    }

    fun hasSafeToSpendWidgets(context: Context): Boolean =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, SafeToSpendWidgetReceiver::class.java))
            .isNotEmpty()
}

/**
 * What a widget draws, with the phone settings that change how: the text size picks the layout, and dark mode the
 * colours where the home screen can't switch them itself (before Android 12). Either one changing makes a new frame.
 */
internal data class WidgetFrame<T>(val content: T, val fontScale: Float, val night: Boolean)

/** The preferences a widget shows. A change to anything else doesn't redraw them. */
private data class WidgetPreferences(
    val onboarded: Boolean,
    val themeId: ThemeId,
    val themeMode: ThemeMode,
    val hideAmounts: Boolean,
    val appLock: Boolean,
    val features: FeatureSet,
    val primaryCurrency: String,
) {
    constructor(prefs: UserPreferences) : this(
        onboarded = prefs.onboardingComplete,
        themeId = prefs.themeId,
        themeMode = prefs.themeMode,
        hideAmounts = prefs.hideAmounts,
        appLock = prefs.appLock,
        features = prefs.features,
        primaryCurrency = prefs.primaryCurrency,
    )
}

/** Redraws the widgets just after local midnight, then books the next night while a Safe to spend widget is placed. */
class WidgetMidnightWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetRefresher.refresh(applicationContext)
        if (WidgetRefresher.hasSafeToSpendWidgets(applicationContext)) schedule(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "widget-midnight-refresh"

        fun schedule(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
            val request = OneTimeWorkRequestBuilder<WidgetMidnightWorker>()
                .setInitialDelay(delayToNextWidgetDay(ZonedDateTime.now()).toMillis(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, policy, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
