package com.lucento.app.feature.wallets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucento.app.core.designsystem.component.MoneyText
import com.lucento.app.core.designsystem.component.icon
import com.lucento.app.core.designsystem.component.label
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.domain.DebtMath
import com.lucento.app.domain.DebtRepayment
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType

/** ISO/IEC 7810 ID-1, the shape of a bank card. The card grows taller if large text needs the room. */
private const val CardRatio = 1.586f

/**
 * A wallet as a bank card: its name and currency where the issuer sits, the type mark top-right where a card network's
 * logo would be, a chip, the balance as the card's main line, and the opening balance and movement count along the
 * bottom edge. Each type has its own solid brand face ([com.lucento.app.core.designsystem.theme.LucentoExtendedColors.walletCard]);
 * two faint circles in the card's ink are the only decoration — flat, no gradients.
 */
@Composable
fun WalletCard(wallet: Wallet, movements: Int, modifier: Modifier = Modifier) {
    val face = LucentoTheme.extendedColors.walletCard(wallet.type)
    val ink = face.content
    val muted = ink.copy(alpha = 0.74f)
    val liability = wallet.type.isLiability
    val valued = wallet.type.isValuedManually
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val minHeight = maxWidth / CardRatio
        Surface(shape = RoundedCornerShape(20.dp), color = face.container, contentColor = ink, modifier = Modifier.fillMaxWidth()) {
            Box {
                Canvas(Modifier.matchParentSize()) {
                    drawCircle(ink.copy(alpha = 0.07f), radius = size.height * 0.8f, center = Offset(size.width * 1.02f, size.height * 1.08f))
                    drawCircle(ink.copy(alpha = 0.05f), radius = size.height * 0.55f, center = Offset(size.width * 0.72f, size.height * 1.2f))
                }
                Column(
                    Modifier.fillMaxWidth().heightIn(min = minHeight).padding(Spacing.lg),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                wallet.name,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.semantics { heading() },
                            )
                            Text(
                                "${wallet.currencyCode} · ${MoneyFormatter.currencyName(wallet.currencyCode)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(Spacing.sm))
                        TypeMark(wallet.type, ink)
                    }

                    Column {
                        Spacer(Modifier.size(Spacing.sm))
                        Chip(if (wallet.type == WalletType.CASH || wallet.type == WalletType.E_WALLET) ink.copy(alpha = 0.22f) else LucentoTheme.extendedColors.accent)
                        Spacer(Modifier.size(Spacing.sm))
                        Text(
                            when {
                                liability -> "Amount owed"
                                valued -> "Estimated value"
                                else -> "Current balance"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = muted,
                        )
                        MoneyText(money = wallet.balance, style = AmountStyles.display, color = ink)
                    }

                    Row(verticalAlignment = Alignment.Bottom) {
                        if (valued) {
                            CardField("Valued", "By you, not by transactions", muted, ink, Modifier.weight(1f))
                        } else {
                            CardField(
                                if (liability) "Owed at start" else "Opening balance",
                                MoneyFormatter.format(wallet.openingBalance),
                                muted,
                                ink,
                                Modifier.weight(1f),
                            )
                            val debt = wallet.debt
                            val rate = debt?.interestRateBps
                            val installment = debt?.installment?.takeIf { debt.repayment == DebtRepayment.MONTHLY }
                            // A debt's terms take the movements' place: rate, then the monthly amortization and term.
                            if (rate != null) {
                                CardField("Interest", DebtMath.formatRate(rate) + " / yr", muted, ink, Modifier.padding(end = Spacing.md))
                            }
                            when {
                                installment != null -> CardField(
                                    "Monthly",
                                    MoneyFormatter.format(installment) + (debt?.termMonths?.let { " × $it" } ?: ""),
                                    muted,
                                    ink,
                                    alignEnd = true,
                                )
                                rate == null -> CardField("Movements", "$movements", muted, ink, alignEnd = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The New wallet sheet's live preview: the same face, type mark and ink as [WalletCard], in a short strip that
 * follows the type, name, currency and amount as they are typed. [amount] is null while the field is empty or invalid.
 */
@Composable
fun WalletCardPreview(type: WalletType, name: String, currencyCode: String, amount: com.lucento.app.core.money.Money?, amountLabel: String, modifier: Modifier = Modifier) {
    val face = LucentoTheme.extendedColors.walletCard(type)
    val ink = face.content
    val muted = ink.copy(alpha = 0.74f)
    Surface(shape = RoundedCornerShape(20.dp), color = face.container, contentColor = ink, modifier = modifier.fillMaxWidth()) {
        Box {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(ink.copy(alpha = 0.07f), radius = size.height * 0.9f, center = Offset(size.width * 1.0f, size.height * 1.15f))
            }
            Row(Modifier.fillMaxWidth().padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        name.ifBlank { "Your ${type.label.lowercase()}" },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (name.isBlank()) muted else ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "$currencyCode · ${MoneyFormatter.currencyName(currencyCode)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.size(Spacing.sm))
                    Text(amountLabel, style = MaterialTheme.typography.labelMedium, color = muted, maxLines = 1)
                    MoneyText(
                        money = amount ?: com.lucento.app.core.money.Money(0, currencyCode),
                        style = AmountStyles.large,
                        color = ink,
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                TypeMark(type, ink)
            }
        }
    }
}

/** The card network's place: the wallet type's glyph above its name in small capitals. */
@Composable
private fun TypeMark(type: WalletType, ink: Color) {
    Column(horizontalAlignment = Alignment.End) {
        Box(
            Modifier.size(36.dp).background(ink.copy(alpha = 0.14f), RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(type.icon, contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.size(Spacing.xxs))
        Text(
            type.label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
            color = ink,
            maxLines = 1,
        )
    }
}

/** A plain EMV-style chip: decoration that says "card" at a glance. */
@Composable
private fun Chip(color: Color) {
    Box(Modifier.size(width = 38.dp, height = 28.dp).background(color, RoundedCornerShape(6.dp)))
}

/** A small capitalised label over a value, like the name and expiry on a card. */
@Composable
private fun CardField(label: String, value: String, muted: Color, ink: Color, modifier: Modifier = Modifier, alignEnd: Boolean = false) {
    Column(modifier, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp), color = muted, maxLines = 1)
        Text(value, style = AmountStyles.row, color = ink, maxLines = 1)
    }
}
