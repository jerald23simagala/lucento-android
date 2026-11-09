package com.lucento.app.feature.onboarding

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Mic
import com.lucento.app.core.designsystem.component.ActionDock
import com.lucento.app.core.designsystem.component.CheckMark
import com.lucento.app.core.designsystem.component.ChoiceCard
import com.lucento.app.core.designsystem.component.DockCount
import com.lucento.app.core.designsystem.component.FactTick
import com.lucento.app.core.designsystem.component.GroupLabel
import com.lucento.app.core.designsystem.component.LucentoMark
import com.lucento.app.core.designsystem.component.LucentoWordmark
import com.lucento.app.core.designsystem.component.OptionRow
import com.lucento.app.core.designsystem.component.OptionTile
import com.lucento.app.core.designsystem.component.PathRail
import com.lucento.app.core.designsystem.component.SetupTitle
import com.lucento.app.core.designsystem.component.SpotIcon
import com.lucento.app.core.designsystem.component.SummaryTile
import com.lucento.app.core.designsystem.component.icon
import com.lucento.app.core.designsystem.theme.TonePair
import com.lucento.app.core.designsystem.component.AmountsShown
import com.lucento.app.core.designsystem.component.SheetHeader
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.foundation.layout.offset
import com.lucento.app.core.designsystem.component.StageIndicator
import com.lucento.app.core.designsystem.component.IconTile
import com.lucento.app.core.designsystem.theme.Hierarchy
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.domain.SetupProgress
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material.icons.outlined.TrackChanges
import com.lucento.app.core.designsystem.component.WalletTypeTile
import com.lucento.app.domain.SpendType

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.lucento.app.core.designsystem.component.LucentoCard
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.ConfirmDialog
import com.lucento.app.core.designsystem.component.CurrencyList
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoWorkingIndicator
import com.lucento.app.core.designsystem.component.LucentoStackedLockup
import com.lucento.app.core.designsystem.component.LucentoMascot
import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.SwitchRow
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.data.preferences.ThemeMode
import com.lucento.app.domain.Feature
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.SolidColor
import com.lucento.app.core.designsystem.component.IllustratedSurface
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.domain.FinancialFocus
import com.lucento.app.feature.budget.CategoryPicks
import com.lucento.app.feature.budget.CategoryPresetPicker
import com.lucento.app.feature.budget.CategoryReview
import com.lucento.app.core.designsystem.component.IconFieldHeader
import com.lucento.app.data.sharedspace.body
import com.lucento.app.data.sharedspace.title

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.Factory),
    /** The profile photo actions, so a Google sign-in on the sync step can offer the account's picture. */
    photo: com.lucento.app.feature.profile.ProfilePhotoState = com.lucento.app.feature.profile.ProfilePhotoState(),
) = AmountsShown {
    val step by viewModel.step.collectAsStateWithLifecycle()
    val furthestStep by viewModel.furthestStep.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    val focuses by viewModel.focuses.collectAsStateWithLifecycle()
    val features by viewModel.features.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val picks by viewModel.picks.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val signedInEmail by viewModel.signedInEmail.collectAsStateWithLifecycle()
    val connecting by viewModel.connecting.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()
    val restoreStage by viewModel.restoreStage.collectAsStateWithLifecycle()
    val joiningByInvitation by viewModel.joiningByInvitation.collectAsStateWithLifecycle()
    val restoreMessage by viewModel.restoreMessage.collectAsStateWithLifecycle()
    val switchPrompt by viewModel.switchPrompt.collectAsStateWithLifecycle()
    val accountCheck by viewModel.accountCheck.collectAsStateWithLifecycle()
    val welcomeBack by viewModel.welcomeBack.collectAsStateWithLifecycle()
    val holdsAccountData by viewModel.holdsAccountData.collectAsStateWithLifecycle()
    val spendTypes by viewModel.spendTypes.collectAsStateWithLifecycle()
    val backupPath by viewModel.backupPath.collectAsStateWithLifecycle()
    val filePreview by viewModel.filePreview.collectAsStateWithLifecycle()
    val fileMessage by viewModel.fileMessage.collectAsStateWithLifecycle()
    val fileBusy by viewModel.fileBusy.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    // "Restore a backup file": Android's file picker; the file is read only after the user picks it.
    val backupPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.inspectBackupFile {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: error("unreadable")
            }
        }
    }
    filePreview?.let { preview ->
        ConfirmDialog(
            title = "Restore this backup?",
            text = "This backup holds:\n" + preview.summary().trim() +
                "\n\nLucento opens with it on this phone. Nothing is uploaded; you can turn on sync later in Settings.",
            confirmLabel = if (fileBusy) "Restoring…" else "Restore",
            onConfirm = viewModel::restoreBackupFile,
            onDismiss = viewModel::cancelBackupFile,
        )
    }

    // Leaving the account when part of it is already here removes that partial copy, so it is confirmed first.
    var confirmLeave by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val onLeaveAccount = { if (holdsAccountData) confirmLeave = true else viewModel.leaveAccount() }
    if (confirmLeave) {
        ConfirmDialog(
            title = "Use Lucento without this account?",
            text = "Everything stays safe in your account. The part that was already downloaded is removed from this " +
                "phone, and you set Lucento up without syncing. Sign in again any time to bring it all back.",
            confirmLabel = "Continue without it",
            onConfirm = {
                confirmLeave = false
                viewModel.leaveAccount()
            },
            onDismiss = { confirmLeave = false },
        )
    }

    // Anonymous usage counts (opt-in in Settings): one onboarding_step_viewed per screen. Draws nothing.
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

    // A new account becoming ready: "Check your inbox" until the email is verified, then "Email verified" (or, after
    // Google, "You're connected") before Lucento moves on. Shown over whichever screen started it.
    val verification by viewModel.verification.state.collectAsStateWithLifecycle()
    val accountReady by viewModel.accountReady.collectAsStateWithLifecycle()
    com.lucento.app.feature.account.AccountStatusDialog(
        waiting = verification,
        ready = accountReady,
        onVisible = viewModel.verification::onVisible,
        onResend = viewModel.verification::resendEmail,
        onChangeEmail = viewModel.verification::cancel,
        onPassword = viewModel.verification::providePassword,
        onContinue = viewModel::continueFromAccount,
    )

    BackHandler(enabled = step > 0 && restoreStage == RestoreStage.NONE) { viewModel.back() }
    BackHandler(enabled = restoreStage != RestoreStage.NONE && restoreStage != RestoreStage.RESTORING) { viewModel.cancelRestore() }

    // Lucento Shared: why a Shared Space just left this phone. The person's own account is checked once it's read.
    val spaceNotice by viewModel.spaceNotice.collectAsStateWithLifecycle()
    spaceNotice?.let { notice ->
        ConfirmDialog(
            title = notice.title,
            text = notice.body,
            confirmLabel = "OK",
            dismissLabel = null,
            onConfirm = viewModel::acknowledgeSpaceNotice,
            onDismiss = viewModel::acknowledgeSpaceNotice,
        )
    }

    // Lucento Shared: invited before setting anything up. Joining replaces the setup; the other way continues it.
    val invitationOffer by viewModel.invitationOffer.collectAsStateWithLifecycle()
    val joiningSpace by viewModel.joiningSpace.collectAsStateWithLifecycle()
    val offerBusy by viewModel.offerBusy.collectAsStateWithLifecycle()
    val offerMessage by viewModel.offerMessage.collectAsStateWithLifecycle()
    val spaceName by viewModel.spaceNameSuggestion.collectAsStateWithLifecycle()
    val spaceOwner by viewModel.spaceOwner.collectAsStateWithLifecycle()
    invitationOffer?.let { invitation ->
        BackHandler { if (!joiningSpace) viewModel.setInvitationAside() }
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding(),
        ) {
            com.lucento.app.feature.sharedspace.InvitationReview(
                invitation = invitation,
                suggestedName = spaceName,
                unsynced = 0,
                joining = joiningSpace,
                busy = offerBusy,
                message = offerMessage,
                onJoin = viewModel::joinInvitation,
                onDecline = viewModel::declineInvitation,
                onBack = viewModel::setInvitationAside,
                notNowLabel = "Set up my own Lucento",
            )
        }
        return@AmountsShown
    }

    // Restoring an account on a new phone replaces the story with its own short flow.
    if (restoreStage != RestoreStage.NONE) {
        RestoreFlow(
            spaceOwner = spaceOwner,
            invited = joiningByInvitation,
            stage = restoreStage,
            welcomeBack = welcomeBack,
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
            holdsAccountData = holdsAccountData,
            onLeaveAccount = onLeaveAccount,
        )
        return@AmountsShown
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        onboardingStage(step)?.let { stage ->
            // Sunrise Path: back, the stage on screen with its screen count ("Personalize · 2 of 5"), the furthest stage
            // reached (going back keeps finished stages finished) and Skip on the optional questions — all from the
            // onboarding's own saved state.
            val (screen, screens) = onboardingScreen(step, features)
            PathRail(
                SetupProgress(stage, onboardingStage(furthestStep) ?: stage, stageProgress(step), screen, screens),
                modifier = Modifier.padding(horizontal = Spacing.xs),
                onBack = viewModel::back,
                onSkip = if (step == OnboardingViewModel.STEP_FOCUS || step == OnboardingViewModel.STEP_SPEND) viewModel::skip else null,
                skipDescription = "Skip to spending categories",
            )
        }

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally(tween(320)) { w -> if (forward) w / 6 else -w / 6 } + fadeIn(tween(320)))
                    .togetherWith(slideOutHorizontally(tween(220)) { w -> if (forward) -w / 6 else w / 6 } + fadeOut(tween(180)))
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "onboarding-step",
        ) { target ->
          // Every step but the welcome sits inside the gutter; the welcome's landscape runs edge to edge.
          Box(Modifier.fillMaxSize().then(if (target == OnboardingViewModel.STEP_WELCOME) Modifier else Modifier.padding(horizontal = Spacing.gutter))) {
            when (target) {
                OnboardingViewModel.STEP_WELCOME -> WelcomeStep(
                    onStartFresh = viewModel::next,
                    onSignIn = if (viewModel.syncAvailable) viewModel::startRestore else null,
                    onInvited = if (viewModel.syncAvailable) viewModel::startInvited else null,
                    onRestoreFile = if (viewModel.fileRestoreAvailable) {
                        { backupPicker.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
                    } else {
                        null
                    },
                    fileMessage = fileMessage,
                    fileBusy = fileBusy,
                )
                OnboardingViewModel.STEP_FOCUS -> FocusStep(focuses, viewModel::toggleFocus, viewModel::next)
                OnboardingViewModel.STEP_SPEND -> SpendStep(spendTypes, viewModel::toggleSpendType, viewModel::next)
                OnboardingViewModel.STEP_CATEGORIES -> CategoriesStep(picks, viewModel::setPicks, viewModel::next)
                OnboardingViewModel.STEP_REVIEW -> CategoryReviewStep(
                    picks = picks,
                    onChange = viewModel::setPicks,
                    onEdit = { viewModel.goTo(OnboardingViewModel.STEP_CATEGORIES) },
                    onContinue = viewModel::next,
                )
                OnboardingViewModel.STEP_ESSENTIALS -> EssentialsStep(
                    currency = currency,
                    onCurrency = viewModel::selectCurrency,
                    theme = theme,
                    onTheme = viewModel::selectTheme,
                    onContinue = viewModel::next,
                )
                OnboardingViewModel.STEP_SYNC -> SyncStep(
                    available = viewModel.syncAvailable,
                    googleAvailable = viewModel.googleAvailable,
                    signedInEmail = signedInEmail,
                    connecting = connecting,
                    message = syncMessage,
                    accountCheck = accountCheck,
                    onGoogle = { viewModel.connectGoogle(context) },
                    onEmail = viewModel::connectEmail,
                    onPrepareEmail = viewModel::prepareConnection,
                    holdsAccountData = holdsAccountData,
                    onRetryCheck = viewModel::retryAccountCheck,
                    onContinueOffline = onLeaveAccount,
                    onContinue = viewModel::next,
                    path = backupPath,
                    onPath = viewModel::selectBackupPath,
                    photo = photo,
                    onBack = viewModel::back,
                )
                else -> ConfirmStep(
                    currency, theme, features, picks, error, signedInEmail,
                    spendTypes = spendTypes,
                    accountUnavailable = signedInEmail != null && accountCheck == AccountCheck.Unavailable,
                    holdsAccountData = holdsAccountData,
                    onLeaveAccount = onLeaveAccount,
                    onFinish = viewModel::finish,
                    onEdit = viewModel::goTo,
                )
            }
          }
        }
    }
}

