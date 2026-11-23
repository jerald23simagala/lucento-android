package com.lucento.app.feature.progress

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.AmountFormat
import com.lucento.app.core.designsystem.component.CardOverline
import com.lucento.app.core.designsystem.component.IconTile
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.NavRow
import com.lucento.app.core.designsystem.component.ProfileButton
import com.lucento.app.core.designsystem.component.StatusTones
import com.lucento.app.core.designsystem.component.amountFormat
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.designsystem.theme.TonePair
import com.lucento.app.domain.Achievement
import com.lucento.app.domain.BudgetHealth
import com.lucento.app.domain.Feature
import com.lucento.app.domain.MonthBudgetResult
import com.lucento.app.domain.MonthRecap
import com.lucento.app.domain.MonthRecaps
import com.lucento.app.domain.RecapMonthResult
import com.lucento.app.domain.YearRecap
import com.lucento.app.feature.budget.YearSwitcher
import com.lucento.app.feature.budget.healthTone
import com.lucento.app.feature.home.monthLabel
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Milestones (owner, 2026-11-23; "Progress" until then): the awards one tap away, then the year on top — how each month
 * did against its budget, as squares, and the year's totals. A month's own recap opens from its square
 * ([MonthRecapScreen]; also from Home's check-in card) and is free; the budget year is Lucento Pro, like Insights'
 * 12 months, with the person's own months as a preview on Free (whose squares still open each month's free recap).
 * Figures come from [MonthRecaps], which reuses the calculators Home, Budget, Wallets and Goals use, so they agree.
 */
@Composable
fun ProgressScreen(
    onBack: () -> Unit,
    /** A month's recap, from its square. */
    onOpenMonth: (YearMonth) -> Unit = {},
    /** The awards page, from the card at the top (owner, 2026-11-23). */
    onOpenAwards: () -> Unit = {},
    viewModel: ProgressViewModel = viewModel(factory = ProgressViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val today = state.today
    val current = YearMonth.from(today)
    // The budget year shown: this calendar year, earlier ones with its switcher (owner, 2026-11-23).
    var shownYear by rememberSaveable { mutableIntStateOf(current.year) }
    val earliestYear = state.earliestMonth.year
    val inputs = state.inputs
    val year = remember(inputs, state.currency, shownYear, today) { inputs?.let { MonthRecaps.calendarYear(it, state.currency, shownYear) } }
    val budgetsOn = state.features.isEnabled(Feature.BUDGETS)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = com.lucento.app.core.designsystem.component.screenGutter()),
    ) {
        LucentoTopBar(title = "Milestones", onBack = onBack, actions = { ProfileButton() })
        Text(
            "How your months are going, from your own records.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.loading || year == null) return@Column

        // The awards have their own page, one tap from the top, with a few of the medals as a cue (owner, 2026-11-23).
        Spacer(Modifier.height(Spacing.md))
        AwardsCard(earned = state.earned, onClick = onOpenAwards)

        Spacer(Modifier.height(Spacing.section))
        // The title on the left and the year on the right, on one line while they fit (owner, 2026-11-23).
        YearHeader(
            switcher = if (state.yearLocked) null else {
                {
                    YearSwitcher(
                        year = shownYear,
                        canGoBack = shownYear > earliestYear,
                        canGoForward = shownYear < current.year,
                        onPrevious = { if (shownYear > earliestYear) shownYear -= 1 },
                        onNext = { if (shownYear < current.year) shownYear += 1 },
                    )
                }
            },
        )
        Spacer(Modifier.height(Spacing.sm))
        if (state.yearLocked) {
            YearPreview(year, onOpenMonth)
        } else {
            // With Budgets off the months are still there to open, without colours (there is no budget to show).
            BudgetSquares(year.months, current = current, onSelect = onOpenMonth, hidden = !budgetsOn)
            Spacer(Modifier.height(Spacing.sm))
            if (budgetsOn) {
                SquaresLegend()
            } else {
                InfoNote("Each month’s budget result shows here when Budgets is on (profile menu › Enabled features).")
            }
            Spacer(Modifier.height(Spacing.md))
            YearTotals(year)
        }
        Spacer(Modifier.height(Spacing.fabClearance))
    }
}

