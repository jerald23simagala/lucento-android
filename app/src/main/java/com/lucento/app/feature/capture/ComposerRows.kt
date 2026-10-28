package com.lucento.app.feature.capture

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.lucento.app.core.designsystem.component.AmountTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.component.CategoryTile
import com.lucento.app.core.designsystem.component.StatusTones
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.capture.ReviewRow
import com.lucento.app.domain.capture.RowEdit
import com.lucento.app.domain.capture.RowField
import com.lucento.app.domain.capture.RowIssue
import com.lucento.app.domain.capture.RowStatus
import com.lucento.app.feature.plan.formatDate
import java.time.LocalDate

/** One tap on a review row: expense and income trade places. A transfer becomes an expense. */
internal fun flipIncomeExpense(type: TransactionType): TransactionType = when (type) {
    TransactionType.EXPENSE -> TransactionType.INCOME
    TransactionType.INCOME -> TransactionType.EXPENSE
    else -> TransactionType.EXPENSE
}

internal fun typeLabel(type: TransactionType): String = when (type) {
    TransactionType.INCOME -> "Income"
    TransactionType.EXPENSE -> "Expense"
    else -> "Transfer"
}

internal fun dateLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> formatDate(date)
}

/**
 * One proposed transaction as its own card, in three rows: a small date (with the account when it isn't the wallet
 * bar's, and remove); the category tile, description and signed amount; then type and category — or a transfer's two
 * accounts — sharing the card's width, so no selector runs past the edge. An incomplete row is outlined in red, its
 * fields are tinted, and the reason is written underneath in words; colour never carries the meaning alone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReviewRowItem(
    row: ReviewRow,
    status: RowStatus?,
    wallets: List<Wallet>,
    categories: List<Category>,
    /** The wallet bar's choice; a row's own account chip shows only when it differs (or needs fixing). */
    activeWalletId: Long?,
    transfersAllowed: Boolean,
    enabled: Boolean,
    onEdit: (RowEdit) -> Unit,
    onPickDate: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val category = categories.firstOrNull { it.id == row.categoryId }
    var editingDescription by rememberSaveable(row.key) { mutableStateOf(false) }
    var editingAmount by rememberSaveable(row.key) { mutableStateOf(false) }
    fun issue(field: RowField): RowIssue? = status?.issueFor(field)
    fun attention(field: RowField): Boolean = issue(field)?.blocking == true
    val incomplete = status?.ready == false

    val shape = MaterialTheme.shapes.medium
    val dark = LucentoTheme.isDark
    val tint = LucentoTheme.extendedColors.shadow
    Surface(
        shape = shape,
        // By day a soft navy shadow lifts each card off the canvas; by night (where shadows don't read) a lighter tone.
        color = if (dark) colors.surfaceContainerHigh else colors.surfaceContainerLowest,
        border = if (incomplete) BorderStroke(1.5.dp, StatusTones.negative.content) else BorderStroke(1.dp, LucentoTheme.extendedColors.borderDefault),
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (dark) Modifier else Modifier.shadow(
                    elevation = 4.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = tint.copy(alpha = 0.08f),
                    spotColor = tint.copy(alpha = 0.16f),
                ),
            ),
    ) {
        Column(Modifier.padding(start = Spacing.md, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.sm)) {
            val typeOptions = buildList {
                add(TransactionType.EXPENSE)
                add(TransactionType.INCOME)
                if (transfersAllowed || row.type.isTransfer) add(TransactionType.TRANSFER_OUT)
            }
            val typeChip: @Composable (Boolean) -> Unit = { small ->
                TypeChip(
                    type = row.type,
                    options = typeOptions,
                    attention = attention(RowField.TYPE),
                    enabled = enabled,
                    small = small,
                    offerMenu = transfersAllowed || row.type.isTransfer,
                    onFlip = { onEdit(RowEdit.Type(flipIncomeExpense(row.type))) },
                    onPick = { onEdit(RowEdit.Type(it)) },
                )
            }

            // 1 — when (small), plus the account if it isn't the wallet bar's, and remove.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    FieldChip(
                        label = dateLabel(row.date),
                        description = "Date: ${formatDate(row.date)}. Change date",
                        icon = Icons.Outlined.CalendarToday,
                        attention = attention(RowField.DATE),
                        softAttention = issue(RowField.DATE)?.blocking == false,
                        enabled = enabled,
                        small = true,
                        onClick = onPickDate,
                    )
                    val showAccount = wallets.size > 1 && row.walletId != activeWalletId
                    if (!row.type.isTransfer && (showAccount || attention(RowField.WALLET))) {
                        Spacer(Modifier.width(6.dp))
                        AccountChip(
                            null, row.walletId, wallets, attention(RowField.WALLET), enabled,
                            small = true,
                            modifier = Modifier.weight(1f, fill = false),
                        ) { onEdit(RowEdit.Account(it)) }
                    }
                }
                IconButton(onClick = onRemove, enabled = enabled) {
                    Icon(Icons.Outlined.Close, contentDescription = "Remove this transaction", tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }

            // 2 — what and how much.
            Row(Modifier.padding(end = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                CategoryTile(category = category, type = row.type, size = 32.dp)
                Spacer(Modifier.width(Spacing.sm))
                Box(Modifier.weight(1f)) {
                    if (editingDescription) {
                        InlineField(
                            initial = row.description,
                            label = "Description",
                            keyboardType = KeyboardType.Text,
                            onDone = {
                                onEdit(RowEdit.Description(it.trim()))
                                editingDescription = false
                            },
                        )
                    } else {
                        val shown = row.description.ifBlank {
                            if (row.type.isTransfer) transferTitle(row, wallets) else category?.name ?: typeLabel(row.type)
                        }
                        Text(
                            shown,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Edit description") { editingDescription = true }
                                .wrapContentHeight(Alignment.CenterVertically),
                        )
                    }
                }
                if (editingAmount) {
                    InlineField(
                        initial = row.amountText,
                        label = "Amount",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.widthIn(min = 96.dp, max = 140.dp),
                        onDone = {
                            onEdit(RowEdit.Amount(it.trim()))
                            editingAmount = false
                        },
                    )
                } else {
                    AmountLabel(row, status, enabled) { editingAmount = true }
                }
            }

            // Competing amounts, as one-tap choices; they wrap rather than run off the card.
            if (row.amountText.isBlank() && row.amountOptions.isNotEmpty()) {
                FlowRow(
                    Modifier.padding(end = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    row.amountOptions.forEach { option ->
                        FieldChip(
                            label = option.toPlainString(),
                            description = "Use ${option.toPlainString()} as the amount",
                            onClick = { onEdit(RowEdit.ChooseAmount(option)) },
                            enabled = enabled,
                            attention = true,
                        )
                    }
                }
            }

            // 3 — type and category (a transfer's two accounts), sharing the card's width so nothing overflows.
            Row(
                Modifier.fillMaxWidth().padding(end = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                typeChip(false)
                if (row.type.isTransfer) {
                    AccountChip("From", row.walletId, wallets, attention(RowField.WALLET), enabled, fill = true, modifier = Modifier.weight(1f)) {
                        onEdit(RowEdit.Account(it))
                    }
                    AccountChip("To", row.toWalletId, wallets, attention(RowField.TO_WALLET), enabled, fill = true, modifier = Modifier.weight(1f)) {
                        onEdit(RowEdit.ToAccount(it))
                    }
                } else {
                    val kind = if (row.type == TransactionType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
                    val pool = categories.filter { it.kind == kind && !it.archived }
                    MenuChip(
                        label = category?.name ?: "Choose category",
                        description = "Category: ${category?.name ?: "none"}. Change category",
                        attention = attention(RowField.CATEGORY),
                        softAttention = category == null && !attention(RowField.CATEGORY),
                        enabled = enabled,
                        fill = true,
                        modifier = Modifier.weight(1f),
                        options = pool.map { c -> c.name to { onEdit(RowEdit.CategoryChoice(c.id)) } } +
                            listOf("No category" to { onEdit(RowEdit.CategoryChoice(null)) }),
                    )
                }
            }

            // Blocking problems are always spelled out; gentle hints show only when nothing blocks the row.
            val shownIssues = status?.issues.orEmpty().let { all -> all.filter { it.blocking }.ifEmpty { all } }
            shownIssues.forEach { IssueLine(it, Modifier.padding(start = 40.dp, end = Spacing.sm, bottom = Spacing.xs)) }
        }
    }
}

/** "Bank → GCash", with "?" for a side still to choose, so a transfer never reads like spending. */
private fun transferTitle(row: ReviewRow, wallets: List<Wallet>): String {
    fun name(id: Long?) = wallets.firstOrNull { it.id == id }?.name ?: "?"
    return "${name(row.walletId)} → ${name(row.toWalletId)}"
}

@Composable
private fun AmountLabel(row: ReviewRow, status: RowStatus?, enabled: Boolean, onClick: () -> Unit) {
    val money = status?.money
    val ext = LucentoTheme.extendedColors
    val color = when {
        money == null -> ext.negative
        row.type == TransactionType.INCOME -> ext.positive
        row.type.isTransfer -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    val text = when {
        money != null && row.type.isTransfer -> MoneyFormatter.format(money)
        money != null && row.type == TransactionType.INCOME -> MoneyFormatter.formatSigned(money, showPlus = true)
        money != null -> MoneyFormatter.formatSigned(-money, showPlus = true)
        row.amountText.isNotBlank() -> row.amountText
        row.amountOptions.isNotEmpty() -> "Choose"
        else -> "Add amount"
    }
    Text(
        text,
        style = AmountStyles.row,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Edit amount", onClick = onClick)
            .padding(horizontal = Spacing.xs)
            .wrapContentHeight(Alignment.CenterVertically)
            .semantics { contentDescription = "Amount: $text. Edit amount" },
    )
}

@Composable
private fun InlineField(
    initial: String,
    label: String,
    keyboardType: KeyboardType,
    onDone: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var value by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val done = { onDone(value) }
    val fieldModifier = modifier.fillMaxWidth().focusRequester(focus)
    if (keyboardType == KeyboardType.Decimal) {
        AmountTextField(
            value = value,
            onValueChange = { value = it },
            filter = { it.take(24) },
            label = { Text(label) },
            textStyle = AmountStyles.row,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { done() }),
            trailingIcon = {
                IconButton(onClick = done) { Icon(Icons.Filled.Check, contentDescription = "Done") }
            },
            modifier = fieldModifier,
        )
    } else {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it.take(200) },
            label = { Text(label) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { done() }),
            trailingIcon = {
                IconButton(onClick = done) { Icon(Icons.Filled.Check, contentDescription = "Done") }
            },
            modifier = fieldModifier,
        )
    }
}

