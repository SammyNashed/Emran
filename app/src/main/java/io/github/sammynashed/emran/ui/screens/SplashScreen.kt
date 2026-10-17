package io.github.sammynashed.emran.ui.screens

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.geometry.Offset

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy

import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.sammynashed.emran.ui.design.LocalSky
import io.github.sammynashed.emran.ui.design.EmranIndigo
import io.github.sammynashed.emran.ui.design.drawEmranMark
import io.github.sammynashed.emran.ui.design.LocalSkyLayer
import io.github.sammynashed.emran.ui.design.LocalSkySize
import io.github.sammynashed.emran.ui.design.LocalSkyTime
import io.github.sammynashed.emran.ui.design.drawSkyTexture
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.drawBehind
import io.github.sammynashed.emran.ui.localization.LocalAppLanguage
import io.github.sammynashed.emran.ui.theme.InterFontFamily
import io.github.sammynashed.emran.ui.theme.ReadexFontFamily
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Settles hard and lands soft: the curve for everything that arrives. */
private val Arrive = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/**
 * "Five pillars at dawn", on the live sky. The caller wraps it in SkyHost, so it is the same
 * sky Home shows and the hand-off has no seam.
 *
 *    0 ms  the launcher icon's tile springs in (slight overshoot)
 *  180 ms  the five pillars rise one after another, like the five prayers; each stretches as it
 *          rises and squashes as it lands; a light tick when the tallest one lands
 *  520 ms  the name rises in, its letter-spacing settling; the tagline follows
 *  560 ms  a glow breathes behind the tile while a band of light sweeps across the pillars
 * 1250 ms  exit: the tile dips, then lifts and dissolves upward, and the splash fades off Home
 *
 * Home is already composed underneath. The animation waits (on the plain sky, which continues
 * the window background) until frames are arriving steadily, so cold-start work never lands
 * mid-motion; that wait is capped at 1.5 s.
 *
 * A tap skips straight to the exit. With animations off in system settings it shows the
 * finished logo briefly, then fades.
 */