/**
 * The budget year's header: its title, then [switcher] on the right of the same line while both fit, below it when
 * they don't (a narrow phone or large text), and what the squares are under them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YearHeader(switcher: (@Composable () -> Unit)?) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            "Your budget year",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(end = Spacing.sm)
                .semantics { heading() },
        )
        if (switcher != null) Box(Modifier.align(Alignment.CenterVertically)) { switcher() }
    }
    Text(
        "How each month did against its budget. Tap a month for its recap.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The way to the awards: how many are earned, with a few of the medals lined up on the right as a cue. */
@Composable
private fun AwardsCard(earned: Set<Achievement>, onClick: () -> Unit) {
    LucentoCard(Modifier.fillMaxWidth(), contentPadding = Spacing.xs) {
        NavRow(
            icon = Icons.Outlined.EmojiEvents,
            title = "Awards",
            summary = "${earned.size} of ${Achievement.entries.size} earned",
            onClick = onClick,
            tone = LucentoTheme.extendedColors.shortcuts.awards,
            trailing = { MedalLineup(earned) },
            wrapTitle = true,
            onClickLabel = "Open your awards",
        )
    }
}

/**
 * Three small medals on the Awards card (owner, 2026-11-23: "a subtle medal lined up on right sides"): the latest
 * earned in gold, then outlines of those still to come, each a little behind the one before. Decorative: the card's
 * summary says how many are earned.
 */
@Composable
private fun MedalLineup(earned: Set<Achievement>) {
    val gold = Achievement.entries.filter { it in earned }.takeLast(MEDALS_SHOWN)
    val shown = (gold + Achievement.entries.filter { it !in earned }).take(MEDALS_SHOWN)
    val size = 24.dp
    val step = 17.dp
    Box(Modifier.width(size + step * (shown.size - 1)).height(size * com.lucento.app.feature.awards.MedalAspect)) {
        shown.forEachIndexed { index, award ->
            com.lucento.app.feature.awards.AwardMedal(
                award,
                earned = award in earned,
                size = size,
                modifier = Modifier
                    .offset(x = step * index)
                    // The first medal in front: the gold ones lead.
                    .zIndex((shown.size - index).toFloat()),
            )
        }
    }
}

private const val MEDALS_SHOWN = 3

/**
 * The calendar year as 12 squares, one flat colour each for how its budget went (owner, 2026-11-23: "Flat colour
 * squares"), in the Budget tab's own levels: green on track, amber getting close (80 %+), red over, grey recorded without
 * a budget. A month with nothing recorded is an outline with a dash, one still to come an empty outline, and today's
 * month says "so far" (and is outlined: today's month). Each square names its month and the share of its budget used, so
 * colour is never the only cue; a tap opens that month's recap. Four across (six on wide windows, three with large text;
 * two at the largest, only as tall as their words need), and a row grows taller rather than cut its words off. [hidden]:
 * the plan preview, or Budgets off, every square without its figure or colour (months with records still open).
 */
