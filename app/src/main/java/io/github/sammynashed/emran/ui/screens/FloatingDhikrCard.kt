package io.github.sammynashed.emran.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sammynashed.emran.service.FloatingDhikr
import io.github.sammynashed.emran.ui.design.EmranIndigo
import io.github.sammynashed.emran.ui.design.EmranOrange
import io.github.sammynashed.emran.ui.design.drawEmranMark
import io.github.sammynashed.emran.ui.theme.InterFontFamily
import io.github.sammynashed.emran.ui.theme.ReadexFontFamily

/** Motion values the overlay window drives (all 0..1). */
class CardMotion(
    val appear: MutableFloatState,
    val held: MutableFloatState,
    val toX: MutableFloatState,
    val remaining: MutableFloatState,
    val leaving: MutableState<Boolean>,
)

private fun seg(x: Float, a: Float, b: Float) = ((x - a) / (b - a)).coerceIn(0f, 1f)
private fun easeOut(x: Float) = 1f - (1f - x) * (1f - x) * (1f - x)
private fun easeBack(x: Float): Float { val c1 = 1.4f; val c3 = c1 + 1f; val t = x - 1f; return 1f + c3 * t * t * t + c1 * t * t }

private val CARD_BG = Color(0xFF0C0C12)
private val CARD_SHAPE = RoundedCornerShape(26.dp)

/**
 * The floating card: Emran's dark tile with its five pillars rising (the same drawing as the app icon
 * and splash), the dhikr in the Mushaf's Hafs face, its source, and a thin indigo-to-orange line that
 * runs down while the card is up. Calm on purpose: one soft entrance, one slow light sweep, then still.
 */
@Composable
fun FloatingDhikrCard(
    dhikr: FloatingDhikr.Dhikr,
    lang: String,
    motion: CardMotion,
    widthPx: Int,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ar = lang == "ar"
    val density = LocalDensity.current
    val a = motion.appear.floatValue
    val held = motion.held.floatValue
    val toX = motion.toX.floatValue
    val cardIn = if (motion.leaving.value) easeOut(seg(a, 0f, 1f)) else easeBack(seg(a, 0f, 0.55f))
    val textIn = easeOut(seg(a, 0.42f, 0.85f))
    val sweep = seg(a, 0.5f, 1f)

    val breathe = rememberInfiniteTransition(label = "breathe")
    val glow by breathe.animateFloat(
        0.22f, 0.42f, infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow",
    )

    CompositionLocalProvider(LocalLayoutDirection provides if (ar) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Box(modifier.padding(14.dp)) {
            Box(
                Modifier
                    .width(with(density) { widthPx.toDp() })
                    .graphicsLayer {
                        val s = (0.94f + 0.06f * cardIn) * (1f + 0.035f * held) * (1f - 0.5f * toX)
                        scaleX = s; scaleY = s
                        alpha = (seg(a, 0f, 0.35f)) * (1f - 0.35f * toX)
                        translationY = (1f - cardIn) * -22.dp.toPx()
                    }
                    .shadow((14 + 10 * held).dp, CARD_SHAPE, ambientColor = EmranIndigo.copy(alpha = 0.5f), spotColor = Color.Black)
                    .clip(CARD_SHAPE)
                    .background(CARD_BG)
                    .background(Brush.verticalGradient(listOf(EmranIndigo.copy(alpha = 0.16f), EmranOrange.copy(alpha = 0.10f))))
                    .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.26f + 0.2f * held), Color.White.copy(alpha = 0.05f))), CARD_SHAPE)
                    .drawWithContent {
                        drawContent()
                        if (sweep in 0.001f..0.999f) {           // one slow diagonal light, then never again
                            val x = -size.width * 0.5f + size.width * 2f * sweep
                            drawRect(
                                Brush.linearGradient(
                                    listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent),
                                    start = Offset(x - size.width * 0.25f, 0f), end = Offset(x + size.width * 0.25f, size.height),
                                ),
                            )
                        }
                    }
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onTap),
            ) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 18.dp, top = 14.dp, bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(46.dp)) {
                            Canvas(Modifier.size(46.dp)) {
                                drawCircle(Brush.radialGradient(listOf(EmranIndigo.copy(alpha = glow), Color.Transparent)), radius = size.minDimension * 0.75f)
                            }
                            Canvas(Modifier.size(38.dp)) {
                                drawEmranMark(center, size.minDimension) { i -> easeBack(seg(a, 0.18f + i * 0.07f, 0.5f + i * 0.07f)) }
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f).graphicsLayer { alpha = textIn; translationY = (1f - textIn) * 8.dp.toPx() }) {
                            Text(
                                if (ar) "عِمران · ذِكْر" else "Emran · Dhikr",
                                color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Medium,
                                fontFamily = if (ar) ReadexFontFamily else InterFontFamily,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                dhikr.ar, color = Color.White, fontSize = 21.sp, lineHeight = 36.sp, fontFamily = HafsFontFamily,
                                maxLines = 3, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Start,
                                style = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Rtl),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (!ar && dhikr.en != dhikr.ar) Text(
                                dhikr.en, color = Color.White.copy(alpha = 0.72f), fontSize = 12.5.sp, lineHeight = 17.sp,
                                fontFamily = InterFontFamily, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                            val src = if (ar) dhikr.sourceAr else dhikr.sourceEn ?: dhikr.sourceAr
                            if (!src.isNullOrBlank()) {
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    src, color = Color.White.copy(alpha = 0.42f), fontSize = 10.5.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    fontFamily = if (ar) ReadexFontFamily else InterFontFamily,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    // Time left: a hairline in the icon's colours that shortens towards the start edge.
                    Box(Modifier.fillMaxWidth().height(2.5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))) {
                        Box(
                            Modifier.fillMaxWidth(motion.remaining.floatValue.coerceIn(0.001f, 1f)).height(2.5.dp).clip(CircleShape)
                                .background(Brush.horizontalGradient(listOf(EmranIndigo, EmranOrange))),
                        )
                    }
                }
            }
        }
    }
}

