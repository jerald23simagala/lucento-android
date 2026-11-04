package com.lucento.app.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing

/*
 * Lucento's conversation pieces, shared by the transaction composer and Ask Lucento so both read as one product:
 * Lucento speaking (mascot + speech bubble), its avatar, the day divider, the user's bubble, Lucento's reply card, the
 * typing dots, and the input bar's field, tools and send button.
 */

/** Lucento introducing a conversation: the mascot beside a speech bubble whose tail points at it. */
@Composable
fun MascotSpeech(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    mood: MascotMood = MascotMood.Greeting,
    mascotSize: Dp = 72.dp,
) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LucentoMascot(size = mascotSize, mood = mood)
        Spacer(Modifier.width(Spacing.sm))
        Surface(
            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp),
            color = if (LucentoTheme.isDark) colors.surfaceContainerHigh else colors.surfaceContainerLowest,
            border = BorderStroke(1.dp, LucentoTheme.extendedColors.borderDefault),
            modifier = Modifier.weight(1f),
        ) {
            Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm + Spacing.xxs)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.semantics { heading() })
                Spacer(Modifier.padding(top = 2.dp))
                Text(body, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

/** Lucento's small round avatar beside its replies. */
@Composable
fun ChatAvatar(modifier: Modifier = Modifier, size: Dp = 36.dp, mood: MascotMood = MascotMood.Happy) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        LucentoMascot(size = size * 0.86f, animate = false, mood = mood)
    }
}

/** "Today" between two hairlines. */
@Composable
fun ChatDayDivider(label: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = colors.outlineVariant)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = Spacing.sm)
                .clip(RoundedCornerShape(50))
                .background(colors.surfaceContainer)
                .padding(horizontal = Spacing.md, vertical = Spacing.xxs),
        )
        HorizontalDivider(Modifier.weight(1f), color = colors.outlineVariant)
    }
}

/** A message's time ("6:49 PM"), small and muted. */
@Composable
fun ChatTimestamp(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier, maxLines = 1)
}

/** The user's message: a pale-blue bubble on the trailing side, its tail toward the edge. [top] adds a label above the text. */
@Composable
fun UserChatBubble(text: String, modifier: Modifier = Modifier, top: (@Composable ColumnScope.() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = Spacing.md + Spacing.xxs, vertical = Spacing.sm + Spacing.xxs)) {
            top?.invoke(this)
            Text(text, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
        }
    }
}

/** Lucento's reply: its avatar, then a card holding the answer. [footer] sits under the card (a time, a source). */
@Composable
fun LucentoReply(
    modifier: Modifier = Modifier,
    mood: MascotMood = MascotMood.Happy,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dark = LucentoTheme.isDark
    val shape = RoundedCornerShape(topStart = 6.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 22.dp)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        ChatAvatar(mood = mood)
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Surface(
                shape = shape,
                color = if (dark) colors.surfaceContainerHigh else colors.surfaceContainerLowest,
                border = BorderStroke(1.dp, LucentoTheme.extendedColors.borderDefault),
                modifier = if (dark) Modifier else Modifier.shadow(
                    elevation = 6.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = LucentoTheme.extendedColors.shadow.copy(alpha = 0.06f),
                    spotColor = LucentoTheme.extendedColors.shadow.copy(alpha = 0.12f),
                ),
            ) {
                Column(Modifier.padding(Spacing.md), content = content)
            }
            footer?.let {
                Spacer(Modifier.padding(top = Spacing.xxs))
                Box(Modifier.padding(start = Spacing.sm)) { it() }
            }
        }
    }
}

/** Three dots rising in turn while Lucento works an answer out; still when animations are off. */
@Composable
fun TypingDots(modifier: Modifier = Modifier, description: String = "Lucento is thinking") {
    val reduced = LucentoTheme.reducedMotion
    val transition = rememberInfiniteTransition(label = "typing")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1_200), RepeatMode.Restart),
        label = "typing-phase",
    )
    val color = MaterialTheme.colorScheme.primary
    Row(
        modifier.semantics { contentDescription = description }.padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { i ->
            val lit = !reduced && phase.toInt() == i
            Box(
                Modifier
                    .padding(bottom = if (lit) 4.dp else 0.dp)
                    .size(8.dp)
                    .alpha(if (reduced || lit) 1f else 0.35f)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/** The input bar's surface: a raised, rounded panel holding the tools, the field and send, in one row. */
@Composable
fun ChatInputBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dark = LucentoTheme.isDark
    val shape = RoundedCornerShape(28.dp)
    Surface(
        shape = shape,
        color = if (dark) colors.surfaceContainerHigh else colors.surfaceContainerLowest,
        border = BorderStroke(1.dp, LucentoTheme.extendedColors.borderDefault),
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (dark) Modifier else Modifier.shadow(
                    elevation = 10.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = LucentoTheme.extendedColors.shadow.copy(alpha = 0.06f),
                    spotColor = LucentoTheme.extendedColors.shadow.copy(alpha = 0.14f),
                ),
            ),
    ) {
        Row(
            Modifier.padding(Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.Bottom,
            content = content,
        )
    }
}

/** The height of every control in [ChatInputBar], so tools, field and send line up. */
val ChatControlSize = 48.dp

/** A tool in the input bar (scan, voice): a soft blue rounded square. */
@Composable
fun ChatToolButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.size(ChatControlSize),
        shape = RoundedCornerShape(14.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary,
        ),
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(22.dp))
    }
}

/** Send: a filled brand circle, faded until there's something to send. */
@Composable
fun ChatSendButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, description: String = "Send") {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(ChatControlSize),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f),
            disabledContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = description, modifier = Modifier.size(20.dp))
    }
}

/** The pill-shaped message field: one control tall at rest, growing to four lines. */
@Composable
fun ChatMessageField(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    placeholder: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
        maxLines = 4,
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { onSend() }),
        modifier = modifier.semantics { contentDescription = description },
        decorationBox = { inner ->
            val shape = RoundedCornerShape(ChatControlSize / 2)
            Box(
                Modifier
                    .background(if (LucentoTheme.isDark) colors.surfaceContainer else colors.surfaceContainerLowest, shape)
                    .border(BorderStroke(1.dp, colors.outline), shape)
                    .heightIn(min = ChatControlSize)
                    .padding(horizontal = Spacing.md + Spacing.xxs, vertical = Spacing.sm),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                inner()
            }
        },
    )
}
