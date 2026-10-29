package com.lucento.app.feature.capture

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
import com.lucento.app.core.designsystem.component.WalletPill
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardVoice
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.ConfirmDialog
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoBottomSheet
import com.lucento.app.core.designsystem.component.LucentoMascot
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
    /** Smarter reading is on, so the entry is sent to Gemini. The privacy line says so. */
    geminiEnabled: Boolean = false,
    viewModel: ComposerViewModel = viewModel(factory = ComposerViewModel.Factory),
) {
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
    val listState = rememberLazyListState()

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

    // ---------------------------------------------------------------- speech (the system recogniser; no permission here)
    val recognizer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        when {
            result.resultCode != Activity.RESULT_OK -> notice = "Listening stopped. You can type it instead."
            heard.isNullOrBlank() -> notice = "Nothing was recognised. Try again, or type it."
            else -> {
                notice = null
                viewModel.send(heard, CaptureSource.SPOKEN)
            }
        }
    }

    fun listen() {
        haptics.tick()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // The interpreter understands English; ask the recogniser for English and to stay on the device.
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say something like “Dinner 450, parking 90”")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            recognizer.launch(intent)
        } catch (e: ActivityNotFoundException) {
            notice = "This phone has no speech recogniser available. Type it instead — it works the same way."
        }
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

    LaunchedEffect(Unit) {
        if (started) return@LaunchedEffect
        started = true
        when (start) {
            ComposerStart.SPEAK -> if (voiceEnabled) listen()
            ComposerStart.SCAN -> if (scanEnabled) launchCamera()
            ComposerStart.TYPE -> Unit
        }
    }

    // Bring the newest message to the top: its bubble, Lucento's one-line reply and as many of its rows as fit.
    // Scrolling to the list's last item instead would push the bubble and reply off-screen whenever a message
    // produces more rows than fit. Index arithmetic mirrors the items emitted below (optional notice, then per
    // message: bubble, reply, rows).
    val itemCount = state.messages.size
    LaunchedEffect(itemCount) {
        if (itemCount == 0) return@LaunchedEffect
        val notice = if (state.loaded && state.wallets.isEmpty()) 1 else 0
        val before = state.messages.dropLast(1).sumOf { m -> 2 + state.rows.count { it.messageId == m.id } }
        listState.animateScrollToItem(notice + before)
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
        // Pinned: Lucento and the one instruction stay in view while the conversation scrolls beneath.
        ComposerBanner(
            mood = when {
                state.summary.total > 0 && state.summary.canSave -> MascotMood.Happy
                else -> MascotMood.Curious
            },
        )
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
            if (state.messages.isEmpty()) {
                item(key = "intro") {
                    ComposerIntro(
                        transfersAllowed = state.transfersAllowed,
                        geminiEnabled = geminiEnabled,
                        onExample = { input = it },
                    )
                }
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
                    val rows = state.rows.filter { it.messageId == message.id }
                    Response(message, rows, state.statuses.filterKeys { k -> rows.any { it.key == k } }.count { !it.value.ready })
                }
                val rows = state.rows.filter { it.messageId == message.id }
                rows.forEach { row ->
                    item(key = row.key) {
                        // Lucento's rows sit under its reply, indented past the avatar; each is its own card.
                        Box(Modifier.padding(start = ReplyIndent)) {
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
        if (state.rows.isNotEmpty()) {
            BatchBar(
                summary = state.summary,
                saving = state.saving,
                onSave = {
                    viewModel.save { count -> onSaved(if (count == 1) "Transaction saved" else "$count transactions saved") }
                },
            )
        }
        notice?.let { InfoNote(it, modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xs)) }
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

        // ---------------------------------------------------------------- composer
        // Every control is the same height and spacing so the bar reads as one balanced row when it first appears.
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (scanEnabled) {
                Box {
                    ComposerToolButton(Icons.Outlined.DocumentScanner, "Scan a receipt") { scanMenu = true }
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
                ComposerToolButton(Icons.Outlined.KeyboardVoice, "Speak transactions", ::listen)
            }
            MessageField(
                value = input,
                onValueChange = { input = it.take(1_000) },
                onSend = { submit() },
                modifier = Modifier.weight(1f),
            )
            FilledIconButton(
                onClick = { submit() },
                enabled = input.isNotBlank() && state.loaded && state.wallets.isNotEmpty(),
                modifier = Modifier.size(ControlSize),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", modifier = Modifier.size(20.dp))
            }
        }
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
            onDismiss = viewModel::dismissWalletPicker,
        )
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
 * The wallet everything flows into, pinned under the banner: one pill per account, the active one filled. Tapping
 * another moves the unsaved rows that were following it. A trailing pill starts a transfer from the active wallet.
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
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter)
            .padding(bottom = Spacing.xs)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (activeWalletId == null) "CHOOSE A WALLET" else "INTO",
            style = MaterialTheme.typography.labelSmall,
            color = if (activeWalletId == null) LucentoTheme.extendedColors.negative else colors.onSurfaceVariant,
            modifier = if (activeWalletId == null) Modifier.clickable(onClick = onChooseMissing) else Modifier,
        )
        // The default wallet leads; the rest keep their usual order.
        wallets.sortedByDescending { it.id == defaultWalletId }.forEach { wallet ->
            val selected = wallet.id == activeWalletId
            val isDefault = wallet.id == defaultWalletId
            val name = if (isDefault) "${wallet.name}, default wallet" else wallet.name
            WalletPill(
                label = wallet.name,
                icon = if (selected) Icons.Filled.Check else wallet.type.icon,
                iconTint = if (selected) null else LucentoTheme.extendedColors.tone(wallet.type).content,
                selected = selected,
                isDefault = isDefault,
                role = Role.RadioButton,
                description = if (selected) "$name, active" else "Use $name",
                onClick = { if (enabled && !selected) onChoose(wallet.id) },
            )
        }
        if (transfersAllowed) {
            WalletPill(
                label = "Transfer",
                icon = Icons.Outlined.SwapHoriz,
                iconTint = colors.primary,
                selected = false,
                isDefault = false,
                role = Role.Button,
                description = "New transfer between your accounts",
                onClick = { if (enabled) onTransfer() },
            )
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
            Text("Where should these go?", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(
                "New transactions go to this wallet unless you name another. You can switch any time from the wallet bar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.sm),
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

/** Height of every control in the input bar, so scan, voice, the message field and send line up. */
private val ControlSize = 48.dp
private val AvatarSize = 28.dp
/** Lucento's replies and rows start past its avatar. */
private val ReplyIndent = AvatarSize + Spacing.sm

/** The pinned header under the top bar: Lucento and the one instruction. Never scrolls away. */
@Composable
private fun ComposerBanner(mood: MascotMood) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LucentoMascot(size = 48.dp, mood = mood, contentDescription = null)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                "Describe what happened",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "Write several at once — each becomes a row to check before saving.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
            )
        }
    }
}

