package com.lucento.app.feature.capture

import com.lucento.app.feature.fx.FxOfflineChoice
import com.lucento.app.core.designsystem.component.AmountsShown
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import com.lucento.app.core.designsystem.component.SheetHeader
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.lucento.app.core.designsystem.component.icon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardVoice
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.ConfirmDialog
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoBottomSheet
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.component.SwitchRow
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.capture.BatchSummary
import com.lucento.app.domain.capture.CaptureSource
import com.lucento.app.domain.capture.ReviewRow
import com.lucento.app.domain.capture.RowEdit
import com.lucento.app.feature.wallets.WalletRow
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import com.lucento.app.core.designsystem.component.ChatDayDivider
import com.lucento.app.core.designsystem.component.ChatInputBar
import com.lucento.app.core.designsystem.component.ChatMessageField
import com.lucento.app.core.designsystem.component.ChatSendButton
import com.lucento.app.core.designsystem.component.ChatTimestamp
import com.lucento.app.core.designsystem.component.ChatToolButton
import com.lucento.app.core.designsystem.component.LucentoReply
import com.lucento.app.core.designsystem.component.MascotSpeech
import com.lucento.app.core.designsystem.component.TypingDots
import com.lucento.app.core.designsystem.component.UserChatBubble
import com.lucento.app.core.designsystem.component.WalletIdentityTile
import com.lucento.app.core.designsystem.component.IconTile
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.Button
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import android.Manifest
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import java.util.Locale
import kotlinx.coroutines.launch

/** How the composer opens: ready to type, listening, or taking a receipt photo. */
enum class ComposerStart { TYPE, SPEAK, SCAN }

