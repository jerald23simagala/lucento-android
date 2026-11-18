package com.lucento.app.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.component.AmountText
import com.lucento.app.core.designsystem.component.CardOverline
import com.lucento.app.core.designsystem.component.MoneyText
import com.lucento.app.core.designsystem.component.StatusPill
import com.lucento.app.core.designsystem.component.amountFormat
import com.lucento.app.core.designsystem.component.animatedFraction
import com.lucento.app.core.designsystem.component.shownAmount
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.OverlineStyle
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.Money
import com.lucento.app.domain.BudgetBurn
import com.lucento.app.domain.BudgetPlan
import com.lucento.app.domain.SafeToSpend
import com.lucento.app.domain.Wallet
import com.lucento.app.feature.budget.healthColor
import com.lucento.app.feature.budget.healthTone

/*
 * The Monthly budget card, redesigned (owner, 2026-11-18): A, how much of the budget is used (a half gauge); B, the two
 * spending limits side by side, Budget remaining and Spendable wallets; C, Safe to spend, the lower of them, as the
 * card's conclusion. B and C are shared with the Budget tab ([SpendingCapacityBlock]) so the two pages never disagree;
 * every figure comes from [SpendingCapacity], read from SafeToSpend.
 *
 * TalkBack: each limit and the verdict is one button with one sentence that carries every figure in it (a description
 * on a single child would have replaced the others in what it reads).
 */

/**
 * Below this width, or at large text sizes, the two limits stack, each taking the full width. 280dp keeps them side by
 * side on a 360dp phone at the default text size.
 */
private val SideBySideFrom = 280.dp

private fun stacked(width: Dp, fontScale: Float): Boolean = width < SideBySideFrom || fontScale >= 1.3f

/** The verdict puts its figure and its words side by side only this wide (tablets, unfolded phones). */
private val VerdictSideBySideFrom = 360.dp

/** What a tap on the verdict does, as TalkBack offers it ("Double-tap to show how…"). */
internal const val SHOW_HOW_LABEL = "Show how Safe to spend is worked out"

/** The gauge's figures sit in its hollow up to this text size; above it they go under the gauge. */
private const val FIGURES_INSIDE_UP_TO = 1.2f

// ---------------------------------------------------------------------------------------------------- A: overview

/**
 * Section A: spending against the month's budget as a half gauge, the amount spent in its hollow with "spent of" the
 * budget under it, the share used, then how Spent splits into budgeted and unbudgeted spending. At large text sizes, or
 * when "spent of" would need a second line, the amounts move under the gauge and the share used takes the hollow, so
 * nothing reaches the stroke.
 */
