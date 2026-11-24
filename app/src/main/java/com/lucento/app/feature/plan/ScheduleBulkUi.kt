package com.lucento.app.feature.plan

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.component.AmountsShown
import com.lucento.app.core.designsystem.component.CheckMark
import com.lucento.app.core.designsystem.component.ChoicePill
import com.lucento.app.core.designsystem.component.FieldHeader
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoBottomSheet
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoSecondaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.SheetHeader
import com.lucento.app.core.designsystem.component.amountFormat
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.domain.RecurringSchedule
import com.lucento.app.domain.ScheduleBulk
import com.lucento.app.domain.signedOccurrence
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/*
 * Scheduled › All schedules, Select mode (tester round, 2026-11-18): pick several plans (or Select all) and change their
 * day or amount, pause or resume them, or delete them, each in one step. Pure rules live in ScheduleBulk; the repository
 * writes each change in one database transaction.
 */

/** The bulk changes that need a sheet; Pause, Resume and Delete act straight away (Delete after a confirmation). */
internal enum class BulkSheet { DAY, AMOUNT }

/** Keeps the selected schedule ids across rotation and process death. */
internal val ScheduleIdsSaver = Saver<Set<Long>, LongArray>(save = { it.toLongArray() }, restore = { it.toSet() })

internal fun schedulesCount(count: Int): String = if (count == 1) "1 schedule" else "$count schedules"

/** "Day changed for 3 schedules", or "… for 3 of 4 schedules" when some couldn’t take the change. */
internal fun bulkDone(what: String, changed: Int, total: Int): String =
    if (changed == total) "$what for ${schedulesCount(changed)}" else "$what for $changed of ${schedulesCount(total)}"

internal const val BULK_NOT_PERMITTED = "Only the Shared Space’s owner can change scheduled payments."

internal const val BULK_FAILED = "That couldn’t be saved, so nothing changed. Try again."

/**
 * Above the list in All schedules: the Select button, or in Select mode "Select all" (a check that clears everything
 * once all is selected), how many are selected, and Done.
 */
@Composable
internal fun SelectionBar(
    selecting: Boolean,
    selectedCount: Int,
    shownCount: Int,
    onStart: () -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onDone: () -> Unit,
    error: String?,
) {
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
        if (!selecting) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ChoicePill(
                    label = "Select",
                    selected = false,
                    onClick = {
                        haptics.tick()
                        onStart()
                    },
                    leadingIcon = Icons.Outlined.Checklist,
                    role = Role.Button,
                    modifier = Modifier.semantics { contentDescription = "Select schedules to change several at once" },
                )
            }
            return@Column
        }
        val all = when {
            selectedCount == 0 -> ToggleableState.Off
            selectedCount >= shownCount -> ToggleableState.On
            else -> ToggleableState.Indeterminate
        }
        // At large text the count moves under the two controls instead of being squeezed between them.
        val stacked = LocalDensity.current.fontScale >= 1.3f
        val count: @Composable (Modifier) -> Unit = { modifier ->
            Text(
                if (selectedCount == 0) "None selected" else "$selectedCount selected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = if (stacked) TextAlign.Start else TextAlign.End,
                modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(MaterialTheme.shapes.small)
                    // Some selected: a tap selects the rest; all selected: a tap clears them.
                    .triStateToggleable(state = all, role = Role.Checkbox) {
                        haptics.tick()
                        onSelectAll(all != ToggleableState.On)
                    }
                    .padding(end = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SelectAllMark(all)
                Spacer(Modifier.width(Spacing.sm))
                Text("Select all", style = MaterialTheme.typography.labelLarge)
            }
            if (stacked) Spacer(Modifier.weight(1f)) else count(Modifier.weight(1f).padding(horizontal = Spacing.sm))
            LucentoTextButton(text = "Done", onClick = onDone)
        }
        if (stacked) count(Modifier.padding(start = Spacing.xxs))
        if (error != null) {
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/**
 * Select mode's actions, pinned under the list: Change day, Change amount, Pause (Resume when every plan selected that
 * can change is paused) and Delete. Two rows of two at large text sizes, so no label is cut.
 */
@Composable
internal fun BulkActionBar(
    hasSelection: Boolean,
    canPause: Boolean,
    resume: Boolean,
    onDay: () -> Unit,
    onAmount: () -> Unit,
    onPause: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val actions = listOf(
        BulkActionItem(Icons.Outlined.CalendarMonth, "Change day", hasSelection, onDay),
        BulkActionItem(Icons.Outlined.Payments, "Change amount", hasSelection, onAmount),
        BulkActionItem(if (resume) Icons.Outlined.PlayCircle else Icons.Outlined.PauseCircle, if (resume) "Resume" else "Pause", canPause, onPause),
        BulkActionItem(Icons.Outlined.Delete, "Delete", hasSelection, onDelete, destructive = true),
    )
    // A floating card (with a hairline for the dark theme, where a shadow can’t show) on an opaque band, so rows scrolled
    // behind it never show through the gaps around it (design review, 2026-11-19).
    Box(
        modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(start = Spacing.sm, end = Spacing.sm, top = Spacing.xs, bottom = Spacing.sm),
    ) {
    Surface(
        Modifier.fillMaxWidth(),
        color = colors.surfaceContainerLowest,
        shadowElevation = 6.dp,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, LucentoTheme.extendedColors.borderDefault),
    ) {
        val rows = if (LocalDensity.current.fontScale >= 1.3f) actions.chunked(2) else listOf(actions)
        Column(Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)) {
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { BulkActionButton(it, Modifier.weight(1f)) }
                }
            }
        }
    }
    }
}

