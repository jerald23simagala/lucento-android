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
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.domain.Category
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * One line of history: category tile, what it was (note, or category when there is no note),
 * where it came from, and the signed amount. Income shows "+" and the positive tone; expenses show
 * "−" in ink — the sign carries the meaning, colour only reinforces it.
 */
@Composable
fun TransactionRow(
    transaction: Transaction,
    category: Category?,
    wallet: Wallet?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingCaption: String? = null,
) {
    val isIncome = transaction.type == TransactionType.INCOME
    val categoryName = category?.name ?: "Uncategorized"
    val title = transaction.note.ifBlank { categoryName }
    val subtitle = listOfNotNull(
        categoryName.takeIf { transaction.note.isNotBlank() },
        wallet?.name,
    ).joinToString(" · ")

    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClickLabel = "Edit transaction", onClick = onClick)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryTile(category = category, type = transaction.type)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            AmountText(
                money = transaction.signedAmount,
                style = AmountStyles.row,
                showPlus = true,
                color = if (isIncome) LucentoTheme.extendedColors.positive else MaterialTheme.colorScheme.onSurface,
            )
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