/**
 * Lucento's only way to add transactions: describe what happened — typed, spoken or as a receipt photo — and every
 * transaction in it appears at once as an editable row. Fix anything that looks wrong in place, then save the batch
 * with one tap. Interpretation runs on this phone unless Smarter reading is on, and nothing is saved until the user saves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionComposerScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onAddWallet: () -> Unit,
    voiceEnabled: Boolean,
    scanEnabled: Boolean,
    transfersEnabled: Boolean,
    start: ComposerStart = ComposerStart.TYPE,
    walletId: Long? = null,
    typeHint: TransactionType? = null,
    // Speak or Scan asked again while this composer is open (a home-screen widget); handled once.
    restart: ComposerStart? = null,
    onRestartHandled: () -> Unit = {},
    // The launch logo or App lock covers the app (a widget can open the composer under either): Speak and Scan wait.
    obscured: Boolean = false,
    viewModel: ComposerViewModel = viewModel(factory = ComposerViewModel.Factory),
) = AmountsShown {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var input by rememberSaveable { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }
    var pickingDateFor by remember { mutableStateOf<String?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var started by rememberSaveable { mutableStateOf(false) }
    var pendingPhoto by remember { mutableStateOf<File?>(null) }
    var scanMenu by remember { mutableStateOf(false) }
    var askingSmartReading by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(walletId, typeHint, transfersEnabled) { viewModel.configure(walletId, typeHint, transfersEnabled) }
    // Several accounts and nothing to go on (no entry wallet, no default): ask once, up front.
    var askedForWallet by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.loaded, state.needsWalletChoice) {
        if (state.loaded && state.needsWalletChoice && !askedForWallet) {
            askedForWallet = true
            viewModel.askForWallet()
        }
    }

    fun submit(source: CaptureSource = CaptureSource.TYPED) {
        if (input.isBlank()) return
        haptics.tick()
        viewModel.send(input, source)
        input = ""
        notice = null
    }

    // ---------------------------------------------------------------- speech
    // Lucento listens itself (VoiceCapture) when the phone has a speech service and the microphone is allowed: it waits
    // while people think before they speak. Otherwise the speech service's own window, which needs no permission here.
    // Both stay offline unless the person agreed to online voice (SpeechRequest). Saveable: a result can arrive after a
    // rotation.
    var listenedOffline by rememberSaveable { mutableStateOf(false) }
    var askingOnlineVoice by rememberSaveable { mutableStateOf(false) }
    val recognizer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        when {
            // Cancelled and "Voice search isn't available" both come back here; offline, offer online voice.
            result.resultCode != Activity.RESULT_OK ->
                notice = if (listenedOffline) SpeechRequest.OFFLINE_FAILED else "Listening stopped. You can type it instead."
            heard.isNullOrBlank() -> notice = "Nothing was recognised. Try again, or type it."
            else -> {
                notice = null
                viewModel.send(heard, CaptureSource.SPOKEN)
            }
        }
    }

    fun launchRecognizer(online: Boolean) {
        listenedOffline = !online
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // The interpreter understands English: the phone's own English, or US English.
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, SpeechRequest.languageTag(Locale.getDefault()))
            // True means offline only: phones without an offline pack then can't listen at all.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, !online)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say something like “Dinner 450, parking 90”")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            recognizer.launch(intent)
        } catch (e: ActivityNotFoundException) {
            notice = "This phone has no speech recogniser available. Type it instead — it works the same way."
        }
    }

    val onVoiceEnd by rememberUpdatedState<(VoiceCommand) -> Unit> { command ->
        when (command) {
            is VoiceCommand.Finish -> {
                val heard = command.text
                if (heard == null) {
                    notice = SpeechRequest.NOTHING_HEARD
                } else {
                    notice = null
                    haptics.tick()
                    viewModel.send(heard, CaptureSource.SPOKEN)
                }
            }
            is VoiceCommand.Fail -> notice = SpeechRequest.failureNotice(command.reason)
            else -> Unit
        }
    }
    val voice = remember { VoiceCapture(context) { onVoiceEnd(it) } }
    // Leaving the app or the composer stops listening; words already heard go back into the field, unsent.
    LifecycleStartEffect(voice) {
        onStopOrDispose {
            voice.cancel()?.let { heard -> if (input.isBlank()) input = heard }
        }
    }

    fun startListening(online: Boolean) {
        listenedOffline = !online
        notice = null
        voice.start(online, SpeechRequest.languageTag(Locale.getDefault()))
    }

    // The mode the microphone was asked for: the online-voice consent may still be being saved when the answer comes.
    var micForOnline by rememberSaveable { mutableStateOf(false) }
    val askMicrophone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Refused: the speech service's own window still works without it.
        if (granted) startListening(micForOnline) else launchRecognizer(micForOnline)
    }

    fun listenWith(online: Boolean) {
        when {
            voice.listening -> Unit
            !SpeechRecognizer.isRecognitionAvailable(context) -> launchRecognizer(online)
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED ->
                startListening(online)
            else -> {
                micForOnline = online
                askMicrophone.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    fun listen() {
        haptics.tick()
        scope.launch { listenWith(online = viewModel.onlineVoiceAllowed()) }
    }

    // ---------------------------------------------------------------- receipts (system camera or photo picker; read on this phone)
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = pendingPhoto
        pendingPhoto = null
        if (saved && file != null) {
            viewModel.readReceipt(context.applicationContext, FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file), cleanup = file)
        } else {
            file?.delete()
        }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.readReceipt(context.applicationContext, uri, cleanup = null)
    }

    fun launchCamera() {
        val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val file = File(dir, "receipt-${System.currentTimeMillis()}.jpg")
        pendingPhoto = file
        try {
            takePicture.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
        } catch (e: ActivityNotFoundException) {
            pendingPhoto = null
            file.delete()
            viewModel.receiptUnavailable("No camera app is available. Choose an image instead.")
        }
    }

    fun launchPicker() {
        try {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (e: ActivityNotFoundException) {
            viewModel.receiptUnavailable("No photo picker is available on this phone.")
        }
    }

    fun begin(mode: ComposerStart) = when (mode) {
        ComposerStart.SPEAK -> if (voiceEnabled) listen() else Unit
        ComposerStart.SCAN -> if (scanEnabled) launchCamera() else Unit
        ComposerStart.TYPE -> Unit
    }

    // The microphone or camera opens only once the composer can be seen, never over the lock screen.
    LaunchedEffect(obscured) {
        if (started || obscured) return@LaunchedEffect
        started = true
        begin(start)
    }
    LaunchedEffect(restart, obscured) {
        val again = restart ?: return@LaunchedEffect
        if (obscured) return@LaunchedEffect
        onRestartHandled()
        begin(again)
    }

    // Bring the newest message to the top: its bubble, then Lucento's reply card with as many of its rows as fit.
    // Scrolling to the list's last item instead would push the bubble off-screen whenever a message produces more rows
    // than fit. Index arithmetic mirrors the items emitted below (optional notice, Lucento's greeting, the day divider,
    // then per message: bubble, reply).
    val itemCount = state.messages.size
    LaunchedEffect(itemCount) {
        if (itemCount == 0) return@LaunchedEffect
        val notice = if (state.loaded && state.wallets.isEmpty()) 1 else 0
        listState.animateScrollToItem(notice + 2 + (itemCount - 1) * 2)
    }

    fun requestBack() {
        if (state.rows.isNotEmpty()) confirmDiscard = true else onBack()
    }
    BackHandler(enabled = state.rows.isNotEmpty()) { confirmDiscard = true }

    Column(Modifier.fillMaxSize().imePadding()) {
        LucentoTopBar(
            title = "Add transactions",
            onBack = ::requestBack,
            modifier = Modifier.padding(horizontal = Spacing.gutter),
        )
        // Pinned: the wallet everything goes into. Lucento's greeting is the conversation's first message, so it scrolls
        // away and leaves the rows room above the keyboard.
        if (state.wallets.size > 1) {
            WalletBar(
                wallets = state.wallets,
                activeWalletId = state.activeWalletId,
                defaultWalletId = state.defaultWalletId,
                transfersAllowed = state.transfersAllowed,
                enabled = !state.saving,
                onChoose = {
                    haptics.tick()
                    viewModel.chooseWallet(it)
                },
                onChooseMissing = viewModel::askForWallet,
                onTransfer = {
                    haptics.tick()
                    viewModel.startTransfer()
                },
            )
        }
        Hairline()

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.md, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (state.loaded && state.wallets.isEmpty()) {
                item(key = "no-accounts") {
                    Column {
                        InfoNote("Add an account first — every transaction belongs to one.")
                        LucentoTextButton(text = "Add an account", onClick = onAddWallet)
                    }
                }
            }
            item(key = "greeting") {
                ComposerBanner(
                    mood = when {
                        state.summary.total > 0 && state.summary.canSave -> MascotMood.Happy
                        state.messages.isEmpty() -> MascotMood.Greeting
                        else -> MascotMood.Curious
                    },
                    transfersAllowed = state.transfersAllowed,
                )
            }
            if (state.messages.isEmpty()) {
                item(key = "intro") {
                    ComposerIntro(transfersAllowed = state.transfersAllowed, onExample = { input = it })
                }
            }
            if (state.messages.isNotEmpty()) {
                item(key = "today") { ChatDayDivider("Today") }
            }
            state.messages.forEach { message ->
                item(key = "m${message.id}") {
                    UserBubble(
                        message = message,
                        editing = state.editingMessageId == message.id,
                        canEdit = message.source != CaptureSource.RECEIPT && message.state !is MessageState.Reading && !state.saving &&
                            state.rows.any { it.messageId == message.id },
                        onEdit = { viewModel.startEditing(message.id)?.let { input = it } },
                    )
                }
                item(key = "r${message.id}") {
                    // Lucento's reply card holds its one-line answer and every row it found, each a tinted panel.
                    val rows = state.rows.filter { it.messageId == message.id }
                    Response(message, rows, state.statuses.filterKeys { k -> rows.any { it.key == k } }.count { !it.value.ready }) {
                        rows.forEach { row ->
                            ReviewRowItem(
                                row = row,
                                status = state.statuses[row.key],
                                wallets = state.wallets,
                                categories = state.categories,
                                activeWalletId = state.activeWalletId,
                                transfersAllowed = state.transfersAllowed,
                                enabled = !state.saving,
                                onEdit = { viewModel.edit(row.key, it) },
                                onPickDate = { pickingDateFor = row.key },
                                onRemove = {
                                    haptics.tick()
                                    viewModel.remove(row.key)
                                },
                                nested = true,
                                modifier = Modifier.padding(top = Spacing.sm),
                                fx = state.fx[row.key],
                            )
                        }
                    }
                }
            }
        }

        state.saveError?.let { error ->
            InfoNote(
                error,
                container = LucentoTheme.extendedColors.warningContainer,
                content = LucentoTheme.extendedColors.warning,
                modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
            )
        }
        if (state.rows.isNotEmpty() && state.fxNeedsChoice) {
            FxOfflineChoice(
                choice = state.fxOffline,
                enabled = !state.saving,
                onChoose = viewModel::setFxOffline,
                modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
            )
        }
        if (state.rows.isNotEmpty()) {
            BatchBar(
                summary = state.summary,
                saving = state.saving,
                onSave = {
                    viewModel.save { count -> onSaved(if (count == 1) "Transaction saved" else "$count transactions saved") }
                },
            )
        }
        notice?.let { text ->
            val offerOnline = SpeechRequest.offersOnline(text)
            InfoNote(
                text,
                modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
                actionLabel = if (offerOnline) SpeechRequest.TRY_ONLINE else null,
                onAction = if (offerOnline) ({ askingOnlineVoice = true }) else null,
            )
        }
        if (state.editingMessageId != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
            ) {
                Row(Modifier.padding(start = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        "Editing your message — your changes to its rows are kept",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    LucentoTextButton(text = "Cancel", onClick = {
                        viewModel.cancelEditing()
                        input = ""
                    })
                }
            }
        }

        // ---------------------------------------------------------------- Smarter reading (owner test 2026-11-09)
        // Next to where the message is typed, since that's what it changes. On asks for the same consent as Settings.
        if (state.smartReadingOffered) {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(start = Spacing.gutter, end = Spacing.gutter - Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    SmartReadingCopy.status(state.smartReading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                com.lucento.app.core.designsystem.component.CompactSwitch(
                    label = "Smarter reading",
                    icon = Icons.Outlined.AutoAwesome,
                    checked = state.smartReading,
                    onCheckedChange = { on -> if (on) askingSmartReading = true else viewModel.setSmartReading(false) },
                )
            }
        }

        // ---------------------------------------------------------------- composer
        // One raised bar: scan and voice as soft tiles, the message field, and send — every control the same height.
        ChatInputBar(
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
        ) {
            if (scanEnabled) {
                Box {
                    ChatToolButton(Icons.Outlined.DocumentScanner, "Scan a receipt or statement", onClick = { scanMenu = true })
                    DropdownMenu(expanded = scanMenu, onDismissRequest = { scanMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Take a photo") },
                            leadingIcon = { Icon(Icons.Outlined.CameraAlt, contentDescription = null) },
                            onClick = {
                                scanMenu = false
                                launchCamera()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Choose an image") },
                            leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null) },
                            onClick = {
                                scanMenu = false
                                launchPicker()
                            },
                        )
                    }
                }
            }
            if (voiceEnabled) {
                ChatToolButton(Icons.Outlined.KeyboardVoice, "Speak transactions", onClick = ::listen)
            }
            ChatMessageField(
                value = input,
                onValueChange = { input = it.take(1_000) },
                onSend = { submit() },
                placeholder = "Type a transaction…",
                description = "Describe transactions",
                modifier = Modifier.weight(1f),
            )
            ChatSendButton(enabled = input.isNotBlank() && state.loaded && state.wallets.isNotEmpty(), onClick = { submit() })
        }
    }

    if (askingSmartReading) {
        com.lucento.app.core.designsystem.component.ConfirmDialog(
            title = SmartReadingCopy.CONSENT_TITLE,
            text = SmartReadingCopy.CONSENT_TEXT,
            confirmLabel = "Turn on",
            onConfirm = {
                askingSmartReading = false
                viewModel.setSmartReading(true)
            },
            onDismiss = { askingSmartReading = false },
        )
    }

    pickingDateFor?.let { key ->
        val row = state.rows.firstOrNull { it.key == key }
        if (row == null) {
            pickingDateFor = null
        } else {
            // The Material date picker works in UTC midnight; convert both ways so the calendar date never shifts.
            val pickerState = rememberDatePickerState(
                initialSelectedDateMillis = row.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            )
            DatePickerDialog(
                onDismissRequest = { pickingDateFor = null },
                confirmButton = {
                    TextButton(onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            viewModel.edit(key, RowEdit.Date(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()))
                        }
                        pickingDateFor = null
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { pickingDateFor = null }) { Text("Cancel") } },
            ) {
                DatePicker(state = pickerState)
            }
        }
    }

    if (state.showWalletPicker) {
        WalletPickerSheet(
            wallets = state.wallets,
            onChoose = { id, remember -> viewModel.chooseWallet(id, remember) },
            // Dismissing never loses what was typed or said: the held message goes back into the field. A receipt is
            // read again from the photo instead, so its amounts keep the receipt rules.
            onDismiss = {
                viewModel.dismissWalletPicker()?.let { (text, source) ->
                    if (source == CaptureSource.RECEIPT) {
                        notice = "Scan not added. Choose a wallet, then scan it again."
                    } else if (input.isBlank()) {
                        input = text
                    }
                }
            },
        )
    }

    if (askingOnlineVoice) {
        ConfirmDialog(
            title = SpeechRequest.CONSENT_TITLE,
            text = SpeechRequest.CONSENT_TEXT,
            confirmLabel = "Use online voice",
            dismissLabel = "Not now",
            onConfirm = {
                askingOnlineVoice = false
                notice = null
                viewModel.allowOnlineVoice()
                haptics.tick()
                // They tapped the mic to speak: listen again straight away, online this time.
                listenWith(online = true)
            },
            onDismiss = { askingOnlineVoice = false },
        )
    }

    voice.state?.let { session ->
        ListeningSheet(state = session, level = { voice.level }, onDone = voice::done, onCancel = { voice.cancel() })
    }

    if (confirmDiscard) {
        val n = state.rows.size
        ConfirmDialog(
            title = if (n == 1) "Discard 1 unsaved transaction?" else "Discard $n unsaved transactions?",
            text = "They haven’t been saved. Anything you’ve already saved stays.",
            confirmLabel = "Discard",
            destructive = true,
            onConfirm = {
                confirmDiscard = false
                viewModel.discardAll()
                onBack()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}

/**
 * The wallet everything flows into, pinned under the banner: "Into", then one chip per account with its own icon (its
 * bank's mark or its type's), the active one filled, the default starred. Tapping another moves the unsaved rows that
 * were following it. A trailing chip starts a transfer from the active wallet.
 */
