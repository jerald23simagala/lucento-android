package com.lucento.app.feature.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.lucento.app.AppContainer
import com.lucento.app.LucentoApplication
import com.lucento.app.R
import com.lucento.app.core.designsystem.theme.ThemeSpec
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.data.preferences.UserPreferences
import com.lucento.app.feature.home.HomeStateBuilder
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/**
 * The Safe to spend widget (docs/WIDGETS.md): Home's Monthly budget card in brief — the same figure, words and colours
 * (built by [HomeStateBuilder], read by [SafeToSpendWidgetModel]) — with Add. Hide amounts and App lock apply here as
 * in the app; it is a home-screen widget only, never on the lock screen. Tapping it opens Home.
 */
class SafeToSpendWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as LucentoApplication).container
        val (first, updates) = WidgetRefresher.frames(context) {
            val prefs = container.preferences.preferences.first()
            WidgetColors.of(ThemeSpec.of(prefs.themeId), prefs.themeMode) to load(container, prefs)
        }
        provideContent {
            val frame by updates.collectAsState(first)
            val (colors, model) = frame.content
            SafeToSpendContent(model, colors, frame.fontScale)
        }
    }

    private suspend fun load(container: AppContainer, prefs: UserPreferences): SafeToSpendWidgetModel {
        if (!prefs.onboardingComplete) return SafeToSpendWidgetModel.NotSetUp
        // Locked: nothing about the month is even read.
        if (prefs.appLock) return SafeToSpendWidgetModel.Locked(MoneyFormatter.masked(prefs.primaryCurrency))
        val home = HomeStateBuilder.build(
            all = container.transactions.observeTransactions().first(),
            wallets = container.wallets.observeWallets().first(),
            categories = container.categories.observeCategories().first(),
            primaryCurrency = prefs.primaryCurrency,
            today = LocalDate.now(),
            budgets = container.budgets.observeBudgets().first(),
            schedules = container.recurring.observeSchedules().first(),
            features = prefs.features,
        )
        return SafeToSpendWidgetModel.from(home, onboarded = true, appLock = false, hideAmounts = prefs.hideAmounts)
    }
}

class SafeToSpendWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SafeToSpendWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetMidnightWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetMidnightWorker.cancel(context)
    }
}

@Composable
private fun SafeToSpendContent(model: SafeToSpendWidgetModel, colors: WidgetColors, scale: Float) {
    val context = LocalContext.current
    val size = LocalSize.current
    val description = when (model) {
        is SafeToSpendWidgetModel.Budget -> model.description
        is SafeToSpendWidgetModel.NetFlow -> model.description
        else -> null
    }
    Column(
        GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(24.dp).background(colors.surface)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .clickable(actionStartActivity(WidgetLaunch.Home.intent(context)))
            .then(if (description != null) GlanceModifier.semantics { contentDescription = description } else GlanceModifier),
    ) {
        val area = WidgetArea(size.width.value, size.height.value, scale)
        when (model) {
            SafeToSpendWidgetModel.NotSetUp -> NotSetUp(colors, area)
            is SafeToSpendWidgetModel.Locked -> Locked(model, colors, area)
            is SafeToSpendWidgetModel.ChooseCategories -> ChooseCategories(model, colors, area)
            is SafeToSpendWidgetModel.NoBudget -> NoBudget(model, colors, area)
            is SafeToSpendWidgetModel.NetFlow -> NetFlow(model, colors, area)
            is SafeToSpendWidgetModel.Budget -> Budget(model, colors, area)
        }
    }
}

/** The widget's size and the phone's text size: what every layout is planned for ([FigureLayout], [MessageLayout]). */
private class WidgetArea(val width: Float, val height: Float, val scale: Float)

/** The compact layout's label: the crest and the words, wrapping rather than cut. */
@Composable
private fun CompactLabel(label: String, colors: WidgetColors, lines: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        WidgetCrest(20.dp)
        Spacer(GlanceModifier.width(8.dp))
        WidgetText(label, colors.onSurfaceVariant, 12f, FontWeight.Medium, maxLines = lines)
    }
}

/** The figure at the size its layout planned. One line, always: an amount is never wrapped. */
@Composable
private fun Figure(text: String, tone: WidgetTone, colors: WidgetColors, sizeSp: Float, modifier: GlanceModifier = GlanceModifier) {
    Text(text, modifier = modifier, style = TextStyle(color = colors.text(tone), fontSize = sizeSp.sp, fontWeight = FontWeight.Bold), maxLines = 1)
}

/** The full layout's figure row: the figure, then "+ Add". */
@Composable
private fun FigureRow(text: String, tone: WidgetTone, colors: WidgetColors, sizeSp: Float) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Figure(text, tone, colors, sizeSp, GlanceModifier.defaultWeight())
        Spacer(GlanceModifier.width(12.dp))
        WidgetAddButton(colors)
    }
}

