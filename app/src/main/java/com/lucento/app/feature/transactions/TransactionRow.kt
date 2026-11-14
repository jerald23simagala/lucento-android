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
 * where it came from, and the signed amount. Income shows "+" in the positive tone, expenses "−" in the negative tone
 * ([amountInk]) — the sign carries the meaning, colour only reinforces it.
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
    /**
     * On a card's or loan's own history: show each amount as its change in what is owed (a payment "−", interest "+").
     * Display only; the stored sign is unchanged.
     */
    owedPerspective: Boolean = false,
) {
    val isTransfer = transaction.isTransfer
    val categoryName = category?.name ?: "Uncategorized"
    val title: String
    val subtitle: String
    val outside = com.lucento.app.domain.OutsideMovement.of(transaction)
    if (outside != null) {
        // "Moved to my investment · Withdrawal · BPI": what it was for as the title, the kind and account beneath.
        val reason = com.lucento.app.domain.OutsideMovement.reasonOf(transaction)
        title = reason.ifBlank { outside.label }
        subtitle = listOfNotNull(outside.label.takeIf { reason.isNotBlank() }, wallet?.name).joinToString(" · ")
    } else if (transaction.isAdjustment) {
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
                    com.lucento.app.domain.OutsideMovement.of(transaction) != null ->
                        "View ${com.lucento.app.domain.OutsideMovement.of(transaction)!!.label.lowercase()}"
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
            val unsigned = isTransfer && !perspectiveOfWallet
            val shown = if (owedPerspective) -transaction.signedAmount else transaction.signedAmount
            when {
                unsigned -> AmountText(
                    money = transaction.amount,
                    style = AmountStyles.row,
                    signed = false,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> AmountText(
                    money = shown,
                    style = AmountStyles.row,
                    showPlus = true,
                    color = when (amountInk(transaction.type, owedPerspective)) {
                        AmountInk.POSITIVE -> LucentoTheme.extendedColors.positive
                        AmountInk.NEGATIVE -> LucentoTheme.extendedColors.negative
                        AmountInk.MUTED -> MaterialTheme.colorScheme.onSurfaceVariant
                        AmountInk.NEUTRAL -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            // Multicurrency: a movement in another currency shows its main-currency value under it, signed the same way.
            com.lucento.app.feature.fx.LocalFxDisplay.current
                .value(transaction, sign = if (unsigned) 0 else if (shown.isNegative) -1 else 1)
                ?.let { com.lucento.app.core.designsystem.component.EquivalentAmount(it, signed = !unsigned) }
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

/** Which ink a history amount takes. Colour only reinforces the sign, which is always shown. */
enum class AmountInk { POSITIVE, NEGATIVE, MUTED, NEUTRAL }

/**
 * Income is positive and expenses negative. Transfers are muted (money only moved between the user's own accounts).
 * Balance adjustments stay neutral ink: a correction is neither earned nor spent, so it is never painted as either.
 * On a card's or loan's own history ([owedPerspective]) amounts are changes in what is owed, also neutral.
 */
fun amountInk(type: TransactionType, owedPerspective: Boolean = false): AmountInk = when {
    owedPerspective -> AmountInk.NEUTRAL
    type == TransactionType.INCOME -> AmountInk.POSITIVE
    type == TransactionType.EXPENSE -> AmountInk.NEGATIVE
    type.isTransfer -> AmountInk.MUTED
    else -> AmountInk.NEUTRAL
}

/** A day group's title in the history: "Today", "Yesterday", or the weekday ("Monday"); [dayCaption] gives the date. */
fun dayTitle(date: LocalDate, today: LocalDate = LocalDate.now(), locale: Locale = Locale.getDefault()): String =
    when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }
    }

/** The date beside [dayTitle], locale-ordered: "Wed 18 Nov" under Today / Yesterday, "16 Nov" under a weekday. */
fun dayCaption(date: LocalDate, today: LocalDate = LocalDate.now(), locale: Locale = Locale.getDefault()): String =
    localizedDate(date, dayCaptionSkeleton(date, today), locale)

/** The caption's date fields: the weekday only when the title doesn't already name it; the year only when it isn't this year. */
internal fun dayCaptionSkeleton(date: LocalDate, today: LocalDate): String {
    val relative = date == today || date == today.minusDays(1)
    return (if (relative) "EEEdMMM" else "dMMM") + if (date.year == today.year) "" else "y"
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
