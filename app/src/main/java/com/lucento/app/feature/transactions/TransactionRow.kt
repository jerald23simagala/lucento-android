package com.lucento.app.feature.transactions

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.component.AmountText
import com.lucento.app.core.designsystem.component.CategoryTile
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.domain.Category
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Transfer
import com.lucento.app.domain.Wallet
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * One line of history: category tile, what it was (note, or category when there is no note),
 * where it came from, and the signed amount. Income shows "+" and the positive tone; expenses show
 * "−" in ink — the sign carries the meaning, colour only reinforces it.
 *
 * Transfers show "From → To". In combined history they are neutral and unsigned (money only moved
 * between the user's own accounts); in a single account's history ([perspectiveOfWallet]) they are signed
 * for that account.
 */
@Composable
fun TransactionRow(
    transaction: Transaction,
    category: Category?,
    wallet: Wallet?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingCaption: String? = null,
    transfer: Transfer? = null,
    walletNames: Map<Long, String> = emptyMap(),
    perspectiveOfWallet: Boolean = false,
    /** Tighter height, a smaller tile and inner padding for rows that sit inside a day card. */
    dense: Boolean = false,
    /** What a tap does, for TalkBack; defaults to opening the editor. */
    onClickLabel: String? = null,
) {
    val isIncome = transaction.type == TransactionType.INCOME
    val isTransfer = transaction.isTransfer
    val categoryName = category?.name ?: "Uncategorized"
    val title: String
    val subtitle: String
    if (transaction.isAdjustment) {
        title = transaction.note.ifBlank { "Balance adjustment" }
        subtitle = listOfNotNull("Balance adjustment".takeIf { transaction.note.isNotBlank() }, wallet?.name).joinToString(" · ")
    } else if (isTransfer) {
        val from = transfer?.let { walletNames[it.fromWalletId] } ?: "Account"
        val to = transfer?.let { walletNames[it.toWalletId] } ?: "Account"
        title = transaction.note.ifBlank { "Transfer" }
        subtitle = "$from → $to"
    } else {
        title = transaction.note.ifBlank { categoryName }
        subtitle = listOfNotNull(
            categoryName.takeIf { transaction.note.isNotBlank() },
            wallet?.name,
        ).joinToString(" · ")
    }

    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = if (dense) 56.dp else 64.dp)
            .clickable(
                role = Role.Button,
                onClickLabel = onClickLabel ?: when {
                    isTransfer -> "Edit transfer"
                    transaction.isAdjustment -> "View balance adjustment"
                    else -> "Edit transaction"
                },
                onClick = onClick,
            )
            .padding(
                horizontal = if (dense) Spacing.md else 0.dp,
                vertical = if (dense) Spacing.xs + Spacing.xxs else Spacing.sm,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryTile(category = category, type = transaction.type, size = if (dense) 36.dp else Sizes.iconTile)
        Spacer(Modifier.width(if (dense) Spacing.md - Spacing.xxs else Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = if (dense) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(Spacing.md))
        Column(horizontalAlignment = Alignment.End) {
            when {
                isTransfer && !perspectiveOfWallet -> AmountText(
                    money = transaction.amount,
                    style = AmountStyles.row,
                    signed = false,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> AmountText(
                    money = transaction.signedAmount,
                    style = AmountStyles.row,
                    showPlus = true,
                    color = when {
                        isIncome -> LucentoTheme.extendedColors.positive
                        isTransfer -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            if (trailingCaption != null) {
                Text(
                    trailingCaption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** "Today", "Yesterday", or a locale-ordered weekday + date (year only when it isn't this year). */
fun dayLabel(date: LocalDate, today: LocalDate = LocalDate.now(), locale: Locale = Locale.getDefault()): String =
    when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> {
            localizedDate(date, if (date.year == today.year) "EEEEdMMMM" else "EEEEdMMMMy", locale)
        }
    }

/** Short relative date for compact rows: "Today", "Yesterday", "12 Mar". */
fun shortDayLabel(date: LocalDate, today: LocalDate = LocalDate.now(), locale: Locale = Locale.getDefault()): String =
    when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> {
            localizedDate(date, if (date.year == today.year) "dMMM" else "dMMMy", locale)
        }
    }

/** Formats with the locale's preferred ordering for [skeleton]; falls back to a medium date. */
private fun localizedDate(date: LocalDate, skeleton: String, locale: Locale): String =
    runCatching {
        date.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale))
    }.getOrElse { date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)) }
