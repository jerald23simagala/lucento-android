package com.lucento.app.feature.progress

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.automirrored.outlined.CallMade
import androidx.compose.material.icons.automirrored.outlined.CallReceived
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.AmountFormat
import com.lucento.app.core.designsystem.component.CardOverline
import com.lucento.app.core.designsystem.component.Confirmation
import com.lucento.app.core.designsystem.component.ConfirmationHost
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.HeroCard
import com.lucento.app.core.designsystem.component.HeroStatTile
import com.lucento.app.core.designsystem.component.IconTile
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.MoneyText
import com.lucento.app.core.designsystem.component.NavRow
import com.lucento.app.core.designsystem.component.ProfileButton
import com.lucento.app.core.designsystem.component.StatusTones
import com.lucento.app.core.designsystem.component.amountFormat
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.Hierarchy
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.designsystem.theme.TonePair
import com.lucento.app.domain.Achievement
import com.lucento.app.domain.BudgetHealth
import com.lucento.app.domain.BudgetMilestones
import com.lucento.app.domain.Category
import com.lucento.app.domain.Feature
import com.lucento.app.domain.MonthBudgetResult
import com.lucento.app.domain.MonthCheckIn
import com.lucento.app.domain.MonthRecap
import com.lucento.app.domain.MonthRecaps
import com.lucento.app.domain.RecapMonthResult
import com.lucento.app.domain.YearRecap
import com.lucento.app.feature.budget.MonthSwitcher
import com.lucento.app.feature.budget.YearSwitcher
import com.lucento.app.feature.budget.healthTone
import com.lucento.app.feature.home.monthLabel
import java.time.YearMonth
import kotlinx.coroutines.launch
import java.time.format.TextStyle
import java.util.Locale

