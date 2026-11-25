package com.lucento.app.feature.help

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Asks Google Play to show its rating card over the activity behind [context] (Play's In-App Review API;
 * docs/HELP_AND_FEEDBACK.md). Google Play decides whether the card appears (it keeps its own limit), draws it, and keeps
 * what the person writes: Lucento learns nothing about a rating either way. Called only when
 * [com.lucento.app.domain.ReviewPromptPolicy] says it's due.
 *
 * Answers whether Play's flow ran without an error, which says nothing about whether the card was shown or used. Never
 * throws: a phone without Google Play, or an app not installed from it, simply gets no card.
 */
suspend fun showPlayRatingCard(context: Context): Boolean = try {
    val activity = context.findActivity() ?: throw IllegalStateException("no activity")
    val manager = ReviewManagerFactory.create(activity)
    val info = suspendCancellableCoroutine { done ->
        manager.requestReviewFlow().addOnCompleteListener { task -> done.resume(if (task.isSuccessful) task.result else null) }
    }
    if (info == null) {
        false
    } else {
        suspendCancellableCoroutine { done ->
            manager.launchReviewFlow(activity, info).addOnCompleteListener { task -> done.resume(task.isSuccessful) }
        }
    }
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    false
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