@Composable
internal fun BudgetOverview(plan: BudgetPlan, burn: BudgetBurn) {
    val fmt = amountFormat()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val used = burn.consumedPercent ?: 0
    val density = LocalDensity.current
    val spentOf = "spent of ${fmt.format(plan.total)}"
    val spentOfStyle = MaterialTheme.typography.bodyMedium
    val measurer = rememberTextMeasurer()
    val figures: @Composable () -> Unit = {
        // Merged, so TalkBack reads the amount and what it is of together.
        Column(Modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
            MoneyText(money = plan.spent, style = AmountStyles.hero, fractionScale = 1f, minScale = 0.5f)
            Text(spentOf, style = spentOfStyle, color = muted, textAlign = TextAlign.Center)
        }
    }
    val share: @Composable () -> Unit = { StatusPill("$used% spent", healthTone(plan.health)) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gaugeWidth = minOf(maxWidth, 240.dp)
        val hollow = gaugeWidth - GaugeInset * 2
        val hollowPx = with(density) { hollow.roundToPx() }
        val wraps = remember(spentOf, spentOfStyle, hollowPx, density) {
            measurer.measure(spentOf, spentOfStyle, constraints = Constraints(maxWidth = hollowPx)).lineCount > 1
        }
        val below = density.fontScale > FIGURES_INSIDE_UP_TO || wraps
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            HalfGauge(fraction = burn.consumedFraction, color = healthColor(plan.health), width = gaugeWidth) {
                Box(Modifier.padding(horizontal = GaugeInset), contentAlignment = Alignment.BottomCenter) {
                    if (below) share() else figures()
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            if (below) figures() else share()
            Spacer(Modifier.height(Spacing.sm))
            SpendingSplit(plan)
        }
    }
}

/** How far the gauge's figures keep from its sides, so a long amount stays clear of the stroke. */
private val GaugeInset = 40.dp

/**
 * "Budgeted ₱X • Unbudgeted ₱Y", centred: on one line when it fits, otherwise the two halves on two lines without the
 * dot, so no line ever starts or ends on it.
 */
@Composable
private fun SpendingSplit(plan: BudgetPlan) {
    val fmt = amountFormat()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.bodyMedium
    val strong = SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    fun half(label: String, money: Money) = buildAnnotatedString {
        append("$label ")
        withStyle(strong) { append(fmt.format(money)) }
    }
    Layout(
        content = {
            Text(half("Budgeted", plan.budgetedSpending), style = style, color = muted, textAlign = TextAlign.Center)
            Text("•", style = style, color = muted)
            Text(half("Unbudgeted", plan.unbudgetedSpending), style = style, color = muted, textAlign = TextAlign.Center)
        },
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = "Budgeted ${fmt.spoken(plan.budgetedSpending)}, unbudgeted ${fmt.spoken(plan.unbudgetedSpending)}"
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val (first, dot, second) = measurables.map { it.measure(loose) }
        val gap = Spacing.sm.roundToPx()
        val inLine = first.width + gap + dot.width + gap + second.width
        // Measured without a width limit (intrinsics), it is as wide as its one line.
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else inLine
        if (inLine <= width) {
            val height = maxOf(first.height, dot.height, second.height)
            layout(width, height) {
                var x = (width - inLine) / 2
                first.placeRelative(x, (height - first.height) / 2)
                x += first.width + gap
                dot.placeRelative(x, (height - dot.height) / 2)
                x += dot.width + gap
                second.placeRelative(x, (height - second.height) / 2)
            }
        } else {
            layout(width, first.height + second.height) {
                first.placeRelative((width - first.width) / 2, 0)
                second.placeRelative((width - second.width) / 2, first.height)
            }
        }
    }
}

/**
 * A half ring filled to [fraction] (clamped to 0..1) in [color], [content] centred along its base. The drawing is
 * decorative: the share used is said in words next to it.
 */
@Composable
private fun HalfGauge(fraction: Float, color: Color, width: Dp, content: @Composable () -> Unit) {
    val track = LucentoTheme.extendedColors.track
    val shown = animatedFraction(fraction.coerceIn(0f, 1f), durationMillis = 600)
    val stroke = 18.dp
    Box(Modifier.width(width).height(width / 2 + stroke / 2), contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.matchParentSize()) {
            val s = stroke.toPx()
            val arc = Size(size.width - s, size.width - s)
            val topLeft = Offset(s / 2f, s / 2f)
            drawArc(track, 180f, 180f, false, topLeft, arc, style = Stroke(s, cap = StrokeCap.Round))
            if (shown > 0f) drawArc(color, 180f, 180f * shown, false, topLeft, arc, style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}

// ---------------------------------------------------------------------------------------------------- B and C

/**
 * Sections B and C with the sheets they open. Budget remaining and the verdict open how Safe to spend is worked out;
 * Spendable wallets opens the choice of wallets, or the explanation on a Partner's phone, where the Shared Space's
 * Owner chooses. [mainCurrency] is the currency the sheets value other wallets into (the figures' own currency);
 * [onEditCard] opens a card's editor from the wallets sheet (its counted credit); [showPerDay] adds Safe per day under
 * the verdict (the Budget tab).
 */
@Composable
internal fun SpendingCapacityBlock(
    capacity: SpendingCapacity,
    safe: SafeToSpend,
    wallets: List<Wallet>,
    mainCurrency: String,
    onSetSpendable: (Long, Boolean) -> Unit,
    onEditCard: ((Wallet) -> Unit)?,
    onReviewDue: (() -> Unit)?,
    showPerDay: Boolean = false,
) {
    var explaining by remember { mutableStateOf(false) }
    var choosingWallets by remember { mutableStateOf(false) }
    val canChoose = com.lucento.app.feature.sharedspace.LocalSharedAccess.current.canManageStructure
    SpendingCapacityPanel(
        capacity = capacity,
        onBudgetRemaining = { explaining = true },
        onWallets = { if (canChoose) choosingWallets = true else explaining = true },
        onVerdict = { explaining = true },
        walletsClickLabel = if (canChoose) "Choose spending wallets" else "Show how spendable wallets is worked out",
        showPerDay = showPerDay,
    )
    if (explaining) {
        SafeToSpendSheet(
            safe = safe,
            onChooseWallets = if (canChoose) {
                {
                    explaining = false
                    choosingWallets = true
                }
            } else {
                null
            },
            onDismiss = { explaining = false },
            onReviewDue = onReviewDue?.let { review ->
                {
                    explaining = false
                    review()
                }
            },
        )
    }
    if (choosingWallets) {
        SpendingWalletsSheet(
            wallets = wallets,
            mainCurrency = mainCurrency,
            money = safe.moneyOnHand,
            onToggle = onSetSpendable,
            onDismiss = { choosingWallets = false },
            onEditCard = onEditCard?.let { edit ->
                { card ->
                    choosingWallets = false
                    edit(card)
                }
            },
        )
    }
}

/** Sections B and C: the two limits side by side (stacked when narrow or at large text), then the verdict. */
@Composable
internal fun SpendingCapacityPanel(
    capacity: SpendingCapacity,
    onBudgetRemaining: () -> Unit,
    onWallets: () -> Unit,
    onVerdict: () -> Unit,
    walletsClickLabel: String = "Choose spending wallets",
    showPerDay: Boolean = false,
) {
    val density = LocalDensity.current
    val fontScale = density.fontScale
    val divider = LucentoTheme.extendedColors.borderDefault
    val measurer = rememberTextMeasurer()
    val budgetFigure = shownAmount(capacity.budgetRemaining, signed = true)
    val walletsFigure = capacity.wallets?.takeIf { capacity.walletsCounted }?.allows?.let { shownAmount(it, signed = true) }.orEmpty()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val isStacked = stacked(maxWidth, fontScale)
            // One size for both figures, set by the wider of the two, so side by side they never differ (design review:
            // each shrank on its own). The room is a column's width less its padding.
            val room = if (isStacked) maxWidth - Spacing.xs * 2 else maxWidth / 2 - Spacing.sm - Spacing.xs * 2
            val roomPx = with(density) { room.toPx() }
            val figureStyle = remember(budgetFigure, walletsFigure, roomPx, density) {
                val base = AmountStyles.large
                val widest = maxOf(
                    measurer.measure(budgetFigure, base).size.width,
                    if (walletsFigure.isEmpty()) 0 else measurer.measure(walletsFigure, base).size.width,
                ).toFloat()
                if (widest <= roomPx) base else (roomPx / widest).let { base.copy(fontSize = base.fontSize * it, lineHeight = base.lineHeight * it) }
            }
            if (isStacked) {
                // At large text sizes a part's amount goes under its name even at full width: beside it, the amount
                // squeezed the name until words broke (360dp at 200 %).
                val compact = fontScale >= 1.3f
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    BudgetRemainingColumn(capacity, onBudgetRemaining, compact, figureStyle, Modifier.fillMaxWidth())
                    WalletsColumn(capacity, onWallets, walletsClickLabel, compact, figureStyle, Modifier.fillMaxWidth())
                }
            } else {
                // One hairline down the middle, as tall as the taller column.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            val x = size.width / 2f
                            drawLine(divider, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                        },
                ) {
                    BudgetRemainingColumn(capacity, onBudgetRemaining, compact = true, figureStyle, Modifier.weight(1f).padding(end = Spacing.sm))
                    WalletsColumn(capacity, onWallets, walletsClickLabel, compact = true, figureStyle, Modifier.weight(1f).padding(start = Spacing.sm))
                }
            }
        }
        Verdict(capacity, onVerdict, showPerDay)
    }
}

