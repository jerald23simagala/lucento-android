package com.lucento.app.feature.transactions

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucento.app.core.designsystem.component.ChoicePill
import com.lucento.app.core.designsystem.component.ConfirmDialog
import com.lucento.app.core.designsystem.component.IconTile
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoBottomSheet
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.FieldHeader
import com.lucento.app.core.designsystem.component.SegmentedToggle
import com.lucento.app.core.designsystem.component.icon
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.data.repository.TransactionRepository
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Amount-first transaction editor. The amount is the largest element and receives focus for new
 * entries; type, wallet, category, date and note follow in the order people think about them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditorSheet(
    initial: TransactionDraft,
    wallets: List<Wallet>,
    categories: List<Category>,
    onSave: (TransactionDraft, Char, (TransactionDraftError?) -> Unit) -> Unit,
    onDelete: ((Long) -> Unit)?,
    onDismiss: () -> Unit,
    /** Offered as a third type for new entries when transfers are possible; switches to the transfer sheet. */
    onSwitchToTransfer: (() -> Unit)? = null,
    /** False when the user tracks a single account (Multiple wallets off) and it is the only choice. */
    showWalletPicker: Boolean = true,
    /** Shown above the form, e.g. "From your receipt — check the amount" for scanned or spoken drafts. */
    sourceNote: String? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val decimalSeparator = remember { MoneyFormatter.decimalSeparator() }

    var draft by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<TransactionDraftError?>(null) }
    var pickingDate by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    // Wallets can arrive a moment after the sheet opens on a cold start; pre-select one when they do.
    LaunchedEffect(wallets) {
        if (draft.walletId == null && wallets.isNotEmpty()) draft = draft.copy(walletId = wallets.first().id)
    }
    val wallet = wallets.firstOrNull { it.id == draft.walletId }
    val kind = if (draft.type == TransactionType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
    // Archived categories stay selectable only when this transaction already uses one.
    val choices = categories.filter { it.kind == kind && (!it.archived || it.id == draft.categoryId) }
    val amountError = error is TransactionDraftError.AmountInvalid || error is TransactionDraftError.AmountZero ||
        error is TransactionDraftError.AmountTooPrecise || error is TransactionDraftError.AmountTooLarge
    val isNew = initial.id == null

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    LucentoBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isNew) "New transaction" else "Edit transaction",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (!isNew && onDelete != null) {
                    IconButton(onClick = { confirmingDelete = true }) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Delete transaction",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.lg))

            if (sourceNote != null) {
                InfoNote(sourceNote)
                Spacer(Modifier.height(Spacing.md))
            }

            val canSwitch = isNew && onSwitchToTransfer != null
            SegmentedToggle(
                options = listOf(TransactionType.EXPENSE to "Expense", TransactionType.INCOME to "Income") +
                    (if (canSwitch) listOf(TransactionType.TRANSFER_OUT to "Transfer") else emptyList()),
                selected = draft.type,
                onSelect = { type ->
                    if (type.isTransfer) {
                        onSwitchToTransfer?.invoke()
                    } else {
                        // A category belongs to one kind; drop it when the kind changes.
                        draft = draft.copy(type = type, categoryId = null)
                    }
                },
            )

            Spacer(Modifier.height(Spacing.xl))
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
            Text(
                when (val e = error) {
                    TransactionDraftError.AmountInvalid -> "Enter an amount, like 12${decimalSeparator}50."
                    TransactionDraftError.AmountZero -> "The amount must be greater than zero."
                    is TransactionDraftError.AmountTooPrecise ->
                        if (e.digits == 0) "${wallet?.currencyCode.orEmpty()} has no decimal places."
                        else "${wallet?.currencyCode.orEmpty()} allows up to ${e.digits} decimal places."
                    TransactionDraftError.AmountTooLarge -> "That amount is larger than Lucento supports."
                    else -> wallet?.let { "${it.currencyCode} · ${MoneyFormatter.currencyName(it.currencyCode)}" } ?: "Choose a wallet below"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (amountError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            )

            Spacer(Modifier.height(Spacing.lg))
            if (showWalletPicker || wallets.size > 1) {
                FieldHeader(if (draft.type == TransactionType.INCOME) "Into wallet" else "From wallet")
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    wallets.forEach { w ->
                        ChoicePill(
                            label = "${w.name} · ${w.currencyCode}",
                            selected = draft.walletId == w.id,
                            leadingIcon = w.type.icon,
                            onClick = {
                                haptics.tick()
                                draft = draft.copy(walletId = w.id)
                                if (error is TransactionDraftError.WalletRequired || amountError) error = null
                            },
                        )
                    }
                }
                if (error is TransactionDraftError.WalletRequired) {
                    Text("Choose a wallet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(Modifier.height(Spacing.md))
            FieldHeader("Category") {
                Text(
                    "Optional",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CategoryGrid(
                categories = choices,
                selectedId = draft.categoryId,
                type = draft.type,
                onToggle = { id ->
                    haptics.tick()
                    draft = draft.copy(categoryId = if (draft.categoryId == id) null else id)
                },
            )

            Spacer(Modifier.height(Spacing.md))
            FieldHeader("Date")
            DateChoices(
                date = draft.date,
                onDate = { draft = draft.copy(date = it) },
                onPick = { pickingDate = true },
            )

            Spacer(Modifier.height(Spacing.lg))
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

            if (error is TransactionDraftError.Rejected) {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "This transaction couldn’t be saved. Check the wallet and category, then try again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(Spacing.xl))
            LucentoPrimaryButton(
                text = when {
                    !isNew -> "Save changes"
                    draft.type == TransactionType.INCOME -> "Add income"
                    else -> "Add expense"
                },
                onClick = {
                    onSave(draft, decimalSeparator) { result ->
                        if (result == null) close() else {
                            haptics.reject()
                            error = result
                        }
                    }
                },
            )
            Spacer(Modifier.height(Spacing.lg))
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
            text = "It will be removed from this device and the wallet balance will update. This can’t be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                confirmingDelete = false
                onDelete(initial.id)
                close()
            },
            onDismiss = { confirmingDelete = false },
        )
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
        BasicTextField(
            value = text,
            onValueChange = { input -> onTextChange(input.filter { it.isDigit() || it == '.' || it == ',' }.take(20)) },
            singleLine = true,
            textStyle = style.copy(color = tint, textAlign = TextAlign.Start),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
            modifier = Modifier
                .weight(1f, fill = false)
                .width(IntrinsicSize.Min)
                .focusRequester(focusRequester)
                .semantics { contentDescription = "Amount" + (currencyCode?.let { " in $it" } ?: "") },
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) {
                        Text("0", style = style, color = colors.outline, maxLines = 1)
                    }
                    inner()
                }
            },
        )
    }
}

/** Category choices as an icon grid: 4 columns on regular phones, 3 on narrow ones. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryGrid(
    categories: List<Category>,
    selectedId: Long?,
    type: TransactionType,
    onToggle: (Long) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 320.dp) 4 else 3
        val gap = Spacing.sm
        val tileWidth = (maxWidth - gap * (columns - 1)) / columns
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
            maxItemsInEachRow = columns,
        ) {
            categories.forEach { category ->
                CategoryChoice(
                    category = category,
                    selected = category.id == selectedId,
                    type = type,
                    onClick = { onToggle(category.id) },
                    modifier = Modifier.width(tileWidth),
                )
            }
        }
    }
}

@Composable
private fun CategoryChoice(
    category: Category,
    selected: Boolean,
    type: TransactionType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val positive = type == TransactionType.INCOME
    val border by animateColorAsState(if (selected) colors.primary else colors.outlineVariant, tween(160), label = "cat-border")
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .border(BorderStroke(if (selected) 1.5.dp else 1.dp, border), MaterialTheme.shapes.medium)
            .selectable(selected = selected, role = Role.Checkbox, onClick = onClick)
            .padding(vertical = Spacing.md, horizontal = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconTile(
            icon = category.icon,
            container = when {
                selected -> colors.primary
                positive -> LucentoTheme.extendedColors.positiveContainer
                else -> colors.surfaceContainerHigh
            },
            content = when {
                selected -> colors.onPrimary
                positive -> LucentoTheme.extendedColors.positive
                else -> colors.onSurface
            },
            size = 36.dp,
            shape = MaterialTheme.shapes.small,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            category.name,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
        )
    }
}

/** Today / Yesterday shortcuts plus a picker; the picker pill shows any other chosen date. */
@Composable
internal fun DateChoices(date: LocalDate, onDate: (LocalDate) -> Unit, onPick: () -> Unit) {
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    val haptics = rememberHaptics()
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
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