@Composable
private fun BudgetSquares(
    months: List<RecapMonthResult>,
    /** Today's month, outlined. */
    current: YearMonth?,
    onSelect: ((YearMonth) -> Unit)?,
    hidden: Boolean = false,
) {
    val textScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        fun side(perRow: Int) = (maxWidth - Spacing.sm * (perRow - 1)) / perRow
        // The most squares across that still hold the running month's three lines (month, share, "so far") at this
        // text size: four on a phone, six on a wide window, three or two with large text.
        val needed = (SQUARE_TEXT_LINES_SP * textScale + SQUARE_PADDING_DP).dp
        val perRow = listOf(6, 4, 3).filter { it != 6 || maxWidth >= 520.dp }.firstOrNull { side(it) >= needed } ?: 2
        // Two across (the largest text): only as tall as the words need, so the year isn't three screens long.
        val square = if (perRow == 2) minOf(side(perRow), needed) else side(perRow)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            months.chunked(perRow).forEach { row ->
                // Square when the words fit; taller (a whole row together) rather than ever cutting them off.
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    row.forEach { result ->
                        MonthSquare(
                            result,
                            isCurrent = !hidden && result.month == current,
                            // A month without records has no recap to show, so only months with one open it.
                            onClick = onSelect?.takeIf { result.hasRecords }?.let { pick -> { pick(result.month) } },
                            hidden = hidden,
                            modifier = Modifier.weight(1f).heightIn(min = square).fillMaxHeight(),
                        )
                    }
                    repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** A square's three lines of text (labelMedium 16 + titleMedium 24 + labelSmall 16 line heights), in sp. */
private const val SQUARE_TEXT_LINES_SP = 56f

/** A square's inner padding, top and bottom, with a little to spare. */
private const val SQUARE_PADDING_DP = 18f

/**
 * A month's square colours ([BudgetSquares]); null for a month with nothing to show, which is only an outline: one still
 * to come, or one with nothing recorded (it has a dash), so grey always means a month recorded without a budget.
 */
@Composable
private fun squareTone(result: RecapMonthResult, hidden: Boolean): TonePair? = when {
    result.upcoming || !result.hasRecords -> null
    hidden -> StatusTones.neutral
    else -> result.budget?.let { healthTone(it.health) } ?: StatusTones.neutral
}

@Composable
private fun MonthSquare(result: RecapMonthResult, isCurrent: Boolean, onClick: (() -> Unit)?, hidden: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val tone = squareTone(result, hidden)
    val ink = tone?.content ?: colors.onSurfaceVariant
    val name = result.month.month.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.getDefault())
    val spoken = RecapCopy.squareDescription(result, hidden)
    val budget = result.budget?.takeUnless { hidden }
    val edge = when {
        isCurrent -> colors.onSurface
        tone == null -> LucentoTheme.extendedColors.borderDefault
        else -> tone.content.copy(alpha = 0.25f)
    }
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(tone?.container ?: androidx.compose.ui.graphics.Color.Transparent)
            .border(BorderStroke(if (isCurrent) 2.dp else 1.dp, edge), MaterialTheme.shapes.medium)
            .let { m ->
                if (onClick != null) m.clickable(role = Role.Button, onClickLabel = "See this month’s recap", onClick = onClick) else m
            }
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(Spacing.sm),
    ) {
        Text(name, style = MaterialTheme.typography.labelMedium, color = ink, maxLines = 1)
        Spacer(Modifier.weight(1f))
        when {
            budget != null -> {
                Text(
                    "${budget.percentUsed}%",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = ink,
                    maxLines = 1,
                )
                if (!result.finished) Text("so far", style = MaterialTheme.typography.labelSmall, color = ink, maxLines = 1)
            }
            result.upcoming || hidden -> Unit
            else -> Text("—", style = MaterialTheme.typography.titleMedium, color = ink)
        }
    }
}