@Composable
private fun BudgetRemainingColumn(capacity: SpendingCapacity, onClick: () -> Unit, compact: Boolean, figureStyle: TextStyle, modifier: Modifier) {
    LimitColumn(
        title = "Budget remaining",
        amount = capacity.budgetRemaining,
        caption = "After upcoming expenses",
        // Below zero it keeps its name: the minus sign says it's over (design review: "Over budget so far −₱…" said it twice).
        lines = listOf(
            "Remaining before upcoming expenses" to capacity.remainingBeforeUpcoming,
            "Upcoming expenses" to -capacity.upcomingExpenses,
        ),
        clickLabel = "Show how budget remaining is worked out",
        onClick = onClick,
        compact = compact,
        figureStyle = figureStyle,
        modifier = modifier,
    )
}

/**
 * Spendable wallets. Until money is recorded in them they don't take part, so the column shows "—" rather than a
 * figure the verdict doesn't use (QA, 2026-11-18). When bills are among what's due from them, the line says so: they
 * are the same bills Budget remaining counts, taken once because Safe to spend is the lower figure.
 */
@Composable
private fun WalletsColumn(
    capacity: SpendingCapacity,
    onClick: () -> Unit,
    clickLabel: String,
    compact: Boolean,
    figureStyle: TextStyle,
    modifier: Modifier,
) {
    val counted = capacity.wallets?.takeIf { capacity.walletsCounted }
    LimitColumn(
        title = "Spendable wallets",
        amount = counted?.allows,
        caption = when {
            capacity.wallets == null -> "No wallet counts as spending money"
            counted == null -> "Not counted yet: no money recorded in them"
            else -> "Available across selected wallets"
        },
        lines = if (counted == null) {
            emptyList()
        } else {
            val withBills = capacity.walletBills?.isZero == false
            listOf(
                "In your spending wallets" to counted.total,
                (if (withBills) "Due from them, bills included" else "Due from them this month") to -counted.deducted,
            )
        },
        clickLabel = clickLabel,
        onClick = onClick,
        compact = compact,
        figureStyle = figureStyle,
        modifier = modifier,
    )
}

