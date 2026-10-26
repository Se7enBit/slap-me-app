package com.noikonomist.slapme.slap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noikonomist.slapme.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Timing of the slap, in ms from the start. */
object SlapTimeline {
    const val SWOOP = 300
    const val FLASH = 250
    const val SHAKE = 420
    const val FADE_START = 1100
    const val FADE = 400
    const val TOTAL = FADE_START + FADE

    /** The overlay is removed after this even if the animation never finishes. */
    const val WATCHDOG = 3000L
}

private val Ink = Color(0xFF1B1B1F)
private val ComicYellow = Color(0xFFFFD23F)
private val ComicOrange = Color(0xFFFFA62B)
private val FlashRed = Color(0xFFE53935)

/**
 * The cartoon slap: hand swoops in from the right → impact (comic background, red flash, "SLAP!"
 * burst, screen shake) → fade out. [onImpact] fires when the hand lands, [onFinished] after the fade.
 */
@Composable
fun SlapAnimation(onImpact: () -> Unit, onFinished: () -> Unit) {
    val impactCallback by rememberUpdatedState(onImpact)
    val finishedCallback by rememberUpdatedState(onFinished)

    val swoop = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    val flash = remember { Animatable(0f) }
    val shake = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    var impacted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        swoop.animateTo(1f, tween(SlapTimeline.SWOOP, easing = FastOutLinearInEasing))
        impacted = true
        impactCallback()
        launch {
            flash.snapTo(0.6f)
            flash.animateTo(0f, tween(SlapTimeline.FLASH, easing = LinearOutSlowInEasing))
        }
        launch { burst.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium)) }
        launch { shake.animateTo(1f, tween(SlapTimeline.SHAKE, easing = LinearEasing)) }
        delay((SlapTimeline.FADE_START - SlapTimeline.SWOOP).toLong())
        alpha.animateTo(0f, tween(SlapTimeline.FADE))
        finishedCallback()
    }

    BoxWithConstraints(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value }) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        // Sizes and offsets follow the shorter side, so portrait and landscape look the same.
        val base = minOf(maxWidth, maxHeight)
        val basePx = with(density) { base.toPx() }
        val shakePx = with(density) { 26.dp.toPx() }
        val handSize = base * 0.78f
        val burstSize = base * 0.9f

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = shake.value
                    if (p > 0f && p < 1f) {
                        val decay = 1f - p
                        translationX = shakePx * decay * sin(p * 9 * 2 * PI).toFloat()
                        translationY = shakePx * 0.6f * decay * cos(p * 7 * 2 * PI).toFloat()
                    }
                },
        ) {
            if (impacted) {
                ComicBackground()
                // Below and left of the hand, so the hand stays visible on top of it.
                SlapBurst(
                    Modifier
                        .align(Alignment.Center)
                        .size(burstSize)
                        .graphicsLayer {
                            translationX = -0.06f * basePx
                            translationY = 0.24f * basePx
                            scaleX = burst.value
                            scaleY = burst.value
                            rotationZ = -8f
                        },
                )
            } else {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f * swoop.value)))
            }

            Image(
                painter = painterResource(R.drawable.slap_hand),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(handSize)
                    .graphicsLayer {
                        val p = swoop.value
                        // Lands up and to the right of center.
                        translationX = (1f - p) * widthPx + 0.14f * basePx
                        translationY = (1f - p) * -basePx * 0.25f - 0.24f * basePx
                        rotationZ = -55f + 40f * p
                        val squash = if (impacted) 1f + 0.08f * (1f - shake.value) else 1f
                        scaleX = squash
                        scaleY = 2f - squash
                    },
            )

            Box(Modifier.fillMaxSize().background(FlashRed.copy(alpha = flash.value)))
        }
    }
}

@Composable
private fun ComicBackground() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(ComicYellow)
        val rays = 18
        val radius = size.maxDimension
        for (i in 0 until rays step 2) {
            val a0 = 2 * PI * i / rays
            val a1 = 2 * PI * (i + 1) / rays
            val path = Path().apply {
                moveTo(center.x, center.y)
                lineTo(center.x + radius * cos(a0).toFloat(), center.y + radius * sin(a0).toFloat())
                lineTo(center.x + radius * cos(a1).toFloat(), center.y + radius * sin(a1).toFloat())
                close()
            }
            drawPath(path, ComicOrange)
        }
    }
}

@Composable
private fun SlapBurst(modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val star = starPath(points = 14, outer = size.minDimension / 2f, inner = size.minDimension / 2.9f)
            drawPath(star, Color.White)
            drawPath(star, Ink, style = Stroke(width = 8.dp.toPx(), join = StrokeJoin.Round))
        }
        val text = stringResource(R.string.slap_burst)
        val style = TextStyle(fontSize = 76.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
        Text(text, style = style.copy(color = Ink, drawStyle = Stroke(width = 22f, join = StrokeJoin.Round)))
        Text(text, style = style.copy(color = FlashRed))
    }
}

private fun DrawScope.starPath(points: Int, outer: Float, inner: Float): Path = Path().apply {
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) outer else inner
        val a = PI * i / points - PI / 2
        val p = Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}
