package com.lucento.app.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.sp
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
import java.util.Locale

/*
 * The Monthly budget card, redesigned (owner, 2026-11-18): A, how much of the budget is used (a half gauge); B, the two
 * spending limits side by side, Budget remaining and Spendable wallets; C, Safe to spend, the lower of them, as the
 * card's conclusion. B and C are shared with the Budget tab ([SpendingCapacityBlock]) so the two pages never disagree;
 * every figure comes from [SpendingCapacity], read from SafeToSpend.
 *
 * Owner, 2026-11-18: keep this order and show both limits' results with fewer words, in lines that balance; then, B as
 * a subtle grid: each limit's name and figure centred (the figure smaller), and under a rule its two parts as a small
 * ledger, the description first and the amounts aligned on the right ("Unspent ₱30,920.10").
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

/** The verdict puts its figure and its words side by side, half and half, this wide (tablets, unfolded phones). */
private val VerdictSideBySideFrom = 360.dp

/**
 * Phones too (owner, 2026-11-23, after a tester saw the sentence drop under the amount and leave the right side
 * empty): from this width (a 360dp phone and up) the sentence sits beside the amount ([VerdictCompactSplit] of the width
 * for the figure) whenever it fits there in [VerdictCompactMaxLines] lines, and the status icon shrinks into the label so
 * the amount keeps most of its size. Large text, a longer sentence and a verdict with a second line stack as before
 * (they would make a tall, narrow column).
 */
private val VerdictCompactSideBySideFrom = 240.dp

/** The figure's share of the width when the verdict is side by side on a phone; the words take the rest. */
private const val VerdictCompactSplit = 0.56f

/** Beside the amount on a phone, the sentence may take this many lines: about the figure's own height. */
private const val VerdictCompactMaxLines = 4

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
            Spacer(Modifier.height(Spacing.md))
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

/** One of a limit's two parts: its description, the amount as shown, and the amount itself (for its sign and speech). */
private data class Part(val label: String, val shown: String, val money: Money)

/** A limit's figure: smaller than the gauge's and the verdict's (owner, 2026-11-18), as the Budget hero's tiles. */
private val LimitFigureStyle = AmountStyles.medium.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)

private val PartAmountStyle = AmountStyles.small.copy(fontWeight = FontWeight.SemiBold)

/**
 * Inside a grid cell: the padding on each side, the room kept for the chevron either side of the centred name, and the
 * least space between a part's description and its amount.
 */
private val CellPadding = Spacing.sm + Spacing.xxs
private val ChevronRoom = 16.dp
private val PartGap = Spacing.xs + Spacing.xxs

/** Below this the parts would be too small to read on one line: each description goes over its amount instead. */
private const val SMALLEST_PART_SCALE = 0.85f