/**
 * Which screen of its stage [step] is, for the path rail: Personalize is the five questions; Set up begins here with the
 * summary (screen 1), then counts the guided-setup pages this person's modules switch on.
 */
private fun onboardingScreen(step: Int, features: Set<Feature>): Pair<Int, Int> = when (onboardingStage(step)) {
    com.lucento.app.domain.SetupStage.PERSONALIZE ->
        (step - OnboardingViewModel.STEP_FOCUS + 1) to (OnboardingViewModel.STEP_ESSENTIALS - OnboardingViewModel.STEP_FOCUS + 1)
    com.lucento.app.domain.SetupStage.SET_UP -> 1 to com.lucento.app.domain.GuidedSetup.setUpScreens(com.lucento.app.domain.FeatureSet(features))
    else -> 0 to 0
}

/** How far through its stage a screen is (0..1), so the current stage's bar fills as the user moves on. */
private fun stageProgress(step: Int): Float = when (onboardingStage(step)) {
    com.lucento.app.domain.SetupStage.PERSONALIZE ->
        (step - OnboardingViewModel.STEP_FOCUS + 1f) / (OnboardingViewModel.STEP_ESSENTIALS - OnboardingViewModel.STEP_FOCUS + 1f)
    // Set up continues in the guided setup (wallets, budget): onboarding's part of it is the first third.
    com.lucento.app.domain.SetupStage.SET_UP -> if (step == OnboardingViewModel.STEP_SYNC) 0.15f else 0.3f
    else -> 0f
}

