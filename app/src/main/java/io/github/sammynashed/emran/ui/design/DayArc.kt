package io.github.sammynashed.emran.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.offset
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
        val radius = screenWidth * 0.40f
        val top = 40.dp
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
            Canvas(Modifier.fillMaxWidth().height(boxHeight)) {
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
                        center = orbAt, radius = 34.dp.toPx(),
                    ),
                    radius = 34.dp.toPx(), center = orbAt,
                )
                drawCircle(sky.orb, 9.dp.toPx(), orbAt)
                drawCircle(Color.White.copy(alpha = 0.85f), 3.5.dp.toPx(), orbAt)
            }

            // Labels sit just outside the arc. The two ends (Fajr, Isha) tuck under their tick so
            // they never run off the screen; everything else is clamped inside the margins.
            marks.forEach { m ->
                val atEnd = m.fraction <= 0.001f || m.fraction >= 0.999f
                val (rawX, rawY) = if (atEnd) {
                    point(m.fraction, radius).let { it.first to (cy + 20.dp) }
                } else {
                    // Step further out when the orb is sitting on this prayer, so the glow never covers its name.
                    val orbHere = abs(sky.dayFraction - m.fraction) < 0.05f
                    point(m.fraction, radius + 24.dp + 14.dp * abs(cos(Math.PI * m.fraction)).toFloat() + (if (orbHere) 16.dp else 0.dp))
                }
                val px = rawX.coerceIn(36.dp, screenWidth - 36.dp)
                val next = m.key == highlightKey
                Box(
                    // absoluteOffset: the arc is in plain left-to-right coordinates, and
                    // plain offset() would mirror the labels away from their ticks in Arabic.
                    Modifier
                        .absoluteOffset(x = px - 36.dp, y = rawY - 9.dp)
                        .width(72.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (ar) m.ar else m.name,
                        color = if (next) sky.ink else sky.inkSoft,
                        fontSize = 12.sp,
                        fontWeight = if (next) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
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