/** A compact, tappable field value. [attention] tints it; [softAttention] uses the quieter hint tone. */
@Composable
private fun FieldChip(
    label: String,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean,
    icon: ImageVector? = null,
    attention: Boolean = false,
    softAttention: Boolean = false,
    trailing: Boolean = false,
    /** When set, the arrow is its own control and [onClick] is only the label. */
    onTrailingClick: (() -> Unit)? = null,
    /** The quieter size used for the date line. */
    small: Boolean = false,
    /** Stretch to the width given (the label ellipsizes, the arrow stays at the end). */
    fill: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val negative = StatusTones.negative
    val (container, content, border) = when {
        attention && !softAttention -> Triple(negative.container, negative.content, negative.content)
        softAttention -> Triple(colors.surfaceContainer, colors.onSurface, colors.outline)
        else -> Triple(if (LucentoTheme.isDark) colors.surfaceContainer else colors.surfaceContainerLowest, colors.onSurface, colors.outlineVariant)
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        color = container,
        contentColor = content,
        border = BorderStroke(1.dp, border),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = if (small) 28.dp else 32.dp)
            .semantics { contentDescription = description },
    ) {
        Row(
            Modifier
                .then(if (fill) Modifier.fillMaxWidth() else Modifier)
                .padding(horizontal = if (small) 8.dp else 10.dp, vertical = if (small) 2.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(if (small) 14.dp else 16.dp))
                Spacer(Modifier.width(Spacing.xs))
            }
            Text(
                label,
                style = if (small) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = fill),
            )
            if (trailing) {
                if (onTrailingClick != null) {
                    IconButton(onClick = onTrailingClick, enabled = enabled, modifier = Modifier.size(if (small) 28.dp else 32.dp)) {
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "More types", modifier = Modifier.size(if (small) 16.dp else 18.dp))
                    }
                } else {
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(if (small) 16.dp else 18.dp))
                }
            }
        }
    }
}