/** The X that rises at the bottom while the card is held. [hot] = the card is over it (1 = let go to dismiss). */
@Composable
fun FloatingDismissTarget(shown: Boolean, hot: Float) {
    val s by animateFloatAsState(if (shown) 1f else 0f, spring(dampingRatio = 0.62f, stiffness = 420f), label = "x")
    val h by animateFloatAsState(hot, tween(120, easing = LinearEasing), label = "hot")
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            if (h > 0f) drawCircle(Brush.radialGradient(listOf(Color(0xFFE5484D).copy(alpha = 0.35f * h * s), Color.Transparent)), radius = size.minDimension / 2f)
        }
        Box(
            Modifier
                .size((60 + 16 * h).dp)
                .graphicsLayer { alpha = s; translationY = (1f - s) * 70.dp.toPx(); scaleX = 0.6f + 0.4f * s; scaleY = 0.6f + 0.4f * s }
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(lerp(Color(0xEB0B0B10), Color(0xFFE5484D), h * 0.9f))
                .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size((26 + 4 * h).dp))
        }
    }
}

/** A live, looping copy of the card for the settings page, so people see exactly what will float in. */
@Composable
fun FloatingDhikrPreview(dhikr: FloatingDhikr.Dhikr, lang: String, seconds: Int, widthPx: Int) {
    val loop = rememberInfiniteTransition(label = "preview")
    val total = 900 + seconds * 1000 + 520 + 900
    val t by loop.animateFloat(0f, total.toFloat(), infiniteRepeatable(tween(total, easing = LinearEasing)), label = "t")
    val motion = remember {
        CardMotion(
            androidx.compose.runtime.mutableFloatStateOf(0f), androidx.compose.runtime.mutableFloatStateOf(0f),
            androidx.compose.runtime.mutableFloatStateOf(0f), androidx.compose.runtime.mutableFloatStateOf(1f),
            androidx.compose.runtime.mutableStateOf(false),
        )
    }
    val showEnd = 900f + seconds * 1000f
    motion.leaving.value = t > showEnd
    motion.appear.floatValue = when {
        t < 900f -> FastOutSlowInEasing.transform(t / 900f)
        t < showEnd -> 1f
        t < showEnd + 520f -> 1f - (t - showEnd) / 520f
        else -> 0f
    }
    motion.remaining.floatValue = (1f - (t - 900f) / (seconds * 1000f)).coerceIn(0f, 1f)
    FloatingDhikrCard(dhikr, lang, motion, widthPx, onTap = {})
}
