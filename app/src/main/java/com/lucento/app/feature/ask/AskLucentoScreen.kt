package com.lucento.app.feature.ask

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.CardOverline
import com.lucento.app.core.designsystem.component.ChatDayDivider
import com.lucento.app.core.designsystem.component.ChatInputBar
import com.lucento.app.core.designsystem.component.ChatMessageField
import com.lucento.app.core.designsystem.component.ChatSendButton
import com.lucento.app.core.designsystem.component.ChoicePill
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.IconTile
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoBottomSheet
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoReply
import com.lucento.app.core.designsystem.component.LucentoSecondaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.component.MascotSpeech
import com.lucento.app.core.designsystem.component.MoneyText
import com.lucento.app.core.designsystem.component.SheetHeader
import com.lucento.app.core.designsystem.component.StatusPill
import com.lucento.app.core.designsystem.component.StatusTones
import com.lucento.app.core.designsystem.component.TypingDots
import com.lucento.app.core.designsystem.component.UserChatBubble
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.Money
import com.lucento.app.data.reports.ReportPdfWriter
import com.lucento.app.domain.ask.CheckedAnswer
import com.lucento.app.domain.ask.LocalAnswers
import com.lucento.app.domain.ask.Report
import com.lucento.app.domain.ask.ReportKind
import com.lucento.app.domain.ask.ReportLine
import com.lucento.app.domain.ask.ReportSection
import java.time.YearMonth

