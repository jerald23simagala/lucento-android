package com.lucento.app.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * A drawn EMV-style contact chip, so the wallet reads as a card at a glance (owner decision 2026-11-02). Decorative only:
 * no card number, expiry or network mark is ever shown, and the chip is hidden from accessibility services.
 */
@Composable
fun CardChip(modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 42.dp, height = 32.dp)) {
        val r = CornerRadius(5.dp.toPx())
        drawRoundRect(Brush.linearGradient(listOf(ChipLight, ChipMid, ChipDark, ChipLight)), cornerRadius = r)
        val line = ChipLine
        val stroke = 1.dp.toPx()
        val w = size.width
        val h = size.height
        // Contact pads: a centre pad and the split lines of a real chip.
        drawRoundRect(line, topLeft = Offset(w * 0.34f, h * 0.24f), size = Size(w * 0.32f, h * 0.52f), cornerRadius = CornerRadius(3.dp.toPx()), style = Stroke(stroke))
        drawLine(line, Offset(0f, h * 0.36f), Offset(w * 0.34f, h * 0.36f), stroke)
        drawLine(line, Offset(0f, h * 0.64f), Offset(w * 0.34f, h * 0.64f), stroke)
        drawLine(line, Offset(w * 0.66f, h * 0.36f), Offset(w, h * 0.36f), stroke)
        drawLine(line, Offset(w * 0.66f, h * 0.64f), Offset(w, h * 0.64f), stroke)
        drawLine(line, Offset(w * 0.5f, 0f), Offset(w * 0.5f, h * 0.24f), stroke)
        drawLine(line, Offset(w * 0.5f, h * 0.76f), Offset(w * 0.5f, h), stroke)
        drawRoundRect(ChipEdge, cornerRadius = r, style = Stroke(stroke))
    }
}

// Metallic gold, the same on every face (like a real card), with darker engraving.
private val ChipLight = Color(0xFFF3DC9A)
private val ChipMid = Color(0xFFD9B45E)
private val ChipDark = Color(0xFFB98E3A)
private val ChipLine = Color(0xFF8C6A26).copy(alpha = 0.75f)
private val ChipEdge = Color(0xFF7A5A1E).copy(alpha = 0.55f)