@Composable
private fun ComposerIntro(
    transfersAllowed: Boolean,
    geminiEnabled: Boolean,
    onExample: (String) -> Unit,
) {
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
            com.lucento.app.domain.ComposerPrivacy.line(geminiEnabled),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 4,
        )
    }
}

/** The user's message: a brand-blue bubble on the trailing side, tail toward the edge. */
@Composable
private fun UserBubble(message: ComposerMessage, editing: Boolean, canEdit: Boolean, onEdit: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp)
    Column(Modifier.fillMaxWidth().padding(top = Spacing.sm, start = 48.dp), horizontalAlignment = Alignment.End) {
        Surface(shape = shape, color = colors.primary, contentColor = colors.onPrimary) {
            Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
                when (message.source) {
                    CaptureSource.SPOKEN -> SourceLabel(Icons.Outlined.KeyboardVoice, "Spoken")
                    CaptureSource.RECEIPT -> SourceLabel(Icons.Outlined.DocumentScanner, "Receipt")
                    CaptureSource.TYPED -> Unit
                }
                Text(message.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (editing) {
            Text("Editing…", style = MaterialTheme.typography.labelMedium, color = colors.primary, modifier = Modifier.padding(top = Spacing.xs))
        } else if (canEdit) {
            Spacer(Modifier.height(Spacing.xs))
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

/** Lucento's reply: its avatar and one short line in a neutral bubble on the leading side. The rows follow. */
@Composable
private fun Response(message: ComposerMessage, rows: List<ReviewRow>, needsAttention: Int) {
    val colors = MaterialTheme.colorScheme
    val live = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    val shape = RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    Row(Modifier.fillMaxWidth().padding(end = 48.dp), verticalAlignment = Alignment.Top) {
        LucentoMascot(size = AvatarSize, animate = false, mood = MascotMood.Curious)
        Spacer(Modifier.width(Spacing.sm))
        Surface(
            shape = shape,
            color = if (LucentoTheme.isDark) colors.surfaceContainerHigh else colors.surfaceContainer,
            contentColor = colors.onSurface,
        ) {
            Box(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
                when (val s = message.state) {
                    MessageState.Reading -> Row(verticalAlignment = Alignment.CenterVertically, modifier = live) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            if (message.source == CaptureSource.RECEIPT) "Reading the receipt on this phone…" else "Reading…",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    is MessageState.NothingFound -> Text(s.message, style = MaterialTheme.typography.bodyMedium, modifier = live)
                    is MessageState.Read -> {
                        val text = when {
                            rows.isEmpty() && s.saved > 0 -> if (s.saved == 1) "Saved." else "Saved all ${s.saved}."
                            rows.isEmpty() -> "Removed."
                            else -> buildString {
                                append(if (s.found == 1) "Found 1 transaction." else "Found ${s.found} transactions.")
                                append(
                                    when (needsAttention) {
                                        0 -> " Review anything that looks wrong."
                                        1 -> " 1 needs a look."
                                        else -> " $needsAttention need a look."
                                    }
                                )
                            }
                        }
                        androidx.compose.foundation.layout.Column {
                        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = live)
                        // Who read it, whenever it isn't plain on-device reading: Gemini, or this phone with the reason.
                        s.engine?.let { engine ->
                            Text(
                                com.lucento.app.domain.ComposerPrivacy.line(geminiEnabled = !engine.onDevice),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 4,
                            )
                        }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The summary and the one primary action. The batch saves all at once, so the button stays disabled — and says why —
 * until every highlighted row is fixed or removed.
 */
@Composable
private fun BatchBar(summary: BatchSummary, saving: Boolean, onSave: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Hairline()
        Column(Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm)) {
            Text(
                when {
                    summary.needsAttention == 0 -> if (summary.ready == 1) "1 transaction ready" else "${summary.ready} transactions ready"
                    summary.needsAttention == 1 -> "1 transaction is incomplete"
                    else -> "${summary.needsAttention} transactions are incomplete"
                },
                style = MaterialTheme.typography.titleSmall,
                color = if (summary.canSave) MaterialTheme.colorScheme.onSurface else LucentoTheme.extendedColors.negative,
            )
            totalsLine(summary)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = muted) }
            Spacer(Modifier.height(Spacing.sm))
            LucentoPrimaryButton(
                text = when {
                    saving -> "Saving…"
                    summary.needsAttention == 1 -> "Fix 1 highlighted to save"
                    summary.needsAttention > 1 -> "Fix ${summary.needsAttention} highlighted to save"
                    summary.ready == 1 -> "Save 1 transaction"
                    else -> "Save ${summary.ready} transactions"
                },
                onClick = onSave,
                enabled = !saving && summary.canSave,
            )
        }
    }
}

/** A round, tonal tool in the input bar (scan, voice). */
@Composable
private fun ComposerToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(ControlSize),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary,
        ),
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp))
    }
}

/** The pill-shaped message field: one line tall at rest (matching the round buttons), growing to four. */
@Composable
private fun MessageField(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = style,
        maxLines = 4,
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { onSend() }),
        modifier = modifier.semantics { contentDescription = "Describe transactions" },
        decorationBox = { inner ->
            val shape = RoundedCornerShape(ControlSize / 2)
            Box(
                Modifier
                    .background(if (LucentoTheme.isDark) colors.surfaceContainer else colors.surfaceContainerLowest, shape)
                    .border(BorderStroke(1.dp, colors.outline), shape)
                    .heightIn(min = ControlSize)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        "Lunch 250, groceries 800…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                inner()
            }
        },
    )
}

private fun totalsLine(summary: BatchSummary): String? {
    val parts = ArrayList<String>()
    if (summary.expenses.isNotEmpty()) parts += "Expenses " + summary.expenses.joinToString(" + ") { MoneyFormatter.formatSigned(-it, showPlus = true) }
    if (summary.income.isNotEmpty()) parts += "Income " + summary.income.joinToString(" + ") { MoneyFormatter.formatSigned(it, showPlus = true) }
    if (summary.transfers > 0) parts += if (summary.transfers == 1) "1 transfer" else "${summary.transfers} transfers"
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
