package com.lucento.app.feature.wallets

import com.lucento.app.core.designsystem.component.AmountFormat
import com.lucento.app.core.designsystem.component.amountFormat
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
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import com.lucento.app.domain.CardCycle
import com.lucento.app.domain.CreditCardMath
import com.lucento.app.domain.DebtMath
import com.lucento.app.domain.DebtRepayment
import com.lucento.app.domain.LoanProgress
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import java.time.LocalDate

/** ISO/IEC 7810 ID-1, the shape of a bank card. The card grows taller if large text needs the room. */
private const val CardRatio = 1.586f

/**
 * A wallet as a bank card: its name (with a debt's yearly rate) and currency where the issuer sits, the type mark
 * top-right where a card network's logo would be, a chip, the balance as the card's main line, and the opening balance
 * and movement count along the bottom edge. Each type has its own solid brand face
 * ([com.lucento.app.core.designsystem.theme.LucentoExtendedColors.walletCard]); two faint circles in the card's ink are
 * the only decoration — flat, no gradients.
 *
 * An instalment loan ([loan]) reads Remaining over a bar of its instalments, with Principal, Paid and the monthly
 * amortization along the bottom. A credit card shows its Credit limit, Available credit and the Amount due on its
 * current statement ([cycle]) along the bottom, when the user entered them.
 */
@Composable
fun WalletCard(
    wallet: Wallet,
    movements: Int,
    modifier: Modifier = Modifier,
    institution: Institution? = wallet.institution(),
    cycle: CardCycle? = null,
    loan: LoanProgress? = null,
    today: LocalDate = LocalDate.now(),
) {
    if (institution != null) {
        InstitutionWalletCard(wallet, institution, movements, modifier, cycle, loan, today)
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
                            NameWithRate(
                                wallet,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = ink,
                                modifier = Modifier.semantics(mergeDescendants = true) { heading() },
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
                        // An instalment loan gives the chip's room to its repayment bar.
                        if (loan == null) {
                            CardChip()
                            Spacer(Modifier.size(Spacing.sm))
                        }
                        BalanceBlock(
                            wallet,
                            label = when {
                                loan != null -> "Remaining"
                                liability -> "Amount owed"
                                wallet.type == WalletType.RECEIVABLE -> "Owed to you"
                                valued -> "Estimated value"
                                else -> "Current balance"
                            },
                            loan = loan,
                            muted = muted,
                            ink = ink,
                        )
                        Spacer(Modifier.size(Spacing.sm))
                    }

                    CardFooter(wallet, movements, muted, ink, cycle, loan, today)
                }
            }
        }
    }
}

/**
 * Along the card's bottom edge: an instalment loan's Principal, Paid and monthly amortization; a credit card's Credit
 * limit, Available credit and Amount due (when entered); otherwise the opening balance (or amount owed at start), then
 * a debt's monthly amortization or the movement count. The yearly rate sits in the title ([NameWithRate]).
 */
@Composable
private fun CardFooter(wallet: Wallet, movements: Int, muted: Color, ink: Color, cycle: CardCycle?, loan: LoanProgress?, today: LocalDate) {
    val fmt = amountFormat()
    if (wallet.type.isValuedManually) {
        CardField("Valued", "By you, not by transactions", muted, ink)
        return
    }
    val debt = wallet.debt
    val installment = debt?.installment?.takeIf { debt.repayment == DebtRepayment.MONTHLY }
    if (loan != null) {
        CardFigures(
            listOfNotNull(
                CardFigure("Principal", loan.principal),
                // A receivable's instalments come in (owner round 10).
                CardFigure(if (wallet.type == WalletType.RECEIVABLE) "Received" else "Paid", loan.paid),
                installment?.let { CardFigure("Monthly", it) },
            ),
            muted,
            ink,
        )
        return
    }
    val limit = debt?.creditLimit
    val available = CreditCardMath.available(wallet)
    if (wallet.type == WalletType.CREDIT_CARD && (available != null || cycle != null)) {
        CardFigures(
            listOfNotNull(
                limit?.let { CardFigure("Credit limit", it) },
                available?.let { CardFigure("Available", it) },
                cycle?.let { CardFigure("Amount due", it.amountDue, caption = dueCaption(it, today)) },
            ),
            muted,
            ink,
        )
        return
    }
    Row(verticalAlignment = Alignment.Bottom) {
        CardField(
            when {
                wallet.type.isLiability -> "Owed at start"
                wallet.type == WalletType.RECEIVABLE -> "Lent at start"
                else -> "Opening balance"
            },
            fmt.format(wallet.openingBalance),
            muted,
            ink,
            Modifier.weight(1f),
        )
        if (installment != null) {
            CardField(
                "Monthly",
                fmt.format(installment) + (debt?.termMonths?.let { " × $it" } ?: ""),
                muted,
                ink,
                alignEnd = true,
            )
        } else {
            CardField("Movements", "$movements", muted, ink, alignEnd = true)
        }
    }
}

