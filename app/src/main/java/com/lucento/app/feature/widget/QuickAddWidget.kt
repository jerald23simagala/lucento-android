package com.lucento.app.feature.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import com.lucento.app.LucentoApplication
import com.lucento.app.R
import com.lucento.app.core.designsystem.theme.ThemeSpec
import com.lucento.app.domain.Feature
import com.lucento.app.feature.capture.ComposerStart
import kotlinx.coroutines.flow.first

/**
 * The Quick add widget (docs/WIDGETS.md): the composer's own bar on the home screen — scanner, mic and the field, in the
 * composer's order. It only opens Lucento; nothing is read or saved here. Speak and Scan show while their modules are
 * on; the label shortens, then the bar becomes one Add, as the cell narrows or the text grows ([QuickAddLayout]).
 */
class QuickAddWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val preferences = (context.applicationContext as LucentoApplication).container.preferences
        val (first, updates) = WidgetRefresher.frames(context) {
            val prefs = preferences.preferences.first()
            QuickAddState(
                colors = WidgetColors.of(ThemeSpec.of(prefs.themeId), prefs.themeMode),
                scan = prefs.features.isEnabled(Feature.RECEIPT_SCANNING),
                voice = prefs.features.isEnabled(Feature.VOICE_ENTRY),
            )
        }
        provideContent {
            val frame by updates.collectAsState(first)
            QuickAddContent(frame.content, frame.fontScale)
        }
    }
}

/** What Quick add shows: the theme's colours and which of Speak and Scan are on. */
private class QuickAddState(val colors: WidgetColors, val scan: Boolean, val voice: Boolean)

class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
}

@Composable
private fun QuickAddContent(state: QuickAddState, scale: Float) {
    val colors = state.colors
    val size = LocalSize.current
    val layout = QuickAddLayout.choose(size.width.value, scale)
    // 48 dp targets, unless the home screen gives the row less: then they fill its height instead of being cut.
    val target = minOf(WIDGET_TARGET_DP, size.height.value).dp
    val tools = layout != QuickAddLayout.SINGLE
    Row(
        GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(36.dp).background(colors.surface).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tools && state.scan) {
            QuickTool(R.drawable.ic_widget_scan, "Scan a receipt", WidgetLaunch.Composer(ComposerStart.SCAN), colors, target)
            Spacer(GlanceModifier.width(8.dp))
        }
        if (tools && state.voice) {
            QuickTool(R.drawable.ic_widget_mic, "Speak a transaction", WidgetLaunch.Composer(ComposerStart.SPEAK), colors, target)
            Spacer(GlanceModifier.width(8.dp))
        }
        QuickField(layout, colors, target, GlanceModifier.defaultWeight())
    }
}

/** Scan or Speak: the composer opens in that mode. */
@Composable
private fun QuickTool(icon: Int, description: String, launch: WidgetLaunch, colors: WidgetColors, target: Dp) {
    val context = LocalContext.current
    Box(
        GlanceModifier.size(target).cornerRadius(16.dp).background(colors.tonal)
            .clickable(actionStartActivity(launch.intent(context))).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Image(ImageProvider(icon), null, colorFilter = ColorFilter.tint(colors.onTonal), modifier = GlanceModifier.size(24.dp))
    }
}

/**
 * The field, outlined like the composer's: the outline colour behind, the surface 1.5 dp in (widgets have no borders).
 * Its name is always "Add a transaction", which begins with whatever it shows ("Add a transaction" or "Add").
 */
@Composable
private fun QuickField(layout: QuickAddLayout, colors: WidgetColors, target: Dp, modifier: GlanceModifier) {
    val context = LocalContext.current
    val full = layout == QuickAddLayout.FULL
    Box(
        modifier.height(target).cornerRadius(24.dp).background(colors.outline).padding(1.5.dp)
            .clickable(actionStartActivity(WidgetLaunch.Composer(ComposerStart.TYPE).intent(context)))
            .semantics { contentDescription = "Add a transaction" },
    ) {
        Row(
            GlanceModifier.fillMaxSize().cornerRadius(23.dp).background(colors.surface).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = if (layout == QuickAddLayout.SINGLE) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            WidgetCrest(22.dp)
            Spacer(GlanceModifier.width(10.dp))
            if (full) {
                WidgetText("Add a transaction", colors.onSurfaceVariant, 15f)
            } else {
                WidgetText("Add", colors.onSurface, 15f, FontWeight.Medium)
            }
        }
    }
}
