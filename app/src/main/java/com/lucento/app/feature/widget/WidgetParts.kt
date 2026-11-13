package com.lucento.app.feature.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.lucento.app.R
import com.lucento.app.feature.capture.ComposerStart

/** Every widget control is at least this tall: Lucento's 48 dp touch target. */
internal val WidgetTarget: Dp = 48.dp

/** The Rising Crest, as approved (never tinted). Decorative: the text beside it names the widget. */
@Composable
internal fun WidgetCrest(size: Dp) {
    Image(ImageProvider(R.drawable.lucento_symbol), contentDescription = null, modifier = GlanceModifier.size(size))
}

/** Text in the phone's own font (widgets can't load Plus Jakarta Sans), never wrapping past [maxLines]. */
@Composable
internal fun WidgetText(
    text: String,
    color: androidx.glance.unit.ColorProvider,
    sizeSp: Float,
    weight: FontWeight = FontWeight.Normal,
    maxLines: Int = 1,
    modifier: GlanceModifier = GlanceModifier,
) {
    Text(text, modifier = modifier, style = TextStyle(color = color, fontSize = sizeSp.sp, fontWeight = weight), maxLines = maxLines)
}

/** The overline row: crest, the label in capitals, and the month (or an icon) on the right. */
@Composable
internal fun WidgetHeader(
    label: String,
    colors: WidgetColors,
    trailing: String? = null,
    trailingIcon: Int? = null,
    trailingIconDescription: String? = null,
) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        WidgetCrest(18.dp)
        Spacer(GlanceModifier.width(8.dp))
        WidgetText(label.uppercase(), colors.onSurfaceVariant, 11f, FontWeight.Bold, modifier = GlanceModifier.defaultWeight())
        if (trailing != null) WidgetText(trailing, colors.onSurfaceVariant, 12f, FontWeight.Medium)
        if (trailingIcon != null) {
            Image(
                ImageProvider(trailingIcon),
                contentDescription = trailingIconDescription,
                colorFilter = ColorFilter.tint(colors.onSurfaceVariant),
                modifier = GlanceModifier.size(16.dp),
            )
        }
    }
}

/** "+ Add": the composer, ready to type. [iconOnly] in the compact layouts, where the words would crowd the figure. */
@Composable
internal fun WidgetAddButton(colors: WidgetColors, iconOnly: Boolean = false) {
    val context = LocalContext.current
    val action = actionStartActivity(WidgetLaunch.Composer(ComposerStart.TYPE).intent(context))
    if (iconOnly) {
        Box(
            GlanceModifier.size(WidgetTarget).cornerRadius(16.dp).background(colors.primary).clickable(action)
                .semantics { contentDescription = "Add a transaction" },
            contentAlignment = Alignment.Center,
        ) {
            Image(ImageProvider(R.drawable.ic_widget_add), null, colorFilter = ColorFilter.tint(colors.onPrimary), modifier = GlanceModifier.size(26.dp))
        }
    } else {
        Row(
            GlanceModifier.height(WidgetTarget).cornerRadius(14.dp).background(colors.primary).padding(start = 14.dp, end = 18.dp)
                .clickable(action).semantics { contentDescription = "Add a transaction" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(ImageProvider(R.drawable.ic_widget_add), null, colorFilter = ColorFilter.tint(colors.onPrimary), modifier = GlanceModifier.size(20.dp))
            Spacer(GlanceModifier.width(6.dp))
            WidgetText("Add", colors.onPrimary, 15f, FontWeight.Medium)
        }
    }
}

/**
 * A state's one action: on the theme's tonal fill ("Set amounts", "Choose categories"), or filled when it is all the
 * state offers ("Open Lucento"). At [lines] 2 it takes the row and its words wrap, at the height its layout planned.
 */
@Composable
internal fun WidgetActionButton(
    text: String,
    launch: WidgetLaunch,
    colors: WidgetColors,
    filled: Boolean,
    lines: Int,
    heightDp: Float,
    modifier: GlanceModifier = GlanceModifier,
) {
    val context = LocalContext.current
    Box(
        modifier.height(heightDp.dp).cornerRadius(14.dp).background(if (filled) colors.primary else colors.tonal)
            .padding(horizontal = 16.dp).clickable(actionStartActivity(launch.intent(context))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = TextStyle(
                color = if (filled) colors.onPrimary else colors.onTonal,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
            maxLines = lines,
        )
    }
}

/**
 * The budget bar, drawn as two boxes so its colours hold on every Android version (Glance's progress bar takes custom
 * colours only from Android 12). Decorative: the percentage beside it says the same in words.
 */
@Composable
internal fun WidgetBar(fraction: Float, fill: androidx.glance.unit.ColorProvider, track: androidx.glance.unit.ColorProvider, widthDp: Float) {
    val filled = widthDp.coerceAtLeast(0f) * fraction.coerceIn(0f, 1f)
    Box(GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp).background(track)) {
        if (filled >= 1f) Box(GlanceModifier.width(filled.dp).height(6.dp).cornerRadius(3.dp).background(fill)) {}
    }
}

/** Home's verdict chip: the words carry it, the tone reinforces them. */
@Composable
internal fun WidgetChip(text: String, tone: WidgetTone, colors: WidgetColors) {
    Box(GlanceModifier.cornerRadius(10.dp).background(colors.container(tone)).padding(horizontal = 8.dp, vertical = 2.dp)) {
        WidgetText(text, colors.text(tone), 12f, FontWeight.Medium)
    }
}