/** A step's headline and what it's for. No icon tile above it: the path rail already names the step. */
@Composable
private fun StepTitle(title: String, body: String?) {
    SetupTitle(title, body)
    Spacer(Modifier.height(22.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WelcomeStep(
    onStartFresh: () -> Unit,
    /** Sign in to an account that already has data; null when cloud sync isn't available in this build. */
    onSignIn: (() -> Unit)?,
    /** Lucento Shared: sign in with the account an invitation was sent to; null without cloud sync. */
    onInvited: (() -> Unit)?,
    /** Pick a backup file exported from Lucento; null when unavailable. */
    onRestoreFile: (() -> Unit)?,
    fileMessage: String?,
    fileBusy: Boolean,
) {
    val spec = LucentoTheme.spec
    val background = MaterialTheme.colorScheme.background
    Column(Modifier.fillMaxSize()) {
        // Sunrise Path: the theme's landscape fills the top and Lucento waves from inside it. On a short screen or at
        // large text the scene gives way first, so the words and the ways in always stay on screen.
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val compact = maxHeight < 300.dp
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(spec.welcomeArt),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                alignment = androidx.compose.ui.BiasAlignment(-0.24f, 1f),
                modifier = Modifier.fillMaxSize(),
            )
            if (LucentoTheme.isDark && !spec.sceneryIsNight) {
                Box(Modifier.fillMaxSize().background(background.copy(alpha = 0.45f)))
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.42f)
                    .background(Brush.verticalGradient(listOf(background.copy(alpha = 0f), background))),
            )
            Row(
                Modifier.align(Alignment.TopStart).padding(start = 24.dp, top = Spacing.lg).semantics(mergeDescendants = true) { heading() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LucentoMark(size = 34.dp, animate = true)
                Spacer(Modifier.width(Spacing.sm))
                LucentoWordmark(height = 22.dp)
            }
            if (maxHeight >= 180.dp) {
                LucentoMascot(
                    size = if (compact) 104.dp else minOf(176.dp, maxHeight * 0.55f),
                    mood = MascotMood.Greeting,
                    contentDescription = "Lucento, waving hello with a bright torch",
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.xs),
                )
            }
        }
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text("Financial clarity, without the complexity.", style = Hierarchy.milestoneTitle)
            Spacer(Modifier.height(10.dp))
            Text(
                "Track what you spend and plan what’s next. Your records stay on this phone unless you turn on sync.",
                style = Hierarchy.lead,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (fileMessage != null) {
                Spacer(Modifier.height(Spacing.sm))
                InfoNote(fileMessage, container = MaterialTheme.colorScheme.errorContainer, content = MaterialTheme.colorScheme.onErrorContainer)
            }
            if (fileBusy) LucentoWorkingIndicator(label = "Reading the backup")
            // Ways in (owner, 2026-11-06): new to Lucento; coming back to an account; invited to someone's Shared Space; a
            // backup file. The last two are rarer, so they sit as a quiet link row under the two buttons.
            ActionDock(fade = false) {
                LucentoPrimaryButton(text = "Get started", onClick = onStartFresh, trailingIcon = Icons.AutoMirrored.Outlined.ArrowForward)
                if (onSignIn != null) {
                    com.lucento.app.core.designsystem.component.LucentoSecondaryButton(text = "I already have Lucento", onClick = onSignIn)
                }
                if (onInvited != null || onRestoreFile != null) {
                    androidx.compose.foundation.layout.FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        if (onInvited != null) QuietLink("I was invited", Icons.Outlined.Group, onInvited, spoken = "I was invited to share")
                        // No separator glyph: when the row wraps (large text) one would be left dangling.
                        if (onInvited != null && onRestoreFile != null) Spacer(Modifier.width(Spacing.sm))
                        if (onRestoreFile != null) QuietLink("Restore a backup", Icons.Outlined.UploadFile, onRestoreFile, spoken = "Restore a backup file")
                    }
                }
            }
        }
    }
}

