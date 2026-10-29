package com.lucento.app.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.AmountTextField
import com.lucento.app.core.designsystem.component.ConfirmDialog
import com.lucento.app.core.designsystem.component.CurrencyList
import com.lucento.app.core.designsystem.component.IllustratedSurface
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoIllustration
import com.lucento.app.core.designsystem.component.LucentoMascot
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoStackedLockup
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.SwitchRow
import com.lucento.app.core.designsystem.component.LucentoWorkingIndicator
import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Motion
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.domain.FirstRunDefaults
import com.lucento.app.domain.FirstRunStep

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.Factory)) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val signedInEmail by viewModel.signedInEmail.collectAsStateWithLifecycle()
    val connecting by viewModel.connecting.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()
    val restoreStage by viewModel.restoreStage.collectAsStateWithLifecycle()
    val restoreMessage by viewModel.restoreMessage.collectAsStateWithLifecycle()
    val switchPrompt by viewModel.switchPrompt.collectAsStateWithLifecycle()
    val usageOptIn by viewModel.usageOptIn.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(step) { viewModel.onStepShown(step) }

    if (switchPrompt) {
        ConfirmDialog(
            title = "Switch account?",
            text = "This phone’s data belongs to another account, and some of it hasn’t synced. " +
                "It will be removed so it is never uploaded to the account you just signed in to. " +
                "Changes that haven’t synced will be lost.",
            confirmLabel = "Remove and switch",
            destructive = true,
            onConfirm = viewModel::confirmSwitch,
            onDismiss = viewModel::cancelSwitch,
        )
    }

    BackHandler(enabled = step != FirstRunStep.WELCOME && restoreStage == RestoreStage.NONE) { viewModel.back() }
    BackHandler(enabled = restoreStage != RestoreStage.NONE && restoreStage != RestoreStage.RESTORING) { viewModel.cancelRestore() }

    // Restoring an account on a new phone replaces the story with its own short flow.
    if (restoreStage != RestoreStage.NONE) {
        RestoreFlow(
            stage = restoreStage,
            signedInEmail = signedInEmail,
            googleAvailable = viewModel.googleAvailable,
            connecting = connecting,
            message = restoreMessage ?: syncMessage,
            onContinue = viewModel::continueRestore,
            onGoogle = { viewModel.connectGoogle(context) },
            onEmail = viewModel::connectEmail,
            onPrepareEmail = viewModel::prepareConnection,
            onRetry = viewModel::runRestore,
            onSetUpAsNew = viewModel::setUpAsNew,
            onBack = viewModel::cancelRestore,
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
    ) {
        StepLabel(step)
        val reducedMotion = LucentoTheme.reducedMotion
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                val slow = if (reducedMotion) 0 else Motion.slow
                val standard = if (reducedMotion) 0 else Motion.standard
                (slideInHorizontally(tween(slow)) { w -> if (forward) w / 6 else -w / 6 } + fadeIn(tween(slow)))
                    .togetherWith(slideOutHorizontally(tween(standard)) { w -> if (forward) -w / 6 else w / 6 } + fadeOut(tween(standard)))
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "onboarding-step",
        ) { target ->
            when (target) {
                FirstRunStep.WELCOME -> WelcomeStep(
                    onContinue = viewModel::next,
                    onRestore = if (viewModel.syncAvailable) viewModel::startRestore else null,
                    usageOptIn = usageOptIn,
                    onUsageOptIn = viewModel::setUsageOptIn,
                )
                FirstRunStep.CURRENCY -> CurrencyStep(
                    currency = currency,
                    onCurrency = viewModel::selectCurrency,
                    onContinue = viewModel::next,
                )
                FirstRunStep.WALLET, FirstRunStep.FIRST_ENTRY -> WalletStep(
                    currency = currency,
                    error = error,
                    onContinue = viewModel::finishWallet,
                )
            }
        }
    }
}

/** "Step N of 4", visible and read once when the screen is focused. Not a live region, so it is not repeated. */
@Composable
private fun StepLabel(step: FirstRunStep) {
    val label = FirstRunStep.progressLabel(step)
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(top = Spacing.sm, bottom = Spacing.md)
            .semantics { contentDescription = label },
    )
}

@Composable
private fun StepTitle(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(Spacing.sm))
    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(Spacing.lg))
}

