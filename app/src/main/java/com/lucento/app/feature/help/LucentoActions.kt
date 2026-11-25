package com.lucento.app.feature.help

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.lucento.app.BuildConfig
import com.lucento.app.core.LucentoLinks
import com.lucento.app.feature.legal.LegalDocuments

/**
 * What Settings › Help & feedback and the profile menu open: a page in the browser, Lucento's Play listing, the phone's
 * share sheet or its email app. Each runs only on a tap and hands over to another app; Lucento itself sends nothing.
 * Every function answers whether something opened, so a caller can say what to do instead.
 */
object LucentoActions {

    fun browse(context: Context, url: String): Boolean = start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    /**
     * Lucento's listing in the Play Store app, where people rate it (Google's "Linking to Google Play": the https link with
     * the Play Store as its package, so no chooser); the same link in the browser on a phone without the Play Store.
     */
    fun openPlayListing(context: Context): Boolean {
        val store = Intent(Intent.ACTION_VIEW, Uri.parse(LucentoLinks.PLAY_STORE)).setPackage("com.android.vending")
        return start(context, store) || browse(context, LucentoLinks.PLAY_STORE)
    }

    /** The phone's share sheet, starting with [LucentoLinks.shareMessage]; the person can change it before sending. */
    fun share(context: Context): Boolean {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, LucentoLinks.shareMessage())
        }
        return start(context, Intent.createChooser(send, "Share Lucento"))
    }

    /**
     * A new email to Lucento in the person's own email app, with the app and Android version under the message so a reply
     * can be specific. They see all of it, can delete any of it, and nothing goes until they send it.
     */
    fun emailSupport(context: Context): Boolean {
        val email = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(LegalDocuments.CONTACT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, SupportEmail.subject(BuildConfig.VERSION_NAME))
            putExtra(Intent.EXTRA_TEXT, SupportEmail.body(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, Build.VERSION.RELEASE, Build.MANUFACTURER, Build.MODEL))
        }
        return start(context, email)
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

/** The email "Contact support" starts (pure, for tests): the person writes above the line, the details sit below it. */
object SupportEmail {
    fun subject(versionName: String): String = "Lucento $versionName"

    fun body(versionName: String, versionCode: Int, androidRelease: String, manufacturer: String, model: String): String {
        val phone = if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
        return "\n\n\n— About this phone (you can delete this) —\nLucento $versionName ($versionCode) · Android $androidRelease · ${phone.trim()}"
    }
}