/** Expense/Income in one tap on every review row. The arrow still lists Transfer when that is allowed. */
@Composable
private fun TypeChip(
    type: TransactionType,
    options: List<TransactionType>,
    attention: Boolean,
    enabled: Boolean,
    small: Boolean,
    offerMenu: Boolean,
    onFlip: () -> Unit,
    onPick: (TransactionType) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        FieldChip(
            label = typeLabel(type),
            description = "Type: ${typeLabel(type)}. Switch between expense and income",
            attention = attention,
            enabled = enabled,
            small = small,
            trailing = offerMenu,
            onClick = onFlip,
            onTrailingClick = if (offerMenu) ({ open = true }) else null,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(typeLabel(option)) }, onClick = {
                    open = false
                    onPick(option)
                })
            }
        }
    }
}

@Composable
private fun MenuChip(
    label: String,
    description: String,
    attention: Boolean,
    enabled: Boolean,
    options: List<Pair<String, () -> Unit>>,
    softAttention: Boolean = false,
    small: Boolean = false,
    fill: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        FieldChip(
            label, description, { open = true }, enabled,
            attention = attention, softAttention = softAttention, trailing = true, small = small, fill = fill,
            modifier = if (fill) Modifier.fillMaxWidth() else Modifier,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (text, action) ->
                DropdownMenuItem(text = { Text(text) }, onClick = {
                    open = false
                    action()
                })
            }
        }
    }
}

