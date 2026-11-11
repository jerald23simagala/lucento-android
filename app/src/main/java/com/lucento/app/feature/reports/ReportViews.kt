package com.lucento.app.feature.reports

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.IconTile
import com.lucento.app.core.designsystem.component.LucentoBottomSheet
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoSecondaryButton
import com.lucento.app.core.designsystem.component.MoneyText
import com.lucento.app.core.designsystem.component.SheetHeader
import com.lucento.app.core.designsystem.component.StatusTones
import com.lucento.app.core.designsystem.component.WalletChoicePill
import com.lucento.app.core.designsystem.component.animatedFraction
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.OverlineStyle
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.designsystem.theme.TonePair
import com.lucento.app.core.money.Money
import com.lucento.app.data.reports.ReportPdfWriter
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.ask.Report
import com.lucento.app.domain.ask.ReportKind
import com.lucento.app.domain.ask.ReportLine
import com.lucento.app.domain.ask.ReportSection
import java.time.YearMonth

/** The order reports are offered in on Insights: the one wallet first, then the whole picture. */
val ReportKindsOnInsights: List<ReportKind> = listOf(
    ReportKind.WALLET_STATEMENT,
    ReportKind.CASH_FLOW,
    ReportKind.NET_WORTH,
    ReportKind.BUDGET_VS_ACTUAL,
    ReportKind.DEBTS,
    ReportKind.TOP_CATEGORIES,
)

val ReportKind.icon: ImageVector
    get() = when (this) {
        ReportKind.NET_WORTH -> Icons.Outlined.AccountBalance
        ReportKind.CASH_FLOW -> Icons.Outlined.SwapVert
        ReportKind.BUDGET_VS_ACTUAL -> Icons.Outlined.PieChart
        ReportKind.DEBTS -> Icons.Outlined.CreditCard
        ReportKind.TOP_CATEGORIES -> Icons.Outlined.Leaderboard
        ReportKind.WALLET_STATEMENT -> Icons.AutoMirrored.Outlined.ReceiptLong
    }

val ReportKind.caption: String
    get() = when (this) {
        ReportKind.NET_WORTH -> "What you own and owe, and the change"
        ReportKind.CASH_FLOW -> "Income and spending by category"
        ReportKind.BUDGET_VS_ACTUAL -> "Each category against its budget"
        ReportKind.DEBTS -> "Balances, rates and next payments"
        ReportKind.TOP_CATEGORIES -> "Where the money went"
        ReportKind.WALLET_STATEMENT -> "One wallet’s month: start, every movement, end"
    }

/** Each report's own tone, so the list scans by colour as well as by name. Decorative: the title says what it is. */
@Composable
fun ReportKind.tone(): TonePair = when (this) {
    ReportKind.WALLET_STATEMENT, ReportKind.NET_WORTH -> StatusTones.brand
    ReportKind.CASH_FLOW -> StatusTones.positive
    ReportKind.BUDGET_VS_ACTUAL -> StatusTones.warning
    ReportKind.DEBTS -> StatusTones.negative
    ReportKind.TOP_CATEGORIES -> StatusTones.neutral
}

/**
 * The reports as one list (Insights, below Ask Lucento): each with its own tone, what it shows, and the way in. A wallet
 * statement needs a wallet money moves through, so it is offered only once there is one ([canOpen]).
 */
@Composable
fun ReportList(onOpen: (ReportKind) -> Unit, modifier: Modifier = Modifier, canOpen: (ReportKind) -> Boolean = { true }) {
    LucentoCard(modifier.fillMaxWidth(), contentPadding = 0.dp) {
        ReportKindsOnInsights.forEachIndexed { i, kind ->
            if (i > 0) Hairline(inset = Spacing.md + 40.dp + Spacing.md)
            val enabled = canOpen(kind)
            val tone = kind.tone()
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 68.dp)
                    .clickable(enabled = enabled, onClickLabel = "Open ${kind.title}", role = Role.Button) { onOpen(kind) }
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(kind.icon, container = tone.container, content = tone.content, size = 40.dp, shape = RoundedCornerShape(12.dp))
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(kind.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
                        if (kind == ReportKind.WALLET_STATEMENT) {
                            Spacer(Modifier.width(Spacing.sm))
                            com.lucento.app.core.designsystem.component.StatusPill("New", StatusTones.brand)
                        }
                    }
                    Text(
                        if (enabled) kind.caption else "Add a wallet first",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.4f),
                )
            }
        }
    }
}