/** What the squares' colours mean, in the Budget tab's own words. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SquaresLegend() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        LegendKey(StatusTones.positive, "On track")
        LegendKey(StatusTones.warning, "Getting close")
        LegendKey(StatusTones.negative, "Over budget")
        LegendKey(StatusTones.neutral, "No budget")
        LegendKey(null, "Nothing recorded")
    }
}

/** A legend swatch, drawn like a small square ([MonthSquare]'s corners in proportion); null tone: an outline. */
@Composable
private fun LegendKey(tone: TonePair?, label: String) {
    val swatch = androidx.compose.foundation.shape.RoundedCornerShape(percent = 20)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(14.dp)
                .clip(swatch)
                .background(tone?.container ?: androidx.compose.ui.graphics.Color.Transparent)
                .border(BorderStroke(1.dp, tone?.content?.copy(alpha = 0.4f) ?: LucentoTheme.extendedColors.borderDefault), swatch),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The year's totals as a bulleted outline (owner, 2026-11-23: "emphasizing the bulleted outlines"): the year and "so far"
 * as its overline, then one line per fact, each with its own mark and its figures in bold — the budgeted months within
 * budget, what was kept, what came in and went out, the months that kept something, and how net worth moved.
 */
@Composable
private fun YearTotals(year: YearRecap) {
    val fmt = amountFormat()
    val lines = remember(year, fmt) { RecapCopy.yearLines(year, fmt) }
    val label = year.months.firstOrNull()?.month?.year ?: year.endMonth.year
    LucentoCard(Modifier.fillMaxWidth(), contentPadding = Spacing.lg) {
        CardOverline(if (year.months.any { !it.finished }) "$label · so far" else "$label")
        Spacer(Modifier.height(Spacing.sm))
        lines.forEachIndexed { index, line ->
            YearBullet(line)
            if (index < lines.lastIndex) Spacer(Modifier.height(Spacing.sm + Spacing.xxs))
        }
    }
}

@Composable
private fun YearBullet(line: YearLine) {
    val strong = MaterialTheme.colorScheme.onSurface
    val text = remember(line, strong) {
        buildAnnotatedString {
            append(line.text)
            line.figures.forEach { figure ->
                val start = line.text.indexOf(figure)
                if (start >= 0) addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = strong), start, start + figure.length)
            }
        }
    }
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top) {
        IconTile(
            icon = line.kind.icon(),
            size = 30.dp,
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.width(Spacing.sm + Spacing.xxs))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(lineBreak = androidx.compose.ui.text.style.LineBreak.Heading),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(top = Spacing.xxs),
        )
    }
}

private fun YearLine.Kind.icon(): ImageVector = when (this) {
    YearLine.Kind.BUDGET -> Icons.Outlined.PieChart
    YearLine.Kind.KEPT -> Icons.Outlined.Savings
    YearLine.Kind.FLOW -> Icons.Outlined.SwapVert
    YearLine.Kind.MONTHS -> Icons.Outlined.CalendarMonth
    YearLine.Kind.NET_WORTH -> Icons.Outlined.AccountBalance
    YearLine.Kind.NOTHING -> Icons.Outlined.EditCalendar
}

/**
 * The budget year on a plan without it: the person's own months as grey squares, every figure and colour hidden, under
 * the plan fact and the way to the plan page, as Insights previews its 12 months (owner, 2026-11-19). Never made-up
 * numbers, and no colour gives a month's result away; a month's square still opens its recap, which is free.
 */