/** A small link with its glyph (the welcome's rarer ways in). */
@Composable
private fun QuietLink(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, spoken: String = text) {
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .heightIn(min = 48.dp)
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary)
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
    welcomeBack: Boolean = false,
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
    /** Part of the account is already on this phone (an interrupted restore). */
    holdsAccountData: Boolean = false,
    /** The way out when the account can't be brought across: set Lucento up without it. */
    onLeaveAccount: () -> Unit = {},
    /** Lucento Shared: the Owner's name when the download is their Shared Space rather than the person's own data. */
    spaceOwner: String? = null,
    /** Lucento Shared: came in through "I was invited to share". */
    invited: Boolean = false,
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
                    if (invited) {
                        StepTitle(
                            "Sign in to join",
                            "Use the Google or email account your invitation was sent to. You’ll see the invitation right " +
                                "after — nothing is shared until you accept it.",
                        )
                    } else {
                        StepTitle("Sign in to your account", "Use the same Google or email account as on your old phone.")
                        Spacer(Modifier.height(Spacing.sm))
                        InfoNote(
                            "Moving from another phone? Sync it first (Settings › Cloud sync › Sync now): anything added there " +
                                "since its last sync won’t come across.",
                            icon = Icons.Outlined.WarningAmber,
                            container = ext.warningContainer,
                            content = ext.warning,
                        )
                    }
                    Spacer(Modifier.height(Spacing.md))
                    // "I already have Lucento" signs in to an account that exists; an invitation may be for someone new
                    // to Lucento, so that path can create the account too.
                    com.lucento.app.feature.settings.ConnectAccountPanel(
                        busy = connecting,
                        googleAvailable = googleAvailable,
                        onGoogle = onGoogle,
                        onEmail = onEmail,
                        onPrepareEmail = onPrepareEmail,
                        emailModes = if (invited) {
                            listOf(com.lucento.app.feature.settings.EmailMode.SIGN_IN, com.lucento.app.feature.settings.EmailMode.CREATE)
                        } else {
                            listOf(com.lucento.app.feature.settings.EmailMode.SIGN_IN)
                        },
                    )
                }
                RestoreStage.RESTORING -> {
                    if (spaceOwner != null) {
                        StepTitle(
                            "Opening $spaceOwner’s Shared Space",
                            "${signedInEmail?.takeIf { it.isNotEmpty() } ?: "Your account"} shares $spaceOwner’s finances, " +
                                "so this phone downloads them. Keep Lucento open — this can take a moment on a slow connection.",
                        )
                    } else if (welcomeBack) {
                        // A returning account recognised on the setup path: say why setup changed course.
                        StepTitle(
                            "Welcome back",
                            "${signedInEmail?.takeIf { it.isNotEmpty() } ?: "Your account"} already has your Lucento data, " +
                                "so we’re bringing it to this phone instead of starting over. Keep Lucento open — this can " +
                                "take a moment on a slow connection.",
                        )
                    } else {
                        StepTitle(
                            "Bringing your money across",
                            "Downloading ${signedInEmail?.takeIf { it.isNotEmpty() } ?: "your account"}. Keep Lucento open — " +
                                "this can take a moment on a slow connection.",
                        )
                    }
                    Spacer(Modifier.height(Spacing.xl))
                    LucentoWorkingIndicator(label = "Restoring your account")
                }
                RestoreStage.EMPTY -> {
                    LucentoMascot(size = 88.dp, mood = MascotMood.Curious, contentDescription = "Lucento")
                    Spacer(Modifier.height(Spacing.md))
                    if (invited) StepTitle(
                        "No invitation for this account yet",
                        "${signedInEmail?.takeIf { it.isNotEmpty() } ?: "This account"} has no invitation waiting. Ask the " +
                            "person who invited you to check the email address, or set Lucento up as your own for now — an " +
                            "invitation that arrives later shows up on Home.",
                    ) else StepTitle(
                        "Nothing to restore yet",
                        "${signedInEmail?.takeIf { it.isNotEmpty() } ?: "This account"} has no wallets, spending categories, " +
                            "transactions, budgets or scheduled payments saved yet. " +
                            "Set Lucento up as new — you stay connected, and it syncs when you finish.",
                    )
                }
                RestoreStage.FAILED -> {
                    // Part of the account may already be here (the download isn't all-or-nothing), so don't say otherwise.
                    StepTitle(
                        "Couldn’t finish bringing your data across",
                        "Everything is still safe in your account. Some of it may already be on this phone — try again to " +
                            "finish. Nothing is lost or duplicated by trying again.",
                    )
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
            RestoreStage.SIGN_IN -> if (holdsAccountData) {
                // Signed out part-way through a restore: Back would only lead here again, so offer the real way out.
                LucentoTextButton(
                    text = "Use Lucento without this account",
                    onClick = onLeaveAccount,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !connecting,
                )
            } else {
                LucentoTextButton(text = "Back", onClick = onBack, modifier = Modifier.fillMaxWidth(), enabled = !connecting)
            }
            RestoreStage.EMPTY -> LucentoPrimaryButton(text = "Set up as new", onClick = onSetUpAsNew)
            RestoreStage.FAILED -> {
                LucentoPrimaryButton(text = "Try again", onClick = onRetry)
                // Not "set up as new": this account has data, and a second setup on top of it would duplicate it. The
                // way out disconnects the account instead (and removes any part already downloaded), so nobody is stuck.
                LucentoTextButton(text = "Use Lucento without this account", onClick = onLeaveAccount, modifier = Modifier.fillMaxWidth())
            }
            RestoreStage.RESTORING, RestoreStage.NONE -> Unit
        }
    }
}

@Composable
private fun FocusStep(selected: Set<FinancialFocus>, onToggle: (FinancialFocus) -> Unit, onContinue: () -> Unit) {
    val optional = FinancialFocus.entries.filterNot { it.mandatory }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            StepTitle(
                "What should Lucento help with?",
                "Pick what matters now. This only decides what’s switched on at first — change it any time in Settings.",
            )
            // Tracking spending and budgeting are what Lucento is for: stated plainly, apart from the real choices.
            GroupLabel("Always included")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(vertical = Spacing.xs),
            ) {
                FinancialFocus.entries.filter { it.mandatory }.forEach { IncludedRow(it) }
            }
            Spacer(Modifier.height(Spacing.lg))
            val added = optional.count { it in selected }
            GroupLabel("Add if you like", trailing = if (added > 0) "$added added" else null)
            LucentoCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                optional.forEachIndexed { i, focus ->
                    OptionRow(
                        icon = focus.icon,
                        tone = focus.tone(),
                        title = focus.title,
                        body = focus.description,
                        checked = focus in selected,
                        onToggle = { onToggle(focus) },
                    )
                    if (i < optional.lastIndex) Hairline(inset = Spacing.lg + Sizes.iconTile + 14.dp)
                }
            }
            Spacer(Modifier.height(Spacing.md))
        }
        ActionDock { LucentoPrimaryButton(text = "Continue", onClick = onContinue, trailingIcon = Icons.AutoMirrored.Outlined.ArrowForward) }
    }
}