/**
 * Sections B and C. B is a subtle grid of two cells, side by side (stacked when narrow or at large text): in each, the
 * limit's name and figure centred, then under a rule its two parts as a small ledger, the description first and the
 * amount aligned on the right. Both cells share one figure size, one part size and the same number of title lines, so
 * their rules and rows line up (owner, 2026-11-18).
 */
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
    val grid = LucentoTheme.extendedColors.borderDefault
    val shape = MaterialTheme.shapes.medium
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.bodySmall
    val counted = capacity.wallets?.takeIf { capacity.walletsCounted }
    // Budget remaining = unspent budget − upcoming expenses; Spendable wallets = what's in them − what's due from them.
    val budgetParts = listOf(
        Part("Unspent", shownAmount(capacity.remainingBeforeUpcoming, signed = true), capacity.remainingBeforeUpcoming),
        Part("Upcoming", shownAmount(-capacity.upcomingExpenses, signed = true), -capacity.upcomingExpenses),
    )
    val walletParts = if (counted == null) {
        emptyList()
    } else {
        listOf(
            Part("In wallets", shownAmount(counted.total, signed = true), counted.total),
            Part("Due", shownAmount(-counted.deducted, signed = true), -counted.deducted),
        )
    }
    val budgetFigure = shownAmount(capacity.budgetRemaining, signed = true)
    val walletsFigure = counted?.allows?.let { shownAmount(it, signed = true) }.orEmpty()
    val budgetTitle = "Budget remaining"
    val walletsTitle = "Spendable wallets"
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val isStacked = stacked(maxWidth, fontScale)
            val cellWidth = if (isStacked) maxWidth else maxWidth / 2
            val innerPx = with(density) { (cellWidth - CellPadding * 2).toPx() }
            val titleRoomPx = with(density) { (cellWidth - CellPadding * 2 - ChevronRoom * 2).roundToPx() }
            val gapPx = with(density) { PartGap.toPx() }
            // One size for both figures, set by the wider of the two, so side by side they never differ.
            val figureStyle = remember(budgetFigure, walletsFigure, innerPx, density) {
                val widest = maxOf(
                    measurer.measure(budgetFigure, LimitFigureStyle).size.width,
                    if (walletsFigure.isEmpty()) 0 else measurer.measure(walletsFigure, LimitFigureStyle).size.width,
                ).toFloat()
                LimitFigureStyle.scaledToFit(widest, innerPx)
            }
            // One size for all four parts, small enough for the widest description and amount to share a line.
            val partScale = remember(budgetParts, walletParts, innerPx, labelStyle, density) {
                val widest = (budgetParts + walletParts).maxOfOrNull { part ->
                    measurer.measure(part.label, labelStyle).size.width + gapPx + measurer.measure(part.shown, PartAmountStyle).size.width
                } ?: 0f
                if (widest <= innerPx) 1f else innerPx / widest.toFloat()
            }
            val partsOnOneLine = partScale >= SMALLEST_PART_SCALE
            val partLabel = if (partsOnOneLine) labelStyle.scaled(partScale) else labelStyle
            val partAmount = if (partsOnOneLine) PartAmountStyle.scaled(partScale) else PartAmountStyle
            // Both names take as many lines as the longer needs, so the rules under the figures run straight across.
            val titleLines = remember(titleRoomPx, density) {
                listOf(budgetTitle, walletsTitle).maxOf { title ->
                    measurer.measure(title.uppercase(Locale.getDefault()), OverlineStyle, constraints = Constraints(maxWidth = titleRoomPx)).lineCount
                }.coerceIn(1, 3)
            }
            val budgetCell: @Composable (Modifier) -> Unit = { m ->
                LimitCell(
                    title = budgetTitle,
                    titleLines = titleLines,
                    amount = capacity.budgetRemaining,
                    note = null,
                    parts = budgetParts,
                    figureStyle = figureStyle,
                    partLabel = partLabel,
                    partAmount = partAmount,
                    partsOnOneLine = partsOnOneLine,
                    clickLabel = "Show how budget remaining is worked out",
                    onClick = onBudgetRemaining,
                    modifier = m,
                )
            }
            val walletsCell: @Composable (Modifier) -> Unit = { m ->
                LimitCell(
                    title = walletsTitle,
                    titleLines = titleLines,
                    // Until money is recorded in them they don't take part: "—", not a figure the verdict doesn't use.
                    amount = counted?.allows,
                    note = when {
                        capacity.wallets == null -> "No spending wallets"
                        counted == null -> "Not counted yet"
                        else -> null
                    },
                    parts = walletParts,
                    figureStyle = figureStyle,
                    partLabel = partLabel,
                    partAmount = partAmount,
                    partsOnOneLine = partsOnOneLine,
                    clickLabel = walletsClickLabel,
                    onClick = onWallets,
                    modifier = m,
                )
            }
            if (isStacked) {
                Column(Modifier.fillMaxWidth().clip(shape).border(1.dp, grid, shape)) {
                    budgetCell(Modifier.fillMaxWidth())
                    Box(Modifier.fillMaxWidth().height(1.dp).background(grid))
                    walletsCell(Modifier.fillMaxWidth())
                }
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .border(1.dp, grid, shape)
                        .drawBehind {
                            val x = size.width / 2f
                            drawLine(grid, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                        },
                ) {
                    budgetCell(Modifier.weight(1f))
                    walletsCell(Modifier.weight(1f))
                }
            }
        }
        Verdict(capacity, onVerdict, showPerDay)
    }
}