/**
 * The full statement in a sheet: the month (not for debts, which are as of today), the wallet for a wallet statement,
 * the headline, every section, how it was calculated, and Save as PDF / Share. Built again on the phone for each month
 * or wallet chosen. The same [Report] is written to the PDF, so the two never disagree.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ReportSheet(
    initial: Report,
    rebuild: (Report, YearMonth) -> Report?,
    onSave: (Report, android.net.Uri, android.content.ContentResolver, (Boolean) -> Unit) -> Unit,
    onShare: (Report, (java.io.File?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    /** Lucento Pro: the statement stays on screen; Save as PDF and Share wait for a plan that includes them. */
    pdfLocked: Boolean = false,
    /** A wallet statement's choices, and the same statement for another of them. */
    statementWallets: List<Wallet> = emptyList(),
    switchWallet: (Report, Long) -> Report? = { _, _ -> null },
) {
    val context = LocalContext.current
    val openPro = com.lucento.app.feature.pro.LocalOpenPro.current
    var report by remember(initial) { mutableStateOf(initial) }
    var message by remember { mutableStateOf<String?>(null) }
    val current = remember { YearMonth.now() }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) onSave(report, uri, context.contentResolver) { ok -> message = if (ok) "Saved as a PDF." else "The PDF couldn’t be saved. Try again." }
    }
    LucentoBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter)
                .navigationBarsPadding(),
        ) {
            SheetHeader(
                title = report.kind.title,
                subtitle = listOfNotNull(report.subject, report.period, report.currencyCode).joinToString(" · "),
                icon = report.kind.icon,
            )
            if (report.kind == ReportKind.WALLET_STATEMENT && statementWallets.size > 1) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    statementWallets.forEach { w ->
                        WalletChoicePill(
                            wallet = w,
                            selected = w.id == report.walletId,
                            onClick = { switchWallet(report, w.id)?.let { report = it; message = null } },
                        )
                    }
                }
            }
            if (report.kind != ReportKind.DEBTS) {
                MonthStepper(
                    month = report.month,
                    canGoBack = report.month > current.minusMonths(24),
                    canGoForward = report.month < current,
                    onMonth = { m -> rebuild(report, m)?.let { report = it; message = null } },
                )
                Spacer(Modifier.height(Spacing.md))
            }
            ReportBody(report)
            Spacer(Modifier.height(Spacing.md))
            message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                Spacer(Modifier.height(Spacing.sm))
            }
            if (pdfLocked) {
                com.lucento.app.feature.pro.ProNote(
                    com.lucento.app.feature.pro.ProCopy.lockedLine(com.lucento.app.domain.entitlement.ProFeature.PDF_STATEMENTS),
                    from = com.lucento.app.domain.entitlement.ProFeature.PDF_STATEMENTS,
                    open = { feature -> onDismiss(); openPro(feature) },
                )
                Spacer(Modifier.height(Spacing.lg))
                return@Column
            }
            LucentoPrimaryButton(text = "Save as PDF", icon = Icons.Outlined.PictureAsPdf, onClick = { saver.launch(ReportPdfWriter.fileName(report)) })
            Spacer(Modifier.height(Spacing.sm))
            LucentoSecondaryButton(
                text = "Share",
                icon = Icons.Outlined.Share,
                onClick = {
                    onShare(report) { file ->
                        if (file == null) {
                            message = "The PDF couldn’t be prepared. Try again."
                        } else {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_SUBJECT, "${report.title} · ${report.period}")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(send, "Share ${report.title}"))
                        }
                    }
                },
            )
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun MonthStepper(month: YearMonth, canGoBack: Boolean, canGoForward: Boolean, onMonth: (YearMonth) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(50)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onMonth(month.minusMonths(1)) }, enabled = canGoBack) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Text(
            month.month.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, java.util.Locale.getDefault()) + " ${month.year}",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = { onMonth(month.plusMonths(1)) }, enabled = canGoForward) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}

/**
 * The statement on screen (tester round 3, "enhance visuals and formatting"): the headline on a tinted panel with the
 * report's mark; a heading wherever a group of sections starts; each section as a card whose lines carry a share bar
 * when they are parts of a whole, with its total on a tinted footer; and how it was calculated, set apart.
 */
@Composable
fun ReportBody(report: Report) {
    HeadlinePanel(report)
    var group: String? = null
    report.sections.forEach { section ->
        if (section.group != null && section.group != group) {
            GroupHeading(section.group)
        } else {
            Spacer(Modifier.height(Spacing.md))
        }
        group = section.group
        SectionCard(section)
    }
    if (report.notes.isNotEmpty()) {
        Spacer(Modifier.height(Spacing.md))
        NotesPanel(report.notes)
    }
}