/**
 * Ask Lucento: questions about the user's own money, and the preset reports as PDFs. The page opens with Lucento's
 * greeting, preset questions and the four statements; every answer says who worked it out (this phone, or Gemini with
 * figures from this phone). AI answers are off until the user agrees in the consent sheet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskLucentoScreen(
    onBack: () -> Unit,
    viewModel: AskLucentoViewModel = viewModel(factory = AskLucentoViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    var input by rememberSaveable { mutableStateOf("") }
    var consentFor by remember { mutableStateOf<String?>(null) }
    var askingConsent by remember { mutableStateOf(false) }
    var openReport by remember { mutableStateOf<Report?>(null) }
    var menu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // The first question this phone can't answer: suggest AI answers once, with that question ready to ask again.
    val offered = state.messages.lastOrNull() as? AskMessage.Answer
    LaunchedEffect(offered?.id, state.aiOfferShown, state.aiAvailable) {
        if (offered == null || !offered.offersAi || state.aiOfferShown || !state.aiAvailable || state.aiOn || askingConsent) return@LaunchedEffect
        consentFor = state.messages.lastOrNull { it is AskMessage.Question && it.id < offered.id }?.let { (it as AskMessage.Question).text }
        askingConsent = true
        viewModel.aiOfferShown()
    }

    // Keep the newest message in view as the conversation grows.
    LaunchedEffect(state.messages.size, state.thinking) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
    }

    fun send(text: String = input) {
        if (text.isBlank() || state.thinking) return
        haptics.tick()
        viewModel.ask(text)
        if (text == input) input = ""
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        LucentoTopBar(
            title = "Ask Lucento",
            onBack = onBack,
            modifier = Modifier.padding(horizontal = Spacing.gutter),
            actions = {
                // AI answers, always in view (owner test 2026-11-09): on asks for consent first; off is immediate.
                if (state.aiAvailable) {
                    com.lucento.app.core.designsystem.component.CompactSwitch(
                        label = "Gemini",
                        icon = Icons.Outlined.AutoAwesome,
                        checked = state.aiOn,
                        onCheckedChange = { on -> if (on) askingConsent = true else viewModel.setAiAnswers(false) },
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("New conversation") },
                            enabled = state.messages.isNotEmpty() && !state.thinking,
                            onClick = {
                                menu = false
                                viewModel.clear()
                            },
                        )
                    }
                }
            },
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.xs, bottom = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(key = "intro") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    MascotSpeech(
                        title = "Ask me about your money",
                        body = if (state.aiActive) {
                            "I answer from your own records and suggest what to do next. Reports are worked out on this phone and can be saved as PDFs."
                        } else {
                            "I answer from your own records. Reports are worked out on this phone and can be saved as PDFs."
                        },
                        mood = if (state.messages.isEmpty()) MascotMood.Greeting else MascotMood.Happy,
                        mascotSize = 64.dp,
                    )
                    AiStatus(state, onTurnOn = { askingConsent = true })
                }
            }
            if (state.messages.isEmpty()) {
                if (state.aiActive) {
                    item(key = "advice") {
                        Column {
                            SmallLabel("Ask for advice")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                LocalAnswers.adviceSuggestions.forEach { q ->
                                    ChoicePill(label = q, selected = false, onClick = { send(q) }, role = androidx.compose.ui.semantics.Role.Button)
                                }
                            }
                        }
                    }
                }
                item(key = "suggestions") {
                    Column {
                        SmallLabel("Try asking")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            LocalAnswers.suggestions.forEach { q ->
                                ChoicePill(label = q, selected = false, onClick = { send(q) }, role = androidx.compose.ui.semantics.Role.Button)
                            }
                        }
                    }
                }
                item(key = "reports") {
                    Column {
                        SmallLabel("Reports")
                        ReportTiles(onOpen = { kind -> haptics.tick(); viewModel.openReport(kind) })
                    }
                }
            } else {
                item(key = "today") { ChatDayDivider("Today") }
            }
            state.messages.forEach { message ->
                item(key = "m${message.id}") {
                    when (message) {
                        is AskMessage.Question -> Box(Modifier.fillMaxWidth().padding(start = 48.dp), contentAlignment = Alignment.CenterEnd) {
                            UserChatBubble(message.text)
                        }
                        is AskMessage.Answer -> AnswerItem(
                            answer = message,
                            onOpenReport = { openReport = it },
                            onTurnOnAi = {
                                consentFor = state.messages.lastOrNull { it is AskMessage.Question && it.id < message.id }?.let { (it as AskMessage.Question).text }
                                askingConsent = true
                            },
                            onFollowUp = { send(it) },
                        )
                    }
                }
            }
            if (state.thinking) {
                item(key = "thinking") {
                    LucentoReply(mood = MascotMood.Curious) { TypingDots() }
                }
            }
        }

        // Preset questions stay one tap away once the conversation has started.
        if (state.messages.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                ReportKind.entries.filter { it != ReportKind.TOP_CATEGORIES }.forEach { kind ->
                    ChoicePill(label = kind.title, selected = false, leadingIcon = kind.icon, onClick = { viewModel.openReport(kind) }, role = androidx.compose.ui.semantics.Role.Button)
                }
            }
        }
        ChatInputBar(Modifier.padding(horizontal = Spacing.gutter).padding(bottom = Spacing.sm, top = Spacing.xxs).navigationBarsPadding()) {
            ChatMessageField(
                value = input,
                onValueChange = { input = it.take(AskLucentoViewModel.QUESTION_LIMIT) },
                onSend = { send() },
                placeholder = if (state.aiActive) "Ask anything about your money…" else "Ask about net worth, budget, debts…",
                description = "Ask Lucento a question",
                modifier = Modifier.weight(1f),
            )
            ChatSendButton(enabled = input.isNotBlank() && !state.thinking && !state.loading, onClick = { send() })
        }
    }

    if (askingConsent) {
        AiConsentSheet(
            onAccept = {
                askingConsent = false
                viewModel.setAiAnswers(true, thenAsk = consentFor)
                consentFor = null
            },
            onDismiss = {
                askingConsent = false
                consentFor = null
            },
        )
    }
    openReport?.let { report ->
        ReportSheet(
            initial = report,
            rebuild = viewModel::rebuild,
            onSave = viewModel::savePdf,
            onShare = viewModel::pdfForSharing,
            onDismiss = { openReport = null },
            pdfLocked = state.pdfLocked,
        )
    }
}

@Composable
private fun SmallLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = Spacing.xs).semantics { heading() },
    )
}

/** Where answers come from right now: this phone only, or AI answers on (Gemini), with today's allowance once known. */
@Composable
private fun AiStatus(state: AskUiState, onTurnOn: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (state.aiActive) {
            StatusPill("AI answers on · Gemini", StatusTones.brand)
            state.remainingToday?.let {
                Text("$it left today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            StatusPill("Reports on this phone", StatusTones.neutral)
            if (state.aiAvailable) LucentoTextButton(text = "Turn on AI answers", onClick = onTurnOn)
        }
    }
}

private val ReportKind.icon: ImageVector
    get() = when (this) {
        ReportKind.NET_WORTH -> Icons.Outlined.AccountBalance
        ReportKind.CASH_FLOW -> Icons.Outlined.SwapVert
        ReportKind.BUDGET_VS_ACTUAL -> Icons.Outlined.PieChart
        ReportKind.DEBTS -> Icons.Outlined.CreditCard
        ReportKind.TOP_CATEGORIES -> Icons.Outlined.Description
    }

private val ReportKind.caption: String
    get() = when (this) {
        ReportKind.NET_WORTH -> "Assets, debts, the change"
        ReportKind.CASH_FLOW -> "Income and spending by category"
        ReportKind.BUDGET_VS_ACTUAL -> "Each category against its budget"
        ReportKind.DEBTS -> "Balances, rates, next payments"
        ReportKind.TOP_CATEGORIES -> "Where the money went"
    }

/** The four statements as a two-by-two grid of tiles. */
@Composable
private fun ReportTiles(onOpen: (ReportKind) -> Unit) {
    val kinds = listOf(ReportKind.NET_WORTH, ReportKind.CASH_FLOW, ReportKind.BUDGET_VS_ACTUAL, ReportKind.DEBTS)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        kinds.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                pair.forEach { kind ->
                    LucentoCard(modifier = Modifier.weight(1f).fillMaxHeight(), onClick = { onOpen(kind) }, contentPadding = Spacing.md) {
                        IconTile(
                            kind.icon,
                            container = MaterialTheme.colorScheme.primaryContainer,
                            content = MaterialTheme.colorScheme.onPrimaryContainer,
                            size = 36.dp,
                            shape = RoundedCornerShape(11.dp),
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        Text(kind.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                        Text(kind.caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                }
            }
        }
    }
}

/** One answer: its text (figures from the phone in bold), any report, a caution, follow-ups, and who worked it out. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnswerItem(
    answer: AskMessage.Answer,
    onOpenReport: (Report) -> Unit,
    onTurnOnAi: () -> Unit,
    onFollowUp: (String) -> Unit,
) {
    LucentoReply(
        mood = if (answer.source == AnswerSource.GEMINI) MascotMood.Happy else MascotMood.Curious,
        footer = { SourceLine(answer.source) },
    ) {
        Text(
            annotated(answer.parts),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        answer.report?.let { report ->
            Spacer(Modifier.height(Spacing.sm))
            ReportPreview(report, onOpen = { onOpenReport(report) })
        }
        answer.note?.let { note ->
            Spacer(Modifier.height(Spacing.sm))
            val ext = LucentoTheme.extendedColors
            InfoNote(
                note,
                icon = Icons.Outlined.WarningAmber,
                container = if (answer.unverified) ext.warningContainer else MaterialTheme.colorScheme.surfaceContainer,
                content = if (answer.unverified) ext.warning else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (answer.offersAi) {
            Spacer(Modifier.height(Spacing.sm))
            LucentoPrimaryButton(text = "Turn on AI answers", icon = Icons.Outlined.AutoAwesome, onClick = onTurnOnAi)
        }
        answer.retry?.let { question ->
            Spacer(Modifier.height(Spacing.sm))
            ChoicePill(label = "Ask Gemini again", selected = false, onClick = { onFollowUp(question) }, role = androidx.compose.ui.semantics.Role.Button)
        }
        if (answer.followUps.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.sm))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                answer.followUps.forEach { q -> ChoicePill(label = q, selected = false, onClick = { onFollowUp(q) }, role = androidx.compose.ui.semantics.Role.Button) }
            }
        }
    }
}

@Composable
private fun annotated(parts: List<CheckedAnswer.Part>): AnnotatedString {
    val figure = SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    // With amounts hidden (round 7) answers hide them too: quoted figures and any amount the AI wrote itself.
    val hidden = com.lucento.app.core.designsystem.component.LocalAmountsHidden.current
    fun shown(text: String) = if (hidden) com.lucento.app.core.money.MoneyFormatter.maskAmountsIn(text) else text
    return buildAnnotatedString {
        parts.forEach { part -> if (part.isFigure) withStyle(figure) { append(shown(part.text)) } else append(shown(part.text)) }
    }
}

/**
 * Who worked the answer out — never leaving local calculation looking like AI, or AI looking like a calculation — and,
 * under every AI answer, that AI can be wrong (owner round 9: the adviser speaks plainly; the note sits here instead).
 */
@Composable
private fun SourceLine(source: AnswerSource) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (source == AnswerSource.GEMINI) Icons.Outlined.AutoAwesome else Icons.Outlined.PhoneAndroid,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(
                if (source == AnswerSource.GEMINI) "Answered by Gemini · bold figures worked out on this phone" else "Calculated on this phone",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (source == AnswerSource.GEMINI) {
            Text(
                AI_CAN_BE_WRONG,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp + Spacing.xs, top = Spacing.xxs),
            )
        }
    }
}

