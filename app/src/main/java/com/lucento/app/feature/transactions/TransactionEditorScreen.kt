package com.lucento.app.feature.transactions

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import com.lucento.app.core.designsystem.component.AmountTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucento.app.core.designsystem.component.CategoryTile
import com.lucento.app.core.designsystem.component.ChoicePill
import com.lucento.app.core.designsystem.component.ConfirmDialog
import com.lucento.app.core.designsystem.component.FieldHeader
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.SegmentedToggle
import com.lucento.app.core.designsystem.component.WalletPill
import com.lucento.app.core.designsystem.component.icon
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.Hierarchy
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.OverlineStyle
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.designsystem.theme.TonePair
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.data.repository.TransactionRepository
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Full-page editor for an existing transaction, opened from history (Edit on a row) and wallet screens. New
 * transactions are added through the composer instead. The page is split into two cards: what happened (type and
 * amount, the hero) and the details (wallet, category, date, note). Save is pinned to the bottom; Delete sits in the
 * top bar and always asks first.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionEditorScreen(
    initial: TransactionDraft,
    wallets: List<Wallet>,
    categories: List<Category>,
    onSave: (TransactionDraft, Char, (TransactionDraftError?) -> Unit) -> Unit,
    onDelete: ((Long) -> Unit)?,
    onClose: () -> Unit,
    /** False when the user tracks a single account (Multiple wallets off) and it is the only choice. */
    showWalletPicker: Boolean = true,
) {
    val haptics = rememberHaptics()
    val decimalSeparator = remember { MoneyFormatter.decimalSeparator() }

    var draft by remember(initial) { mutableStateOf(initial) }
    var error by remember { mutableStateOf<TransactionDraftError?>(null) }
    var pickingDate by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    // Wallets can arrive a moment after the page opens on a cold start; pre-select one when they do.
    LaunchedEffect(wallets) {
        if (draft.walletId == null && wallets.isNotEmpty()) draft = draft.copy(walletId = wallets.first().id)
    }
    BackHandler(onBack = onClose)

    val wallet = wallets.firstOrNull { it.id == draft.walletId }
    val kind = if (draft.type == TransactionType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
    // Archived categories stay selectable only when this transaction already uses one.
    val choices = categories.filter { it.kind == kind && (!it.archived || it.id == draft.categoryId) }
    val amountError = error is TransactionDraftError.AmountInvalid || error is TransactionDraftError.AmountZero ||
        error is TransactionDraftError.AmountTooPrecise || error is TransactionDraftError.AmountTooLarge
    val isNew = initial.id == null
    val ext = LucentoTheme.extendedColors
    val expenseTone = TonePair(ext.negativeContainer, ext.negative)
    val incomeTone = TonePair(ext.positiveContainer, ext.positive)
    val tone = if (draft.type == TransactionType.INCOME) incomeTone else expenseTone

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        LucentoTopBar(
            title = if (isNew) "New transaction" else "Edit transaction",
            onBack = onClose,
            modifier = Modifier.padding(horizontal = Spacing.gutter),
            actions = {
                if (!isNew && onDelete != null) {
                    IconButton(
                        onClick = { confirmingDelete = true },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete transaction")
                    }
                }
            },
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter)
                .padding(top = Spacing.xs, bottom = Spacing.sm),
            // Sized to fit a regular phone without scrolling; the scroll is only a fallback for small screens.
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // ---- What happened: type and amount
            LucentoCard(modifier = Modifier.fillMaxWidth(), contentPadding = Spacing.md) {
                SegmentedToggle(
                    options = listOf(TransactionType.EXPENSE to "Expense", TransactionType.INCOME to "Income"),
                    selected = draft.type,
                    onSelect = { type ->
                        // A category belongs to one kind; drop it when the kind changes.
                        draft = draft.copy(type = type, categoryId = null)
                    },
                    selectedTone = { if (it == TransactionType.INCOME) incomeTone else expenseTone },
                    compact = true,
                )
                Spacer(Modifier.height(Spacing.sm))
                // The amount sits on its own tinted panel, so it reads as the one figure that matters.
                val panel by animateColorAsState(tone.container.copy(alpha = 0.55f), tween(180), label = "amount-panel")
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(panel, MaterialTheme.shapes.medium)
                        .padding(vertical = Spacing.sm, horizontal = Spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AmountEntry(
                        text = draft.amountText,
                        currencyCode = wallet?.currencyCode,
                        type = draft.type,
                        isError = amountError,
                        autoFocus = isNew,
                        onTextChange = {
                            draft = draft.copy(amountText = it)
                            if (amountError) error = null
                        },
                    )
                    // Only an error or a missing wallet earns a line here; the wallet pills already say the currency.
                    val caption = when (val e = error) {
                            TransactionDraftError.AmountInvalid -> "Enter an amount, like 12${decimalSeparator}50."
                            TransactionDraftError.AmountZero -> "The amount must be greater than zero."
                            is TransactionDraftError.AmountTooPrecise ->
                                if (e.digits == 0) "${wallet?.currencyCode.orEmpty()} has no decimal places."
                                else "${wallet?.currencyCode.orEmpty()} allows up to ${e.digits} decimal places."
                            TransactionDraftError.AmountTooLarge -> "That amount is larger than Lucento supports."
                            else -> if (wallet == null) "Choose a wallet below" else null
                        }
                    if (caption != null) {
                        Text(
                            caption,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (amountError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // ---- Details: wallet, category, date, note
            LucentoCard(modifier = Modifier.fillMaxWidth(), contentPadding = Spacing.md) {
                if (showWalletPicker || wallets.size > 1) {
                    FieldLabel(if (draft.type == TransactionType.INCOME) "Into wallet" else "From wallet")
                    // Wraps onto new lines so every wallet stays inside the card.
                    FlowRow(
                        Modifier
                            .fillMaxWidth()
                            .selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        // The currency is only worth repeating on every pill when wallets differ in it.
                        val mixedCurrencies = wallets.map { it.currencyCode }.distinct().size > 1
                        wallets.forEach { w ->
                            val selected = draft.walletId == w.id
                            WalletPill(
                                label = if (mixedCurrencies) "${w.name} · ${w.currencyCode}" else w.name,
                                icon = if (selected) Icons.Filled.Check else w.type.icon,
                                iconTint = if (selected) null else ext.tone(w.type).content,
                                selected = selected,
                                isDefault = false,
                                role = Role.RadioButton,
                                description = if (selected) "${w.name}, selected" else "Use ${w.name}",
                                onClick = {
                                    if (!selected) {
                                        haptics.tick()
                                        draft = draft.copy(walletId = w.id)
                                        if (error is TransactionDraftError.WalletRequired || amountError) error = null
                                    }
                                },
                            )
                        }
                    }
                    if (error is TransactionDraftError.WalletRequired) {
                        Text("Choose a wallet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    SectionDivider()
                }

                CategoryDropdown(
                    categories = choices,
                    selectedId = draft.categoryId,
                    type = draft.type,
                    onSelect = { id ->
                        haptics.tick()
                        draft = draft.copy(categoryId = id)
                    },
                )

                SectionDivider()
                FieldLabel("Date")
                DateChoices(
                    date = draft.date,
                    onDate = { draft = draft.copy(date = it) },
                    onPick = { pickingDate = true },
                )

                SectionDivider()
                OutlinedTextField(
                    value = draft.note,
                    onValueChange = { draft = draft.copy(note = it.take(TransactionRepository.MAX_NOTE)) },
                    label = { Text("Note") },
                    placeholder = { Text(if (draft.type == TransactionType.INCOME) "e.g. October salary" else "e.g. Weekly shop") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    shape = MaterialTheme.shapes.small,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (error is TransactionDraftError.Rejected) {
                Text(
                    "This transaction couldn’t be saved. Check the wallet and category, then try again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        // Pinned save, clear of the keyboard (the app scaffold already insets the gesture bar).
        Hairline()
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
        ) {
            LucentoPrimaryButton(
                text = when {
                    !isNew -> "Save changes"
                    draft.type == TransactionType.INCOME -> "Add income"
                    else -> "Add expense"
                },
                onClick = {
                    if (saving) return@LucentoPrimaryButton
                    saving = true
                    onSave(draft, decimalSeparator) { result ->
                        saving = false
                        if (result == null) onClose() else {
                            haptics.reject()
                            error = result
                        }
                    }
                },
            )
        }
    }

    if (pickingDate) {
        // The Material date picker works in UTC midnight; convert both ways so the calendar date never shifts.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = draft.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        draft = draft.copy(date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (confirmingDelete && initial.id != null && onDelete != null) {
        ConfirmDialog(
            title = "Delete this transaction?",
            text = "It will be permanently deleted from this device and the wallet balance will update. " +
                "This can’t be undone.",
            confirmLabel = "Delete permanently",
            destructive = true,
            onConfirm = {
                confirmingDelete = false
                onDelete(initial.id)
                onClose()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

/** Separates the groups inside the details card. */
@Composable
private fun SectionDivider() {
    Hairline(modifier = Modifier.padding(vertical = Spacing.sm))
}

/** A one-line field label: the compact counterpart of [FieldHeader], so the whole editor fits on one screen. */
@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = Hierarchy.fieldLabel,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = Spacing.xxs).semantics { heading() },
    )
}

/**
 * Category as a full-width dropdown: the field shows the chosen category's tile and name (or "No category"); the menu
 * lists every choice with its tile, plus "No category" to clear it. The menu matches the field's width.
 */
@Composable
private fun CategoryDropdown(
    categories: List<Category>,
    selectedId: Long?,
    type: TransactionType,
    onSelect: (Long?) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    var expanded by remember { mutableStateOf(false) }
    var fieldWidth by remember { mutableStateOf(0.dp) }
    val selected = categories.firstOrNull { it.id == selectedId }
    val border by animateColorAsState(if (expanded) colors.primary else colors.outlineVariant, tween(160), label = "cat-field")

    Box(Modifier.fillMaxWidth()) {
        Surface(
            onClick = { expanded = true },
            shape = MaterialTheme.shapes.small,
            color = colors.surfaceContainerLowest,
            border = BorderStroke(if (expanded) 1.5.dp else 1.dp, border),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .onSizeChanged { fieldWidth = with(density) { it.width.toDp() } }
                .semantics {
                    role = Role.DropdownList
                    contentDescription = "Category, ${selected?.name ?: "none"}"
                },
        ) {
            Row(
                Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryTile(category = selected, type = type, size = 32.dp)
                Spacer(Modifier.width(Spacing.md))
                // The label rides inside the field (like the Note field's), saving a header row.
                Column(Modifier.weight(1f)) {
                    Text("Category · optional", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1)
                    Text(
                        selected?.name ?: "No category",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selected != null) colors.onSurface else colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = colors.onSurfaceVariant)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(fieldWidth).heightIn(max = 360.dp),
        ) {
            DropdownMenuItem(
                text = { Text("No category") },
                leadingIcon = { CategoryTile(category = null, type = type, size = 28.dp) },
                trailingIcon = if (selectedId == null) ({ Icon(Icons.Filled.Check, contentDescription = "Selected", tint = colors.primary) }) else null,
                onClick = {
                    expanded = false
                    onSelect(null)
                },
            )
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { CategoryTile(category = category, type = type, size = 28.dp) },
                    trailingIcon = if (category.id == selectedId) ({ Icon(Icons.Filled.Check, contentDescription = "Selected", tint = colors.primary) }) else null,
                    onClick = {
                        expanded = false
                        onSelect(category.id)
                    },
                )
            }
        }
    }
}

/** Large, centred amount input with the wallet's currency symbol. Shrinks for long values. */
@Composable
internal fun AmountEntry(
    text: String,
    currencyCode: String?,
    type: TransactionType,
    isError: Boolean,
    autoFocus: Boolean,
    onTextChange: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val colors = MaterialTheme.colorScheme
    val tint by animateColorAsState(
        when {
            isError -> colors.error
            type == TransactionType.INCOME -> LucentoTheme.extendedColors.positive
            else -> colors.onSurface
        },
        tween(180),
        label = "amount-tint",
    )
    val base = AmountStyles.entry
    val style = when {
        text.length <= 9 -> base
        text.length <= 13 -> base.copy(fontSize = 30.sp, lineHeight = 36.sp)
        else -> base.copy(fontSize = 24.sp, lineHeight = 30.sp)
    }

    if (autoFocus) {
        LaunchedEffect(Unit) {
            delay(150) // let the sheet attach before asking for the keyboard
            runCatching { focusRequester.requestFocus() }
        }
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = (
                when {
                    type.isTransfer -> ""
                    type == TransactionType.INCOME -> "+"
                    else -> MoneyFormatter.MINUS
                }
                ) +
                (currencyCode?.let { MoneyFormatter.currencySymbol(it) } ?: ""),
            style = style.copy(fontSize = style.fontSize * 0.7f),
            color = colors.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.width(Spacing.xs))
        AmountTextField(
            value = text,
            onValueChange = onTextChange,
            filter = { input -> input.filter { it.isDigit() || it == '.' || it == ',' }.take(20) },
            modifier = Modifier
                .weight(1f, fill = false)
                .width(IntrinsicSize.Min)
                .focusRequester(focusRequester)
                .semantics { contentDescription = "Amount" + (currencyCode?.let { " in $it" } ?: "") },
            field = { editor, onEdit, fieldModifier ->
                BasicTextField(
                    value = editor,
                    onValueChange = onEdit,
                    singleLine = true,
                    textStyle = style.copy(color = tint, textAlign = TextAlign.Start),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                    modifier = fieldModifier,
                    decorationBox = { inner ->
                        Box {
                            if (editor.text.isEmpty()) {
                                Text("0", style = style, color = colors.outline, maxLines = 1)
                            }
                            inner()
                        }
                    },
                )
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
/** Today / Yesterday shortcuts plus a picker; the picker pill shows any other chosen date. */
@Composable
internal fun DateChoices(date: LocalDate, onDate: (LocalDate) -> Unit, onPick: () -> Unit) {
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    val haptics = rememberHaptics()
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        ChoicePill(label = "Today", selected = date == today, onClick = { haptics.tick(); onDate(today) })
        ChoicePill(label = "Yesterday", selected = date == yesterday, onClick = { haptics.tick(); onDate(yesterday) })
        val custom = date != today && date != yesterday
        ChoicePill(
            label = if (custom) shortDayLabel(date, today) else "Other date",
            selected = custom,
            leadingIcon = Icons.Outlined.CalendarMonth,
            role = Role.Button,
            onClick = onPick,
        )
    }
}