private data class BulkActionItem(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
    val destructive: Boolean = false,
)

@Composable
private fun BulkActionButton(item: BulkActionItem, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .heightIn(min = 64.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = item.enabled, role = Role.Button, onClick = item.onClick)
            .alpha(if (item.enabled) 1f else 0.38f)
            .padding(horizontal = Spacing.xxs, vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(item.icon, contentDescription = null, tint = if (item.destructive) colors.error else colors.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            item.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (item.destructive) colors.error else colors.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

private fun dayLabel(date: LocalDate, today: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "MMM d" else "MMM d, yyyy", Locale.getDefault()))

/** No line break inside: a date ("Dec 30") stays whole (design review, 2026-11-19). */
private fun whole(text: String): String = text.replace(' ', NO_BREAK_SPACE)

/** A signed amount that can't break, not even between "−" and "₱" (a word joiner after the sign). */
internal fun wholeAmount(text: String): String {
    val sign = text.firstOrNull()
    return if (sign == '\u2212' || sign == '+' || sign == '-') "$sign$WORD_JOINER${whole(text.substring(1))}" else whole(text)
}

private const val NO_BREAK_SPACE = '\u00a0'
private const val WORD_JOINER = '\u2060'

/** Between two dates: kept on one line with both ("Dec 30 → Dec 31"). */
private const val DATE_ARROW = "\u00a0\u2192\u00a0"

/** Between two amounts: the arrow stays with the first; a long pair may break after it, never inside an amount. */
private const val AMOUNT_ARROW = "\u00a0\u2192 "

/** One line of a sheet’s "What changes" list: the plan and what happens to it, and how TalkBack says it. */
private data class PreviewLine(
    val name: String,
    val change: AnnotatedString,
    val spoken: String,
    val changes: Boolean,
    /** Its new date is before today, so it will show as overdue. */
    val overdue: Boolean = false,
) {
    constructor(name: String, change: String, spoken: String, changes: Boolean) : this(name, AnnotatedString(change), spoken, changes)
}

/** A date for a preview line, marked "(overdue)" in the negative colour when it is before today. */
private fun dated(date: LocalDate, today: LocalDate, negative: androidx.compose.ui.graphics.Color): AnnotatedString = buildAnnotatedString {
    append(whole(dayLabel(date, today)))
    if (date.isBefore(today)) {
        append(" ")
        withStyle(SpanStyle(color = negative)) { append("(overdue)") }
    }
}

private fun spokenDate(date: LocalDate, today: LocalDate): String = dayLabel(date, today) + if (date.isBefore(today)) ", overdue" else ""

/** " · ends Dec 20, 2026" when a move changes the plan’s end date, so a loan’s last payment never moves unseen (QA, 2026-11-19). */
private fun endsText(before: RecurringSchedule, after: RecurringSchedule?): String {
    val end = after?.endDate ?: return ""
    if (end == before.endDate || ScheduleBulk.isOnce(after)) return ""
    return " · ends " + whole(end.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())))
}

