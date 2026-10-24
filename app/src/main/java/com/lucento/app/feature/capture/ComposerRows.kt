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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.lucento.app.core.designsystem.component.ChoicePill
import com.lucento.app.core.designsystem.component.StatusTones
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.capture.CaptureSource
import com.lucento.app.domain.capture.ReviewRow
import com.lucento.app.domain.capture.RowEdit
import com.lucento.app.domain.capture.RowField
import com.lucento.app.domain.capture.RowIssue
import com.lucento.app.domain.capture.RowStatus
import com.lucento.app.feature.plan.formatDate
import java.time.LocalDate

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
 * One proposed transaction: category tile, description and signed amount on the first line (both tap to edit in
 * place), then compact selectors for type, category (or the two accounts of a transfer), date and account. Fields that
 * need attention are tinted and explained underneath in words; colour never carries the meaning alone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReviewRowItem(
    row: ReviewRow,
    status: RowStatus?,
    wallets: List<Wallet>,
    categories: List<Category>,
    showAccount: Boolean,
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
    var expanded by rememberSaveable(row.key) { mutableStateOf(false) }
    fun issue(field: RowField): RowIssue? = status?.issueFor(field)
    fun attention(field: RowField): Boolean = issue(field)?.blocking == true

    Column(modifier.fillMaxWidth().padding(vertical = Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryTile(category = category, type = row.type)
            Spacer(Modifier.width(Spacing.md))
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
                    val shown = row.description.ifBlank { category?.name ?: typeLabel(row.type) }
                    Text(
                        shown,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Edit description") { editingDescription = true }
                            .padding(vertical = Spacing.md),
                    )
                }
            }
            Spacer(Modifier.width(Spacing.sm))
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

        if (row.amountText.isBlank() && row.amountOptions.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(start = 52.dp, top = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                row.amountOptions.forEach { option ->
                    ChoicePill(
                        label = option.toPlainString(),
                        selected = false,
                        onClick = { onEdit(RowEdit.ChooseAmount(option)) },
                        role = Role.Button,
                    )
                }
            }
        }

        FlowRow(
            modifier = Modifier.padding(start = 52.dp, top = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            val typeOptions = buildList {
                add(TransactionType.EXPENSE)
                add(TransactionType.INCOME)
                if (transfersAllowed || row.type.isTransfer) add(TransactionType.TRANSFER_OUT)
            }
            MenuChip(
                label = typeLabel(row.type),
                description = "Type: ${typeLabel(row.type)}. Change type",
                attention = attention(RowField.TYPE),
                enabled = enabled,
                options = typeOptions.map { typeLabel(it) to { onEdit(RowEdit.Type(it)) } },
            )
            if (row.type.isTransfer) {
                AccountChip("From", row.walletId, wallets, attention(RowField.WALLET), enabled) { onEdit(RowEdit.Account(it)) }
                AccountChip("To", row.toWalletId, wallets, attention(RowField.TO_WALLET), enabled) { onEdit(RowEdit.ToAccount(it)) }
            } else {
                val kind = if (row.type == TransactionType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
                val pool = categories.filter { it.kind == kind && !it.archived }
                MenuChip(
                    label = category?.name ?: "Choose category",
                    description = "Category: ${category?.name ?: "none"}. Change category",
                    attention = category == null || attention(RowField.CATEGORY),
                    softAttention = category == null && !attention(RowField.CATEGORY),
                    enabled = enabled,
                    options = pool.map { c -> c.name to { onEdit(RowEdit.CategoryChoice(c.id)) } } +
                        listOf("No category" to { onEdit(RowEdit.CategoryChoice(null)) }),
                )
            }
            FieldChip(
                label = dateLabel(row.date),
                description = "Date: ${formatDate(row.date)}. Change date",
                icon = Icons.Outlined.CalendarToday,
                attention = issue(RowField.DATE) != null,
                softAttention = issue(RowField.DATE)?.blocking == false,
                enabled = enabled,
                onClick = onPickDate,
            )
            if (!row.type.isTransfer && (showAccount || attention(RowField.WALLET))) {
                AccountChip(null, row.walletId, wallets, attention(RowField.WALLET), enabled) { onEdit(RowEdit.Account(it)) }
            }
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Hide details" else "Show details",
                    tint = colors.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove, enabled = enabled) {
                Icon(Icons.Outlined.Close, contentDescription = "Remove this transaction", tint = colors.onSurfaceVariant)
            }
        }

        status?.issues?.forEach { IssueLine(it, Modifier.padding(start = 52.dp, top = Spacing.xs)) }

        if (expanded) {
            Column(Modifier.padding(start = 52.dp, top = Spacing.sm)) {
                Text(
                    when (row.source) {
                        CaptureSource.RECEIPT -> "READ FROM THE RECEIPT"
                        CaptureSource.SPOKEN -> "FROM WHAT YOU SAID"
                        CaptureSource.TYPED -> "FROM WHAT YOU WROTE"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
                Text(row.sourceText, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                if (row.edited.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        "You changed: " + row.edited.sortedBy { it.ordinal }.joinToString(", ") { fieldName(it) },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun fieldName(field: RowField) = when (field) {
    RowField.DESCRIPTION -> "description"
    RowField.TYPE -> "type"
    RowField.AMOUNT -> "amount"
    RowField.CATEGORY -> "category"
    RowField.DATE -> "date"
    RowField.WALLET -> "account"
    RowField.TO_WALLET -> "destination account"
}

@Composable
private fun AmountLabel(row: ReviewRow, status: RowStatus?, enabled: Boolean, onClick: () -> Unit) {
    val money = status?.money
    val ext = LucentoTheme.extendedColors
    val color = when {
        money == null -> ext.warning
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
            .padding(horizontal = Spacing.xs, vertical = Spacing.md)
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
    OutlinedTextField(
        value = value,
        onValueChange = { value = it.take(if (keyboardType == KeyboardType.Decimal) 24 else 200) },
        label = { Text(label) },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        textStyle = if (keyboardType == KeyboardType.Decimal) AmountStyles.row else MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            capitalization = if (keyboardType == KeyboardType.Text) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone(value) }),
        trailingIcon = {
            IconButton(onClick = { onDone(value) }) { Icon(Icons.Filled.Check, contentDescription = "Done") }
        },
        modifier = modifier.fillMaxWidth().focusRequester(focus),
    )
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
) {
    val colors = MaterialTheme.colorScheme
    val warning = StatusTones.warning
    val (container, content, border) = when {
        attention && !softAttention -> Triple(warning.container, warning.content, warning.content)
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
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 36.dp)
            .semantics { contentDescription = description },
    ) {
        Row(Modifier.padding(horizontal = Spacing.md, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(Spacing.xs))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
            if (trailing) Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
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
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FieldChip(label, description, { open = true }, enabled, attention = attention, softAttention = softAttention, trailing = true)
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
private fun AccountChip(prefix: String?, walletId: Long?, wallets: List<Wallet>, attention: Boolean, enabled: Boolean, onChoose: (Long) -> Unit) {
    val name = wallets.firstOrNull { it.id == walletId }?.name
    val label = when {
        prefix != null && name != null -> "$prefix $name"
        prefix != null -> "$prefix: choose"
        else -> name ?: "Choose account"
    }
    var open by remember { mutableStateOf(false) }
    Box {
        FieldChip(
            label = label,
            description = "${prefix ?: "Account"}: ${name ?: "not chosen"}. Change account",
            onClick = { open = true },
            enabled = enabled,
            icon = if (prefix != null) Icons.Outlined.SwapHoriz else null,
            attention = attention || name == null,
            trailing = true,
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
    val color = if (issue.blocking) LucentoTheme.extendedColors.warning else MaterialTheme.colorScheme.onSurfaceVariant
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