/** Under every AI answer, and in the consents. */
internal const val AI_CAN_BE_WRONG = "AI advice can be inaccurate. Check it before you act on it."

/** A report inside an answer: its headline figure and first lines, and the way into the full statement. */
@Composable
private fun ReportPreview(report: Report, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer, RoundedCornerShape(16.dp))
            .padding(Spacing.sm + Spacing.xxs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(report.kind.icon, container = colors.primaryContainer, content = colors.onPrimaryContainer, size = 32.dp, shape = RoundedCornerShape(10.dp))
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(report.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(report.period, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 2)
            }
        }
        // The headline figure on its own line, so the title never has to give way to it at large text.
        report.headline.values.firstOrNull()?.let { amount ->
            Row(Modifier.fillMaxWidth().padding(top = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Text(report.headline.label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                MoneyText(money = amount, style = AmountStyles.row.copy(fontWeight = FontWeight.Bold), color = colors.onSurface, fractionScale = 1f, minScale = 0.6f)
            }
            Hairline(modifier = Modifier.padding(vertical = Spacing.xxs))
        }
        val rows = report.sections.firstOrNull { it.lines.isNotEmpty() }?.lines.orEmpty().take(5)
        if (rows.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.xs))
            rows.forEach { line -> PreviewLine(line) }
        }
        Spacer(Modifier.height(Spacing.xs))
        LucentoSecondaryButton(text = "Open report", icon = Icons.Outlined.Description, onClick = onOpen)
    }
}

