package com.lucento.app.feature.capture

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.KeyboardVoice
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.ConfirmDialog
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoMascot
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.capture.BatchSummary
import com.lucento.app.domain.capture.CaptureSource
import com.lucento.app.domain.capture.ReviewRow
import com.lucento.app.domain.capture.RowEdit
import java.io.File
import java.time.Instant
import java.time.ZoneOffset

/** How the composer opens: ready to type, listening, or taking a receipt photo. */
enum class ComposerStart { TYPE, SPEAK, SCAN }

/**
 * Lucento's only way to add transactions: describe what happened — typed, spoken or as a receipt photo — and every
 * transaction in it appears at once as an editable row. Fix anything that looks wrong in place, then save the batch
 * with one tap. Interpretation runs on this phone; nothing is sent anywhere and nothing is saved until the user saves.
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

    // Keep the newest message and its rows in view.
    val itemCount = state.messages.size
    LaunchedEffect(itemCount) {
        if (itemCount > 0) listState.animateScrollToItem(listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
    }

    fun requestBack() {
        if (state.rows.isNotEmpty()) confirmDiscard = true else onBack()
    }
    BackHandler(enabled = state.rows.isNotEmpty()) { confirmDiscard = true }

    Column(Modifier.fillMaxSize().imePadding()) {
        LucentoTopBar(
            title = "Add transactions",
            subtitle = "Describe what happened · review · save",
            onBack = ::requestBack,
            modifier = Modifier.padding(horizontal = Spacing.gutter),
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.sm, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
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
                    ComposerIntro(transfersAllowed = state.transfersAllowed, onExample = { input = it })
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
                rows.forEachIndexed { index, row ->
                    item(key = row.key) {
                        Column {
                            if (index > 0) Hairline(inset = 52.dp)
                            ReviewRowItem(
                                row = row,
                                status = state.statuses[row.key],
                                wallets = state.wallets,
                                categories = state.categories,
                                showAccount = state.wallets.size > 1,
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
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Editing your message — your changes to its rows are kept",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                LucentoTextButton(text = "Cancel", onClick = {
                    viewModel.cancelEditing()
                    input = ""
                })
            }
        }

        // ---------------------------------------------------------------- composer
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(start = Spacing.sm, end = Spacing.gutter, top = Spacing.sm, bottom = Spacing.md),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (scanEnabled) {
                Box {
                    IconButton(onClick = { scanMenu = true }) {
                        Icon(Icons.Outlined.DocumentScanner, contentDescription = "Scan a receipt")
                    }
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
                IconButton(onClick = ::listen) {
                    Icon(Icons.Outlined.KeyboardVoice, contentDescription = "Speak transactions")
                }
            }
            if (!scanEnabled && !voiceEnabled) Spacer(Modifier.width(Spacing.md))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.take(1_000) },
                placeholder = { Text("Try “Lunch 250, groceries 800, salary 35000”…") },
                maxLines = 4,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Spacing.sm))
            FilledIconButton(
                onClick = { submit() },
                enabled = input.isNotBlank() && state.loaded && state.wallets.isNotEmpty(),
                modifier = Modifier.size(52.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
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

@Composable
private fun ComposerIntro(transfersAllowed: Boolean, onExample: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LucentoMascot(size = 72.dp, mood = MascotMood.Curious)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text("Describe what happened", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(
                    "Write several at once. Each one becomes a row you can check before saving.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(Spacing.lg))
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
            "Read on this phone — nothing is sent anywhere.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UserBubble(message: ComposerMessage, editing: Boolean, canEdit: Boolean, onEdit: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = if (LucentoTheme.isDark) colors.surfaceContainerHigh else colors.surfaceContainer,
            contentColor = colors.onSurface,
            modifier = Modifier.widthIn(max = 320.dp).clip(MaterialTheme.shapes.large),
        ) {
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
            Text("Editing…", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs))
        } else if (canEdit) {
            LucentoTextButton(text = "Edit message", onClick = onEdit)
        }
    }
}

@Composable
private fun SourceLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Spacing.xs)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(Spacing.xs))
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Lucento's reply: one short line, never a paragraph. The rows themselves follow. */
@Composable
private fun Response(message: ComposerMessage, rows: List<ReviewRow>, needsAttention: Int) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val live = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    when (val s = message.state) {
        MessageState.Reading -> Row(verticalAlignment = Alignment.CenterVertically, modifier = live) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(Spacing.sm))
            Text(
                if (message.source == CaptureSource.RECEIPT) "Reading the receipt on this phone…" else "Reading…",
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
            )
        }
        is MessageState.NothingFound -> Text(s.message, style = MaterialTheme.typography.bodyMedium, color = muted, modifier = live)
        is MessageState.Read -> {
            val pending = rows.size
            val text = when {
                pending == 0 && s.saved > 0 -> if (s.saved == 1) "Saved." else "Saved all ${s.saved}."
                pending == 0 -> "Removed."
                else -> buildString {
                    append(if (s.found == 1) "Found 1 transaction." else "Found ${s.found} transactions.")
                    if (s.saved > 0) append(" ${s.saved} saved.")
                    append(
                        when {
                            needsAttention == 0 -> " Review anything that looks wrong."
                            needsAttention == 1 -> " 1 needs a look."
                            else -> " $needsAttention need a look."
                        }
                    )
                }
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = muted, modifier = live)
        }
    }
}

/** The summary and the one primary action. Says before the tap exactly what will and won't be saved. */
@Composable
private fun BatchBar(summary: BatchSummary, saving: Boolean, onSave: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Hairline()
        Column(Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.md)) {
            Text(
                when {
                    summary.needsAttention == 0 -> if (summary.ready == 1) "1 transaction ready" else "${summary.ready} transactions ready"
                    summary.ready == 0 -> if (summary.total == 1) "1 transaction needs a look" else "${summary.total} transactions need a look"
                    else -> "${summary.ready} ready · ${summary.needsAttention} to check"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            totalsLine(summary)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = muted) }
            Spacer(Modifier.height(Spacing.sm))
            LucentoPrimaryButton(
                text = when {
                    saving -> "Saving…"
                    summary.ready == 0 -> "Fix the highlighted fields to save"
                    summary.needsAttention == 0 && summary.ready == 1 -> "Save 1 transaction"
                    summary.needsAttention == 0 -> "Save ${summary.ready} transactions"
                    else -> "Save ${summary.ready} ready"
                },
                onClick = onSave,
                enabled = !saving && summary.ready > 0,
            )
            if (summary.needsAttention > 0 && summary.ready > 0) {
                Text(
                    if (summary.needsAttention == 1) "The other one stays here until it’s fixed." else "The other ${summary.needsAttention} stay here until they’re fixed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
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
