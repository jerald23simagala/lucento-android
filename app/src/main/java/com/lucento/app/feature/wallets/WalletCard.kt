package com.lucento.app.feature.wallets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import com.lucento.app.core.banks.Institution
import com.lucento.app.core.designsystem.component.CardChip
import com.lucento.app.core.designsystem.component.InstitutionCardMark
import com.lucento.app.core.designsystem.component.institution
import com.lucento.app.core.designsystem.component.markSpellsName
import com.lucento.app.core.designsystem.component.registryColor
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
import com.lucento.app.core.designsystem.component.InstitutionAvatar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.Role
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
fun WalletCard(wallet: Wallet, movements: Int, modifier: Modifier = Modifier, institution: Institution? = wallet.institution()) {
    if (institution != null) {
        InstitutionWalletCard(wallet, institution, movements, modifier)
        return
    }
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
                        CardChip()
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
                        MoneyText(money = wallet.cardAmount, style = AmountStyles.large, color = ink, fractionScale = 1f)
                    }

                    CardFooter(wallet, movements, muted, ink)
                }
            }
        }
    }
}

/** Opening balance (or amount owed at start), then a debt's terms or the movement count, along the card's bottom edge. */
@Composable
private fun CardFooter(wallet: Wallet, movements: Int, muted: Color, ink: Color) {
    Row(verticalAlignment = Alignment.Bottom) {
        if (wallet.type.isValuedManually) {
            CardField("Valued", "By you, not by transactions", muted, ink, Modifier.weight(1f))
        } else {
            CardField(
                if (wallet.type.isLiability) "Owed at start" else "Opening balance",
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

/**
 * A wallet linked to an institution: the institution's face (a contrast-checked gradient from the design registry),
 * its logo or monogram top-left, the account-type chip top-right, a decorative card chip, the user's name for the
 * wallet, then the balance. Not a payment card: no number, expiry or network mark. Liabilities read differently without
 * colour alone — "Amount owed" with the signed amount (spoken the same way), a dashed type chip and a hatched trailing edge.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InstitutionWalletCard(wallet: Wallet, institution: Institution, movements: Int, modifier: Modifier) {
    val card = institution.card
    val ink = registryColor(card.ink)
    val muted = ink.copy(alpha = card.mutedAlpha.coerceIn(0.74f, 1f))
    val accent = registryColor(card.accent)
    val stops = card.stops.map(::registryColor).ifEmpty { listOf(registryColor(institution.brand)) }
    val brush = if (stops.size == 1) SolidColor(stops.first()) else Brush.linearGradient(stops)
    val liability = wallet.type.isLiability
    val label = when {
        liability -> "Amount owed"
        wallet.type.isValuedManually -> "Estimated value"
        else -> "Current balance"
    }
    val spoken = "${institution.name}, ${wallet.type.label}, ${wallet.name}, ${label.lowercase()} ${MoneyFormatter.formatSigned(wallet.cardAmount, showPlus = false)}"
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val minHeight = maxWidth / CardRatio
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(brush)
                .semantics(mergeDescendants = true) { contentDescription = spoken },
        ) {
            Canvas(Modifier.matchParentSize()) { drawTemplate(card.template, ink, accent, liability) }
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight)
                    .padding(start = Spacing.lg, top = Spacing.lg, bottom = Spacing.lg, end = if (liability) Spacing.lg + 8.dp + HatchWidth else Spacing.lg),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // The chip moves under the mark when large text leaves no room beside it, instead of being cut off.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.Start),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false).align(Alignment.CenterVertically)) {
                        InstitutionCardMark(institution)
                        if (!institution.markSpellsName) {
                            Spacer(Modifier.width(Spacing.sm))
                            Text(
                                institution.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = ink,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.align(Alignment.CenterVertically)) { TypeChip(wallet.type, ink) }
                }
                Column {
                    Spacer(Modifier.size(Spacing.md))
                    CardChip()
                    Spacer(Modifier.size(Spacing.md))
                    Text(
                        wallet.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.size(Spacing.xs))
                    Text(label, style = MaterialTheme.typography.labelMedium, color = muted)
                    MoneyText(money = wallet.cardAmount, style = AmountStyles.large, color = ink, fractionScale = 1f)
                    Spacer(Modifier.size(Spacing.md))
                }
                CardFooter(wallet, movements, muted, ink)
            }
        }
    }
}

/** A debt shows as its signed effect on net worth (−1,250.00 owed), so meaning never rests on the label or colour alone. */
private val Wallet.cardAmount get() = if (type.isLiability) netWorthContribution else balance

private val HatchWidth = 14.dp

/** Template decoration, kept faint and away from the balance; liabilities add the hatched trailing band. */
private fun DrawScope.drawTemplate(template: String, ink: Color, accent: Color, liability: Boolean) {
    when (template) {
        "brand_gradient" -> {
            drawCircle(ink.copy(alpha = 0.07f), radius = size.height * 0.8f, center = Offset(size.width * 1.02f, size.height * 1.08f))
            drawCircle(ink.copy(alpha = 0.05f), radius = size.height * 0.55f, center = Offset(size.width * 0.72f, size.height * 1.2f))
        }
        "glass" -> drawRoundRect(
            ink.copy(alpha = 0.06f),
            topLeft = Offset(size.width * 0.55f, -size.height * 0.2f),
            size = Size(size.width * 0.6f, size.height * 0.75f),
            cornerRadius = CornerRadius(24.dp.toPx()),
        )
        "premium_dark", "minimal_light" -> drawRect(accent, topLeft = Offset(0f, size.height - 3.dp.toPx()), size = Size(size.width, 3.dp.toPx()))
        "minimal_brand_accent" -> drawRect(accent, size = Size(6.dp.toPx(), size.height))
    }
    if (liability) {
        val band = HatchWidth.toPx()
        val left = size.width - band
        val step = 6.dp.toPx()
        clipRect(left = left, top = 0f, right = size.width, bottom = size.height) {
            var x = left - size.height
            while (x < size.width) {
                drawLine(ink.copy(alpha = 0.35f), Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 1.5.dp.toPx())
                x += step
            }
        }
    }
}

/** The account type on an institution card: filled for assets, a dashed outline for debts. */
@Composable
private fun TypeChip(type: WalletType, ink: Color, label: String = type.label) {
    val shape = RoundedCornerShape(50)
    val chip = Modifier
        .then(
            if (type.isLiability) {
                Modifier.drawBehind {
                    val stroke = 1.5.dp.toPx()
                    drawRoundRect(
                        ink,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(size.height / 2),
                        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                    )
                }
            } else {
                Modifier.background(ink.copy(alpha = 0.14f), shape)
            }
        )
        .padding(horizontal = Spacing.sm + Spacing.xxs, vertical = Spacing.xxs + 1.dp)
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
        color = ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = chip,
    )
}

/**
 * The New wallet sheet's live preview: the same face, type mark and ink as [WalletCard], in a short strip that
 * follows the type, name, currency and amount as they are typed. [amount] is null while the field is empty or invalid.
 */
@Composable
fun WalletCardPreview(
    type: WalletType,
    name: String,
    currencyCode: String,
    amount: com.lucento.app.core.money.Money?,
    amountLabel: String,
    modifier: Modifier = Modifier,
    institution: Institution? = null,
) {
    val face = LucentoTheme.extendedColors.walletCard(type)
    val ink = institution?.let { registryColor(it.card.ink) } ?: face.content
    val muted = ink.copy(alpha = 0.74f)
    val stops = institution?.card?.stops?.map(::registryColor).orEmpty()
    val background = when {
        stops.size > 1 -> Brush.linearGradient(stops)
        stops.size == 1 -> SolidColor(stops.first())
        else -> SolidColor(face.container)
    }
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(background)) {
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
                        fractionScale = 1f,
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                if (institution != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        InstitutionCardMark(institution)
                        Spacer(Modifier.size(Spacing.xs))
                        TypeChip(type, ink)
                    }
                } else {
                    TypeMark(type, ink)
                }
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


/**
 * A wallet's face at list size (Wallets' card view, two or three to a row): the same type or institution face as
 * [WalletCard], 118dp tall — chip or institution icon and the type chip on top, then the name (one line) and the balance,
 * which shrinks rather than wraps. A debt reads without colour: a dashed type chip, a hatched trailing edge, the signed
 * amount and "owed". TalkBack hears one sentence with the signed amount.
 */
@Composable
fun CompactWalletFace(wallet: Wallet, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val institution = wallet.institution()
    val face = LucentoTheme.extendedColors.walletCard(wallet.type)
    val ink = institution?.let { registryColor(it.card.ink) } ?: face.content
    val stops = institution?.card?.stops?.map(::registryColor).orEmpty()
    val background = when {
        stops.size > 1 -> Brush.linearGradient(stops)
        stops.size == 1 -> SolidColor(stops.first())
        institution != null -> SolidColor(registryColor(institution.brand))
        else -> SolidColor(face.container)
    }
    val liability = wallet.type.isLiability
    val shape = RoundedCornerShape(16.dp)
    val spoken = listOfNotNull(
        institution?.name,
        wallet.type.label,
        wallet.name,
        (if (liability) "amount owed " else "balance ") + MoneyFormatter.formatSigned(wallet.cardAmount, showPlus = false),
    ).joinToString(", ")
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 118.dp)
            .clip(shape)
            .background(background)
            .clickable(role = Role.Button, onClickLabel = "Open wallet", onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
    ) {
        Canvas(Modifier.matchParentSize()) {
            val ring = Stroke(1.dp.toPx())
            drawCircle(ink.copy(alpha = 0.12f), radius = 75.dp.toPx(), center = Offset(size.width + 15.dp.toPx(), 5.dp.toPx()), style = ring)
            drawCircle(ink.copy(alpha = 0.12f), radius = 45.dp.toPx(), center = Offset(size.width - 25.dp.toPx(), size.height + 5.dp.toPx()), style = ring)
            if (liability) drawTemplate("", ink, ink, liability = true)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 118.dp)
                .padding(start = 14.dp, top = Spacing.md, bottom = Spacing.md, end = if (liability) 14.dp + HatchWidth else 14.dp),
            // Name and balance sit at the bottom of the face, the marks at the top, however tall large text makes it.
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Spacing.sm)) {
                if (institution != null) InstitutionAvatar(institution, size = 28.dp) else CardChip(Modifier.size(width = 22.dp, height = 16.dp))
                Spacer(Modifier.weight(1f).widthIn(min = Spacing.xs))
                TypeChip(wallet.type, ink, label = wallet.type.shortLabel)
            }
            Column {
            Text(
                wallet.name,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                MoneyText(
                    money = wallet.cardAmount,
                    style = AmountStyles.medium.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
                    color = ink,
                    fractionScale = 1f,
                    minScale = 0.5f,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (liability) {
                    Spacer(Modifier.width(6.dp))
                    Text("owed", style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.78f), maxLines = 1)
                }
            }
            }
        }
    }
}

/** A small capitalised label over a value, like the name and expiry on a card. */
@Composable
private fun CardField(label: String, value: String, muted: Color, ink: Color, modifier: Modifier = Modifier, alignEnd: Boolean = false) {
    Column(modifier, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp), color = muted, maxLines = 2)
        Text(value, style = AmountStyles.row, color = ink, maxLines = 1)
    }
}
