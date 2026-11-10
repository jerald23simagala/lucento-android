package com.lucento.app.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.theme.AwardArtwork

/**
 * The Lucento Pro member's coin (owner, 2026-11-09): struck in the award gold with the Rising Crest's three rising bars
 * engraved on it. It marks a plan the person holds — never something locked, which is the blue "Pro" pill. Decorative:
 * whoever places it says "Lucento Pro" in words or in the element's description.
 */
@Composable
fun ProCrest(modifier: Modifier = Modifier, size: Dp = 18.dp) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val edge = r * 0.12f
        drawCircle(
            Brush.radialGradient(
                0f to AwardArtwork.GoldHighlight,
                0.45f to AwardArtwork.GoldLight,
                0.8f to AwardArtwork.Gold,
                1f to AwardArtwork.GoldDeep,
                center = Offset(r * 0.7f, r * 0.6f),
                radius = r * 1.5f,
            ),
            radius = r,
        )
        drawCircle(AwardArtwork.GoldEdge, radius = r - edge / 2f, style = Stroke(edge))
        // Three rising bars on a 20-unit grid (the same drawing as the proposal page).
        val u = this.size.minDimension / 20f
        val bars = listOf(5.2f to 10.6f, 8.8f to 8.2f, 12.4f to 5.4f)
        for ((x, top) in bars) {
            drawRoundRect(
                AwardArtwork.Engraving,
                topLeft = Offset(x * u, top * u),
                size = Size(2.4f * u, (14.8f - top) * u),
                cornerRadius = CornerRadius(1f * u),
            )
        }
    }
}

/**
 * "Pro" or "Lifetime" after the person's name in the profile menu: the [ProCrest] and the word on a gold chip. Read as
 * "Lucento Pro" (or "Pro Lifetime") by TalkBack.
 */
@Composable
fun MemberChip(label: String, modifier: Modifier = Modifier) {
    val spoken = if (label == "Pro") "Lucento Pro" else "Pro $label"
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.linearGradient(listOf(AwardArtwork.GoldHighlight, AwardArtwork.GoldLight, AwardArtwork.Gold)))
            .border(1.dp, AwardArtwork.GoldEdge.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(start = 4.dp, end = 8.dp, top = 2.dp, bottom = 2.dp)
            .clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ProCrest(size = 14.dp)
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = AwardArtwork.Engraving,
            maxLines = 1,
        )
    }
}

/** The [ProCrest] ringed in [ring] (the page colour) so it stands apart from a photo or initials behind it. */
@Composable
internal fun RingedCrest(ring: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    ProCrest(
        modifier
            .clip(CircleShape)
            .background(ring)
            .padding(1.5.dp),
        size = size - 3.dp,
    )
}