/** The compact layout: the label, the figure across the width, then the status beside a round Add. */
@Composable
private fun ColumnScope.CompactFigure(
    label: String,
    figure: String,
    figureTone: WidgetTone,
    status: String,
    statusTone: WidgetTone,
    colors: WidgetColors,
    layout: FigureLayout,
) {
    if (layout.showLabel) CompactLabel(label, colors, layout.labelLines)
    Figure(figure, figureTone, colors, layout.figureSp)
    Spacer(GlanceModifier.defaultWeight())
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (layout.statusLines > 0) {
            WidgetText(status, colors.text(statusTone), 12f, FontWeight.Medium, maxLines = layout.statusLines, modifier = GlanceModifier.defaultWeight())
        } else {
            Spacer(GlanceModifier.defaultWeight())
        }
        Spacer(GlanceModifier.width(8.dp))
        WidgetAddButton(colors, iconOnly = true)
    }
}

@Composable
private fun ColumnScope.Budget(model: SafeToSpendWidgetModel.Budget, colors: WidgetColors, area: WidgetArea) {
    // The bills line is offered first, so it stays longest: it is what makes the figure and the bar add up.
    val bills = model.bills?.let { WidgetLine(it, 13f, maxLines = 1) }
    val perDay = model.perDay?.let { WidgetLine(it, 13f, maxLines = 1) }
    // Home's ring says the same: "72% spent".
    val caption = "${model.usedPercent}% spent"
    // The compact layout's one status: Home's reason when nothing is left, otherwise the verdict.
    val status = model.reason ?: model.verdict
    val layout = FigureLayout.choose(
        area.width, area.height, area.scale,
        label = model.label, month = model.monthLabel, figure = model.figure,
        essential = model.reason?.let { WidgetLine(it, 13f) },
        extras = listOfNotNull(bills, perDay),
        footer = WidgetFooter(caption, model.verdict),
        status = WidgetLine(status, 12f),
    )
    if (layout.compact) {
        val statusTone = if (model.reason != null) WidgetTone.NEUTRAL else model.verdictTone
        CompactFigure(model.label, model.figure, model.figureTone, status, statusTone, colors, layout)
        return
    }
    WidgetHeader(model.label, colors, trailing = model.monthLabel.takeIf { layout.showMonth })
    Spacer(GlanceModifier.height(6.dp))
    FigureRow(model.figure, model.figureTone, colors, layout.figureSp)
    model.reason?.let { WidgetText(it, colors.onSurface, 13f, FontWeight.Medium, maxLines = layout.essentialLines) }
    val billsLines = if (bills != null) layout.extra(0) else 0
    val perDayLines = if (perDay != null) layout.extra(if (bills != null) 1 else 0) else 0
    if (perDay != null && perDayLines > 0) WidgetText(perDay.text, colors.onSurfaceVariant, 13f, maxLines = perDayLines)
    if (bills != null && billsLines > 0) WidgetText(bills.text, colors.onSurfaceVariant, 13f, maxLines = billsLines)
    Spacer(GlanceModifier.defaultWeight())
    WidgetBar(model.usedFraction, colors.indicator(model.barTone), colors.track, area.width - WIDGET_PAD_H_DP)
    Spacer(GlanceModifier.height(6.dp))
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        WidgetText(caption, colors.onSurfaceVariant, 12f, maxLines = layout.captionLines, modifier = GlanceModifier.defaultWeight())
        Spacer(GlanceModifier.width(8.dp))
        WidgetChip(model.verdict, model.verdictTone, colors)
    }
}

@Composable
private fun ColumnScope.NetFlow(model: SafeToSpendWidgetModel.NetFlow, colors: WidgetColors, area: WidgetArea) {
    val layout = FigureLayout.choose(
        area.width, area.height, area.scale,
        label = "Net flow", month = model.monthLabel, figure = model.figure,
        essential = WidgetLine(model.detail, 13f),
        extras = listOfNotNull(model.note?.let { WidgetLine(it, 12f) }),
        status = WidgetLine(model.detail, 12f),
    )
    if (layout.compact) {
        CompactFigure("Net flow", model.figure, model.tone, model.detail, WidgetTone.NEUTRAL, colors, layout)
        return
    }
    WidgetHeader("Net flow", colors, trailing = model.monthLabel.takeIf { layout.showMonth })
    Spacer(GlanceModifier.height(6.dp))
    FigureRow(model.figure, model.tone, colors, layout.figureSp)
    WidgetText(model.detail, colors.onSurfaceVariant, 13f, maxLines = layout.essentialLines)
    Spacer(GlanceModifier.defaultWeight())
    val noteLines = layout.extra(0)
    if (model.note != null && noteLines > 0) WidgetText(model.note, colors.onSurfaceVariant, 12f, maxLines = noteLines)
}

private const val LOCKED_STATUS = "Hidden while App lock is on"
private const val LOCKED_HINT = "Open Lucento and unlock to see your month"