@Composable
private fun WalletBar(
    wallets: List<Wallet>,
    activeWalletId: Long?,
    defaultWalletId: Long?,
    transfersAllowed: Boolean,
    enabled: Boolean,
    onChoose: (Long) -> Unit,
    onChooseMissing: () -> Unit,
    onTransfer: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(bottom = Spacing.xs)) {
        Text(
            if (activeWalletId == null) "Choose a wallet" else "Into",
            style = MaterialTheme.typography.labelLarge,
            color = if (activeWalletId == null) LucentoTheme.extendedColors.negative else colors.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = Spacing.gutter)
                .then(if (activeWalletId == null) Modifier.clickable(onClick = onChooseMissing) else Modifier),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The default wallet leads; the rest keep their usual order.
            wallets.sortedByDescending { it.id == defaultWalletId }.forEach { wallet ->
                val selected = wallet.id == activeWalletId
                val isDefault = wallet.id == defaultWalletId
                val name = if (isDefault) "${wallet.name}, default wallet" else wallet.name
                WalletChip(
                    label = wallet.name,
                    selected = selected,
                    starred = isDefault,
                    description = if (selected) "$name, active" else "Use $name",
                    role = Role.RadioButton,
                    onClick = { if (enabled && !selected) onChoose(wallet.id) },
                ) { WalletIdentityTile(wallet, size = 26.dp) }
            }
            if (transfersAllowed) {
                WalletChip(
                    label = "Transfer",
                    selected = false,
                    starred = false,
                    description = "New transfer between your accounts",
                    role = Role.Button,
                    onClick = { if (enabled) onTransfer() },
                ) {
                    IconTile(Icons.Outlined.SwapHoriz, container = colors.primaryContainer, content = colors.primary, size = 26.dp, shape = RoundedCornerShape(7.dp))
                }
            }
        }
    }
}

