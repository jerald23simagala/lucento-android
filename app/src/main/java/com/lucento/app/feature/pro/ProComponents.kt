package com.lucento.app.feature.pro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoBottomSheet
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.SheetHeader
import com.lucento.app.core.designsystem.component.StatusPill
import com.lucento.app.core.designsystem.component.StatusTones
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

/** One feature in the plan comparison: its name, then each plan's allowance; wraps instead of truncating at large text. */
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
            .clearAndSetSemantics { contentDescription = "$name. Free: $free. Pro: $pro. Lifetime: $lifetime." },
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