/**
 * One limit: its name with a chevron (it opens more), the figure (signed; red below zero, the minus sign says it too),
 * what it is in a line, and its two parts, so the figure can be checked at a glance. The figures stay in neutral ink:
 * green is kept for the verdict. [figureStyle] is shared by both limits, so side by side they are the same size.
 * [compact]: each part's amount goes under its name. The whole column is one button, read as one sentence.
 */
@Composable
private fun LimitColumn(
    title: String,
    amount: Money?,
    caption: String,
    lines: List<Pair<String, Money>>,
    clickLabel: String,
    onClick: () -> Unit,
    compact: Boolean,
    figureStyle: TextStyle,
    modifier: Modifier,
) {
    val ext = LucentoTheme.extendedColors
    val fmt = amountFormat()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val spoken = buildString {
        append(title)
        if (amount != null) append(", ").append(fmt.spokenSigned(amount, showPlus = false))
        append(". ").append(caption).append('.')
        lines.forEach { (label, money) -> append(' ').append(label).append(' ').append(fmt.spokenSigned(money, showPlus = false)).append('.') }
    }
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClickLabel = clickLabel, onClick = onClick)
            .clearAndSetSemantics {
                text = AnnotatedString(title)
                contentDescription = spoken
                role = Role.Button
                onClick(label = clickLabel) {
                    onClick()
                    true
                }
            }
            .padding(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        CardOverline(
            title,
            trailing = {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = muted, modifier = Modifier.size(20.dp))
            },
        )
        if (amount != null) {
            MoneyText(
                money = amount,
                style = figureStyle,
                color = if (amount.isNegative) ext.negative else MaterialTheme.colorScheme.onSurface,
                fractionScale = 1f,
                minScale = 0.55f,
            )
        } else {
            Text("—", style = figureStyle, color = muted)
        }
        Text(caption, style = MaterialTheme.typography.bodySmall.copy(lineBreak = LineBreak.Heading), color = muted)
        if (lines.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                lines.forEach { (label, money) -> BreakdownLine(label, money, compact) }
            }
        }
    }
}