/** A wallet as a rounded chip: its tile, its name, a star when it's the default; filled brand blue when active. */
@Composable
private fun WalletChip(
    label: String,
    selected: Boolean,
    starred: Boolean,
    description: String,
    role: Role,
    onClick: () -> Unit,
    tile: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val ext = LucentoTheme.extendedColors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) colors.primary else if (LucentoTheme.isDark) colors.surfaceContainerHigh else colors.surfaceContainerLowest,
        contentColor = if (selected) colors.onPrimary else colors.onSurface,
        border = if (selected) null else BorderStroke(1.dp, ext.borderDefault),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .semantics(mergeDescendants = true) {
                contentDescription = description
                this.role = role
                if (role == Role.RadioButton) this.selected = selected
            },
    ) {
        Row(Modifier.padding(start = 6.dp, end = Spacing.md, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            tile()
            Spacer(Modifier.width(Spacing.sm))
            Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            if (starred) {
                Spacer(Modifier.width(Spacing.xs))
                Icon(Icons.Filled.Star, contentDescription = null, tint = ext.accent, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** "Where should these go?" — asked when there are several accounts and no default or entry wallet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletPickerSheet(wallets: List<Wallet>, onChoose: (Long, Boolean) -> Unit, onDismiss: () -> Unit) {
    var remember by rememberSaveable { mutableStateOf(true) }
    LucentoBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter).navigationBarsPadding()) {
            SheetHeader(
                title = "Where should these go?",
                subtitle = "New transactions go to this wallet unless you name another. You can switch any time from the wallet bar.",
                icon = Icons.Outlined.AccountBalanceWallet,
            )
            wallets.forEachIndexed { index, wallet ->
                if (index > 0) Hairline(inset = 52.dp)
                WalletRow(
                    wallet = wallet,
                    modifier = Modifier.clickable(role = Role.Button, onClickLabel = "Use this wallet") {
                        onChoose(wallet.id, remember && wallet.type.isSpendable)
                    },
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            SwitchRow(
                title = "Remember as my default",
                description = "Cash, bank and e-wallets can be the default. You can change it in the wallet’s settings.",
                checked = remember,
                onCheckedChange = { remember = it },
            )
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

/** Lucento's greeting, the conversation's first message: what to do, in its speech bubble. */
@Composable
private fun ComposerBanner(mood: MascotMood, transfersAllowed: Boolean) {
    MascotSpeech(
        title = "Tell me about your transaction",
        body = "You can write naturally, like “Lunch 250 and groceries 800”" + if (transfersAllowed) " or “Transfer 5,000 to savings.”" else ".",
        mood = mood,
        mascotSize = 60.dp,
    )
}

@Composable
private fun ComposerIntro(transfersAllowed: Boolean, onExample: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text("TRY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
        val examples = buildList {
            add("Lunch 250, groceries 800, salary 35000")
            add("Yesterday coffee 180 and dinner 650")
            if (transfersAllowed) add("Transfer 5000 from bank to savings")
        }
        examples.forEach { example ->
            LucentoTextButton(text = "“$example”", onClick = { onExample(example) })
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "Nothing is sent unless you turn on cloud sync, crash reports, or Smarter reading.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The user's message: a pale-blue bubble on the trailing side, its time beneath, and "Edit message" when it can be. */
@Composable
private fun UserBubble(message: ComposerMessage, editing: Boolean, canEdit: Boolean, onEdit: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(top = Spacing.xs, start = 48.dp), horizontalAlignment = Alignment.End) {
        UserChatBubble(
            text = message.text,
            top = when (message.source) {
                CaptureSource.SPOKEN -> ({ SourceLabel(Icons.Outlined.KeyboardVoice, "Spoken") })
                CaptureSource.RECEIPT -> ({ SourceLabel(Icons.Outlined.DocumentScanner, "Scan") })
                CaptureSource.TYPED -> null
            },
        )
        Row(Modifier.padding(top = Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
            if (editing) {
                Text("Editing…", style = MaterialTheme.typography.labelMedium, color = colors.primary)
                Spacer(Modifier.width(Spacing.sm))
            } else if (canEdit) {
                Surface(
                    onClick = onEdit,
                    shape = RoundedCornerShape(50),
                    color = colors.primaryContainer,
                    contentColor = colors.primary,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Row(Modifier.padding(horizontal = Spacing.md, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(Spacing.xs))
                        Text("Edit message", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.width(Spacing.sm))
            }
            ChatTimestamp(timeOf(message.sentAtEpochMillis))
        }
    }
}

@Composable
private fun SourceLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Spacing.xs)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(Spacing.xs))
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall)
    }
}

/** "6:49 PM", in the phone's own time format. */
private fun timeOf(epochMillis: Long): String =
    java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)
        .withLocale(java.util.Locale.getDefault())
        .format(java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault()))

/**
 * Lucento's reply: its avatar and a card with one short line — "Got it! Here are 3 transactions I’ll add:" — then the
 * rows it found ([rows]), and who read the message when it wasn't plain on-device reading. Its time sits beneath.
 */
@Composable
private fun Response(message: ComposerMessage, rows: List<ReviewRow>, needsAttention: Int, rowsContent: @Composable () -> Unit) {
    val live = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    val reading = message.state is MessageState.Reading
    LucentoReply(
        mood = when {
            reading -> MascotMood.Curious
            rows.isNotEmpty() && needsAttention == 0 -> MascotMood.Happy
            else -> MascotMood.Curious
        },
        footer = if (reading) null else ({ ChatTimestamp(timeOf(message.sentAtEpochMillis)) }),
    ) {
        when (val s = message.state) {
            MessageState.Reading -> Row(verticalAlignment = Alignment.CenterVertically, modifier = live) {
                TypingDots(description = "Reading")
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    if (message.source == CaptureSource.RECEIPT) "Reading the scan on this phone…" else "Reading…",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            is MessageState.NothingFound -> Text(s.message, style = MaterialTheme.typography.bodyMedium, modifier = live)
            is MessageState.Read -> {
                val text = when {
                    rows.isEmpty() && s.saved > 0 -> if (s.saved == 1) "Saved." else "Saved all ${s.saved}."
                    rows.isEmpty() -> "Removed."
                    needsAttention == 0 -> if (rows.size == 1) "Got it! Here’s the transaction I’ll add:" else "Got it! Here are ${rows.size} transactions I’ll add:"
                    needsAttention == 1 -> if (rows.size == 1) "Here’s what I read — it needs a look:" else "Here are ${rows.size} transactions — 1 needs a look:"
                    else -> "Here are ${rows.size} transactions — $needsAttention need a look:"
                }
                Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), modifier = live)
                // A scan says what it was read as (one receipt, or a statement's lines) and what to check.
                if (rows.isNotEmpty()) {
                    s.note?.let { note ->
                        Text(
                            note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.xxs),
                        )
                    }
                }
                rowsContent()
                // Who read it, whenever it isn't plain on-device reading: Gemini, or this phone with the reason.
                s.engine?.takeIf { !it.onDevice || it.label.contains("·") }?.let { engine ->
                    Text(
                        if (engine.onDevice) "Read on this phone — ${engine.label.substringAfter("· ")}" else "Read by ${engine.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
            }
        }
    }
}

/**
 * The summary and the one primary action. The batch saves all at once, so the button stays disabled — and says why —
 * until every highlighted row is fixed or removed. While the keyboard is up it folds into one line with a compact button,
 * so the conversation keeps room above the keyboard.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BatchBar(summary: BatchSummary, saving: Boolean, onSave: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val compact = WindowInsets.isImeVisible
    val title = when {
        summary.needsAttention == 0 -> if (summary.ready == 1) "1 transaction ready" else "${summary.ready} transactions ready"
        summary.needsAttention == 1 -> "1 transaction is incomplete"
        else -> "${summary.needsAttention} transactions are incomplete"
    }
    val label = when {
        saving -> "Saving…"
        summary.needsAttention == 1 -> "Fix 1 highlighted to save"
        summary.needsAttention > 1 -> "Fix ${summary.needsAttention} highlighted to save"
        summary.ready == 1 -> "Save 1 transaction"
        else -> "Save ${summary.ready} transactions"
    }
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Hairline()
        if (compact) {
            Row(Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (summary.canSave) MaterialTheme.colorScheme.onSurface else LucentoTheme.extendedColors.negative,
                        maxLines = 1,
                    )
                    totalsLine(summary)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = muted, maxLines = 1) }
                }
                Spacer(Modifier.width(Spacing.sm))
                Button(
                    onClick = onSave,
                    enabled = !saving && summary.canSave,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        when {
                            saving -> "Saving…"
                            summary.needsAttention > 0 -> "Fix ${summary.needsAttention} first"
                            else -> "Save ${summary.ready}"
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        } else {
            Column(Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (summary.canSave) MaterialTheme.colorScheme.onSurface else LucentoTheme.extendedColors.negative,
                )
                totalsLine(summary)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = muted) }
                Spacer(Modifier.height(Spacing.sm))
                LucentoPrimaryButton(text = label, onClick = onSave, enabled = !saving && summary.canSave)
            }
        }
    }
}

private fun totalsLine(summary: BatchSummary): String? {
    val parts = ArrayList<String>()
    if (summary.expenses.isNotEmpty()) parts += "Expenses " + summary.expenses.joinToString(" + ") { MoneyFormatter.formatSigned(-it, showPlus = true) }
    if (summary.income.isNotEmpty()) parts += "Income " + summary.income.joinToString(" + ") { MoneyFormatter.formatSigned(it, showPlus = true) }
    if (summary.transfers > 0) parts += if (summary.transfers == 1) "1 transfer" else "${summary.transfers} transfers"
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
