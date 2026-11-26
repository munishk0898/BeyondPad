package com.beyondpad.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.beyondpad.app.display.LinkState
import com.beyondpad.app.ui.theme.BP

/**
 * Line drawing of the tablet, the cable and the monitor. The monitor screen shows what the
 * tablet actually knows: a second screen, a hidden one, no signal, or nothing plugged in.
 */
@Composable
fun Stage(state: LinkState, monitorText: String, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Restart), label = "dash",
    )
    val lineColor = when (state) {
        LinkState.READY -> BP.Accent
        LinkState.HIDDEN_BY_SYSTEM -> BP.Warn
        LinkState.ADAPTER_NO_VIDEO -> BP.TextDim
        LinkState.NOTHING -> BP.TextFaint
    }

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = (w / 260f).coerceIn(2f, 4f)

        // Tablet (landscape 7:5), left
        val tw = w * 0.30f
        val th = tw * 5f / 7f
        val tl = Offset(w * 0.04f, h * 0.52f - th / 2f)
        drawRoundRect(BP.Surface, tl, Size(tw, th), CornerRadius(tw * 0.06f))
        drawRoundRect(BP.TextDim, tl, Size(tw, th), CornerRadius(tw * 0.06f), style = Stroke(stroke))
        // Tablet screen content: a few app tiles
        val pad = tw * 0.10f
        val tile = (tw - pad * 2) / 5f
        for (r in 0 until 2) for (c in 0 until 4) {
            drawRoundRect(
                BP.Outline, Offset(tl.x + pad + c * tile * 1.25f, tl.y + pad + r * tile * 1.25f),
                Size(tile * 0.8f, tile * 0.8f), CornerRadius(tile * 0.2f),
            )
        }

        // Monitor (16:9) with stand, right
        val mw = w * 0.50f
        val mh = mw * 9f / 16f
        val ml = Offset(w * 0.46f, h * 0.44f - mh / 2f)
        val screenColor = when (state) {
            LinkState.READY -> BP.AccentSoft
            LinkState.HIDDEN_BY_SYSTEM -> Color(0xFF2A2318)
            else -> Color(0xFF0A0B0D)
        }
        drawRoundRect(screenColor, ml, Size(mw, mh), CornerRadius(mw * 0.02f))
        drawRoundRect(if (state == LinkState.NOTHING) BP.TextFaint else BP.TextDim, ml, Size(mw, mh), CornerRadius(mw * 0.02f), style = Stroke(stroke))
        val neckX = ml.x + mw / 2f
        drawLine(BP.TextDim, Offset(neckX, ml.y + mh), Offset(neckX, ml.y + mh + h * 0.10f), stroke * 1.6f, StrokeCap.Round)
        drawLine(BP.TextDim, Offset(neckX - mw * 0.14f, ml.y + mh + h * 0.10f), Offset(neckX + mw * 0.14f, ml.y + mh + h * 0.10f), stroke * 1.6f, StrokeCap.Round)

        if (state == LinkState.HIDDEN_BY_SYSTEM) {
            // Faint copy of the tablet tiles: what a system-level mirror looks like.
            val scale = mh * 0.55f / th
            val cw = tw * scale; val ch = th * scale
            val cl = Offset(ml.x + (mw - cw) / 2f, ml.y + (mh - ch) / 2f - mh * 0.06f)
            drawRoundRect(BP.Warn.copy(alpha = 0.35f), cl, Size(cw, ch), CornerRadius(cw * 0.05f), style = Stroke(stroke))
        }

        val label = measurer.measure(
            monitorText,
            TextStyle(color = if (state == LinkState.READY) BP.Text else BP.TextDim, fontSize = 15.sp, fontWeight = FontWeight.Medium),
        )
        drawText(label, topLeft = Offset(ml.x + (mw - label.size.width) / 2f, ml.y + mh * 0.80f - label.size.height / 2f))

        // Cable from the tablet's side port into the bottom of the monitor
        val from = Offset(tl.x + tw, tl.y + th * 0.5f)
        val to = Offset(ml.x + mw * 0.18f, ml.y + mh)
        val path = Path().apply {
            moveTo(from.x, from.y)
            cubicTo(from.x + w * 0.08f, from.y, to.x - w * 0.02f, to.y + h * 0.22f, to.x, to.y)
        }
        val dash = w * 0.012f
        val effect = when (state) {
            LinkState.READY -> PathEffect.dashPathEffect(floatArrayOf(dash * 3, dash), -pulse * dash * 4)
            LinkState.HIDDEN_BY_SYSTEM -> null
            else -> PathEffect.dashPathEffect(floatArrayOf(dash, dash))
        }
        drawPath(path, lineColor, style = Stroke(stroke * 1.4f, cap = StrokeCap.Round, pathEffect = effect))
        plug(from, lineColor, stroke)
        if (state != LinkState.NOTHING) plug(to, lineColor, stroke)
    }
}

private fun DrawScope.plug(at: Offset, color: Color, stroke: Float) {
    drawCircle(color, radius = stroke * 2.6f, center = at)
}
