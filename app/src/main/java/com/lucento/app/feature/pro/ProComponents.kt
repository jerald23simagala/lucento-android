package com.lucento.app.feature.pro

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoBottomSheet
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.SheetHeader
import com.lucento.app.core.designsystem.component.StatusPill
import com.lucento.app.core.designsystem.component.StatusTones
import com.lucento.app.core.designsystem.theme.OverlineStyle
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.domain.entitlement.ComparisonRow
import com.lucento.app.domain.entitlement.PlanNotice
import com.lucento.app.domain.entitlement.ProFeature

/** A small "Pro" mark beside something the current plan doesn't include. Read as "Lucento Pro feature" by TalkBack. */
@Composable
fun ProBadge(modifier: Modifier = Modifier) {
    StatusPill(
        text = "Pro",
        tone = StatusTones.brand,
        modifier = modifier.clearAndSetSemantics { contentDescription = "Lucento Pro feature" },
    )
}

/**
 * Every lock in Lucento looks the same: one quiet note, where the person is, saying what isn't on their plan and what
 * stays free, with a link to the Lucento Pro page — never a pop-up over what they were doing (docs/MONETIZATION.md §6.1).
 * [open] replaces the default navigation when the note sits in a sheet that must close first.
 */
@Composable
fun ProNote(text: String, from: ProFeature?, modifier: Modifier = Modifier, open: ((ProFeature?) -> Unit)? = null) {
    val openPro = LocalOpenPro.current
    InfoNote(
        text = text,
        modifier = modifier,
        icon = Icons.Outlined.WorkspacePremium,
        actionLabel = "See Lucento Pro",
        onAction = { (open ?: openPro)(from) },
    )
}

/** Width of each plan column in the comparison table: fits "No limit" and "1 Partner" at the default text size. */
private val PlanColumn = 64.dp

/**
 * The Free / Pro / Lifetime comparison as a real table: feature names on the left, one column per plan, grouped into what
 * runs on the phone and what runs online. At large text sizes (fontScale ≥ 1.3) three columns no longer fit, so each row
 * becomes [ComparisonItem]: the name, then every plan's allowance in words. TalkBack reads each row in words either way.
 */
@Composable
internal fun PlanComparison(rows: List<ComparisonRow>, modifier: Modifier = Modifier) {
    val table = LocalDensity.current.fontScale < 1.3f
    Column(modifier.fillMaxWidth()) {
        if (table) TableHeader()
        ProCopy.comparisonGroups(rows).forEach { (title, group) ->
            GroupLabel(title)
            group.forEachIndexed { i, row ->
                if (i > 0) Hairline()
                if (table) ComparisonTableRow(row) else ComparisonItem(row)
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Text(ProCopy.FIRST_YEAR_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The column names. Hidden from TalkBack: every row already says which plan allows what. */
@Composable
private fun TableHeader() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm)
            .clearAndSetSemantics {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
        listOf("Free" to false, "Pro" to true, "Lifetime" to true).forEach { (plan, paid) ->
            Text(
                plan,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = if (paid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.width(PlanColumn),
            )
        }
    }
}

@Composable
private fun GroupLabel(title: String) {
    Text(
        title.uppercase(),
        style = OverlineStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(top = Spacing.lg, bottom = Spacing.xs)
            .clearAndSetSemantics {
                heading()
                text = AnnotatedString(title)
            },
    )
}

@Composable
private fun ComparisonTableRow(row: ComparisonRow) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.minTouch)
            .padding(vertical = Spacing.sm)
            .clearAndSetSemantics { contentDescription = ProCopy.comparisonDescription(row) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            ProCopy.featureName(row.feature),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(end = Spacing.sm),
        )
        TableCellView(ProCopy.tableCell(row.feature, row.free))
        TableCellView(ProCopy.tableCell(row.feature, row.pro))
        TableCellView(ProCopy.lifetimeTableCell(row))
    }
}

/** A tick and a dash differ in shape, not only colour; values are short words or numbers. */
@Composable
private fun TableCellView(cell: ProCopy.TableCell) {
    Box(Modifier.width(PlanColumn), contentAlignment = Alignment.Center) {
        when (cell) {
            ProCopy.TableCell.Included -> Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Sizes.icon),
            )
            ProCopy.TableCell.NotIncluded -> Text("–", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
            is ProCopy.TableCell.Value -> Text(
                cell.text,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

/** One feature in the plan comparison at large text: its name, then each plan's allowance in words. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ComparisonItem(row: ComparisonRow, modifier: Modifier = Modifier) {
    val name = ProCopy.featureName(row.feature)
    val free = ProCopy.cell(row.feature, row.free)
    val pro = ProCopy.cell(row.feature, row.pro)
    val lifetime = ProCopy.lifetimeCell(row)
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.md)
            .clearAndSetSemantics { contentDescription = ProCopy.comparisonDescription(row) },
    ) {
        Text(name, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(Spacing.xs))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            PlanValue("Free", free)
            PlanValue("Pro", pro)
            PlanValue("Lifetime", lifetime)
        }
    }
}

@Composable
private fun PlanValue(plan: String, value: String) {
    Row {
        Text("$plan  ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
    }
}

/** Shown once, on the first start after a plan ends (customer copy D-7): what changed, and that nothing was removed. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PlanNoticeSheet(notice: PlanNotice, onSeePro: () -> Unit, onDismiss: () -> Unit) {
    val copy = ProCopy.notice(notice)
    LucentoBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter)
                .navigationBarsPadding(),
        ) {
            SheetHeader(
                title = copy.title,
                subtitle = "Lucento Pro",
                icon = Icons.Outlined.WorkspacePremium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(copy.body, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(Spacing.lg))
            LucentoPrimaryButton(text = "See Lucento Pro", onClick = onSeePro, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Spacing.xs))
            LucentoTextButton(text = "OK", onClick = onDismiss)
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}