/** An always-on module: its glyph, its name and an "Included" tag. Information only. */
@Composable
private fun IncludedRow(focus: FinancialFocus) {
    val tone = focus.tone()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clearAndSetSemantics { contentDescription = "${focus.title}, always included" }
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(focus.icon, container = tone.container, content = tone.content, size = 32.dp, shape = MaterialTheme.shapes.small)
        Spacer(Modifier.width(14.dp))
        Text(focus.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = Spacing.sm, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(3.dp))
            Text("Included", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Each module's tone, matching the colours its screens use elsewhere. */
@Composable
private fun FinancialFocus.tone(): TonePair {
    val ext = LucentoTheme.extendedColors
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        FinancialFocus.EVERYDAY_SPENDING, FinancialFocus.BUDGETS -> TonePair(scheme.primaryContainer, scheme.onPrimaryContainer)
        FinancialFocus.SAVINGS -> ext.savings
        FinancialFocus.DEBTS -> ext.credit
        FinancialFocus.RECURRING -> ext.loan
        FinancialFocus.TRENDS -> ext.eWallet
    }
}


/** What each focus is about, with the same icons the dashboard uses for these modules. */
private val FinancialFocus.icon: androidx.compose.ui.graphics.vector.ImageVector
    get() = when (this) {
        FinancialFocus.EVERYDAY_SPENDING -> Icons.AutoMirrored.Outlined.ReceiptLong
        FinancialFocus.BUDGETS -> Icons.Outlined.DonutLarge
        FinancialFocus.SAVINGS -> Icons.Outlined.TrackChanges
        FinancialFocus.DEBTS -> Icons.Outlined.CreditCard
        FinancialFocus.RECURRING -> Icons.Outlined.EventRepeat
        FinancialFocus.TRENDS -> Icons.Outlined.BarChart
    }


@Composable
private fun SpendStep(selected: Set<SpendType>, onToggle: (SpendType) -> Unit, onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            StepTitle("How do you pay?", "Choose all that apply. We’ll suggest these accounts when you set up; you can add others any time.")
            // Ways to pay are things you hold, so they're tiles in their wallet type's colour, two to a row.
            SpendType.entries.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { type ->
                        OptionTile(
                            icon = type.walletType.icon,
                            tone = LucentoTheme.extendedColors.tone(type.walletType),
                            title = type.title,
                            body = type.description,
                            checked = type in selected,
                            onToggle = { onToggle(type) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            wide = pair.size == 1,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Row(Modifier.padding(horizontal = Spacing.xs), verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text("Credit cards and loans also switch on Debts & loans.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(Spacing.md))
        }
        ActionDock {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val chosen = SpendType.entries.filter { it in selected }
                DockCount(
                    if (chosen.isEmpty()) "None chosen" else "${chosen.size} chosen",
                    if (chosen.isEmpty()) "Add accounts later" else chosen.joinToString { it.shortName }.replaceFirstChar { it.uppercase() },
                    Modifier.widthIn(max = 150.dp),
                )
                LucentoPrimaryButton(text = "Continue", onClick = onContinue, modifier = Modifier.weight(1f), trailingIcon = Icons.AutoMirrored.Outlined.ArrowForward)
            }
        }
    }
}

/** A way to pay in a few words, for the dock's count line. */
private val SpendType.shortName: String
    get() = when (this) {
        SpendType.CASH -> "cash"
        SpendType.BANK -> "bank"
        SpendType.E_WALLET -> "e-wallet"
        SpendType.CREDIT_CARD -> "credit card"
        SpendType.LOAN -> "loans"
    }

@Composable
private fun CategoriesStep(picks: CategoryPicks, onChange: (CategoryPicks) -> Unit, onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            StepTitle(
                "What do you spend on?",
                "We’ve started you with everyday essentials — add or remove anything. Your budget is built from these; amounts come later.",
            )
            CategoryPresetPicker(picks = picks, onChange = onChange)
            Spacer(Modifier.height(Spacing.lg))
        }
        ActionDock {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val groups = com.lucento.app.domain.CategoryPresets.groups.count { g -> g.presets.any { it.key in picks.presetKeys } } +
                    if (picks.customNames.isNotEmpty() || picks.existingIds.isNotEmpty()) 1 else 0
                DockCount(
                    if (picks.count == 0) "None chosen" else "${picks.count} chosen",
                    if (picks.count == 0) "Choose at least one" else "Across $groups ${if (groups == 1) "group" else "groups"}",
                    Modifier.widthIn(max = 150.dp),
                )
                LucentoPrimaryButton(
                    text = if (picks.count == 0) "Choose one" else "Review",
                    onClick = onContinue,
                    enabled = picks.count > 0,
                    modifier = Modifier.weight(1f),
                    trailingIcon = if (picks.count > 0) Icons.AutoMirrored.Outlined.ArrowForward else null,
                )
            }
        }
    }
}

