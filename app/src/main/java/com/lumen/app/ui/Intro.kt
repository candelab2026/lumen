package com.lumen.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lumen.app.R
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

val Violet = Color(0xFF7C3AED)

/** Simbolo del logo: schermo TV con gradiente blu-viola e triangolo play. */
@Composable
fun LogoMark(size: Dp, playProgress: Float = 1f) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val grad = Brush.linearGradient(listOf(Accent, Violet), Offset.Zero, Offset(w, w))
        drawRoundRect(grad, Offset(0f, 0f), Size(w, w * 0.82f), CornerRadius(w * 0.2f))
        drawRoundRect(grad, Offset(w * 0.28f, w * 0.88f), Size(w * 0.44f, w * 0.08f), CornerRadius(w * 0.04f))
        drawRoundRect(
            Color(0x33FFFFFF), Offset(w * 0.07f, w * 0.07f), Size(w * 0.86f, w * 0.68f),
            CornerRadius(w * 0.14f), style = Stroke(w * 0.025f),
        )
        val cx = w * 0.53f
        val cy = w * 0.41f
        val r = w * 0.2f * playProgress
        val p = Path().apply {
            moveTo(cx - r * 0.8f, cy - r)
            lineTo(cx - r * 0.8f, cy + r)
            lineTo(cx + r, cy)
            close()
        }
        drawPath(p, Color.White)
    }
}

@Composable
fun Logo(size: Int = 28) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((size / 3).dp)) {
        LogoMark((size * 1.3f).dp)
        Row {
            Text("LUMEN", color = TextMain, fontSize = size.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        }
    }
}

/** Animazione iniziale: il simbolo compare con un rimbalzo, il play si apre, poi esce il nome. */
@Composable
fun IntroScreen(expiryText: String? = null) {
    val expiry = remember { Animatable(0f) }
    LaunchedEffect(expiryText) {
        if (expiryText != null) {
            delay(2200)
            expiry.animateTo(1f, tween(600))
        }
    }
    val scale = remember { Animatable(0.4f) }
    val markAlpha = remember { Animatable(0f) }
    val play = remember { Animatable(0f) }
    val text = remember { Animatable(0f) }
    val sub = remember { Animatable(0f) }

    LaunchedEffect(Unit) { markAlpha.animateTo(1f, tween(350)) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(Unit) {
        delay(450)
        play.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        delay(800)
        text.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        delay(1300)
        sub.animateTo(1f, tween(500))
    }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "ring")

    Box(
        Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF1B2547), Bg))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(200.dp)) {
                    val rad = this.size.minDimension / 2 * (0.5f + ring * 0.5f)
                    drawCircle(Accent.copy(alpha = (1f - ring) * 0.35f * markAlpha.value), rad, style = Stroke(3f))
                }
                Box(Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }.alpha(markAlpha.value)) {
                    LogoMark(110.dp, play.value)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.graphicsLayer { translationY = (1f - text.value) * 24f }.alpha(text.value)) {
                val spacing = (4 + (1f - text.value) * 10).sp
                Text("LUMEN", color = TextMain, fontSize = 48.sp, fontWeight = FontWeight.Bold, letterSpacing = spacing)
            }
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.tagline), color = TextDim, fontSize = 14.sp, letterSpacing = 3.sp, modifier = Modifier.alpha(sub.value))
            Spacer(Modifier.height(22.dp))
            Text(
                expiryText ?: " ", color = Gold, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.alpha(expiry.value),
            )
        }
        Text(
            "v${com.lumen.app.BuildConfig.VERSION_NAME}",
            color = TextDim, fontSize = 12.sp, letterSpacing = 1.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp).alpha(sub.value),
        )
    }
}