@Composable
private fun WelcomeStep(
    onContinue: () -> Unit,
    onRestore: (() -> Unit)?,
    usageOptIn: Boolean,
    onUsageOptIn: (Boolean) -> Unit,
) {
    val mascot = if (LocalDensity.current.fontScale >= 1.3f) 96.dp else 140.dp
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Spacing.md))
            LucentoStackedLockup(symbolSize = if (LocalDensity.current.fontScale >= 1.3f) 72.dp else 96.dp, animate = true, wordmarkModifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Financial clarity without the complexity.",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Track what you spend, plan what’s next — privately, on this phone. No account, no bank login.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.lg))
            IllustratedSurface(
                illustration = LucentoIllustration.SunriseLandscape,
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.background,
                overlay = if (LucentoTheme.isDark) SolidColor(MaterialTheme.colorScheme.background.copy(alpha = 0.45f)) else null,
                artworkAlignment = Alignment.BottomCenter,
                contentPadding = PaddingValues(top = Spacing.lg, bottom = Spacing.sm),
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    LucentoMascot(size = mascot, mood = MascotMood.Greeting, contentDescription = "Lucento, waving hello with a bright torch")
                }
            }
            Spacer(Modifier.height(Spacing.lg))
            InfoNote(
                "Your data stays on this phone. Uninstalling Lucento or clearing its storage erases it, unless cloud sync is on.",
            )
            Spacer(Modifier.height(Spacing.md))
            SwitchRow(
                title = "Share anonymous usage counts",
                description = "Helps improve Lucento. Never amounts or what you type. Event names and timings only, with a random install id, kept 90 days.",
                checked = usageOptIn,
                onCheckedChange = onUsageOptIn,
            )
            Spacer(Modifier.height(Spacing.md))
        }
        LucentoPrimaryButton(text = "Get started", onClick = onContinue)
        if (onRestore != null) {
            Spacer(Modifier.height(Spacing.xs))
            LucentoTextButton(text = "I already have an account", onClick = onRestore, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CurrencyStep(currency: String, onCurrency: (String) -> Unit, onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f)) {
            StepTitle("Your main currency", "Used on Home and Insights. Each account keeps its own currency — Lucento never converts.")
            CurrencyList(selected = currency, onSelect = onCurrency, modifier = Modifier.weight(1f).fillMaxWidth())
        }
        Spacer(Modifier.height(Spacing.md))
        LucentoPrimaryButton(text = "Continue", onClick = onContinue)
    }
}

@Composable
private fun WalletStep(currency: String, error: String?, onContinue: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(FirstRunDefaults.DEFAULT_WALLET_NAME) }
    var balance by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            StepTitle(
                "What’s in your main account?",
                "One account is enough to start. You can add more later in Wallets.",
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.md))
            AmountTextField(
                value = balance,
                onValueChange = { balance = it },
                label = { Text("Balance") },
                prefix = { Text(MoneyFormatter.currencySymbol(currency) + " ") },
                supportingText = { Text("Leave empty to start at zero.") },
                isError = error != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            if (error != null) {
                Spacer(Modifier.height(Spacing.sm))
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(Spacing.md))
        LucentoPrimaryButton(text = "Continue", onClick = { onContinue(name, balance) })
    }
}

/**
 * "I already have an account": a reminder to sync the old phone first, sign-in, then the download. A restored account
 * goes straight to Home (no story, no guided setup); an empty one continues with the normal setup, still connected.
 */