@Composable
private fun CategoryReviewStep(
    picks: CategoryPicks,
    onChange: (CategoryPicks) -> Unit,
    onEdit: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            StepTitle(
                if (picks.count == 0) "Your categories" else "Your ${picks.count} ${if (picks.count == 1) "category" else "categories"}",
                "Tap × to remove one. Rename, reorder or add more any time on the Budget tab.",
            )
            LucentoCard(Modifier.fillMaxWidth(), contentPadding = Spacing.lg) {
                CategoryReview(picks = picks, onChange = onChange)
            }
            Spacer(Modifier.height(Spacing.md))
        }
        ActionDock {
            LucentoPrimaryButton(
                text = if (picks.count == 0) "Choose at least one category" else "Confirm ${picks.count} ${if (picks.count == 1) "category" else "categories"}",
                onClick = onContinue,
                enabled = picks.count > 0,
            )
            LucentoTextButton(text = "Add or change categories", onClick = onEdit, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun EssentialsStep(
    currency: String,
    onCurrency: (String) -> Unit,
    theme: ThemeMode,
    onTheme: (ThemeMode) -> Unit,
    onContinue: () -> Unit,
) {
    Column(Modifier.fillMaxSize().imePadding()) {
        // The one choice that can't be undone comes first, with its warning beside it; the full list follows search.
        // Everything above the search scrolls with the currencies, so a short screen at large text still has room.
        CurrencyList(
            selected = currency,
            onSelect = onCurrency,
            modifier = Modifier.weight(1f),
            header = {
                item(key = "essentials-header") {
                    Column {
                        SetupTitle("Currency and look", null)
                        Spacer(Modifier.height(Spacing.lg))
                        GroupLabel("Main currency")
                        MainCurrencyCard(currency)
                        Row(Modifier.padding(start = Spacing.xs, end = Spacing.xs, top = 10.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Outlined.Lock, contentDescription = null, tint = LucentoTheme.extendedColors.accentText, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Every total uses this currency, and it can’t be changed later. Wallets can still use other " +
                                    "currencies — they’re converted into it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(Spacing.xl))
                        GroupLabel("Appearance")
                        ThemePreviews(theme, onTheme)
                        Spacer(Modifier.height(Spacing.xl))
                        GroupLabel("Change currency")
                    }
                }
            },
        )
        ActionDock { LucentoPrimaryButton(text = "Continue", onClick = onContinue, trailingIcon = Icons.AutoMirrored.Outlined.ArrowForward) }
    }
}

/** The chosen main currency as one clear card: its symbol, its name and its code. */
@Composable
private fun MainCurrencyCard(code: String) {
    val ext = LucentoTheme.extendedColors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.5.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large)
            .semantics(mergeDescendants = true) {}
            .padding(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape).background(ext.highlight), contentAlignment = Alignment.Center) {
            Text(MoneyFormatter.currencySymbol(code), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = ext.onHighlight, maxLines = 1)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(MoneyFormatter.currencyName(code), style = MaterialTheme.typography.titleMedium)
            Text("$code · your main currency", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Match phone, Light or Dark as three small previews of the app, so the choice is seen rather than read. */
@Composable
private fun ThemePreviews(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val haptics = rememberHaptics()
    val options = listOf(ThemeMode.SYSTEM to "Match phone", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark")
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { (mode, label) ->
            val isSelected = mode == selected
            val shape = RoundedCornerShape(16.dp)
            Column(
                Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .selectable(selected = isSelected, role = Role.RadioButton) {
                        haptics.tick()
                        onSelect(mode)
                    }
                    .padding(bottom = Spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(108.dp)
                        .clip(shape)
                        .border(
                            if (isSelected) 2.dp else 1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else LucentoTheme.extendedColors.borderDefault,
                            shape,
                        ),
                ) {
                    when (mode) {
                        ThemeMode.SYSTEM -> Row(Modifier.fillMaxSize()) {
                            MiniApp(com.lucento.app.core.designsystem.theme.LightColors, Modifier.weight(1f))
                            MiniApp(com.lucento.app.core.designsystem.theme.DarkColors, Modifier.weight(1f))
                        }
                        ThemeMode.LIGHT -> MiniApp(com.lucento.app.core.designsystem.theme.LightColors, Modifier.fillMaxSize())
                        ThemeMode.DARK -> MiniApp(com.lucento.app.core.designsystem.theme.DarkColors, Modifier.fillMaxSize())
                    }
                    if (isSelected) CheckMark(true, Modifier.align(Alignment.BottomEnd).padding(7.dp), size = 22.dp)
                }
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else null,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** A thumbnail of the app in [colors]: a header block, a line and a row. Decorative. */
@Composable
private fun MiniApp(colors: androidx.compose.material3.ColorScheme, modifier: Modifier) {
    Column(modifier.fillMaxHeight().background(colors.background).padding(10.dp)) {
        Box(Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(7.dp)).background(colors.primary))
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(0.7f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(colors.outlineVariant))
        Spacer(Modifier.height(7.dp))
        Box(Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(5.dp)).background(colors.primaryContainer))
    }
}

/**
 * After a Google sign-in the account's picture becomes the profile photo by default (owner, 2026-11-05; AppViewModel
 * fetches it once and keeps it only on this phone). This card says so while it arrives and once it's there; it can be
 * changed or removed from the profile menu. Nothing shows if it couldn't be fetched.
 */
@Composable
private fun GooglePhotoOffer(photo: com.lucento.app.feature.profile.ProfilePhotoState) {
    val done = photo.image != null
    if (!done && !photo.busy) return
    com.lucento.app.core.designsystem.component.LucentoCard(Modifier.fillMaxWidth(), contentPadding = Spacing.md) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                com.lucento.app.core.designsystem.component.ProfileAvatar(null, size = 56.dp, photo = photo.image)
                if (photo.busy) androidx.compose.material3.CircularProgressIndicator(Modifier.size(56.dp), strokeWidth = 2.5.dp)
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    if (done) "Your Google photo is on your profile" else "Getting your Google photo…",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    if (done) "It’s kept only on this phone. Change or remove it any time from the profile menu."
                    else "It’s kept only on this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * "Keep your money safe" — the first question after Get started (owner, 2026-11-06). Two answers, each opening what
 * follows from it: **Sync with an account** offers Google, or an email account made on its own page (create only: this
 * path is for people new to Lucento; returning ones use "I already have Lucento"); **Keep it on this phone only** shows
 * Continue. Nothing leaves the phone until an account is connected, and sync itself starts when setup finishes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SyncStep(
    available: Boolean,
    googleAvailable: Boolean,
    signedInEmail: String?,
    connecting: Boolean,
    message: String?,
    accountCheck: AccountCheck,
    holdsAccountData: Boolean,
    onGoogle: () -> Unit,
    onEmail: (String, String, com.lucento.app.feature.settings.EmailMode) -> Unit,
    onPrepareEmail: () -> Unit,
    onRetryCheck: () -> Unit,
    onContinueOffline: () -> Unit,
    onContinue: () -> Unit,
    path: BackupPath?,
    onPath: (BackupPath) -> Unit,
    photo: com.lucento.app.feature.profile.ProfilePhotoState = com.lucento.app.feature.profile.ProfilePhotoState(),
    onBack: () -> Unit = {},
) {
    // Without cloud sync in this build, only this phone is offered.
    val shownPath = if (!available) BackupPath.DEVICE_ONLY else path
    var emailPage by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var legal by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<com.lucento.app.feature.legal.LegalDoc?>(null) }
    legal?.let { com.lucento.app.feature.legal.LegalSheet(it, onDismiss = { legal = null }) }
    val onEmailPage = emailPage && signedInEmail == null && available
    // Back from the account form returns to the two choices, not to the welcome screen.
    BackHandler(enabled = onEmailPage && !connecting) { emailPage = false }

    AnimatedContent(
        targetState = onEmailPage,
        transitionSpec = {
            val forward = targetState
            (slideInHorizontally(tween(300)) { w -> if (forward) w / 6 else -w / 6 } + fadeIn(tween(300)))
                .togetherWith(slideOutHorizontally(tween(200)) { w -> if (forward) -w / 6 else w / 6 } + fadeOut(tween(160)))
        },
        modifier = Modifier.fillMaxSize(),
        label = "account-page",
    ) { showForm ->
        if (showForm) {
          Column(Modifier.fillMaxSize()) {
            PathRail(null, onBack = if (connecting) null else ({ emailPage = false }), backDescription = "Back to the choices")
            CreateAccountPage(
                googleAvailable = googleAvailable,
                connecting = connecting,
                message = message,
                onEmail = onEmail,
                onOpenLegal = { legal = it },
                onBack = { emailPage = false },
            )
          }
            return@AnimatedContent
        }
        Column(Modifier.fillMaxSize().imePadding()) {
            PathRail(null, onBack = onBack)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                SetupTitle(
                    "Keep your money safe",
                    if (signedInEmail == null) {
                        "Choose where Lucento keeps your records. You can change this any time in Settings."
                    } else {
                        "Your records are backed up to your account and stay the same on every phone you use."
                    },
                )
                if (signedInEmail == null) {
                    Spacer(Modifier.height(14.dp))
                    // Facts the app guarantees whichever card is chosen.
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FactTick("No bank login")
                        FactTick("Works offline")
                        FactTick("Private by default")
                    }
                }
                Spacer(Modifier.height(20.dp))
                when {
                    signedInEmail != null && accountCheck == AccountCheck.Checking -> LucentoWorkingIndicator(label = "Checking your account")
                    signedInEmail != null && accountCheck == AccountCheck.Unavailable -> InfoNote(
                        if (!holdsAccountData) {
                            "We couldn’t reach your account to see what’s already in it, so nothing has been set up or " +
                                "uploaded yet. Check your connection and try again — or carry on without syncing for now."
                        } else {
                            "We couldn’t reach your account, and part of it is already on this phone. Try again to finish " +
                                "bringing it across — or use Lucento without this account for now. Your account keeps everything."
                        },
                        icon = Icons.Outlined.WarningAmber,
                        container = LucentoTheme.extendedColors.warningContainer,
                        content = LucentoTheme.extendedColors.warning,
                    )
                    signedInEmail != null -> ConnectedAccount(signedInEmail, photo)
                    else -> Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (available) {
                            ChoiceCard(
                                icon = Icons.Outlined.CloudSync,
                                tone = TonePair(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer),
                                title = "Sync with an account",
                                body = "Backed up automatically and the same on every phone you use.",
                                badge = "Recommended",
                                selected = shownPath == BackupPath.ACCOUNT,
                                onSelect = { onPath(BackupPath.ACCOUNT) },
                            ) {
                                if (connecting) {
                                    com.lucento.app.core.designsystem.component.ConnectingIndicator(
                                        "Connecting your account…",
                                        Modifier.fillMaxWidth().padding(vertical = Spacing.md),
                                    )
                                } else {
                                    if (googleAvailable) com.lucento.app.feature.settings.GoogleSignInButton(onClick = onGoogle)
                                    com.lucento.app.core.designsystem.component.LucentoSecondaryButton(
                                        text = if (googleAvailable) "Use email instead" else "Create an account with email",
                                        icon = Icons.Outlined.Email,
                                        onClick = {
                                            onPrepareEmail()
                                            emailPage = true
                                        },
                                    )
                                    if (googleAvailable) com.lucento.app.feature.settings.GoogleConsentNote(onOpen = { legal = it })
                                }
                            }
                        }
                        ChoiceCard(
                            icon = Icons.Outlined.PhoneAndroid,
                            tone = TonePair(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant),
                            title = "Keep it on this phone only",
                            body = "Nothing leaves this phone. Export a backup file any time from Settings › Your data.",
                            selected = shownPath == BackupPath.DEVICE_ONLY,
                            onSelect = { onPath(BackupPath.DEVICE_ONLY) },
                        ) {
                            Text(
                                "Without a backup, losing this phone or uninstalling Lucento loses your records.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!available) InfoNote("Cloud sync isn’t available in this version of Lucento. Everything works fully offline.")
                    }
                }
                // Only beside the account choice it came from: "Keep it on this phone only" has nothing to do with it.
                if (message != null && !connecting && (signedInEmail != null || shownPath == BackupPath.ACCOUNT)) {
                    Spacer(Modifier.height(Spacing.md))
                    InfoNote(message)
                }
                Spacer(Modifier.height(Spacing.md))
            }
            ActionDock {
            when {
                available && signedInEmail != null && accountCheck == AccountCheck.Unavailable -> {
                    LucentoPrimaryButton(text = "Try again", onClick = onRetryCheck)
                    LucentoTextButton(
                        text = if (holdsAccountData) "Use Lucento without this account" else "Continue without syncing",
                        onClick = onContinueOffline,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                signedInEmail != null && accountCheck == AccountCheck.Checking -> LucentoPrimaryButton(text = "Continue", onClick = {}, enabled = false)
                signedInEmail != null -> {
                    LucentoPrimaryButton(text = "Continue", onClick = onContinue)
                    LucentoTextButton(
                        text = "Don’t sync — keep it on this phone",
                        onClick = onContinueOffline,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                shownPath == BackupPath.DEVICE_ONLY -> LucentoPrimaryButton(text = "Continue", onClick = onContinue, trailingIcon = Icons.AutoMirrored.Outlined.ArrowForward)
                // Sync chosen: the card above holds the next step (Google or email). Nothing chosen yet: just the cards.
                else -> Text(
                    "Signing in only connects sync. Lucento never asks for your bank login.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            }
        }
    }
}

/** Signed in already (came back to this step): which account, and its Google photo when there is one. */
@Composable
private fun ConnectedAccount(email: String, photo: com.lucento.app.feature.profile.ProfilePhotoState) {
    LucentoCard(Modifier.fillMaxWidth(), contentPadding = Spacing.md) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(
                Icons.Outlined.CloudDone,
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
                size = Sizes.iconTile,
                shape = MaterialTheme.shapes.small,
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text("Syncing with", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(email.ifEmpty { "Your account" }, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Sync starts when you finish setting up.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (photo.googleAvailable) {
        Spacer(Modifier.height(Spacing.md))
        GooglePhotoOffer(photo)
    }
}

/** "Use email instead": a create-only account page. Back (the path rail above it) returns to the two choices. */
@Composable
private fun CreateAccountPage(
    googleAvailable: Boolean,
    connecting: Boolean,
    message: String?,
    onEmail: (String, String, com.lucento.app.feature.settings.EmailMode) -> Unit,
    onOpenLegal: (com.lucento.app.feature.legal.LegalDoc) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            StepTitle(
                "Create your account",
                "Use an email you can open now — we’ll send a link to verify it. Your account keeps your records backed " +
                    "up and in sync.",
            )
            com.lucento.app.feature.settings.EmailAccountForm(
                modes = listOf(com.lucento.app.feature.settings.EmailMode.CREATE),
                busy = connecting,
                message = message,
                onSubmit = onEmail,
                onOpenLegal = onOpenLegal,
            )
            if (googleAvailable && !connecting) {
                Spacer(Modifier.height(Spacing.xs))
                LucentoTextButton(text = "Use Google instead", onClick = onBack, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfirmStep(
    currency: String,
    theme: ThemeMode,
    features: Set<Feature>,
    picks: CategoryPicks,
    error: String?,
    signedInEmail: String?,
    spendTypes: Set<SpendType>,
    /** Finishing couldn't check the connected account: Try again, or finish without syncing. */
    accountUnavailable: Boolean,
    holdsAccountData: Boolean,
    onLeaveAccount: () -> Unit,
    onFinish: () -> Unit,
    /** Back to an earlier step to change what it set (only backwards: [OnboardingViewModel.goTo]). */
    onEdit: (Int) -> Unit,
) {
    val ext = LucentoTheme.extendedColors
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // A milestone: Lucento stands on a sunrise-tinted banner, part of the scene rather than beside the title.
            Box(Modifier.fillMaxWidth()) {
                Column(
                    Modifier
                        .padding(top = 14.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(ext.highlight, scheme.surfaceContainerLow, scheme.primaryContainer)))
                        .padding(start = 20.dp, top = 20.dp, bottom = 20.dp, end = 124.dp),
                ) {
                    Text("Lucento is personalized", style = Hierarchy.stepTitle, modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.height(6.dp))
                    Text("Here’s how it starts. Next: your accounts and budget.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
                LucentoMascot(size = 128.dp, mood = MascotMood.Success, modifier = Modifier.align(Alignment.TopEnd))
            }
            Spacer(Modifier.height(14.dp))
            val themeLabel = when (theme) {
                ThemeMode.SYSTEM -> "Match phone"
                ThemeMode.LIGHT -> "Light"
                ThemeMode.DARK -> "Dark"
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryTile(
                    label = "Currency",
                    value = "${MoneyFormatter.currencySymbol(currency)} $currency",
                    detail = "${MoneyFormatter.currencyName(currency)} · locked",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    marker = Icons.Outlined.Lock,
                    markerTint = ext.accentText,
                )
                SummaryTile(
                    label = "Look",
                    value = themeLabel,
                    detail = if (theme == ThemeMode.SYSTEM) "Light by day, dark at night" else "Always $themeLabel".lowercase().replaceFirstChar { it.uppercase() },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    marker = Icons.Outlined.Edit,
                    onClick = { onEdit(OnboardingViewModel.STEP_ESSENTIALS) },
                    onClickLabel = "Change the look",
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val groups = com.lucento.app.domain.CategoryPresets.groups.filter { g -> g.presets.any { it.key in picks.presetKeys } }
                SummaryTile(
                    label = "Categories",
                    value = "${picks.count} chosen",
                    detail = null,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    marker = Icons.Outlined.Edit,
                    onClick = { onEdit(OnboardingViewModel.STEP_REVIEW) },
                    onClickLabel = "Change categories",
                    extra = if (groups.isEmpty()) null else {
                        {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                groups.take(3).forEach { g ->
                                    val key = g.presets.first().iconKey
                                    SpotIcon(com.lucento.app.core.designsystem.component.CategoryIcons.forKey(key), ext.categoryTone(key), size = 24.dp)
                                }
                                if (groups.size > 3) {
                                    Text("+${groups.size - 3}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = scheme.onSurfaceVariant)
                                }
                            }
                        }
                    },
                )
                val ways = SpendType.entries.filter { it in spendTypes }
                SummaryTile(
                    label = "You pay with",
                    value = if (ways.isEmpty()) "Not chosen" else null,
                    detail = if (ways.isEmpty()) "Add accounts next" else ways.joinToString { it.shortName }.replaceFirstChar { it.uppercase() },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    marker = Icons.Outlined.Edit,
                    onClick = { onEdit(OnboardingViewModel.STEP_SPEND) },
                    onClickLabel = "Change how you pay",
                    extra = if (ways.isEmpty()) null else {
                        {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ways.take(4).forEach { w ->
                                    val tone = LucentoTheme.extendedColors.tone(w.walletType)
                                    IconTile(w.walletType.icon, container = tone.container, content = tone.content, size = 32.dp, shape = MaterialTheme.shapes.small)
                                }
                            }
                        }
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
            val on = Feature.entries.filter { it in features }
            LucentoCard(Modifier.fillMaxWidth(), contentPadding = Spacing.lg) {
                Text("SWITCHED ON", style = Hierarchy.cardOverline, color = scheme.onSurfaceVariant, modifier = Modifier.semantics { contentDescription = "Switched on" })
                Spacer(Modifier.height(10.dp))
                if (on.isEmpty()) {
                    Text("Just the essentials", style = MaterialTheme.typography.bodyMedium)
                } else {
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        on.take(4).forEach { TagPill(it.title, featureIcon(it), highlighted = true) }
                        if (on.size > 4) TagPill("+${on.size - 4} more", null, highlighted = false)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(scheme.surfaceContainerLowest)
                    .border(1.dp, scheme.outlineVariant, MaterialTheme.shapes.large)
                    .clickable(role = Role.Button, onClickLabel = "Change where your records live") { onEdit(OnboardingViewModel.STEP_SYNC) }
                    .heightIn(min = 58.dp)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(
                    if (signedInEmail != null) Icons.Outlined.CloudSync else Icons.Outlined.PhoneAndroid,
                    container = scheme.surfaceContainerHigh,
                    content = scheme.onSurfaceVariant,
                    size = 32.dp,
                    shape = MaterialTheme.shapes.small,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (signedInEmail != null) "Synced with your account" else "Kept on this phone", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (signedInEmail != null) signedInEmail.ifEmpty { "Connected" } else "Export a backup any time in Settings",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = scheme.onSurfaceVariant)
            }
            // What the rest of Set up holds for this person (their modules decide), so the next minutes aren't a surprise.
            val next = com.lucento.app.domain.GuidedSetup.steps(com.lucento.app.domain.FeatureSet(features))
                .filter { it.stage == com.lucento.app.domain.SetupStage.SET_UP }
            if (next.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.lg))
                GroupLabel("Up next")
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    next.forEachIndexed { i, step ->
                        val (label, icon) = when (step) {
                            com.lucento.app.domain.GuideStep.WALLETS -> "Accounts" to Icons.Outlined.AccountBalanceWallet
                            com.lucento.app.domain.GuideStep.DEBTS -> "Cards & loans" to Icons.Outlined.CreditCard
                            com.lucento.app.domain.GuideStep.BUDGETS -> "Budget" to Icons.Outlined.DonutLarge
                            else -> "Goal" to Icons.Outlined.Flag
                        }
                        TagPill(label, icon, highlighted = i == 0)
                        if (i < next.lastIndex) {
                            Icon(
                                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = scheme.outline,
                                modifier = Modifier.size(18.dp).align(Alignment.CenterVertically),
                            )
                        }
                    }
                }
            }
            if (accountUnavailable) {
                Spacer(Modifier.height(Spacing.md))
                InfoNote(
                    if (!holdsAccountData) {
                        "We couldn’t reach your account to see what’s already in it, so nothing has been set up or " +
                            "uploaded yet. Check your connection and try again — or finish without syncing for now."
                    } else {
                        "We couldn’t reach your account, and part of it is already on this phone. Try again to finish " +
                            "bringing it across — or use Lucento without this account for now. Your account keeps everything."
                    },
                    icon = Icons.Outlined.WarningAmber,
                    container = ext.warningContainer,
                    content = ext.warning,
                )
            }
            if (error != null) {
                Spacer(Modifier.height(Spacing.md))
                InfoNote(error, container = scheme.errorContainer, content = scheme.onErrorContainer)
            }
            Spacer(Modifier.height(Spacing.md))
        }
        ActionDock {
            if (accountUnavailable) {
                LucentoPrimaryButton(text = "Try again", onClick = onFinish)
                LucentoTextButton(
                    text = if (holdsAccountData) "Use Lucento without this account" else "Finish without syncing",
                    onClick = onLeaveAccount,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LucentoPrimaryButton(text = "Continue to set up", onClick = onFinish, trailingIcon = Icons.AutoMirrored.Outlined.ArrowForward)
            }
        }
    }
}

/** A small rounded tag with an optional glyph; [highlighted] tags are pale sky. */
@Composable
private fun TagPill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, highlighted: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (highlighted) scheme.primaryContainer else scheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (highlighted) scheme.onPrimaryContainer else scheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (highlighted) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        )
    }
}

/** Each module's glyph, as on Home's shortcuts. */
private fun featureIcon(feature: Feature): androidx.compose.ui.graphics.vector.ImageVector = when (feature) {
    Feature.MULTIPLE_WALLETS -> Icons.Outlined.AccountBalanceWallet
    Feature.BUDGETS -> Icons.Outlined.DonutLarge
    Feature.SAVINGS_GOALS -> Icons.Outlined.TrackChanges
    Feature.DEBTS_LOANS -> Icons.Outlined.CreditCard
    Feature.RECURRING -> Icons.Outlined.EventRepeat
    Feature.INSIGHTS -> Icons.Outlined.BarChart
    Feature.RECEIPT_SCANNING -> Icons.Outlined.DocumentScanner
    Feature.VOICE_ENTRY -> Icons.Outlined.Mic
}