/** A part of a limit: its name and its signed amount (a deduction in red, with its minus sign); stacked when [compact]. */
@Composable
private fun BreakdownLine(label: String, money: Money, compact: Boolean) {
    val ink = if (money.isNegative) LucentoTheme.extendedColors.negative else MaterialTheme.colorScheme.onSurface
    val labelStyle = MaterialTheme.typography.bodySmall.copy(lineBreak = LineBreak.Heading)
    val amountStyle = AmountStyles.small.copy(fontWeight = FontWeight.SemiBold)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    if (compact) {
        Column(Modifier.fillMaxWidth()) {
            Text(label, style = labelStyle, color = muted)
            AmountText(money, style = amountStyle, color = ink)
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = labelStyle, color = muted, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(Spacing.sm))
            AmountText(money, style = amountStyle, color = ink)
        }
    }
}

/**
 * Section C, the card's conclusion: Safe to spend, the lower of the two limits, with the sentence that says which one set
 * it (or, at zero, what took it). Green while there is something to spend, red at zero; the icon and the words say the
 * same, so colour is never the only cue. The whole panel opens how it is worked out.
 */
@Composable
private fun Verdict(capacity: SpendingCapacity, onClick: () -> Unit, showPerDay: Boolean) {
    val ext = LucentoTheme.extendedColors
    val fmt = amountFormat()
    val nothing = capacity.nothingSafe
    val ink = if (nothing) ext.negative else ext.positive
    val container = if (nothing) ext.negativeContainer else ext.positiveContainer
    val fontScale = LocalDensity.current.fontScale
    val perDay = capacity.perDay?.takeIf { showPerDay && !nothing }
    val days = "${capacity.daysLeft} ${if (capacity.daysLeft == 1) "day" else "days"}"
    val spoken = buildString {
        append("Safe to spend, ").append(fmt.spoken(capacity.shown)).append('.')
        if (perDay != null) append(' ').append(fmt.spoken(perDay)).append(" a day for ").append(days).append('.')
        append(' ').append(capacity.headline).append(' ').append(capacity.detail)
    }
    val figure: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(ink.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (nothing) Icons.Outlined.ErrorOutline else Icons.Filled.Check, contentDescription = null, tint = ink, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text("SAFE TO SPEND", style = OverlineStyle, color = ink)
                MoneyText(money = capacity.shown, style = AmountStyles.headline, color = ink, fractionScale = 1f, minScale = 0.5f)
                if (perDay != null) {
                    Text("${fmt.format(perDay)} a day for $days", style = MaterialTheme.typography.bodySmall, color = ink)
                }
            }
            // It opens how the figure is worked out (design review: the old line's (i) was the only visible cue).
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = ink, modifier = Modifier.size(24.dp))
        }
    }
    val words: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(
                capacity.headline,
                style = MaterialTheme.typography.titleSmall.copy(lineBreak = LineBreak.Heading),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                capacity.detail,
                style = MaterialTheme.typography.bodySmall.copy(lineBreak = LineBreak.Heading),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(container)
            .clickable(role = Role.Button, onClickLabel = SHOW_HOW_LABEL, onClick = onClick)
            .clearAndSetSemantics {
                text = AnnotatedString("Safe to spend")
                contentDescription = spoken
                role = Role.Button
                onClick(label = SHOW_HOW_LABEL) {
                    onClick()
                    true
                }
            }
            .padding(Spacing.md),
    ) {
        if (stacked(maxWidth, fontScale) || maxWidth < VerdictSideBySideFrom) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                figure()
                words(Modifier.fillMaxWidth())
            }
        } else {
            val line = ink.copy(alpha = 0.25f)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .drawBehind {
                        val x = size.width * 0.5f
                        drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f).padding(end = Spacing.md)) { figure() }
                words(Modifier.weight(1f).padding(start = Spacing.md))
            }
        }
    }
}