@Composable
internal fun RestoreFlow(
    stage: RestoreStage,
    signedInEmail: String?,
    googleAvailable: Boolean,
    connecting: Boolean,
    message: String?,
    onContinue: () -> Unit,
    onGoogle: () -> Unit,
    onEmail: (String, String, com.lucento.app.feature.settings.EmailMode) -> Unit,
    onPrepareEmail: () -> Unit,
    onRetry: () -> Unit,
    onSetUpAsNew: () -> Unit,
    onBack: () -> Unit,
) {
    val ext = LucentoTheme.extendedColors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(Spacing.lg))
            when (stage) {
                RestoreStage.WARN -> {
                    LucentoMascot(size = 88.dp, mood = MascotMood.Curious, contentDescription = "Lucento, ready to help you move")
                    Spacer(Modifier.height(Spacing.md))
                    StepTitle(
                        "Moving to a new phone?",
                        "Sign in with your Lucento account and everything it has synced comes across: wallets, " +
                            "transactions, budgets, goals and scheduled payments.",
                    )
                    Spacer(Modifier.height(Spacing.md))
                    InfoNote(
                        "Sync your old phone first (recommended). Anything added there since its last sync stays on that " +
                            "phone and won’t show up here. On the old phone, open Lucento › Settings › Cloud sync › Sync now.",
                        icon = Icons.Outlined.WarningAmber,
                        container = ext.warningContainer,
                        content = ext.warning,
                    )
                }
                RestoreStage.SIGN_IN -> {
                    StepTitle("Sign in to your account", "Use the same Google or email account as on your old phone.")
                    Spacer(Modifier.height(Spacing.md))
                    com.lucento.app.feature.settings.ConnectAccountPanel(
                        busy = connecting,
                        googleAvailable = googleAvailable,
                        onGoogle = onGoogle,
                        onEmail = onEmail,
                        onPrepareEmail = onPrepareEmail,
                    )
                }
                RestoreStage.RESTORING -> {
                    StepTitle(
                        "Bringing your money across",
                        "Downloading ${signedInEmail?.takeIf { it.isNotEmpty() } ?: "your account"}. Keep Lucento open — " +
                            "this can take a moment on a slow connection.",
                    )
                    Spacer(Modifier.height(Spacing.xl))
                    LucentoWorkingIndicator(label = "Restoring your account")
                }
                RestoreStage.EMPTY -> {
                    LucentoMascot(size = 88.dp, mood = MascotMood.Curious, contentDescription = "Lucento")
                    Spacer(Modifier.height(Spacing.md))
                    StepTitle(
                        "Nothing to restore yet",
                        "${signedInEmail?.takeIf { it.isNotEmpty() } ?: "This account"} has no wallets or categories saved. " +
                            "Set Lucento up as new — you stay connected, and it syncs when you finish.",
                    )
                }
                RestoreStage.FAILED -> {
                    StepTitle("Couldn’t bring your data across", "Nothing was changed on this phone.")
                }
                RestoreStage.NONE -> Unit
            }
            if (message != null && stage != RestoreStage.RESTORING) {
                Spacer(Modifier.height(Spacing.sm))
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(Spacing.md))
        when (stage) {
            RestoreStage.WARN -> {
                LucentoPrimaryButton(text = "Continue", onClick = onContinue)
                LucentoTextButton(text = "Back", onClick = onBack, modifier = Modifier.fillMaxWidth())
            }
            RestoreStage.SIGN_IN -> LucentoTextButton(text = "Back", onClick = onBack, modifier = Modifier.fillMaxWidth(), enabled = !connecting)
            RestoreStage.EMPTY -> LucentoPrimaryButton(text = "Set up as new", onClick = onSetUpAsNew)
            RestoreStage.FAILED -> {
                LucentoPrimaryButton(text = "Try again", onClick = onRetry)
                LucentoTextButton(text = "Set up as new instead", onClick = onSetUpAsNew, modifier = Modifier.fillMaxWidth())
            }
            RestoreStage.RESTORING, RestoreStage.NONE -> Unit
        }
    }
}

@Preview(name = "Welcome 1.3", fontScale = 1.3f, widthDp = 360, heightDp = 760)
@Preview(name = "Welcome 2.0", fontScale = 2.0f, widthDp = 360, heightDp = 760)
@Composable
private fun WelcomeFontScalePreview() {
    LucentoTheme {
        Column(Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.gutter)) {
            StepLabel(FirstRunStep.WELCOME)
            WelcomeStep(onContinue = {}, onRestore = {}, usageOptIn = false, onUsageOptIn = {})
        }
    }
}

@Preview(name = "Currency 1.3", fontScale = 1.3f, widthDp = 360, heightDp = 760)
@Preview(name = "Wallet 2.0", fontScale = 2.0f, widthDp = 360, heightDp = 760)
@Composable
private fun SetupFontScalePreview() {
    LucentoTheme {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(Spacing.gutter)) {
            StepLabel(FirstRunStep.WALLET)
            WalletStep(currency = "PHP", error = null, onContinue = { _, _ -> })
        }
    }
}
