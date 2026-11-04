package com.lucento.app.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SouthEast
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.AmountText
import com.lucento.app.core.designsystem.component.CardOverline
import com.lucento.app.core.designsystem.component.CategoryTile
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.IconTile
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.WalletIdentityTile
import com.lucento.app.core.designsystem.component.label
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.designsystem.theme.TonePair
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.Locale

/**
 * Read-only view of one transaction, opened by tapping a history row. Nothing here changes data: the Edit pill in the
 * top bar opens the editor (or the transfer sheet). If the transaction disappears (deleted from the editor), the page
 * steps back on its own.
 *
 * The page reads top to bottom: what happened (tile, title, signed amount, kind and day) on a card washed in the
 * transaction's tone; for a transfer, the route between the two wallets with their own tiles; then every detail on its
 * own line with an icon — category, wallet, date, currency, note and when it was recorded.
 */
@Composable
fun TransactionDetailScreen(
    transactionId: Long,
    onBack: () -> Unit,
    onEdit: (Transaction) -> Unit,
    viewModel: TransactionDetailViewModel = viewModel(
        key = "transaction-$transactionId",
        factory = TransactionDetailViewModel.factory(transactionId),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val transaction = state.transaction
    LaunchedEffect(state.loading, transaction == null) {
        if (!state.loading && transaction == null) onBack()
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LucentoTopBar(
            title = when {
                transaction?.isTransfer == true -> "Transfer"
                transaction?.isAdjustment == true -> "Balance adjustment"
                else -> "Transaction"
            },
            onBack = onBack,
            modifier = Modifier.padding(horizontal = Spacing.gutter),
            actions = {
                // An adjustment isn't income or spending, so it has no editor: delete it from the list and adjust again.
                if (transaction != null && !transaction.isAdjustment) EditPill(onClick = { onEdit(transaction) })
            },
        )
        if (transaction == null) return@Column

        val ext = LucentoTheme.extendedColors
        val isTransfer = transaction.isTransfer
        val isIncome = transaction.type == TransactionType.INCOME
        val isAdjustment = transaction.isAdjustment
        val tone = when {
            isTransfer -> TonePair(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)
            isAdjustment -> TonePair(ext.warningContainer, ext.warning)
            isIncome -> TonePair(ext.positiveContainer, ext.positive)
            else -> TonePair(ext.negativeContainer, ext.negative)
        }
        val categoryName = state.category?.name ?: "Uncategorized"
        val transfer = state.transfer
        val title = when {
            transaction.note.isNotBlank() -> transaction.note
            isTransfer -> "Transfer"
            isAdjustment -> "Balance adjustment"
            else -> categoryName
        }
        val kindLabel = when {
            isTransfer -> "Transfer"
            isAdjustment -> "Balance adjustment"
            isIncome -> "Income"
            else -> "Expense"
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter)
                .padding(top = Spacing.xs, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // ---- What happened, on a wash of its tone
            LucentoCard(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(tone.container.copy(alpha = 0.75f), tone.container.copy(alpha = 0f))))
                        .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
                ) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        CategoryTile(category = state.category, type = transaction.type, size = 60.dp)
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(Spacing.xxs))
                        AmountText(
                            money = if (isTransfer) transaction.amount else transaction.signedAmount,
                            style = AmountStyles.hero,
                            signed = !isTransfer,
                            showPlus = true,
                            color = tone.content,
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                            Pill(kindLabel, tone)
                            Pill(relativeDay(transaction.date), TonePair(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant))
                        }
                    }
                }
            }

            // ---- A transfer's route: from one wallet to the other
            if (isTransfer && transfer != null) {
                LucentoCard(modifier = Modifier.fillMaxWidth(), contentPadding = Spacing.md) {
                    CardOverline("Route")
                    Spacer(Modifier.height(Spacing.sm))
                    RouteStop("From", state.walletsById[transfer.fromWalletId], state.walletNames[transfer.fromWalletId])
                    Row(Modifier.padding(start = 10.dp).heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.SouthEast, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.md))
                        Text(
                            "Moved between your own wallets — not income or spending",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    RouteStop("To", state.walletsById[transfer.toWalletId], state.walletNames[transfer.toWalletId])
                }
            }

            // ---- Every detail, one line each
            LucentoCard(modifier = Modifier.fillMaxWidth(), contentPadding = Spacing.md) {
                CardOverline("Details")
                Spacer(Modifier.height(Spacing.xs))
                val lines = buildList<@Composable () -> Unit> {
                    if (!isTransfer && !isAdjustment) {
                        add {
                            DetailLine(
                                label = "Category",
                                value = categoryName,
                                muted = state.category == null,
                                leading = { CategoryTile(category = state.category, type = transaction.type, size = DetailTileSize) },
                            )
                        }
                    }
                    if (!isTransfer) {
                        add {
                            val wallet = state.wallet
                            DetailLine(
                                label = when {
                                    isAdjustment -> "Wallet"
                                    isIncome -> "Paid into"
                                    else -> "Paid from"
                                },
                                value = wallet?.name ?: "—",
                                supporting = wallet?.type?.label,
                                leading = { if (wallet != null) WalletIdentityTile(wallet, size = DetailTileSize) else IconLeading(Icons.Outlined.Payments) },
                            )
                        }
                    }
                    add {
                        DetailLine(
                            label = "Date",
                            value = fullDate(transaction.date),
                            // Only a relative word adds anything under the full date ("Today", "3 days ago").
                            supporting = relativeDay(transaction.date).takeIf {
                                kotlin.math.abs(ChronoUnit.DAYS.between(LocalDate.now(), transaction.date)) <= 6
                            },
                            leading = { IconLeading(Icons.Outlined.CalendarMonth) },
                        )
                    }
                    add {
                        DetailLine(
                            label = "Currency",
                            value = transaction.currencyCode,
                            supporting = currencyName(transaction.currencyCode),
                            leading = { IconLeading(Icons.Outlined.Payments) },
                        )
                    }
                    add {
                        DetailLine(
                            label = "Note",
                            value = transaction.note.ifBlank { "No note" },
                            muted = transaction.note.isBlank(),
                            leading = { IconLeading(Icons.AutoMirrored.Outlined.Notes) },
                        )
                    }
                    if (transaction.createdAtEpochMillis > 0L) {
                        add {
                            DetailLine(
                                label = "Recorded",
                                value = recordedAt(transaction.createdAtEpochMillis),
                                leading = { IconLeading(Icons.Outlined.Schedule) },
                            )
                        }
                    }
                }
                lines.forEachIndexed { index, line ->
                    line()
                    if (index < lines.lastIndex) Hairline(inset = DetailTileSize + Spacing.md)
                }
            }
            if (isAdjustment) {
                InfoNote(
                    "Recorded when this wallet's balance was set by hand: the gap between what Lucento tracked and the " +
                        "actual balance. It isn't counted as income or spending. A large one usually means transactions " +
                        "weren't recorded — delete it and add them instead if you find them.",
                    icon = Icons.Outlined.WarningAmber,
                    container = ext.warningContainer,
                    content = ext.warning,
                )
            }
        }
    }
}