/** The Change day sheet’s note, for what is selected: weekly plans only when there are some (overdue is the footer’s). */
private fun dayNote(months: Boolean, weeks: Boolean, weekSpan: String): String {
    val where = when {
        months && weeks -> "in the same month (weekly plans: the same week, $weekSpan)"
        weeks -> "in the same week, $weekSpan"
        else -> "in the same month"
    }
    return "Each plan’s next date moves to this day $where, so nothing is skipped. How often it repeats stays, and what it " +
        "already recorded stays as it was."
}

/**
 * A sheet’s pinned footer: a hairline over what scrolls beneath it, a [summary] when there is one, its message, then
 * its button, always in reach (design review, 2026-11-19).
 */
@Composable
private fun SheetFooter(error: String?, summary: AnnotatedString? = null, button: @Composable () -> Unit) {
    com.lucento.app.core.designsystem.component.Hairline()
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = Spacing.gutter)
            .padding(top = Spacing.sm, bottom = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (summary != null) {
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (error != null) {
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        button()
    }
}

/** The plans a sheet changes, at most [PREVIEW_LIMIT] of them, then how many more. */
@Composable
private fun ChangePreview(lines: List<PreviewLine>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        FieldHeader("What changes")
        val stacked = LocalDensity.current.fontScale >= 1.3f
        lines.take(PREVIEW_LIMIT).forEach { line ->
            if (stacked) {
                Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = line.spoken }) {
                    Text(line.name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        line.change,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (line.changes) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@forEach
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics { contentDescription = line.spoken },
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    line.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    line.change,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (line.changes) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val more = lines.size - PREVIEW_LIMIT
        if (more > 0) SheetCaption(if (more == 1) "and 1 more schedule" else "and $more more schedules")
    }
}

private const val PREVIEW_LIMIT = 6

@Composable
private fun SheetCaption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * Change day: a day of the month for monthly, yearly and one-time plans and a weekday for weekly ones (whichever the
 * selection has), and what changes for each plan. Each plan’s next date moves to that day in the same month or week,
 * so nothing is skipped or added.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ChangeDaySheet(
    schedules: List<RecurringSchedule>,
    today: LocalDate,
    error: String?,
    onApply: (monthDay: Int?, weekday: DayOfWeek?, firstDayOfWeek: DayOfWeek) -> Unit,
    onDismiss: () -> Unit,
) {
    // The phone’s week: the chips start on its first day, and "the same week" means that week (QA, 2026-11-19).
    val firstDayOfWeek = remember { WeekFields.of(Locale.getDefault()).firstDayOfWeek }
    var monthDay by rememberSaveable { mutableStateOf<Int?>(null) }
    var weekday by rememberSaveable { mutableStateOf<DayOfWeek?>(null) }
    val negative = LucentoTheme.extendedColors.negative
    val live = schedules.filter { !it.isFinished }
    val byMonth = live.filter { !ScheduleBulk.takesWeekday(it) }
    val byWeek = live.filter { ScheduleBulk.takesWeekday(it) }
    val lines = remember(schedules, monthDay, weekday, firstDayOfWeek, today, negative) {
        schedules.map { schedule ->
        val next = schedule.nextDue
        val weekly = ScheduleBulk.takesWeekday(schedule)
        val chosen = if (weekly) weekday != null else monthDay != null
        val moved = when {
            next == null -> null
            weekly -> weekday?.let { ScheduleBulk.onWeekday(schedule, it, firstDayOfWeek) }
            else -> monthDay?.let { ScheduleBulk.onMonthDay(schedule, it) }
        }
        // The first date that changes: usually the next one, a later one when the next stays (the 30th moved to the 31st).
        val change = moved?.let { ScheduleBulk.firstDifference(schedule, it) }
        fun date(d: LocalDate?): AnnotatedString = d?.let { dated(it, today, negative) } ?: AnnotatedString("—")
        fun said(d: LocalDate?): String = d?.let { spokenDate(it, today) } ?: "no date"
        val ends = endsText(schedule, moved)
        val overdue = change?.second?.isBefore(today) == true
        when {
            next == null -> PreviewLine(schedule.name, "Ended", "${schedule.name}: ended, stays as it is", changes = false)
            !chosen -> PreviewLine(schedule.name, date(next), "${schedule.name}: next on ${said(next)}", changes = false)
            change == null -> PreviewLine(
                schedule.name,
                buildAnnotatedString { append("Stays "); append(date(next)) },
                "${schedule.name}: stays on ${said(next)}",
                changes = false,
            )
            // The next date moves (and an overdue one can stop being overdue, or a new one become it).
            change.first == next -> PreviewLine(
                schedule.name,
                buildAnnotatedString { append(date(change.first)); append(DATE_ARROW); append(date(change.second)); append(ends) },
                "${schedule.name}: from ${said(change.first)} to ${said(change.second)}$ends",
                changes = true,
                overdue = overdue,
            )
            // The next date stays; a later one is the first to change, on a line of its own (design review, 2026-11-19).
            else -> PreviewLine(
                schedule.name,
                buildAnnotatedString {
                    append("Stays "); append(date(next)); append("\n")
                    append("then "); append(date(change.first)); append(DATE_ARROW); append(date(change.second)); append(ends)
                },
                "${schedule.name}: next stays on ${said(next)}; then from ${said(change.first)} to ${said(change.second)}$ends",
                changes = true,
                overdue = overdue,
            )
        }
        }
    }
    val changes = lines.count { it.changes }
    val overdueCount = lines.count { it.overdue }
    val weekSpan = firstDayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " to " +
        firstDayOfWeek.plus(6).getDisplayName(TextStyle.SHORT, Locale.getDefault())
    LucentoBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SheetHeader(title = "Change day", subtitle = schedulesCount(schedules.size), icon = Icons.Outlined.CalendarMonth)
            if (live.isEmpty()) {
                Text(
                    "These schedules have ended, so there’s no next date to move.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                if (byMonth.isNotEmpty()) {
                    Column {
                        FieldHeader("Day of the month")
                        if (byWeek.isNotEmpty()) SheetCaption("For monthly, yearly and one-time plans")
                        Spacer(Modifier.height(Spacing.xs))
                        FlowRow(
                            modifier = Modifier.selectableGroup(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            (1..31).forEach { day ->
                                DayCell(text = day.toString(), spoken = "Day $day", selected = monthDay == day, onClick = { monthDay = day })
                            }
                        }
                        val chosen = monthDay
                        if (chosen != null && chosen > 28) {
                            Spacer(Modifier.height(Spacing.xs))
                            SheetCaption("Months without day $chosen use their last day.")
                        }
                    }
                }
                if (byWeek.isNotEmpty()) {
                    Column {
                        FieldHeader("Day of the week")
                        if (byMonth.isNotEmpty()) SheetCaption("For weekly plans")
                        Spacer(Modifier.height(Spacing.xs))
                        FlowRow(
                            modifier = Modifier.selectableGroup(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            (0L until 7L).map { firstDayOfWeek.plus(it) }.forEach { day ->
                                val full = day.getDisplayName(TextStyle.FULL, Locale.getDefault())
                                ChoicePill(
                                    label = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                                    selected = weekday == day,
                                    onClick = { weekday = day },
                                    // Read once, by its full name ("Monday", not "Monday, Mon").
                                    modifier = Modifier.clearAndSetSemantics {
                                        contentDescription = full
                                        role = Role.RadioButton
                                        selected = weekday == day
                                        onClick {
                                            weekday = day
                                            true
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                ChangePreview(lines)
                InfoNote(dayNote(byMonth.isNotEmpty(), byWeek.isNotEmpty(), weekSpan))
            }
            Spacer(Modifier.height(Spacing.xs))
        }
        // Pinned under the list: the button never scrolls out of reach (design review, 2026-11-19).
        SheetFooter(
            error = error,
            summary = if (overdueCount == 0) null else buildAnnotatedString {
                withStyle(SpanStyle(color = negative)) {
                    append(if (overdueCount == 1) "1 date will be overdue" else "$overdueCount dates will be overdue")
                }
                append(": the day is before today.")
            },
        ) {
            if (live.isEmpty()) {
                LucentoPrimaryButton(text = "Close", onClick = onDismiss)
            } else {
                LucentoPrimaryButton(
                    text = if (changes == 0) "Change day" else "Change day for ${schedulesCount(changes)}",
                    enabled = changes > 0,
                    onClick = { onApply(monthDay, weekday, firstDayOfWeek) },
                )
            }
        }
    }
}

/** A day of the month to choose: a 48 dp circle, filled when chosen. */
@Composable
private fun DayCell(text: String, spoken: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (selected) colors.primary else colors.surfaceContainer, tween(160), label = "day-fill")
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(fill)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) colors.onPrimary else colors.onSurface,
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/**
 * Change amount: one new amount for every plan selected, when they share a currency (a debt payment’s is its whole
 * payment; the interest split is entered at Mark paid), with what changes for each.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChangeAmountSheet(
    schedules: List<RecurringSchedule>,
    error: String?,
    onApply: (Money) -> Unit,
    onDismiss: () -> Unit,
) = AmountsShown {
    val separator = remember { MoneyFormatter.decimalSeparator() }
    val fmt = amountFormat()
    val live = schedules.filter { !it.isFinished }
    val currency = ScheduleBulk.sharedCurrency(live)
    var text by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val parsed = currency?.let { code -> parsePositive(text, code, separator).first }
    // Signed as in the list: a bill reads −₱350.00, an income +₱35,000.00 (design review, 2026-11-19); never broken.
    val lines = schedules.map { schedule ->
        val now = fmt.signed(schedule.signedOccurrence)
        when {
            schedule.isFinished -> PreviewLine(schedule.name, "Ended", "${schedule.name}: ended, stays as it is", changes = false)
            parsed == null || parsed == schedule.total -> PreviewLine(schedule.name, wholeAmount(now), "${schedule.name}: $now", changes = false)
            else -> {
                val becomes = fmt.signed(if (schedule.signedOccurrence.isNegative) -parsed else parsed)
                PreviewLine(
                    schedule.name,
                    wholeAmount(now) + AMOUNT_ARROW + wholeAmount(becomes),
                    "${schedule.name}: from $now to $becomes",
                    changes = true,
                )
            }
        }
    }
    val changes = lines.count { it.changes }
    val usable = live.isNotEmpty() && currency != null
    LucentoBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SheetHeader(title = "Change amount", subtitle = schedulesCount(schedules.size), icon = Icons.Outlined.Payments)
            if (!usable || currency == null) {
                Text(
                    if (live.isEmpty()) {
                        "These schedules have ended, so there’s nothing to change."
                    } else {
                        "These schedules use ${live.map { it.currencyCode }.distinct().sorted().joinToString(" and ")}. " +
                            "Select schedules in one currency to give them one amount."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                MoneyField(
                    value = text,
                    onValueChange = {
                        text = it
                        message = null
                    },
                    label = "New amount",
                    currencyCode = currency,
                    error = message ?: error,
                    supporting = if (live.any { it.isDebtPayment || it.isCollection }) {
                        "For a card or loan, this is the whole payment. You split off interest when you mark it paid."
                    } else {
                        null
                    },
                )
                ChangePreview(lines)
            }
            Spacer(Modifier.height(Spacing.xs))
        }
        // Pinned under the list, as in Change day (design review, 2026-11-19).
        SheetFooter(error = null) {
            if (!usable || currency == null) {
                LucentoPrimaryButton(text = "Close", onClick = onDismiss)
            } else {
                LucentoPrimaryButton(
                    text = if (changes == 0) "Change amount" else "Change amount for ${schedulesCount(changes)}",
                    // Typed but not yet an amount: the tap says what is wrong; an amount nobody would change: nothing to do.
                    enabled = text.isNotBlank() && (parsed == null || changes > 0),
                    onClick = {
                        val (money, problem) = parsePositive(text, currency, separator)
                        if (money == null) {
                            message = problem?.message(currency, separator)
                            return@LucentoPrimaryButton
                        }
                        onApply(money)
                    },
                )
            }
        }
    }
}

/**
 * The scheduled-payment editor’s warning when the card or loan it pays already has a payment plan (or the receivable a
 * collection plan): a second one would remind twice. “Adjust existing” opens Scheduled with those plans selected, to
 * change their day or amount there; “Add another” carries on with this one (tester round, 2026-11-18).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DuplicatePlanWarning(
    debtName: String,
    collection: Boolean,
    plans: List<RecurringSchedule>,
    today: LocalDate,
    onAdjust: () -> Unit,
    onAddAnother: () -> Unit,
    modifier: Modifier = Modifier,
) = AmountsShown {
    val ext = LucentoTheme.extendedColors
    val fmt = amountFormat()
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(ext.warningContainer)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = ext.warning, modifier = Modifier.padding(top = 1.dp).size(20.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text(
                duplicateTitle(debtName, collection, plans.size),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                // Announced when it appears (after choosing the debt), wherever the person is on the page.
                modifier = Modifier.semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
            )
        }
        plans.take(DUPLICATES_LISTED).forEach { plan ->
            val repeat = if (ScheduleBulk.isOnce(plan)) "Once" else frequencyLabel(plan.frequency, plan.interval)
            Text(
                listOfNotNull(
                    plan.name,
                    wholeAmount(fmt.signed(plan.signedOccurrence)),
                    repeat,
                    plan.nextDue?.let { "next ${dayLabel(it, today)}" },
                    "paused".takeIf { plan.paused },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (plans.size > DUPLICATES_LISTED) SheetCaption("and ${plans.size - DUPLICATES_LISTED} more")
        Text(
            duplicateBody(collection),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            LucentoSecondaryButton(text = "Adjust existing", onClick = onAdjust, fillWidth = false)
            // Deep blue on the amber box: 6.6:1 (primary blue was 4.37:1, under 4.5:1; design review, 2026-11-19).
            LucentoTextButton(text = "Add another", onClick = onAddAnother, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

private const val DUPLICATES_LISTED = 3

/** "Car loan already has a payment plan" (or N plans; a receivable’s are collection plans). */
internal fun duplicateTitle(debtName: String, collection: Boolean, count: Int): String {
    val kind = if (collection) "collection plan" else "payment plan"
    return if (count == 1) "$debtName already has a $kind" else "$debtName already has $count ${kind}s"
}

internal fun duplicateBody(collection: Boolean): String =
    if (collection) "A second plan would remind you twice to collect it." else "A second plan would remind you twice to pay it."

/** "Rent, Phone plan and Water", at most five names and then "and N more". */
internal fun scheduleNames(names: List<String>): String {
    val shown = names.take(5)
    val rest = names.size - shown.size
    return when {
        rest > 0 -> shown.joinToString(", ") + " and $rest more"
        shown.size <= 1 -> shown.joinToString()
        else -> shown.dropLast(1).joinToString(", ") + " and " + shown.last()
    }
}

/** Select all’s check: empty, checked, or a dash when only some are selected. */
@Composable
private fun SelectAllMark(state: ToggleableState) {
    if (state != ToggleableState.Indeterminate) {
        CheckMark(state == ToggleableState.On)
        return
    }
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.size(24.dp).clip(CircleShape).background(scheme.primary), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Remove, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(16.dp))
    }
}