@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sky = LocalSky.current
    val skyLayer = LocalSkyLayer.current
    val skySize = LocalSkySize.current
    val skyTime = LocalSkyTime.current
    val ar = LocalAppLanguage.current == "ar"
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }

    val tileScale = remember { Animatable(0.82f) }
    val tileAlpha = remember { Animatable(0f) }
    val bars = remember { List(5) { Animatable(0f) } }
    val sheen = remember { Animatable(-0.5f) }
    val halo = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }
    val tagline = remember { Animatable(0f) }
    // Exit.
    val lift = remember { Animatable(0f) }        // 0 resting, 1 gone upward
    val dip = remember { Animatable(1f) }
    val textOut = remember { Animatable(1f) }
    val whole = remember { Animatable(1f) }

    var exiting by remember { mutableStateOf(false) }
    var timeline by remember { mutableStateOf<Job?>(null) }

    fun exit() {
        if (exiting) return
        exiting = true
        timeline?.cancel()
        scope.launch {
            if (reduceMotion) {
                whole.animateTo(0f, tween(200, easing = LinearEasing))
            } else {
                // Overlapping action: the words leave first, the tile a beat later, the sky last.
                launch { textOut.animateTo(0f, tween(160, easing = FastOutLinearInEasing)) }
                dip.animateTo(0.95f, tween(90, easing = FastOutLinearInEasing))          // anticipation
                launch { dip.animateTo(1f, tween(260, easing = Arrive)) }
                launch { lift.animateTo(1f, tween(320, easing = FastOutLinearInEasing)) }
                delay(60)
                whole.animateTo(0f, tween(300, easing = LinearEasing))
            }
            onSplashFinished()
        }
    }

    LaunchedEffect(Unit) {
        timeline = launch {
            // Hold until the app has finished its first heavy frames: six in a row on time.
            var last = 0L
            var steady = 0
            val t0 = System.nanoTime()
            while (steady < 6 && System.nanoTime() - t0 < 1_500_000_000L) {
                withFrameNanos { n ->
                    steady = if (last != 0L && n - last < 24_000_000L) steady + 1 else 0
                    last = n
                }
            }
            if (reduceMotion) {
                tileScale.snapTo(1f); tileAlpha.snapTo(1f); bars.forEach { it.snapTo(1f) }
                word.snapTo(1f); tagline.snapTo(1f); halo.snapTo(0.3f)
                delay(500)
            } else {
                launch { tileAlpha.animateTo(1f, tween(220, easing = LinearEasing)) }
                launch { tileScale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 300f)) }
                bars.forEachIndexed { i, bar ->
                    launch {
                        delay(180L + i * 70L)
                        // An under-damped spring overshoots past full height (the stretch) and
                        // dips back below it on the rebound (the squash).
                        bar.animateTo(1f, spring(dampingRatio = 0.48f, stiffness = 420f))
                    }
                }
                launch { delay(180L + 2 * 70L + 150L); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                launch { delay(520); word.animateTo(1f, tween(460, easing = Arrive)) }
                launch { delay(660); tagline.animateTo(1f, tween(420, easing = Arrive)) }
                launch {
                    delay(560)
                    halo.animateTo(0.6f, tween(320, easing = Arrive))
                    halo.animateTo(0.3f, tween(500, easing = Arrive))
                }
                launch { delay(600); sheen.animateTo(1.5f, tween(520, easing = Arrive)) }
                delay(1250)
            }
            exit()
        }
    }

    val tileDp = 112.dp
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = whole.value }
            // The same sky texture Home is drawn on (rendered once by SkyHost), so this costs a copy, not a shader.
            .drawBehind { drawSkyTexture(skyLayer, sky, if (skySize.width > 0f) skySize else size, skyTime.value) }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { exit() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(
                Modifier
                    .size(tileDp * 2.2f)
                    .graphicsLayer {
                        val s = tileScale.value * dip.value * (1f + 0.22f * lift.value)
                        scaleX = s; scaleY = s
                        translationY = -36.dp.toPx() * lift.value
                        alpha = tileAlpha.value * (1f - lift.value)
                        compositingStrategy = CompositingStrategy.Offscreen
                    },
            ) {
                val c = center
                val tilePx = tileDp.toPx()
                // Glow behind the tile, in the colour the sky is glowing right now.
                if (halo.value > 0f) {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(sky.glow.copy(alpha = halo.value), EmranIndigo.copy(alpha = halo.value * 0.35f), Color.Transparent),
                            center = c, radius = size.minDimension / 2f,
                        ),
                        radius = size.minDimension / 2f, center = c,
                    )
                }
                // The launcher icon, with each pillar at its own height of the rise.
                val tl = Offset(c.x - tilePx / 2f, c.y - tilePx / 2f)
                val pillars = drawEmranMark(c, tilePx) { bars[it].value }
                // Dawn light: a soft diagonal band sweeping across the pillars only.
                if (sheen.value > -0.4f && sheen.value < 1.4f) {
                    clipPath(pillars) {
                        val sx = tl.x + tilePx * sheen.value
                        drawRect(
                            Brush.linearGradient(
                                0f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.55f), 1f to Color.Transparent,
                                start = Offset(sx - tilePx * 0.25f, tl.y + tilePx), end = Offset(sx + tilePx * 0.25f, tl.y),
                            ),
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                if (ar) "عِمران" else "Emran",
                color = sky.ink,
                fontSize = 34.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = if (ar) ReadexFontFamily else InterFontFamily,
                // The tracking settles as the word arrives (Latin only: spacing Arabic would break its joins).
                letterSpacing = if (ar) 0.em else (0.30f - 0.20f * word.value).em,
                modifier = Modifier.graphicsLayer {
                    alpha = word.value * textOut.value
                    translationY = 14.dp.toPx() * (1f - word.value) - 20.dp.toPx() * lift.value
                },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (ar) "الصلاة عِمران الروح" else "The prosperity of the soul",
                color = sky.inkSoft,
                fontSize = 15.sp,
                fontFamily = if (ar) ReadexFontFamily else InterFontFamily,
                letterSpacing = if (ar) 0.em else 0.04.em,
                modifier = Modifier.graphicsLayer {
                    alpha = tagline.value * textOut.value
                    translationY = 10.dp.toPx() * (1f - tagline.value)
                },
            )
        }
    }
}