private val DetailTileSize = 36.dp

/** One detail: its icon or tile, a small label over the value, and an optional second line. Read as one sentence. */
@Composable
private fun DetailLine(
    label: String,
    value: String,
    leading: @Composable () -> Unit,
    supporting: String? = null,
    muted: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = Spacing.sm)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** A detail's icon on the same tile size as category and wallet tiles, so every line starts on one edge. */
@Composable
private fun IconLeading(icon: ImageVector) {
    IconTile(
        icon,
        container = MaterialTheme.colorScheme.surfaceContainerHigh,
        content = MaterialTheme.colorScheme.primary,
        size = DetailTileSize,
        shape = RoundedCornerShape(10.dp),
    )
}

/** One end of a transfer: the wallet's own tile, "From"/"To" and its name and type. */
@Composable
private fun RouteStop(label: String, wallet: Wallet?, fallbackName: String?) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (wallet != null) WalletIdentityTile(wallet, size = 40.dp) else IconLeading(Icons.Outlined.Payments)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(
                wallet?.name ?: fallbackName ?: "Account",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (wallet != null) {
            Text(wallet.type.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun Pill(text: String, tone: TonePair) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = tone.content,
        maxLines = 1,
        modifier = Modifier
            .background(tone.container, RoundedCornerShape(50))
            .padding(horizontal = Spacing.md, vertical = Spacing.xxs),
    )
}

/** "Monday, November 3, 2026" in the reader's locale. */
private fun fullDate(date: LocalDate, locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).format(date)

/** "Today", "Yesterday", "3 days ago", "In 2 days", or the short date once it's further away. */
internal fun relativeDay(date: LocalDate, today: LocalDate = LocalDate.now(), locale: Locale = Locale.getDefault()): String {
    val days = ChronoUnit.DAYS.between(today, date)
    return when {
        days == 0L -> "Today"
        days == -1L -> "Yesterday"
        days == 1L -> "Tomorrow"
        days in -6L..-2L -> "${-days} days ago"
        days in 2L..6L -> "In $days days"
        else -> shortDayLabel(date, today, locale)
    }
}

private fun currencyName(code: String): String? =
    runCatching { Currency.getInstance(code).getDisplayName(Locale.getDefault()) }.getOrNull()?.takeIf { it != code }

private fun recordedAt(epochMillis: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
        .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

/** The page's one action: a brand-tinted pill matching the Back pill opposite it. */
@Composable
private fun EditPill(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .semantics {
                contentDescription = "Edit"
                role = Role.Button
            },
    ) {
        Row(
            Modifier.heightIn(min = 40.dp).padding(start = Spacing.md, end = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text("Edit", style = MaterialTheme.typography.labelLarge)
        }
    }
}
