package com.lucento.app.core.designsystem.component

import android.content.res.Resources
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lucento.app.R
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Motion
import com.lucento.app.core.designsystem.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParser

/** One shape of the symbol exactly as the approved drawable defines it: its path, colour and the group offset. */
private class MarkPart(val path: Path, val color: Color)

private class MarkArtwork(val viewport: Float, val dx: Float, val dy: Float, val parts: List<MarkPart>)

/**
 * Reads the Rising Crest symbol's own paths from `res/drawable/lucento_symbol.xml`, so the animation draws the approved
 * artwork unchanged — same shapes, same colours — and only stages how it appears. Nothing is redrawn or re-tinted.
 */
// A vector drawable is compiled XML, so getXml reads it; lint only expects R.xml ids there.
@android.annotation.SuppressLint("ResourceType")
private fun loadArtwork(resources: Resources): MarkArtwork {
    val parser = resources.getXml(R.drawable.lucento_symbol)
    val ns = "http://schemas.android.com/apk/res/android"
    var viewport = 600f
    var dx = 0f
    var dy = 0f
    val parts = ArrayList<MarkPart>()
    while (parser.next() != XmlPullParser.END_DOCUMENT) {
        if (parser.eventType != XmlPullParser.START_TAG) continue
        when (parser.name) {
            "vector" -> viewport = parser.getAttributeValue(ns, "viewportWidth")?.toFloatOrNull() ?: viewport
            "group" -> {
                dx = parser.getAttributeValue(ns, "translateX")?.toFloatOrNull() ?: 0f
                dy = parser.getAttributeValue(ns, "translateY")?.toFloatOrNull() ?: 0f
            }
            "path" -> {
                val data = parser.getAttributeValue(ns, "pathData") ?: continue
                val color = parser.getAttributeValue(ns, "fillColor")?.let { Color(android.graphics.Color.parseColor(it)) } ?: continue
                parts += MarkPart(PathParser().parsePathString(data).toPath(), color)
            }
        }
    }
    return MarkArtwork(viewport, dx, dy, parts)
}

/**
 * The Lucento symbol rising into place: the three bars grow up from the baseline one after another, left to right,
 * then the dot lands. With [loop] it rises, holds and starts again — the sign-in indicator. Reduced motion shows the
 * finished symbol and nothing moves.
 */
@Composable
fun RisingMark(modifier: Modifier = Modifier, size: Dp = 96.dp, loop: Boolean = false, onRisen: () -> Unit = {}) {
    val resources = LocalContext.current.resources
    val art = remember(resources) { loadArtwork(resources) }
    val still = LucentoTheme.reducedMotion
    // One progress per part: 0 = not yet visible, 1 = in place.
    val progress = remember(art) { art.parts.map { Animatable(if (still) 1f else 0f) } }
    LaunchedEffect(art, still, loop) {
        if (still) {
            onRisen()
            return@LaunchedEffect
        }
        do {
            progress.forEach { it.snapTo(0f) }
            progress.forEachIndexed { i, p ->
                launch {
                    delay(i * Motion.fast.toLong())
                    p.animateTo(1f, tween(durationMillis = Motion.slow, easing = FastOutSlowInEasing))
                }
            }
            delay((progress.size - 1) * Motion.fast.toLong() + Motion.slow.toLong())
            onRisen()
            if (loop) delay(700)
        } while (loop)
    }
    Canvas(modifier.size(size).semantics { contentDescription = "Lucento" }) {
        val s = this.size.minDimension / art.viewport
        scale(s, s, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            translate(art.dx, art.dy) {
                art.parts.forEachIndexed { i, part ->
                    val t = progress[i].value
                    if (t <= 0f) return@forEachIndexed
                    val bounds = part.path.getBounds()
                    if (i < art.parts.lastIndex) {
                        // A bar grows upward from its own base: reveal it from the bottom edge.
                        val top = bounds.bottom - bounds.height * t
                        clipRect(left = bounds.left - 2f, top = top, right = bounds.right + 2f, bottom = bounds.bottom + 2f) {
                            drawPath(part.path, part.color)
                        }
                    } else {
                        // The dot drops in from above and settles.
                        translate(0f, -40f * (1f - t)) { drawPath(part.path, part.color, alpha = t) }
                    }
                }
            }
        }
    }
}

/**
 * The launch moment: the symbol rises bar by bar, then the "lucento" wordmark fades up beneath it; [onFinished] runs a
 * beat later. Shown once per cold start over the theme background.
 */
@Composable
fun LaunchAnimation(onFinished: () -> Unit) {
    val word = remember { Animatable(0f) }
    val still = LucentoTheme.reducedMotion
    LaunchedEffect(Unit) {
        if (still) {
            word.snapTo(1f)
            delay(300)
            onFinished()
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            RisingMark(size = 112.dp, onRisen = {
                if (!still) {
                    scope.launch {
                        word.animateTo(1f, tween(Motion.slow, easing = FastOutSlowInEasing))
                        delay(260)
                        onFinished()
                    }
                }
            })
            Spacer(Modifier.height(Spacing.md))
            LucentoWordmark(
                height = 36.dp,
                modifier = Modifier.graphicsLayer {
                    alpha = word.value
                    translationY = 16.dp.toPx() * (1f - word.value)
                },
            )
        }
    }
}

/** Shown while an account connects: the rising symbol on a loop and what is happening, in words. */
@Composable
fun ConnectingIndicator(text: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        RisingMark(size = 72.dp, loop = true)
        Spacer(Modifier.height(Spacing.sm))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