@Composable
private fun ColumnScope.Locked(model: SafeToSpendWidgetModel.Locked, colors: WidgetColors, area: WidgetArea) {
    val layout = FigureLayout.choose(
        area.width, area.height, area.scale,
        label = "Safe to spend", month = null, figure = model.masked,
        essential = WidgetLine(LOCKED_STATUS, 13f),
        extras = listOf(WidgetLine(LOCKED_HINT, 12f)),
        status = WidgetLine(LOCKED_STATUS, 12f),
    )
    if (layout.compact) {
        CompactFigure("Safe to spend", model.masked, WidgetTone.NEUTRAL, LOCKED_STATUS, WidgetTone.NEUTRAL, colors, layout)
        return
    }
    WidgetHeader("Safe to spend", colors, trailingIcon = R.drawable.ic_widget_lock, trailingIconDescription = "App lock is on")
    Spacer(GlanceModifier.height(6.dp))
    FigureRow(model.masked, WidgetTone.NEUTRAL, colors, layout.figureSp)
    WidgetText(LOCKED_STATUS, colors.onSurfaceVariant, 13f, maxLines = layout.essentialLines)
    Spacer(GlanceModifier.defaultWeight())
    val hintLines = layout.extra(0)
    if (hintLines > 0) WidgetText(LOCKED_HINT, colors.onSurfaceVariant, 12f, maxLines = hintLines)
}

/** A message state's last row: its action (wrapping when its words don't fit), with Add beside it where it has one. */
@Composable
private fun ActionRow(
    text: String,
    launch: WidgetLaunch,
    filled: Boolean,
    withAdd: Boolean,
    colors: WidgetColors,
    layout: MessageLayout,
    scale: Float,
) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.End) {
        WidgetActionButton(
            text, launch, colors, filled,
            lines = layout.actionLines,
            heightDp = MessageLayout.actionRowDp(layout.actionLines, scale),
            modifier = if (layout.actionLines > 1) GlanceModifier.defaultWeight() else GlanceModifier,
        )
        if (withAdd) {
            Spacer(GlanceModifier.width(8.dp))
            WidgetAddButton(colors, iconOnly = !layout.addWithWord)
        }
    }
}

@Composable
private fun ColumnScope.NoBudget(model: SafeToSpendWidgetModel.NoBudget, colors: WidgetColors, area: WidgetArea) {
    val message = "No budget for ${model.monthLabel} yet"
    val detail = "Give your categories an amount to see your pace."
    val layout = MessageLayout.choose(
        area.width, area.height, area.scale,
        labelDp = headerDp(area.scale) + 8f, label = "Safe to spend", month = model.monthLabel,
        message = WidgetLine(message, 15f), detail = WidgetLine(detail, 13f),
        action = "Set amounts", withAdd = true,
    )
    if (layout.showLabel) {
        WidgetHeader("Safe to spend", colors, trailing = model.monthLabel.takeIf { layout.showMonth })
        Spacer(GlanceModifier.height(8.dp))
    }
    if (layout.messageLines > 0) WidgetText(message, colors.onSurface, 15f, FontWeight.Medium, maxLines = layout.messageLines)
    if (layout.detailLines > 0) WidgetText(detail, colors.onSurfaceVariant, 13f, maxLines = layout.detailLines)
    Spacer(GlanceModifier.defaultWeight())
    ActionRow("Set amounts", WidgetLaunch.Budget, filled = false, withAdd = true, colors, layout, area.scale)
}

@Composable
private fun ColumnScope.ChooseCategories(model: SafeToSpendWidgetModel.ChooseCategories, colors: WidgetColors, area: WidgetArea) {
    val message = "Choose the categories you spend on — your budget is built from them."
    val layout = MessageLayout.choose(
        area.width, area.height, area.scale,
        labelDp = headerDp(area.scale) + 8f, label = "Safe to spend", month = model.monthLabel,
        message = WidgetLine(message, 13f, maxLines = 4), detail = null,
        action = "Choose categories", withAdd = false,
    )
    if (layout.showLabel) {
        WidgetHeader("Safe to spend", colors, trailing = model.monthLabel.takeIf { layout.showMonth })
        Spacer(GlanceModifier.height(8.dp))
    }
    if (layout.messageLines > 0) WidgetText(message, colors.onSurface, 13f, maxLines = layout.messageLines)
    Spacer(GlanceModifier.defaultWeight())
    ActionRow("Choose categories", WidgetLaunch.Categories, filled = false, withAdd = false, colors, layout, area.scale)
}

@Composable
private fun ColumnScope.NotSetUp(colors: WidgetColors, area: WidgetArea) {
    val message = "Finish setting up Lucento"
    val detail = "This widget fills in once your wallets are ready."
    val layout = MessageLayout.choose(
        area.width, area.height, area.scale,
        labelDp = 28f + 10f, label = "", month = null,
        message = WidgetLine(message, 15f), detail = WidgetLine(detail, 13f),
        action = "Open Lucento", withAdd = false,
    )
    if (layout.showLabel) {
        WidgetCrest(28.dp)
        Spacer(GlanceModifier.height(10.dp))
    }
    if (layout.messageLines > 0) WidgetText(message, colors.onSurface, 15f, FontWeight.Medium, maxLines = layout.messageLines)
    if (layout.detailLines > 0) WidgetText(detail, colors.onSurfaceVariant, 13f, maxLines = layout.detailLines)
    Spacer(GlanceModifier.defaultWeight())
    ActionRow("Open Lucento", WidgetLaunch.Home, filled = true, withAdd = false, colors, layout, area.scale)
}
