package com.lucento.app.feature.support

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoFields
import com.lucento.app.core.designsystem.component.ChoicePill
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoSecondaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.SectionHeader
import com.lucento.app.core.designsystem.component.SwitchRow
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.domain.support.FormProblem
import com.lucento.app.domain.support.SupportRules
import com.lucento.app.domain.support.SupportTopic
import com.lucento.app.feature.legal.LegalDoc
import com.lucento.app.feature.legal.LegalSheet
import java.text.NumberFormat

/**
 * Settings › Help › Contact support (docs/SUPPORT.md). One page: what it's about, the message, where to reply (signed
 * out only), and exactly what is attached about the phone, with one switch to leave it out. Replies come by email.
 */
@Composable
fun ContactSupportScreen(
    onBack: () -> Unit,
    viewModel: ContactSupportViewModel = viewModel(factory = ContactSupportViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var legal by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val emailUs = {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${SupportRules.SUPPORT_EMAIL}")))
        }
        Unit
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter),
    ) {
        LucentoTopBar(title = "Contact support", onBack = onBack)
        when (val result = state.result) {
            is ContactSupportViewModel.Result.Sent -> {
                // After the server has the ticket, never before (a committed write).
                androidx.compose.runtime.LaunchedEffect(result.ticket) { haptics.confirm() }
                Outcome(
                    icon = Icons.Outlined.CheckCircle,
                    tint = MaterialTheme.colorScheme.primary,
                    title = "Thanks. We’ll reply by email to ${result.replyTo}.",
                    body = "Your reference is ${result.ticket}. If you write to us about it, mention it.",
                    onDone = onBack,
                    onAnother = viewModel::startAnother,
                )
            }
            ContactSupportViewModel.Result.Queued -> Outcome(
                icon = Icons.Outlined.CloudOff,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                title = "Saved. It will be sent when you’re online.",
                body = "You can close Lucento: it goes on its own once this phone has a connection, and we reply by email.",
                onDone = onBack,
                onAnother = viewModel::startAnother,
            )
            else -> Form(
                state = state,
                onTopic = viewModel::setTopic,
                onMessage = viewModel::setMessage,
                onEmail = viewModel::setEmail,
                onIncludeDetails = viewModel::setIncludeDetails,
                onSend = viewModel::send,
                onPrivacy = { legal = true },
                onEmailUs = emailUs,
            )
        }
        History(state)
        Spacer(Modifier.height(Spacing.section))
        Text(
            "Prefer email? Write to ${SupportRules.SUPPORT_EMAIL}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LucentoTextButton(text = "Write an email instead", onClick = emailUs)
        Spacer(Modifier.height(Spacing.xxl))
    }
    if (legal) LegalSheet(LegalDoc.PRIVACY, onDismiss = { legal = false })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Form(
    state: ContactSupportViewModel.UiState,
    onTopic: (SupportTopic) -> Unit,
    onMessage: (String) -> Unit,
    onEmail: (String) -> Unit,
    onIncludeDetails: (Boolean) -> Unit,
    onSend: () -> Unit,
    onPrivacy: () -> Unit,
    onEmailUs: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    if (!state.available) {
        InfoNote("This version of Lucento can’t send requests itself. Email ${SupportRules.SUPPORT_EMAIL} and we’ll reply there.", actionLabel = "Write an email", onAction = onEmailUs)
        return
    }
    Text(
        "Tell us what’s happening, and we’ll reply by email.",
        style = MaterialTheme.typography.bodyMedium,
        color = colors.onSurfaceVariant,
    )

    SectionHeader("What is it about?", Modifier.padding(top = Spacing.lg))
    FlowRow(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SupportTopic.entries.forEach { topic ->
            ChoicePill(label = topic.label, selected = state.topic == topic, onClick = { onTopic(topic) })
        }
    }
    if (state.problem == FormProblem.NO_TOPIC) ProblemText(FormProblem.NO_TOPIC.message)

    SectionHeader("Your message", Modifier.padding(top = Spacing.lg))
    val tooLong = state.message.trim().length > SupportRules.MAX_MESSAGE
    val messageProblem = state.problem?.takeIf { it == FormProblem.MESSAGE_EMPTY || it == FormProblem.MESSAGE_TOO_LONG }
    val count = NumberFormat.getIntegerInstance()
    OutlinedTextField(
        value = state.message,
        onValueChange = onMessage,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("What happened, what you expected, and anything you already tried") },
        minLines = 5,
        maxLines = 12,
        isError = messageProblem != null || tooLong,
        shape = LucentoFields.shape,
        colors = LucentoFields.filledColors(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        supportingText = {
            Row(Modifier.fillMaxWidth()) {
                Text(messageProblem?.message ?: "", Modifier.weight(1f))
                Text("${count.format(state.message.trim().length)} / ${count.format(SupportRules.MAX_MESSAGE)}")
            }
        },
    )

    if (state.signedIn) {
        Text(
            "We’ll reply to ${state.accountEmail}, the address of the account you’re signed in to.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    } else {
        SectionHeader("Where should we reply?", Modifier.padding(top = Spacing.lg))
        val emailProblem = state.problem == FormProblem.EMAIL_INVALID
        OutlinedTextField(
            value = state.email,
            onValueChange = onEmail,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Your email address") },
            singleLine = true,
            isError = emailProblem,
            shape = LucentoFields.shape,
            colors = LucentoFields.filledColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done, autoCorrectEnabled = false),
            supportingText = { Text(if (emailProblem) FormProblem.EMAIL_INVALID.message else "Only used to answer this request.") },
        )
    }

    SectionHeader("About this phone", Modifier.padding(top = Spacing.lg))
    SwitchRow(
        title = "Attach details about this phone",
        checked = state.includeDetails,
        onCheckedChange = onIncludeDetails,
        description = "Helps us find the problem. Nothing from your records is attached.",
        icon = Icons.Outlined.PhoneAndroid,
    )
    Details(state)

    Spacer(Modifier.height(Spacing.lg))
    InfoNote(
        "Your request goes to Lucento support. It’s kept until it’s resolved and deleted 12 months later, or straight away if you delete your account.",
        actionLabel = "Privacy Policy",
        onAction = onPrivacy,
    )
    (state.result as? ContactSupportViewModel.Result.Refused)?.let { refused ->
        Spacer(Modifier.height(Spacing.md))
        InfoNote(
            refused.message,
            icon = Icons.Outlined.ErrorOutline,
            container = colors.errorContainer,
            content = colors.onErrorContainer,
            actionLabel = "Write an email",
            onAction = onEmailUs,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    Spacer(Modifier.height(Spacing.lg))
    LucentoPrimaryButton(text = if (state.sending) "Sending…" else "Send", onClick = onSend, enabled = !state.sending, icon = Icons.Outlined.Email)
}

/** Exactly what goes with the request, line by line; nothing when the switch is off. */
@Composable
private fun Details(state: ContactSupportViewModel.UiState) {
    val colors = MaterialTheme.colorScheme
    val details = state.details
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(colors.surfaceContainer)
            .padding(Spacing.md),
    ) {
        if (!state.includeDetails || details == null) {
            Text(
                if (!state.includeDetails) "Nothing about this phone is attached." else "Reading this phone’s details…",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        } else {
            Text("Attached", style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
            Spacer(Modifier.height(Spacing.xs))
            SupportRules.preview(details, System.currentTimeMillis()).forEach { (label, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).semantics(mergeDescendants = true) {}) {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.weight(0.42f))
                    Text(value, style = MaterialTheme.typography.bodySmall, color = colors.onSurface, modifier = Modifier.weight(0.58f))
                }
            }
        }
    }
}

@Composable
private fun ProblemText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = Spacing.xs).semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun Outcome(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    title: String,
    body: String,
    onDone: () -> Unit,
    onAnother: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(top = Spacing.xl).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.Start,
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(Spacing.lg))
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(Spacing.sm))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(Spacing.xl))
    LucentoPrimaryButton(text = "Done", onClick = onDone)
    Spacer(Modifier.height(Spacing.sm))
    LucentoSecondaryButton(text = "Write another request", onClick = onAnother)
}

/** Requests written on this phone: waiting to go, sent (with their reference), or given up. */
@Composable
private fun History(state: ContactSupportViewModel.UiState) {
    if (state.history.isEmpty() && state.waiting == 0) return
    val colors = MaterialTheme.colorScheme
    SectionHeader("Your recent requests", Modifier.padding(top = Spacing.section))
    if (state.waiting > 0) {
        Text(
            if (state.waiting == 1) "1 request is waiting to be sent." else "${state.waiting} requests are waiting to be sent.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
        )
        Spacer(Modifier.height(Spacing.xs))
    }
    state.history.asReversed().forEach { item ->
        val topic = SupportTopic.fromKey(item.topic)?.label ?: "Request"
        Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (item.sent) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = if (item.sent) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                if (item.sent) "${item.ticket} · $topic · sent ${SupportRules.ago(item.atMillis, System.currentTimeMillis()).lowercase()}"
                else "$topic · couldn’t be sent. Email us instead.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