/** [this] at the size that fits [width] pixels into [room], or as it is when it already fits. */
private fun TextStyle.scaledToFit(width: Float, room: Float): TextStyle = if (width <= room) this else scaled(room / width)

private fun TextStyle.scaled(scale: Float): TextStyle =
    if (scale >= 1f) this else copy(fontSize = fontSize * scale, lineHeight = lineHeight * scale)

/**
 * One grid cell: the limit's name (with a chevron in the corner: it opens more) and its figure centred, signed and red
 * below zero, in neutral ink, green being kept for the verdict; under a rule, its two parts as a small ledger, or [note]
 * when it has no figure. The whole cell is one button, read as one sentence.
 */
@Composable
private fun LimitCell(
    title: String,
    titleLines: Int,
    amount: Money?,
    note: String?,
    parts: List<Part>,
    figureStyle: TextStyle,
    partLabel: TextStyle,
    partAmount: TextStyle,
    partsOnOneLine: Boolean,
    clickLabel: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val ext = LucentoTheme.extendedColors
    val fmt = amountFormat()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val spoken = buildString {
        append(title)
        if (amount != null) append(", ").append(fmt.spokenSigned(amount, showPlus = false))
        if (note != null) append(". ").append(note)
        append('.')
        parts.forEach { part -> append(' ').append(part.label).append(' ').append(fmt.spokenSigned(part.money, showPlus = false)).append('.') }
    }
    Column(
        modifier
            .clickable(role = Role.Button, onClickLabel = clickLabel, onClick = onClick)
            .clearAndSetSemantics {
                text = AnnotatedString(title)
                contentDescription = spoken
                role = Role.Button
                onClick(label = clickLabel) {
                    onClick()
                    true
                }
            },
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = CellPadding, vertical = Spacing.md)) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                // Only the name keeps clear of the chevron; the figure has the cell's full width (its size is set for it).
                Text(
                    title.uppercase(Locale.getDefault()),
                    style = OverlineStyle,
                    color = muted,
                    textAlign = TextAlign.Center,
                    minLines = titleLines,
                    maxLines = titleLines,
                    modifier = Modifier.padding(horizontal = ChevronRoom),
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
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = muted,
                modifier = Modifier.align(Alignment.TopEnd).size(ChevronRoom),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(LucentoTheme.extendedColors.borderDefault))
        Column(
            Modifier.fillMaxWidth().padding(horizontal = CellPadding, vertical = Spacing.sm + Spacing.xxs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            if (note != null) {
                Text(note, style = partLabel, color = muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            parts.forEach { part -> PartRow(part, partLabel, partAmount, partsOnOneLine) }
        }
    }
}

/**
 * A part: its description on the left and its amount aligned on the right (a deduction in red, with its minus sign).
 * When the two can't share a line ([oneLine] false for every part, or this row alone being too wide), the description
 * sits over the amount, which stays on the right: a word is never broken to make room (360dp at 200 % broke "Upcoming").
 */
@Composable
private fun PartRow(part: Part, label: TextStyle, amount: TextStyle, oneLine: Boolean) {
    val ink = if (part.money.isNegative) LucentoTheme.extendedColors.negative else MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Layout(
        content = {
            Text(part.label, style = label, color = muted)
            Text(part.shown, style = amount, color = ink, maxLines = 1, softWrap = false)
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val (labelMeasurable, amountMeasurable) = measurables
        val gap = PartGap.roundToPx()
        val amountPlaceable = amountMeasurable.measure(Constraints())
        val labelWidth = labelMeasurable.maxIntrinsicWidth(Constraints.Infinity)
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else labelWidth + gap + amountPlaceable.width
        if (oneLine && labelWidth + gap + amountPlaceable.width <= width) {
            val labelPlaceable = labelMeasurable.measure(Constraints(maxWidth = width - gap - amountPlaceable.width))
            val height = maxOf(labelPlaceable.height, amountPlaceable.height)
            layout(width, height) {
                labelPlaceable.placeRelative(0, (height - labelPlaceable.height) / 2)
                amountPlaceable.placeRelative(width - amountPlaceable.width, (height - amountPlaceable.height) / 2)
            }
        } else {
            val labelPlaceable = labelMeasurable.measure(Constraints(maxWidth = width))
            layout(width, labelPlaceable.height + amountPlaceable.height) {
                labelPlaceable.placeRelative(0, 0)
                amountPlaceable.placeRelative(maxOf(0, width - amountPlaceable.width), labelPlaceable.height)
            }
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
    val density = LocalDensity.current
    val fontScale = density.fontScale
    // Measures the sentence at the width it would have beside the amount on a phone (VerdictCompactMaxLines).
    val measurer = rememberTextMeasurer()
    val headlineStyle = MaterialTheme.typography.titleSmall.copy(lineBreak = LineBreak.Heading, fontWeight = FontWeight.Bold)
    val perDay = capacity.perDay?.takeIf { showPerDay && !nothing }
    val days = "${capacity.daysLeft} ${if (capacity.daysLeft == 1) "day" else "days"}"
    val detail = capacity.detail
    val spoken = buildString {
        append("Safe to spend, ").append(fmt.spoken(capacity.shown)).append('.')
        if (perDay != null) append(' ').append(fmt.spoken(perDay)).append(" a day for ").append(days).append('.')
        append(' ').append(capacity.headline)
        if (detail != null) append(' ').append(detail)
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
    // Beside the words on a phone: the status icon moves into the label, so the amount keeps most of its width.
    val compactFigure: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (nothing) Icons.Outlined.ErrorOutline else Icons.Filled.Check, contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text("SAFE TO SPEND", style = OverlineStyle, color = ink)
                }
                MoneyText(money = capacity.shown, style = AmountStyles.headline, color = ink, fractionScale = 1f, minScale = 0.5f)
                if (perDay != null) {
                    Text("${fmt.format(perDay)} a day for $days", style = MaterialTheme.typography.bodySmall, color = ink)
                }
            }
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
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall.copy(lineBreak = LineBreak.Heading),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
        // Figure and words side by side, [split] of the width for the figure, with a hairline between them.
        val divided: @Composable (Float, Dp, @Composable () -> Unit) -> Unit = { split, gap, left ->
            val line = ink.copy(alpha = 0.25f)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .drawBehind {
                        val x = size.width * split
                        drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(split).padding(end = gap)) { left() }
                words(Modifier.weight(1f - split).padding(start = gap))
            }
        }
        // On a phone: beside the amount only while the sentence fits there in a few lines, at a normal text size.
        val besideOnPhone = fontScale < 1.3f && detail == null && maxWidth >= VerdictCompactSideBySideFrom && run {
            val wordsWidth = with(density) { (maxWidth * (1f - VerdictCompactSplit) - Spacing.sm).roundToPx() }
            measurer.measure(capacity.headline, headlineStyle, constraints = Constraints(maxWidth = wordsWidth.coerceAtLeast(1)))
                .lineCount <= VerdictCompactMaxLines
        }
        when {
            !stacked(maxWidth, fontScale) && maxWidth >= VerdictSideBySideFrom -> divided(0.5f, Spacing.md) { figure() }
            besideOnPhone -> divided(VerdictCompactSplit, Spacing.sm) { compactFigure() }
            else -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                figure()
                words(Modifier.fillMaxWidth())
            }
        }
    }
}