@Composable
private fun HeadlinePanel(report: Report) {
    val colors = MaterialTheme.colorScheme
    val tone = report.kind.tone()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(colors.primaryContainer, colors.primaryContainer.copy(alpha = 0.35f))))
            .padding(Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(report.kind.icon, container = tone.content, content = colors.surface, size = 32.dp, shape = RoundedCornerShape(10.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text(
                report.headline.label.uppercase(),
                style = OverlineStyle,
                color = colors.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        report.headline.values.firstOrNull()?.let {
            MoneyText(money = it, style = AmountStyles.hero, color = colors.onSurface, fractionScale = 1f, minScale = 0.5f)
        }
        report.headline.detail?.let { Text(shownText(it), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
    }
}

/**
 * A report's prose with its amounts hidden when the user has hidden their figures: details such as "Balance at the start
 * $6,570.00" are written as text, so [MoneyText]'s own hiding doesn't reach them. Dates, counts and percentages stay.
 */
@Composable
private fun shownText(text: String): String =
    if (com.lucento.app.core.designsystem.component.amountFormat().hidden) com.lucento.app.core.money.MoneyFormatter.maskAmountsIn(text) else text

@Composable
private fun GroupHeading(title: String) {
    Text(
        title.uppercase(),
        style = OverlineStyle,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(top = Spacing.lg, bottom = Spacing.sm)
            .semantics { heading() },
    )
}

@Composable
private fun SectionCard(section: ReportSection) {
    val colors = MaterialTheme.colorScheme
    LucentoCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
        Row(Modifier.fillMaxWidth().padding(start = Spacing.md, end = Spacing.md, top = Spacing.md, bottom = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Text(
                section.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (section.lines.isNotEmpty()) {
                Text(
                    if (section.lines.size == 1) "1 line" else "${section.lines.size} lines",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        if (section.lines.isEmpty() && section.total == null) {
            Text(
                section.emptyText,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md),
            )
            return@LucentoCard
        }
        if (section.lines.isEmpty()) {
            Text(
                section.emptyText,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
        }
        section.lines.forEachIndexed { i, line ->
            if (i > 0) Hairline(inset = Spacing.md)
            ReportRow(line, section.columns, Modifier.padding(horizontal = Spacing.md))
        }
        section.total?.let { total ->
            Spacer(Modifier.height(Spacing.xs))
            Box(Modifier.fillMaxWidth().background(colors.surfaceContainerHigh.copy(alpha = 0.6f))) {
                ReportRow(total, section.columns, Modifier.padding(horizontal = Spacing.md))
            }
        }
    }
}

/** One row: label and detail, a share bar when it is part of a whole; a single figure at the end, several beneath. */
@Composable
private fun ReportRow(line: ReportLine, columns: List<String>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val weight = if (line.emphasis) FontWeight.Bold else FontWeight.Normal
    Column(modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = Spacing.sm), verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    shownText(line.label),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (line.emphasis) FontWeight.Bold else FontWeight.Medium),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                line.detail?.let { Text(shownText(it), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
            }
            if (columns.size <= 1) {
                line.values.firstOrNull()?.let { amount ->
                    Spacer(Modifier.width(Spacing.sm))
                    MoneyText(
                        money = amount,
                        style = AmountStyles.row.copy(fontWeight = if (line.emphasis) FontWeight.Bold else FontWeight.SemiBold),
                        color = colors.onSurface,
                        showPlus = line.signed,
                        fractionScale = 1f,
                        minScale = 0.6f,
                    )
                }
            }
        }
        line.share?.let { share ->
            Spacer(Modifier.height(6.dp))
            ShareBar(share)
        }
        if (columns.size > 1) {
            Row(Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
                columns.forEachIndexed { i, header ->
                    Column(Modifier.weight(1f), horizontalAlignment = if (i == columns.lastIndex) Alignment.End else Alignment.Start) {
                        Text(header, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                        val value: Money? = line.values.getOrNull(i)
                        if (value != null) {
                            MoneyText(money = value, style = AmountStyles.small.copy(fontWeight = weight), color = colors.onSurface, fractionScale = 1f, minScale = 0.6f)
                        } else {
                            Text("—", style = AmountStyles.small, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** A thin bar of a line's share of its section (display only; the exact amount and percentage are printed). */
@Composable
private fun ShareBar(fraction: Float) {
    val ext = LucentoTheme.extendedColors
    val animated = animatedFraction(fraction.coerceIn(0f, 1f))
    Box(
        Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(50))
            .background(ext.track)
            .clearAndSetSemantics {},
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(5.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun NotesPanel(notes: List<String>) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer, RoundedCornerShape(16.dp))
            .padding(Spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text("How this was calculated", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(Spacing.xxs))
            notes.forEach { note ->
                Text("• $note", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = Spacing.xxs))
            }
        }
    }
}