@Composable
private fun PreviewLine(line: ReportLine) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(line.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(Spacing.sm))
        line.values.lastOrNull { it != null }?.let { MoneyText(money = it, style = AmountStyles.small, color = MaterialTheme.colorScheme.onSurface, fractionScale = 1f) }
    }
}

/**
 * The full statement: month stepper (not for debts, which are as of today), the headline, every section and its total,
 * how it was calculated, and Save as PDF / Share. Built again on the phone for each month chosen.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ReportSheet(
    initial: Report,
    rebuild: (Report, YearMonth) -> Report?,
    onSave: (Report, android.net.Uri, android.content.ContentResolver, (Boolean) -> Unit) -> Unit,
    onShare: (Report, (java.io.File?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    /** Lucento Pro: the statement stays on screen; Save as PDF and Share wait for a plan that includes them. */
    pdfLocked: Boolean = false,
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
            SheetHeader(title = report.title, subtitle = "${report.period} · ${report.currencyCode}", icon = report.kind.icon)
            if (report.kind != ReportKind.DEBTS) {
                MonthStepper(
                    month = report.month,
                    canGoBack = report.month > current.minusMonths(24),
                    canGoForward = report.month < current,
                    onMonth = { m -> rebuild(report, m)?.let { report = it; message = null } },
                )
                Spacer(Modifier.height(Spacing.sm))
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
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(onClick = { onMonth(month.plusMonths(1)) }, enabled = canGoForward) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}

/** The statement on screen: the headline on a tinted panel, then each section as rows with its total. */
@Composable
private fun ReportBody(report: Report) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().background(colors.primaryContainer.copy(alpha = 0.55f), RoundedCornerShape(18.dp)).padding(Spacing.md),
    ) {
        Text(report.headline.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.onPrimaryContainer)
        report.headline.values.firstOrNull()?.let { MoneyText(money = it, style = AmountStyles.hero, color = colors.onSurface, fractionScale = 1f, minScale = 0.5f) }
        report.headline.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
    }
    report.sections.forEach { section ->
        Spacer(Modifier.height(Spacing.md))
        SectionTable(section)
    }
    Spacer(Modifier.height(Spacing.md))
    report.notes.forEach { note ->
        Text("• $note", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = Spacing.xxs))
    }
}