/**
 * Milestones (owner, 2026-11-23; "Progress" until then): the awards one tap away, how a month went, and how each month of
 * the year did against its budget — all from the person's own records. A month's recap is free; the budget year is Lucento
 * Pro, like Insights' 12 months, with the same preview of the person's own months on Free. Figures come from
 * [MonthRecaps], which reuses the calculators Home, Budget, Wallets and Goals use, so they always agree. The awards have
 * their own page ([com.lucento.app.feature.awards.AwardsScreen]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProgressScreen(
    onBack: () -> Unit,
    /** The month to open on; null: last month in the first days of a new one, otherwise this month so far. */
    initialMonth: YearMonth? = null,
    /** The awards page, from the button at the top (owner, 2026-11-23). */
    onOpenAwards: () -> Unit = {},
    /** Insights, when that module is on. */
    onOpenInsights: (() -> Unit)? = null,
    viewModel: ProgressViewModel = viewModel(factory = ProgressViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val today = state.today
    val current = YearMonth.from(today)
    var chosen by rememberSaveable { mutableStateOf(initialMonth?.toString()) }
    val month = chosen?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        ?.coerceIn(state.earliestMonth, current)
        ?: MonthCheckIn.recapMonth(today)?.takeIf { it >= state.earliestMonth } ?: current
    // The budget year shown: this calendar year, earlier ones with its switcher (owner, 2026-11-23).
    var shownYear by rememberSaveable { mutableIntStateOf(month.year) }
    val earliestYear = state.earliestMonth.year
    // The recap stepped into another year: the squares follow, so its month's square is the one marked.
    LaunchedEffect(month.year) { if (month.year in earliestYear..current.year) shownYear = month.year }
    val inputs = state.inputs
    val recap = remember(inputs, state.currency, month) { inputs?.let { MonthRecaps.month(it, state.currency, month) } }
    val year = remember(inputs, state.currency, shownYear, today) { inputs?.let { MonthRecaps.calendarYear(it, state.currency, shownYear) } }

    var celebration by remember { mutableStateOf<Confirmation?>(null) }
    var celebrated by remember { mutableStateOf(false) }

    // The check-in's recap, looked at: Home stops offering it.
    LaunchedEffect(state.loading, month) {
        if (!state.loading && month == MonthCheckIn.recapMonth(today)) viewModel.markRecapSeen(month)
    }
    // Last month kept within budget is celebrated once, here or on the Budget tab, whichever comes first (the same flag).
    LaunchedEffect(state.loading, month, state.acknowledgedBudgetMonth) {
        val ledger = state.ledger
        if (state.loading || celebrated || ledger == null || inputs == null || month != current.minusMonths(1)) return@LaunchedEffect
        val kept = BudgetMilestones.monthToAcknowledge(
            categories = inputs.categories,
            budgets = inputs.budgets,
            ledger = ledger,
            currencyCode = state.primaryCurrency,
            today = today,
            lastAcknowledged = state.acknowledgedBudgetMonth,
        ) ?: return@LaunchedEffect
        celebrated = true
        viewModel.acknowledgeBudgetMonth(kept.month)
        celebration = Confirmation("You stayed within ${monthLabel(kept.month)}’s budget", kind = Confirmation.Kind.Milestone)
    }

    // A month picked in the year changes the recap above it: the recap comes into view (critic r3, P2).
    val recapRequester = remember { BringIntoViewRequester() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
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
            if (state.loading || recap == null || year == null) return@Column

            // The awards have their own page, one tap from the top (owner, 2026-11-23).
            Spacer(Modifier.height(Spacing.md))
            LucentoCard(Modifier.fillMaxWidth(), contentPadding = Spacing.xs) {
                NavRow(
                    icon = Icons.Outlined.EmojiEvents,
                    title = "Awards",
                    summary = "${state.earned.size} of ${Achievement.entries.size} earned",
                    onClick = onOpenAwards,
                    tone = LucentoTheme.extendedColors.shortcuts.awards,
                    wrapTitle = true,
                    onClickLabel = "Open your awards",
                )
            }

            Spacer(Modifier.height(Spacing.section))
            Box(Modifier.bringIntoViewRequester(recapRequester)) {
                MonthSwitcher(
                    month = month,
                    canGoForward = month < current,
                    canGoBack = month > state.earliestMonth,
                    onPrevious = { if (month > state.earliestMonth) chosen = month.minusMonths(1).toString() },
                    onNext = { if (month < current) chosen = month.plusMonths(1).toString() },
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            RecapHero(recap)
            Spacer(Modifier.height(Spacing.md))
            RecapDetails(recap, state.categoriesById)
            if (onOpenInsights != null) {
                LucentoTextButton(
                    text = "See trends in Insights",
                    onClick = onOpenInsights,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }

            Spacer(Modifier.height(Spacing.section))
            // The calendar year as squares, one colour for how each month's budget went (owner, 2026-11-23).
            SectionTitle("Your budget year", "How each month did against its budget.")
            Spacer(Modifier.height(Spacing.sm))
            if (state.yearLocked) {
                YearPreview(year)
            } else {
                YearSwitcher(
                    year = shownYear,
                    canGoBack = shownYear > earliestYear,
                    canGoForward = shownYear < current.year,
                    onPrevious = { if (shownYear > earliestYear) shownYear -= 1 },
                    onNext = { if (shownYear < current.year) shownYear += 1 },
                )
                Spacer(Modifier.height(Spacing.sm))
                if (state.features.isEnabled(Feature.BUDGETS)) {
                    BudgetSquares(year.months, selected = month, onSelect = {
                        chosen = it.toString()
                        scope.launch { recapRequester.bringIntoView() }
                    })
                    Spacer(Modifier.height(Spacing.sm))
                    SquaresLegend()
                } else {
                    InfoNote("Each month’s budget result shows here when Budgets is on (profile menu › Enabled features).")
                }
                Spacer(Modifier.height(Spacing.md))
                YearTotals(year)
            }
            Spacer(Modifier.height(Spacing.fabClearance))
        }
        ConfirmationHost(confirmation = celebration, onDismissed = { celebration = null })
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.semantics { heading() },
        )
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The month as one statement on brand blue: what was kept (signed: less is minus), the sentence that says what it means,
 * then money in and money out. A month still running says "so far".
 */
@Composable
private fun RecapHero(recap: MonthRecap) {
    val fmt = amountFormat()
    val ext = LucentoTheme.extendedColors
    HeroCard {
        Spacer(Modifier.height(Spacing.xs))
        // A polite live region: a month picked in the year (or with the arrows) is announced as it appears.
        CardOverline(
            "${monthLabel(recap.month)} ${recap.month.year}" + if (recap.finished) "" else " · so far",
            color = ext.onHeroMuted,
            modifier = Modifier.semantics {
                liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
                contentDescription = "Showing ${monthLabel(recap.month)} ${recap.month.year}" + if (recap.finished) "" else ", so far"
            },
        )
        Spacer(Modifier.height(6.dp))
        if (!recap.hasRecords) {
            Text(
                "Nothing recorded in ${monthLabel(recap.month)}" + if (recap.finished) "." else " yet.",
                style = MaterialTheme.typography.titleMedium,
                color = ext.onHero,
            )
            Text(
                "Add what came in and went out, and this is where its story appears.",
                style = MaterialTheme.typography.bodySmall,
                color = ext.onHeroMuted,
            )
            Spacer(Modifier.height(Spacing.xs))
            return@HeroCard
        }
        MoneyText(money = recap.net, style = AmountStyles.headline, color = ext.onHero, showPlus = true, fractionScale = 1f, minScale = 0.5f)
        Text(RecapCopy.headline(recap), style = MaterialTheme.typography.bodyMedium, color = ext.onHeroMuted)
        Spacer(Modifier.height(14.dp))
        val inTile: @Composable (Modifier) -> Unit = { m ->
            HeroStatTile("Money in", Icons.AutoMirrored.Outlined.CallReceived, description = "Money in ${fmt.spoken(recap.income)}", modifier = m) {
                MoneyText(money = recap.income, style = HeroFigure, color = ext.onHero, fractionScale = 1f)
            }
        }
        val outTile: @Composable (Modifier) -> Unit = { m ->
            HeroStatTile("Money out", Icons.AutoMirrored.Outlined.CallMade, description = "Money out ${fmt.spoken(recap.spent)}", modifier = m) {
                MoneyText(money = recap.spent, style = HeroFigure, color = ext.onHero, fractionScale = 1f)
            }
        }
        if (LocalDensity.current.fontScale >= 1.3f) {
            // Large text: each tile takes the full width, so its label and figure are never cut off.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                inTile(Modifier.fillMaxWidth())
                outTile(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                inTile(Modifier.weight(1f).fillMaxHeight())
                outTile(Modifier.weight(1f).fillMaxHeight())
            }
        }
        Spacer(Modifier.height(Spacing.xs))
    }
}

private val HeroFigure = AmountStyles.medium.copy(fontWeight = FontWeight.Bold)

/** The month in a few lines: only those with something to say, each an icon, a statement and what it rests on. */
@Composable
private fun RecapDetails(recap: MonthRecap, categories: Map<Long, Category>) {
    val fmt = amountFormat()
    val lines = remember(recap, categories, fmt) { RecapCopy.lines(recap, { id -> id?.let { categories[it]?.name } }, fmt) }
    if (lines.isEmpty()) return
    LucentoCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
        lines.forEachIndexed { index, line ->
            RecapRow(line)
            if (index < lines.lastIndex) Hairline(inset = Spacing.md + 34.dp + Spacing.sm + Spacing.xxs)
        }
    }
}

@Composable
private fun RecapRow(line: RecapLine) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = Spacing.md, vertical = Spacing.sm + Spacing.xxs),
        verticalAlignment = if (line.details.isEmpty()) Alignment.CenterVertically else Alignment.Top,
    ) {
        IconTile(
            icon = line.kind.icon(),
            size = 34.dp,
            container = MaterialTheme.colorScheme.surfaceContainerHigh,
            content = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(Spacing.sm + Spacing.xxs))
        Column(Modifier.weight(1f)) {
            Text(line.title, style = Hierarchy.cardTitle.copy(lineBreak = androidx.compose.ui.text.style.LineBreak.Heading))
            line.details.forEach { detail ->
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall.copy(lineBreak = androidx.compose.ui.text.style.LineBreak.Heading),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun RecapLine.Kind.icon(): ImageVector = when (this) {
    RecapLine.Kind.BUDGET -> Icons.Outlined.PieChart
    RecapLine.Kind.TOP -> Icons.Outlined.ShoppingBag
    RecapLine.Kind.MORE -> Icons.AutoMirrored.Outlined.TrendingUp
    RecapLine.Kind.LESS -> Icons.AutoMirrored.Outlined.TrendingDown
    RecapLine.Kind.NET_WORTH -> Icons.Outlined.AccountBalance
    RecapLine.Kind.GOALS -> Icons.Outlined.TrackChanges
    RecapLine.Kind.DEBTS -> Icons.Outlined.CreditCard
    RecapLine.Kind.DAYS -> Icons.Outlined.CalendarMonth
}

/**
 * The calendar year as 12 squares, one flat colour each for how its budget went (owner, 2026-11-23: "Flat colour
 * squares"), in the Budget tab's own levels: green on track, amber getting close (80 %+), red over, grey recorded without
 * a budget. A month with nothing recorded is an outline with a dash, one still to come an empty outline, and today's
 * month says "so far". Each square names its month and the share of its budget used, so colour is never the only cue; a
 * tap shows that month's recap above. Four across (six on wide windows, three with large text; two at the largest, only
 * as tall as their words need), and a row grows taller rather than cut its words off. [hidden]: the plan preview, every
 * square without its figure or colour.
 */
@Composable
private fun BudgetSquares(
    months: List<RecapMonthResult>,
    selected: YearMonth?,
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
                            selected = !hidden && result.month == selected,
                            // A month without records has no recap to show, so only months with one open it.
                            onClick = onSelect?.takeIf { result.hasRecords && !hidden }?.let { pick -> { pick(result.month) } },
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
private fun MonthSquare(result: RecapMonthResult, selected: Boolean, onClick: (() -> Unit)?, hidden: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val tone = squareTone(result, hidden)
    val ink = tone?.content ?: colors.onSurfaceVariant
    val name = result.month.month.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.getDefault())
    val spoken = RecapCopy.squareDescription(result, hidden)
    val budget = result.budget?.takeUnless { hidden }
    val edge = when {
        selected -> colors.onSurface
        tone == null -> LucentoTheme.extendedColors.borderDefault
        else -> tone.content.copy(alpha = 0.25f)
    }
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(tone?.container ?: androidx.compose.ui.graphics.Color.Transparent)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, edge), MaterialTheme.shapes.medium)
            .let { m ->
                if (onClick != null) m.clickable(role = Role.Button, onClickLabel = "See this month’s recap", onClick = onClick) else m
            }
            .clearAndSetSemantics {
                contentDescription = spoken
                this.selected = selected
            }
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

/** The year's totals in words: how many budgeted months stayed within, what was kept, how net worth moved. */
@Composable
private fun YearTotals(year: YearRecap) {
    val fmt = amountFormat()
    val lines = remember(year, fmt) { RecapCopy.yearLines(year, fmt) }
    LucentoCard(Modifier.fillMaxWidth(), contentPadding = Spacing.lg) {
        lines.forEachIndexed { index, line ->
            Text(line, style = if (index == 0) Hierarchy.cardTitle else MaterialTheme.typography.bodyMedium)
            if (index < lines.lastIndex) Spacer(Modifier.height(Spacing.xs))
        }
    }
}

/**
 * The budget year on a plan without it: the person's own months as grey squares, every figure and colour hidden, under
 * the plan fact and the way to the plan page, as Insights previews its 12 months (owner, 2026-11-19). Never made-up
 * numbers, and no colour gives a month's result away.
 */
@Composable
private fun YearPreview(year: YearRecap) {
    val openPro = com.lucento.app.feature.pro.LocalOpenPro.current
    LucentoCard(Modifier.fillMaxWidth(), contentPadding = Spacing.lg) {
        if (year.recordedMonths > 0) {
            val described = "Your months of ${year.months.first().month.year}, with their results hidden."
            Box(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = described }) {
                Box(Modifier.alpha(0.6f)) { BudgetSquares(year.months, selected = null, onSelect = null, hidden = true) }
            }
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
                "Each month’s recap above is always free.",
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
     * The calendar year's totals, the first being its headline: how many budgeted months stayed within, then what was
     * kept, what came in and went out, and how net worth moved. A year still running says "so far"; a year whose records
     * began after January is told from that month, so a few months are never called a year (design review 2026-11-23).
     */
    fun yearLines(year: YearRecap, fmt: AmountFormat): List<String> = buildList {
        val label = year.months.firstOrNull()?.month?.year ?: year.endMonth.year
        val running = year.months.any { !it.finished }
        val soFar = if (running) " so far" else ""
        if (year.recordedMonths == 0) {
            add("Nothing recorded in $label" + if (running) " yet." else ".")
            return@buildList
        }
        if (year.budgetedMonths > 0) add("Within budget in ${year.withinBudgetMonths} of ${year.budgetedMonths} budgeted months$soFar")
        val first = year.firstRecordedMonth
        val span = if (first != null && first.monthValue > 1) "since ${monthLabel(first)}" + if (running) "" else " $label"
        else "in $label$soFar"
        val kept = if (year.net.isNegative) "${fmt.format(-year.net)} more went out than came in $span"
        else "${fmt.format(year.net)} kept $span"
        add(if (isEmpty()) kept else "$kept.")
        add("${fmt.format(year.income)} came in and ${fmt.format(year.spent)} went out.")
        add("More came in than went out in ${year.keptMonths} of ${year.recordedMonths} months with records.")
        year.netWorthChange?.let { add("Net worth ${fmt.signed(it)} in $label$soFar.") }
    }
}
