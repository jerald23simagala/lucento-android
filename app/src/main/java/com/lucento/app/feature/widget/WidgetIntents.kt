package com.lucento.app.feature.widget

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.lucento.app.MainActivity

/**
 * The intent a widget tap sends: Lucento itself, told where to go. The data URI is per target, so each button gets its
 * own PendingIntent (extras alone don't make two of them differ). An open Lucento is brought forward and told through
 * onNewIntent instead of stacking a second copy.
 */
internal fun WidgetLaunch.intent(context: Context): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(WidgetLaunch.ACTION)
        .setData("lucento-widget://open/$key".toUri())
        .putExtra(WidgetLaunch.EXTRA_TARGET, key)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

/** The widget target [intent] carries, or null for every other way in (the launcher icon, a restored task…). */
fun widgetLaunchFrom(intent: Intent?): WidgetLaunch? =
    if (intent?.action == WidgetLaunch.ACTION) WidgetLaunch.fromKey(intent.getStringExtra(WidgetLaunch.EXTRA_TARGET)) else null