/** "by Nov 5", or "Overdue · Nov 5" once the due day has passed with something still to pay. */
private fun dueCaption(cycle: CardCycle, today: LocalDate): String =
    if (cycle.isOverdue(today)) "Overdue · ${shortDate(cycle.dueDate)}" else "by ${shortDate(cycle.dueDate)}"

/** " (24% / yr)" for a debt with a rate; empty otherwise. */
private fun Wallet.rateSuffix(): String = debt?.interestRateBps?.let { " (${DebtMath.formatRate(it)} / yr)" }.orEmpty()

/**
 * The wallet's name with its yearly rate appended in a lighter weight — "Car loan (24% / yr)". It wraps onto a second
 * line rather than cutting the name when the type mark and large text leave little room.
 */
@Composable
private fun NameWithRate(wallet: Wallet, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val rate = wallet.rateSuffix()
    Text(
        buildAnnotatedString {
            append(wallet.name)
            if (rate.isNotEmpty()) withStyle(SpanStyle(fontWeight = FontWeight.Normal)) { append(rate) }
        },
        style = style,
        color = color,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * The card's main figure: [label] over the balance; an instalment loan adds its repayment bar — one segment per
 * instalment, the paid ones green — and "8 of 36 instalments paid" in words, so the bar's colour is never the only cue.
 */
@Composable
private fun BalanceBlock(wallet: Wallet, label: String, loan: LoanProgress?, muted: Color, ink: Color) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = muted)
    MoneyText(money = wallet.cardAmount, style = AmountStyles.large, color = ink, fractionScale = 1f, minScale = 0.6f)
    // Multicurrency: the main-currency value under a foreign card's own amount.
    com.lucento.app.feature.fx.LocalFxDisplay.current.netWorthValue(wallet)?.let {
        com.lucento.app.core.designsystem.component.EquivalentAmount(it, color = ink.copy(alpha = 0.82f))
    }
    if (loan != null) {
        Spacer(Modifier.size(Spacing.sm))
        InstallmentBar(paid = loan.installmentsPaid, total = loan.installments, paidColor = LucentoTheme.extendedColors.paidOnCard(ink), track = ink.copy(alpha = 0.26f))
        Spacer(Modifier.size(Spacing.xs))
        Text(
            "${loan.installmentsPaid} of ${loan.installments} ${if (loan.installments == 1) "installment" else "installments"} " +
                if (wallet.type == WalletType.RECEIVABLE) "received" else "paid",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = ink,
        )
    }
}

/**
 * One segment per instalment with a small cut between them, the first [paid] in [paidColor]. When the term is so long
 * that segments would be thinner than the cuts, it draws one continuous bar instead.
 */
@Composable
private fun InstallmentBar(paid: Int, total: Int, paidColor: Color, track: Color) {
    // The paid segments fill in one after another when the card is first shown.
    val filled = com.lucento.app.core.designsystem.component.animatedFraction(
        if (total > 0) paid.coerceIn(0, total).toFloat() / total else 0f,
        durationMillis = 900,
    )
    Canvas(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50))) {
        if (total <= 0) return@Canvas
        val cut = 2.dp.toPx()
        val segment = (size.width - cut * (total - 1)) / total
        if (segment < 3.dp.toPx()) {
            drawRect(track)
            drawRect(paidColor, size = Size(size.width * filled, size.height))
            return@Canvas
        }
        val paidSoFar = filled * total
        for (i in 0 until total) {
            val left = i * (segment + cut)
            drawRect(track, topLeft = Offset(left, 0f), size = Size(segment, size.height))
            val part = (paidSoFar - i).coerceIn(0f, 1f)
            if (part > 0f) drawRect(paidColor, topLeft = Offset(left, 0f), size = Size(segment * part, size.height))
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
private fun InstitutionWalletCard(
    wallet: Wallet,
    institution: Institution,
    movements: Int,
    modifier: Modifier,
    cycle: CardCycle?,
    loan: LoanProgress?,
    today: LocalDate,
) {
    val fmt = amountFormat()
    val card = institution.card
    val ink = registryColor(card.ink)
    val muted = ink.copy(alpha = card.mutedAlpha.coerceIn(0.74f, 1f))
    val accent = registryColor(card.accent)
    val stops = card.stops.map(::registryColor).ifEmpty { listOf(registryColor(institution.brand)) }
    val brush = if (stops.size == 1) SolidColor(stops.first()) else Brush.linearGradient(stops)
    val liability = wallet.type.isLiability
    val label = when {
        loan != null -> "Remaining"
        liability -> "Amount owed"
        wallet.type == WalletType.RECEIVABLE -> "Owed to you"
        wallet.type.isValuedManually -> "Estimated value"
        else -> "Current balance"
    }
    val spoken = "${institution.name}, ${wallet.type.label}, ${wallet.name}${wallet.rateSuffix()}, ${label.lowercase()} " +
        fmt.spokenSigned(wallet.cardAmount, showPlus = false) + cardDetailsSpoken(wallet, cycle, loan, today, fmt)
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
                    // The mark and name take the whole row beside the chip (owner, 2026-11-06: a spacer here took half the
                    // free width, so "Bank of Commerce" read "Bank of …"). The name wraps to two lines before it's cut.
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).align(Alignment.CenterVertically)) {
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
                    Box(Modifier.align(Alignment.CenterVertically)) { TypeChip(wallet.type, ink) }
                }
                Column {
                    Spacer(Modifier.size(Spacing.md))
                    if (loan == null) {
                        CardChip()
                        Spacer(Modifier.size(Spacing.md))
                    }
                    NameWithRate(wallet, style = MaterialTheme.typography.bodyMedium, color = muted)
                    Spacer(Modifier.size(Spacing.xs))
                    BalanceBlock(wallet, label = label, loan = loan, muted = muted, ink = ink)
                    Spacer(Modifier.size(Spacing.md))
                }
                CardFooter(wallet, movements, muted, ink, cycle, loan, today)
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
 * [WalletCard], 118dp tall — chip or institution icon and the type chip on top, then the name (up to two lines) and the balance,
 * which shrinks rather than wraps. A debt reads without colour: a dashed type chip, a hatched trailing edge, the signed
 * amount and "owed". TalkBack hears one sentence with the signed amount.
 */
@Composable
fun CompactWalletFace(wallet: Wallet, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val fmt = amountFormat()
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
        (if (liability) "amount owed " else if (wallet.type == WalletType.RECEIVABLE) "owed to you " else "balance ") +
            fmt.signed(wallet.cardAmount, showPlus = false),
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
            // Two lines before anything is cut (owner, 2026-11-06: "Bank of Commerce" read "Bank of …").
            Text(
                wallet.name,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = ink,
                maxLines = 2,
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
            com.lucento.app.feature.fx.LocalFxDisplay.current.netWorthValue(wallet)?.let {
                com.lucento.app.core.designsystem.component.EquivalentAmount(it, color = ink.copy(alpha = 0.82f))
            }
            }
        }
    }
}

