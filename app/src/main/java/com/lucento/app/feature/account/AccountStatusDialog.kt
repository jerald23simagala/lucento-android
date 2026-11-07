package com.lucento.app.feature.account

import android.content.ActivityNotFoundException
import android.content.Intent
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.lucento.app.core.designsystem.component.IconTile
import com.lucento.app.core.designsystem.component.LucentoAnimation
import com.lucento.app.core.designsystem.component.LucentoLottieAnimation
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoSecondaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.Elevation
import com.lucento.app.core.designsystem.theme.Spacing
import kotlinx.coroutines.delay

/** The account is ready: verified (email) or connected (Google). [settling] while Lucento checks what's in it. */
data class AccountReady(val email: String, val emailVerified: Boolean, val settling: Boolean = false)

/**
 * The one dialog for a new account becoming ready (owner, 2026-11-06). While an email account waits for its link:
 * "Check your inbox", which notices on its own when the link has been opened. Then the same dialog turns into
 * "Email verified" (or, after Google, "You're connected") and Lucento moves on — by itself after a moment, or at
 * Continue. With TalkBack on it waits for Continue, so nothing changes while it is being read.
 *
 * It can't be tapped away; Back while waiting means "Use a different email".
 */
@Composable
fun AccountStatusDialog(
    waiting: EmailVerification.State?,
    ready: AccountReady?,
    onVisible: (Boolean) -> Unit,
    onResend: () -> Unit,
    onChangeEmail: () -> Unit,
    onPassword: (String) -> Unit,
    onContinue: () -> Unit,
) {
    val verifiedEmail = (waiting as? EmailVerification.State.Verified)?.email
    val done = ready ?: verifiedEmail?.let { AccountReady(it, emailVerified = true, settling = true) }
    val wait = waiting as? EmailVerification.State.Waiting
    if (done == null && wait == null) return

    // Checks while Lucento is on screen — above all on coming back from the email app — and never in the background.
    if (wait != null) {
        LifecycleResumeEffect(Unit) {
            onVisible(true)
            onPauseOrDispose { onVisible(false) }
        }
    }

    Dialog(
        onDismissRequest = { if (done == null) onChangeEmail() else if (!done.settling) onContinue() },
        properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = Elevation.md,
            modifier = Modifier
                .padding(horizontal = Spacing.lg, vertical = Spacing.xl)
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .imePadding(),
        ) {
            AnimatedContent(
                targetState = done != null,
                transitionSpec = { (fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.96f)).togetherWith(fadeOut(tween(120))) },
                label = "account-status",
            ) { isDone ->
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.lg, vertical = Spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (isDone && done != null) {
                        ReadyContent(done, onContinue)
                    } else if (wait != null) {
                        WaitingContent(wait, onResend, onChangeEmail, onPassword)
                    }
                }
            }
        }
    }
}