@Composable
private fun YearPreview(year: YearRecap, onOpenMonth: (YearMonth) -> Unit) {
    val openPro = com.lucento.app.feature.pro.LocalOpenPro.current
    LucentoCard(Modifier.fillMaxWidth(), contentPadding = Spacing.lg) {
        if (year.recordedMonths > 0) {
            BudgetSquares(year.months, current = null, onSelect = onOpenMonth, hidden = true)
            Spacer(Modifier.height(Spacing.md))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WorkspacePremium, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text("Your budget year is part of Lucento Pro", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "See how each month did against its budget, in colour, and the year’s totals. " +
                "Each month’s recap is free: tap a month.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.md))
        LucentoPrimaryButton(
            text = com.lucento.app.feature.pro.ProCopy.WALL_SEE_PLAN,
            onClick = { openPro(com.lucento.app.domain.entitlement.ProFeature.INSIGHTS_LONG_PERIODS) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** One line of the month's recap: what it is about, its statement, and the facts under it. */
internal data class RecapLine(val kind: Kind, val title: String, val details: List<String> = emptyList()) {
    enum class Kind { BUDGET, TOP, MORE, LESS, NET_WORTH, GOALS, DEBTS, DAYS }
}

/** One line of the year's totals: what it is about, its sentence, and the [figures] in it shown in bold. */
internal data class YearLine(val kind: Kind, val text: String, val figures: List<String> = emptyList()) {
    enum class Kind { BUDGET, KEPT, FLOW, MONTHS, NET_WORTH, NOTHING }
}

/** The recap's words, from its figures only (pure apart from month names), so every case is pinned in tests. */
internal object RecapCopy {
    /** What the hero's signed figure means. */
    fun headline(recap: MonthRecap): String = when {
        recap.income.isZero -> "No income recorded" + if (recap.finished) " in ${monthLabel(recap.month)}." else " yet this month."
        recap.net.isNegative -> "More went out than came in" + if (recap.finished) "." else ", so far."
        recap.net.isZero -> "What came in and what went out matched."
        else -> (if (recap.finished) "Kept: more came in than went out." else "Kept so far: more came in than went out.")
    }

    fun lines(recap: MonthRecap, name: (Long?) -> String?, fmt: AmountFormat): List<RecapLine> = buildList {
        val so = if (recap.finished) "" else " so far"
        recap.budget?.let { b ->
            val title = if (b.kept) "Within budget$so" else "${fmt.format(-b.remaining)} over budget$so"
            val details = buildList {
                add("${fmt.format(b.spent)} of ${fmt.format(b.total)} spent")
                add("${b.within} of ${b.budgeted} ${if (b.budgeted == 1) "category" else "categories"} within budget")
                // Why this month can't be a Steady Month, said where the verdict is (critic r2, P1-3).
                b.since?.let { add("Your budget began on ${monthLabel(YearMonth.from(it))} ${it.dayOfMonth}") }
                if (b.over.isNotEmpty()) {
                    add("Over: " + b.over.take(MonthRecaps.TOP).joinToString(", ") { "${name(it.categoryId) ?: "Uncategorized"} ${fmt.format(it.amount)}" })
                }
            }
            add(RecapLine(RecapLine.Kind.BUDGET, title, details))
        }
        if (recap.top.isNotEmpty()) {
            add(
                RecapLine(
                    RecapLine.Kind.TOP,
                    "Where it went",
                    recap.top.map { c -> "${name(c.categoryId) ?: "Uncategorized"} ${fmt.format(c.amount)}" + (c.percent?.let { " · $it%" } ?: "") },
                ),
            )
        }
        recap.change?.let { c ->
            val what = name(c.categoryId) ?: "Uncategorized"
            val before = monthLabel(recap.month.minusMonths(1))
            val more = !c.change.isNegative
            val through = recap.changeThrough
            add(
                RecapLine(
                    if (more) RecapLine.Kind.MORE else RecapLine.Kind.LESS,
                    if (through == null) "$what: ${fmt.format(c.change.abs())} ${if (more) "more" else "less"} than $before"
                    else "$what: ${fmt.format(c.change.abs())} ${if (more) "more" else "less"} than at this point in $before",
                    listOf(
                        if (through == null) "${fmt.format(c.current)} this time, ${fmt.format(c.previous)} in $before"
                        else "${fmt.format(c.current)} so far, ${fmt.format(c.previous)} by $before ${through.dayOfMonth}",
                    ),
                ),
            )
        }
        recap.netWorth?.let { worth ->
            add(
                RecapLine(
                    RecapLine.Kind.NET_WORTH,
                    "Net worth ${fmt.format(worth)}" + if (recap.finished) " at the month’s end" else " now",
                    listOfNotNull(recap.netWorthChange?.let { "${fmt.signed(it)} since the end of ${monthLabel(recap.month.minusMonths(1))}" }),
                ),
            )
        }
        recap.setAside?.takeIf { !it.isZero || recap.goalsReached > 0 }?.let { saved ->
            val title = when {
                saved.isNegative -> "${fmt.format(-saved)} taken back from goals"
                saved.isZero -> "Nothing new set aside for goals"
                else -> "${fmt.format(saved)} set aside for goals"
            }
            val reached = when (recap.goalsReached) {
                0 -> null
                1 -> "1 goal reached its target"
                else -> "${recap.goalsReached} goals reached their target"
            }
            add(RecapLine(RecapLine.Kind.GOALS, title, listOfNotNull(reached)))
        }
        recap.paidToDebts?.takeIf { !it.isZero }?.let { paid ->
            add(RecapLine(RecapLine.Kind.DEBTS, "${fmt.format(paid)} paid to cards and loans", listOf("Payments move your own money, so they lower what you owe.")))
        }
        if (recap.days > 0) {
            add(RecapLine(RecapLine.Kind.DAYS, "Recorded on ${recap.daysRecorded} of ${recap.days} days"))
        }
    }

    /** A month square's one description for screen readers: the month, the share of its budget used and its level. */
    fun squareDescription(result: RecapMonthResult, hidden: Boolean = false): String {
        val name = "${monthLabel(result.month)} ${result.month.year}"
        if (result.upcoming) return "$name: still to come"
        if (hidden) return name
        val budget = result.budget
        val soFar = if (result.finished) "" else ", so far"
        return when {
            budget != null -> "$name$soFar: ${budget.percentUsed}% of the budget used, ${levelWords(budget)}"
            !result.hasRecords -> "$name: nothing recorded"
            else -> "$name$soFar: no budget"
        }
    }

    /** A budget's level in the Budget tab's words; spent to the cent is used up, not over. */
    fun levelWords(budget: MonthBudgetResult): String = when (budget.health) {
        BudgetHealth.NOT_SET -> "no budget"
        BudgetHealth.ON_TRACK -> "on track"
        BudgetHealth.APPROACHING, BudgetHealth.LOW -> "getting close"
        BudgetHealth.EXCEEDED -> if (budget.remaining.isZero) "fully used" else "over budget"
    }

    /**
     * The calendar year's totals as bullet lines, the budget first: how many budgeted months stayed within, what was kept,
     * what came in and went out, the months that kept something, and how net worth moved, each with its figures (shown
     * in bold). A year still running says "so far"; a year whose records began after January is told from that month, so
     * a few months are never called a year (design review 2026-11-23).
     */
    fun yearLines(year: YearRecap, fmt: AmountFormat): List<YearLine> = buildList {
        val label = year.months.firstOrNull()?.month?.year ?: year.endMonth.year
        val running = year.months.any { !it.finished }
        val soFar = if (running) " so far" else ""
        if (year.recordedMonths == 0) {
            add(YearLine(YearLine.Kind.NOTHING, "Nothing recorded in $label" + if (running) " yet" else ""))
            return@buildList
        }
        if (year.budgetedMonths > 0) {
            val within = "${year.withinBudgetMonths} of ${year.budgetedMonths}"
            add(YearLine(YearLine.Kind.BUDGET, "Within budget in $within budgeted months$soFar", listOf(within)))
        }
        val first = year.firstRecordedMonth
        val span = if (first != null && first.monthValue > 1) "since ${monthLabel(first)}" + if (running) "" else " $label"
        else "in $label$soFar"
        val net = fmt.format(if (year.net.isNegative) -year.net else year.net)
        val kept = if (year.net.isNegative) "$net more went out than came in $span" else "$net kept $span"
        add(YearLine(YearLine.Kind.KEPT, kept, listOf(net)))
        val income = fmt.format(year.income)
        val spent = fmt.format(year.spent)
        add(YearLine(YearLine.Kind.FLOW, "$income came in and $spent went out", listOf(income, spent)))
        val keptMonths = "${year.keptMonths} of ${year.recordedMonths}"
        add(YearLine(YearLine.Kind.MONTHS, "More came in than went out in $keptMonths months with records", listOf(keptMonths)))
        year.netWorthChange?.let {
            val change = fmt.signed(it)
            add(YearLine(YearLine.Kind.NET_WORTH, "Net worth $change in $label$soFar", listOf(change)))
        }
    }
}