@Composable
private fun SectionTable(section: ReportSection) {
    LucentoCard(Modifier.fillMaxWidth(), contentPadding = Spacing.md) {
        CardOverline(section.title)
        Spacer(Modifier.height(Spacing.xs))
        if (section.lines.isEmpty() && section.total == null) {
            Text(section.emptyText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        section.lines.forEachIndexed { i, line ->
            if (i > 0) Hairline()
            TableLine(line, section.columns)
        }
        section.total?.let {
            if (section.lines.isNotEmpty()) Hairline(strong = true)
            TableLine(it, section.columns)
        }
    }
}

/** One row: a single figure sits at the end; several (budget, spent, left) sit under the label with their headers. */
@Composable
private fun TableLine(line: ReportLine, columns: List<String>) {
    val colors = MaterialTheme.colorScheme
    val weight = if (line.emphasis) FontWeight.Bold else FontWeight.Normal
    Column(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = Spacing.xs), verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(line.label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (line.emphasis) FontWeight.Bold else FontWeight.Medium))
                line.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
            }
            if (columns.size <= 1) {
                line.values.firstOrNull()?.let { amount ->
                    Spacer(Modifier.width(Spacing.sm))
                    MoneyText(money = amount, style = AmountStyles.row.copy(fontWeight = weight), color = colors.onSurface, fractionScale = 1f, minScale = 0.6f)
                }
            }
        }
        if (columns.size > 1) {
            Row(Modifier.fillMaxWidth().padding(top = Spacing.xxs)) {
                columns.forEachIndexed { i, header ->
                    Column(Modifier.weight(1f), horizontalAlignment = if (i == columns.lastIndex) Alignment.End else Alignment.Start) {
                        Text(header, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                        val value: Money? = line.values.getOrNull(i)
                        if (value != null) MoneyText(money = value, style = AmountStyles.small.copy(fontWeight = weight), color = colors.onSurface, fractionScale = 1f, minScale = 0.6f)
                        else Text("—", style = AmountStyles.small, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/**
 * The consent for AI answers: what is sent, what never is, where it goes, the daily allowance and that it can be turned
 * off. Nothing is sent until "Turn on AI answers".
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AiConsentSheet(onAccept: () -> Unit, onDismiss: () -> Unit) {
    LucentoBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter).navigationBarsPadding()) {
            SheetHeader(title = "Turn on AI answers?", subtitle = "Ask anything about your money and get advice, answered by Gemini.", icon = Icons.Outlined.AutoAwesome)
            ConsentPoint("Sent", AI_SENT)
            ConsentPoint("Never sent", AI_NEVER_SENT)
            ConsentPoint("Checked here", "Every amount in an answer is one of your figures, or a sum this phone worked out from them — shown in bold. Gemini gives you its own recommendations from your figures, but AI can misread a question or miss something only you know, so check advice before you act on it.")
            ConsentPoint("Limits", "Needs your sync account. Each account has a daily allowance, shared with Smarter reading. Turn this off any time from the menu or Settings.")
            Spacer(Modifier.height(Spacing.md))
            LucentoPrimaryButton(text = "Turn on AI answers", onClick = onAccept)
            Spacer(Modifier.height(Spacing.sm))
            LucentoSecondaryButton(text = "Not now", onClick = onDismiss)
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

/** What AI answers send (the consent sheet and Settings say the same). */
internal const val AI_SENT =
    "Your question; your figures — monthly totals, categories, balances, budgets, debts, goals and upcoming bills; projections " +
        "worked out on this phone; and your transactions from the last 60 days in your main currency (date, amount, type, " +
        "category, account and note) — to Lucento’s server, which asks Google’s Gemini. Nothing is kept there."
internal const val AI_NEVER_SENT = "Receipts or photos, older transactions, accounts in other currencies, or your sign-in details."

@Composable
private fun ConsentPoint(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
