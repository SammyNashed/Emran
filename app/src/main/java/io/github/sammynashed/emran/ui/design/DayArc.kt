package io.github.sammynashed.emran.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sammynashed.emran.data.PrayerTimeCalculator.PrayerTimes
import io.github.sammynashed.emran.ui.localization.Text
import io.github.sammynashed.emran.ui.theme.OutfitFontFamily
import java.time.Duration
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private class Mark(val name: String, val ar: String, val fraction: Float, val key: String)

/**
 * The day, drawn as one quiet arc.
 *
 * It spans the prayer-day from Fajr (one end) to Isha (the other), with a tick for
 * each prayer and a sun or moon orb at "now". It replaces the old wheel: same
 * information (what is next, how long is left), a fraction of the visual noise.
 * In Arabic the day runs right to left, as the page does.
 *
 * Below the arc sit the next prayer, a live countdown and its adhan time.
 */
@Composable
fun DayArc(
    times: PrayerTimes,
    nextName: String,
    countdown: String,
    nextTime: String,
    lang: String,
    modifier: Modifier = Modifier,
) {
    val sky = LocalSky.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val ar = lang == "ar"

    val span = Duration.between(times.fajr, times.isha).seconds.coerceAtLeast(1).toFloat()
    fun frac(t: java.time.LocalDateTime) =
        (Duration.between(times.fajr, t).seconds / span).coerceIn(0f, 1f)

    val marks = listOf(
        Mark("Fajr", "الفجر", 0f, "Fajr"),
        Mark("Dhuhr", "الظهر", frac(times.dhuhr), "Dhuhr"),
        Mark("Asr", "العصر", frac(times.asr), "Asr"),
        Mark("Maghrib", "المغرب", frac(times.maghrib), "Maghrib"),
        Mark("Isha", "العشاء", 1f, "Isha"),
    )
    val sunriseFraction = frac(times.sunrise)
    // The adhan clock is the same moment as the highlighted tick; Sunrise highlights Fajr's neighbour.
    val highlightKey = if (nextName == "Sunrise") null else nextName.removeSuffix(" (Tomorrow)")

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val screenWidth = maxWidth
        val radius = screenWidth * 0.30f
        val top = 36.dp
        val cx = maxWidth / 2
        val cy = top + radius
        val boxHeight = cy + 66.dp

        // Position of a point on the arc, in dp, for a day-fraction f at a given radius.
        fun point(f: Float, r: androidx.compose.ui.unit.Dp): Pair<androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp> {
            val theta = Math.PI * f
            val dx = (r.value * cos(theta)).toFloat()
            val dy = (r.value * sin(theta)).toFloat()
            return (if (rtl) cx + dx.dp else cx - dx.dp) to (cy - dy.dp)
        }

        // The arc is drawn and its labels placed in plain left-to-right coordinates; the
        // day's direction in Arabic comes from `rtl` above. Without this a Box would
        // start its children from the RIGHT edge and mirror every label off its tick.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(Modifier.fillMaxWidth().height(boxHeight)) {
            val clock = LocalSkyTime.current
            Canvas(Modifier.fillMaxWidth().height(boxHeight).graphicsLayer { clip = false }) {
                val pulse = 1f + 0.10f * kotlin.math.sin(clock.value * 1.7f)
                val r = radius.toPx()
                val c = Offset(cx.toPx(), cy.toPx())
                val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                val topLeft = Offset(c.x - r, c.y - r)
                val arcSize = Size(r * 2, r * 2)

                // The whole day, faint.
                drawArc(sky.ink.copy(alpha = 0.22f), 180f, 180f, false, topLeft, arcSize, style = stroke)
                // The part already lived, brighter.
                val lived = sky.dayFraction * 180f
                if (lived > 0.5f) {
                    if (rtl) drawArc(sky.ink.copy(alpha = 0.85f), 0f, -lived, false, topLeft, arcSize, style = stroke)
                    else drawArc(sky.ink.copy(alpha = 0.85f), 180f, lived, false, topLeft, arcSize, style = stroke)
                }

                fun at(f: Float, rr: Float): Offset {
                    val th = Math.PI * f
                    val dx = (rr * cos(th)).toFloat()
                    val dy = (rr * sin(th)).toFloat()
                    return Offset(if (rtl) c.x + dx else c.x - dx, c.y - dy)
                }

                // Prayer ticks.
                marks.forEach { m ->
                    val next = m.key == highlightKey
                    val a = at(m.fraction, r - (if (next) 9.dp.toPx() else 6.dp.toPx()))
                    val b = at(m.fraction, r + (if (next) 9.dp.toPx() else 6.dp.toPx()))
                    drawLine(
                        color = if (next) sky.ink else sky.ink.copy(alpha = 0.6f),
                        start = a, end = b,
                        strokeWidth = (if (next) 3.5f else 2f).dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                // Sunrise, a small dot with no label.
                drawCircle(sky.ink.copy(alpha = 0.4f), 2.5.dp.toPx(), at(sunriseFraction, r))

                // The orb: a halo, then a bright core.
                val orbAt = at(sky.dayFraction, r)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(sky.orb.copy(alpha = 0.55f), Color.Transparent),
                        center = orbAt, radius = 34.dp.toPx() * pulse,
                    ),
                    radius = 34.dp.toPx() * pulse, center = orbAt,
                )
                drawCircle(sky.orb, 9.dp.toPx(), orbAt)
                drawCircle(Color.White.copy(alpha = 0.85f), 3.5.dp.toPx(), orbAt)
            }

            // Each name sits OUTSIDE the arc, on a zero-width anchor, and grows AWAY from it:
            // left of the arc it ends at the anchor, right of it it starts there, at the top it
            // is centred above its tick, and the two ends (Fajr, Isha) sit under their tick.
            // Anchoring (rather than a fixed-width box that must be clamped to the screen)
            // means nothing can ever push a name back onto the line. Coordinates are plain
            // left-to-right (see the Ltr provider above); the day's direction in Arabic is
            // already inside `point`.
            val gap = 14.dp
            marks.forEach { m ->
                val next = m.key == highlightKey
                val atEnd = m.fraction <= 0.001f || m.fraction >= 0.999f
                // The orb's glow is ~34 dp wide. When it sits near this prayer, slide the name a
                // little ALONG the arc, away from the orb, so it stays right beside its own tick
                // instead of being pushed out into the sky.
                val orbHere = abs(sky.dayFraction - m.fraction) < 0.07f
                val slide = if (orbHere && !atEnd) (if (sky.dayFraction > m.fraction) -0.032f else 0.032f) else 0f
                val (ax, ay) = point((m.fraction + slide).coerceIn(0f, 1f), radius + gap)
                val anchor = when {
                    atEnd -> 0
                    ax < cx - 28.dp -> -1
                    ax > cx + 28.dp -> 1
                    else -> 0
                }
                val (x, y) = when {
                    atEnd -> point(m.fraction, radius).first to (cy + (if (orbHere) 34.dp else 20.dp))
                    anchor == 0 -> ax to (ay - 12.dp)
                    else -> ax to ay
                }
                AnchoredLabel(
                    x = x, y = y, side = anchor,
                    text = if (ar) m.ar else m.name,
                    color = if (next) sky.ink else sky.inkSoft,
                    bold = next,
                )
            }

            // Next prayer, countdown, adhan time.
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = top + radius * 0.34f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val shown = nextName.removeSuffix(" (Tomorrow)")
                Text(
                    text = if (ar) arName(shown) else shown,
                    color = sky.ink,
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = if (ar) null else OutfitFontFamily,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                Text(
                    text = countdown,
                    color = sky.ink,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraLight,
                    fontFamily = OutfitFontFamily,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    bypassNumerals = true,
                )
                Text(
                    text = (if (ar) "الأذان " else "Adhan ") + nextTime,
                    color = sky.inkSoft,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    bypassNumerals = true,
                )
            }
        }
        }
    }
}

private fun arName(name: String) = when (name) {
    "Fajr" -> "الفجر"
    "Sunrise" -> "الشروق"
    "Dhuhr" -> "الظهر"
    "Asr" -> "العصر"
    "Maghrib" -> "المغرب"
    "Isha" -> "العشاء"
    else -> name
}

/**
 * Text placed relative to a point: [side] -1 ends at the point, 1 starts at it, 0 is centred
 * on it. The point is where the layout says it is, with no width of its own to clamp.
 */
@Composable
private fun AnchoredLabel(
    x: androidx.compose.ui.unit.Dp,
    y: androidx.compose.ui.unit.Dp,
    side: Int,
    text: String,
    color: Color,
    bold: Boolean,
) {
    Box(
        Modifier.layout { measurable, constraints ->
            val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0, maxWidth = androidx.compose.ui.unit.Constraints.Infinity))
            layout(0, 0) {
                val dx = when (side) { -1 -> -p.width; 1 -> 0; else -> -p.width / 2 }
                p.place(x.roundToPx() + dx, y.roundToPx() - p.height / 2)
            }
        },
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 13.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
        )
    }
}