@Composable
private fun WaitingContent(
    state: EmailVerification.State.Waiting,
    onResend: () -> Unit,
    onChangeEmail: () -> Unit,
    onPassword: (String) -> Unit,
) {
    val context = LocalContext.current
    var noEmailApp by remember { mutableStateOf(false) }
    IconTile(
        Icons.Outlined.MarkEmailUnread,
        container = MaterialTheme.colorScheme.primaryContainer,
        content = MaterialTheme.colorScheme.primary,
        size = 64.dp,
        shape = CircleShape,
    )
    Spacer(Modifier.height(Spacing.md))
    Text(
        "Check your inbox",
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(Spacing.sm))
    Text(
        androidx.compose.ui.text.buildAnnotatedString {
            append("We sent a welcome email to ")
            pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface))
            append(state.email)
            pop()
            append(". Open it and tap ")
            pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface))
            append("Verify email")
            pop()
            append(" to finish creating your account.")
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(Spacing.lg))
    if (state.needsPassword) {
        PasswordAgain(state, onPassword)
    } else {
        // What the dialog is doing while it waits, so it never looks stuck.
        LucentoLottieAnimation(LucentoAnimation.Loading, Modifier.size(width = 64.dp, height = 20.dp))
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "Waiting for you to verify. Lucento carries on by itself — you can open the email on any device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    val notice = when {
        noEmailApp -> "There’s no email app on this phone. Open your email in a browser or on another device."
        else -> when (state.notice) {
            EmailVerification.Notice.SENT_AGAIN -> "Sent again. It can take a minute to arrive — check Spam or Promotions too."
            EmailVerification.Notice.TOO_SOON -> "An email was sent moments ago. Wait a minute before asking for another."
            EmailVerification.Notice.SEND_FAILED -> "Couldn’t send it just now. Check your connection and try again."
            EmailVerification.Notice.OFFLINE -> "Can’t reach Lucento right now. It keeps checking once you’re back online."
            EmailVerification.Notice.WRONG_PASSWORD -> null
            null -> null
        }
    }
    if (notice != null) {
        Spacer(Modifier.height(Spacing.md))
        Text(
            notice,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    Spacer(Modifier.height(Spacing.lg))
    if (!state.needsPassword) {
        LucentoPrimaryButton(
            text = "Open email app",
            onClick = {
                noEmailApp = !openEmailApp(context)
            },
        )
        Spacer(Modifier.height(Spacing.sm))
    }
    LucentoSecondaryButton(
        text = if (state.resendInSeconds > 0) "Send again in ${state.resendInSeconds}s" else "Send the email again",
        onClick = onResend,
        enabled = state.resendInSeconds == 0,
    )
    LucentoTextButton(
        text = "Use a different email",
        onClick = onChangeEmail,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Lucento was closed while the person was in their email: the password, once, to keep checking. */
@Composable
private fun PasswordAgain(state: EmailVerification.State.Waiting, onPassword: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    Text(
        if (state.notice == EmailVerification.Notice.WRONG_PASSWORD) {
            "That password doesn’t match this account. Enter the one you chose for it."
        } else {
            "Lucento was closed while you were away. Enter your password to keep going."
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (state.notice == EmailVerification.Notice.WRONG_PASSWORD) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    Spacer(Modifier.height(Spacing.sm))
    OutlinedTextField(
        value = password,
        onValueChange = { password = it.take(128) },
        label = { Text("Password") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onPassword(password) }),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(Spacing.sm))
    LucentoPrimaryButton(text = "Continue", onClick = { onPassword(password) }, enabled = password.isNotEmpty() && !state.checking)
}

@Composable
private fun ReadyContent(ready: AccountReady, onContinue: () -> Unit) {
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val proceed by rememberUpdatedState(onContinue)
    // The success is real (the server confirmed it), so it is celebrated once — then Lucento moves on by itself,
    // except with TalkBack, where the person continues when they've heard it.
    LaunchedEffect(Unit) { haptics.confirm() }
    LaunchedEffect(ready.settling) {
        val touchExploration = context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true
        if (!ready.settling && !touchExploration) {
            delay(AUTO_CONTINUE_MILLIS)
            proceed()
        }
    }
    LucentoLottieAnimation(LucentoAnimation.Saved, Modifier.size(96.dp))
    Spacer(Modifier.height(Spacing.sm))
    Text(
        if (ready.emailVerified) "Email verified" else "You’re connected",
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics {
            heading()
            liveRegion = LiveRegionMode.Polite
        },
    )
    Spacer(Modifier.height(Spacing.sm))
    Text(
        androidx.compose.ui.text.buildAnnotatedString {
            pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface))
            append(ready.email.ifEmpty { "Your account" })
            pop()
            append(" is your Lucento account. Your records sync once setup is finished.")
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(Spacing.lg))
    if (ready.settling) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            LucentoLottieAnimation(LucentoAnimation.Loading, Modifier.size(width = 64.dp, height = 20.dp))
            Text("Getting things ready…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LucentoPrimaryButton(text = "Continue", onClick = onContinue)
    }
}

/** Opens the phone's email app (its inbox), so the person doesn't have to find it. False when there is none. */
private fun openEmailApp(context: android.content.Context): Boolean = try {
    context.startActivity(
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

/** How long "Email verified" / "You're connected" stays before Lucento moves on by itself. */
private const val AUTO_CONTINUE_MILLIS = 2_200L
