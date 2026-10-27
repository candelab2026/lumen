package com.lumen.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import kotlin.math.cos
import kotlin.math.sin

/** Icone semplici disegnate a mano (nessuna libreria di icone): 0 Per te, 1 TV Live, 2 Film, 3 Serie, 4 Guida, 5 Impostazioni. */
@Composable
fun TabIcon(index: Int, color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.width
        val stroke = Stroke(s * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun o(x: Float, y: Float) = Offset(x * s, y * s)
        fun sz(w: Float, h: Float) = Size(w * s, h * s)
        val r = CornerRadius(s * 0.12f)
        when (index) {
            0 -> { // casa
                val roof = Path().apply {
                    moveTo(0.08f * s, 0.5f * s); lineTo(0.5f * s, 0.12f * s); lineTo(0.92f * s, 0.5f * s)
                }
                drawPath(roof, color, style = stroke)
                drawRoundRect(color, o(0.2f, 0.46f), sz(0.6f, 0.42f), CornerRadius(s * 0.06f), style = stroke)
            }
            1 -> { // televisore
                drawRoundRect(color, o(0.08f, 0.16f), sz(0.84f, 0.56f), r, style = stroke)
                drawLine(color, o(0.3f, 0.88f), o(0.7f, 0.88f), s * 0.09f, StrokeCap.Round)
            }
            2 -> { // pellicola
                drawRoundRect(color, o(0.12f, 0.14f), sz(0.76f, 0.72f), r, style = stroke)
                drawLine(color, o(0.32f, 0.14f), o(0.32f, 0.86f), s * 0.07f)
                drawLine(color, o(0.68f, 0.14f), o(0.68f, 0.86f), s * 0.07f)
                drawLine(color, o(0.12f, 0.5f), o(0.88f, 0.5f), s * 0.07f)
            }
            3 -> { // due schermate sovrapposte
                drawRoundRect(color, o(0.3f, 0.1f), sz(0.62f, 0.5f), r, style = stroke)
                drawRoundRect(color, o(0.08f, 0.34f), sz(0.62f, 0.54f), r, style = stroke)
            }
            4 -> { // griglia
                drawRoundRect(color, o(0.1f, 0.14f), sz(0.8f, 0.72f), r, style = stroke)
                drawLine(color, o(0.1f, 0.38f), o(0.9f, 0.38f), s * 0.07f)
                drawLine(color, o(0.1f, 0.62f), o(0.9f, 0.62f), s * 0.07f)
                drawLine(color, o(0.38f, 0.38f), o(0.38f, 0.86f), s * 0.07f)
            }
            else -> { // ingranaggio
                val c = Offset(s / 2, s / 2)
                drawCircle(color, s * 0.17f, c, style = stroke)
                for (k in 0 until 8) {
                    val a = k * Math.PI / 4
                    val dx = cos(a).toFloat()
                    val dy = sin(a).toFloat()
                    drawLine(color, c + Offset(dx, dy) * (s * 0.3f), c + Offset(dx, dy) * (s * 0.43f), s * 0.1f, StrokeCap.Round)
                }
                drawCircle(color, s * 0.3f, c, style = Stroke(s * 0.07f))
            }
        }
    }
}