@Composable
private fun AccountChip(
    prefix: String?,
    walletId: Long?,
    wallets: List<Wallet>,
    attention: Boolean,
    enabled: Boolean,
    small: Boolean = false,
    fill: Boolean = false,
    modifier: Modifier = Modifier,
    onChoose: (Long) -> Unit,
) {
    val name = wallets.firstOrNull { it.id == walletId }?.name
    val label = when {
        prefix != null && name != null -> "$prefix $name"
        prefix != null -> "$prefix: choose"
        else -> name ?: "Choose account"
    }
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        FieldChip(
            label = label,
            description = "${prefix ?: "Account"}: ${name ?: "not chosen"}. Change account",
            onClick = { open = true },
            enabled = enabled,
            // The row's title already shows the arrow; the words alone keep room for the account name.
            icon = null,
            attention = attention || name == null,
            trailing = true,
            small = small,
            fill = fill,
            modifier = if (fill) Modifier.fillMaxWidth() else Modifier,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            wallets.forEach { w ->
                DropdownMenuItem(text = { Text("${w.name} · ${w.currencyCode}") }, onClick = {
                    open = false
                    onChoose(w.id)
                })
            }
        }
    }
}

@Composable
private fun IssueLine(issue: RowIssue, modifier: Modifier = Modifier) {
    val color = if (issue.blocking) LucentoTheme.extendedColors.negative else MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier, verticalAlignment = Alignment.Top) {
        Icon(
            if (issue.blocking) Icons.Outlined.ErrorOutline else Icons.Outlined.Info,
            contentDescription = null,
            tint = color,
            modifier = Modifier.padding(top = 1.dp).size(16.dp),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(issue.message, style = MaterialTheme.typography.bodySmall, color = color)
    }
}