/** What an institution card's single spoken sentence adds for a loan's progress or a card's limit and amount due. */
private fun cardDetailsSpoken(wallet: Wallet, cycle: CardCycle?, loan: LoanProgress?, today: LocalDate, fmt: AmountFormat): String = buildString {
    if (loan != null) {
        val done = if (wallet.type == WalletType.RECEIVABLE) "received" else "paid"
        append(", principal ${fmt.spoken(loan.principal)}, $done ${fmt.spoken(loan.paid)}")
        append(", ${loan.installmentsPaid} of ${loan.installments} installments $done")
    }
    val limit = wallet.debt?.creditLimit
    val available = CreditCardMath.available(wallet)
    if (limit != null && available != null) {
        append(", credit limit ${fmt.spoken(limit)}, available ${fmt.spokenSigned(available, showPlus = false)}")
    }
    if (cycle != null) append(", amount due ${fmt.spoken(cycle.amountDue)} ${dueCaption(cycle, today)}")
}

/** One labelled amount on a card's bottom edge; [caption] goes under the amount ("by Nov 8"). */
private data class CardFigure(val label: String, val money: com.lucento.app.core.money.Money, val caption: String? = null)

/**
 * Up to three labelled amounts across the card's bottom edge, as a small grid: the labels in one row (bottom-aligned,
 * so a label that wraps at large text grows upwards), the amounts in the next — always on one line, shrinking rather
 * than being cut — and any caption under its amount. The last figure is right-aligned like a card's expiry.
 */
@Composable
private fun CardFigures(figures: List<CardFigure>, muted: Color, ink: Color) {
    if (figures.isEmpty()) return
    val labelStyle = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp)
    @Composable
    fun Cells(content: @Composable (CardFigure, Modifier, Boolean) -> Unit, alignment: Alignment.Vertical) {
        Row(verticalAlignment = alignment, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            figures.forEachIndexed { index, figure ->
                val end = figures.size > 1 && index == figures.lastIndex
                content(figure, Modifier.weight(1f), end)
            }
        }
    }
    Column {
        Cells({ figure, modifier, end ->
            Text(figure.label.uppercase(), style = labelStyle, color = muted, maxLines = 2, textAlign = if (end) TextAlign.End else TextAlign.Start, modifier = modifier)
        }, Alignment.Bottom)
        Cells({ figure, modifier, end ->
            Box(modifier, contentAlignment = if (end) Alignment.CenterEnd else Alignment.CenterStart) {
                MoneyText(money = figure.money, style = AmountStyles.row, color = ink, fractionScale = 1f, minScale = 0.6f)
            }
        }, Alignment.Top)
        if (figures.any { it.caption != null }) {
            Cells({ figure, modifier, end ->
                Text(figure.caption.orEmpty(), style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1, textAlign = if (end) TextAlign.End else TextAlign.Start, modifier = modifier)
            }, Alignment.Top)
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
