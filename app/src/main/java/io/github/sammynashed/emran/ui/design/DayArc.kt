package io.github.sammynashed.emran.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
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
import io.github.sammynashed.emran.ui.theme.InterFontFamily
import java.time.Duration
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private class Mark(val name: String, val ar: String, val fraction: Float, val key: String, val time: String)

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

    val use24 = io.github.sammynashed.emran.data.TimeFormat.use24
    fun hm(t: java.time.LocalDateTime) = io.github.sammynashed.emran.data.TimeFormat.clock(t.toLocalTime(), use24)
    val marks = listOf(
        Mark("Fajr", "الفجر", 0f, "Fajr", hm(times.fajr)),
        Mark(
            io.github.sammynashed.emran.data.PrayerNames.name("Dhuhr", false, times.dhuhr.toLocalDate()),
            io.github.sammynashed.emran.data.PrayerNames.name("Dhuhr", true, times.dhuhr.toLocalDate()),
            frac(times.dhuhr), "Dhuhr", hm(times.dhuhr),
        ),
        Mark("Asr", "العصر", frac(times.asr), "Asr", hm(times.asr)),
        Mark("Maghrib", "المغرب", frac(times.maghrib), "Maghrib", hm(times.maghrib)),
        Mark("Isha", "العشاء", 1f, "Isha", hm(times.isha)),
    )
    val sunriseFraction = frac(times.sunrise)
    // The adhan clock is the same moment as the highlighted tick; Sunrise highlights Fajr's neighbour.
    val highlightKey = if (nextName == "Sunrise") null else nextName.removeSuffix(" (Tomorrow)")

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val screenWidth = maxWidth
        val radius = screenWidth * 0.31f
        val top = 36.dp
        val cx = maxWidth / 2
        val cy = top + radius
        val boxHeight = cy + 70.dp

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
                // The part already lived, brighter, with a soft glow under it.
                val lived = sky.dayFraction * 180f
                if (lived > 0.5f) {
                    val glow = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                    if (rtl) drawArc(sky.ink.copy(alpha = 0.10f), 0f, -lived, false, topLeft, arcSize, style = glow)
                    else drawArc(sky.ink.copy(alpha = 0.10f), 180f, lived, false, topLeft, arcSize, style = glow)
                    if (rtl) drawArc(sky.ink.copy(alpha = 0.85f), 0f, -lived, false, topLeft, arcSize, style = stroke)
                    else drawArc(sky.ink.copy(alpha = 0.85f), 180f, lived, false, topLeft, arcSize, style = stroke)
                }

                fun at(f: Float, rr: Float): Offset {
                    val th = Math.PI * f
                    val dx = (rr * cos(th)).toFloat()
                    val dy = (rr * sin(th)).toFloat()
                    return Offset(if (rtl) c.x + dx else c.x - dx, c.y - dy)
                }

                // Prayer dots: solid once prayed past, dim ahead, the next one bigger with a halo.
                marks.forEach { m ->
                    val p = at(m.fraction, r)
                    when {
                        m.key == highlightKey -> {
                            drawCircle(sky.ink.copy(alpha = 0.18f), 11.dp.toPx(), p)
                            drawCircle(sky.ink, 5.5.dp.toPx(), p)
                        }
                        m.fraction <= sky.dayFraction -> drawCircle(sky.ink.copy(alpha = 0.9f), 4.dp.toPx(), p)
                        else -> {
                            drawCircle(sky.ink.copy(alpha = 0.35f), 4.dp.toPx(), p)
                            drawCircle(sky.ink.copy(alpha = 0.75f), 4.dp.toPx(), p, style = Stroke(1.5.dp.toPx()))
                        }
                    }
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

            // Each label (name over time) is one block centred just outside its own dot, on the
            // line from the arc's centre through the dot, far enough out that the block clears
            // the arc at any angle. The top one runs inline (room sideways, not upwards); the two
            // ends sit under their dots. Coordinates are plain left-to-right (see the Ltr provider
            // above); the day's direction in Arabic is already inside `point`.
            marks.forEach { m ->
                val next = m.key == highlightKey
                val atEnd = m.fraction <= 0.001f || m.fraction >= 0.999f
                // When the orb's glow sits on this prayer, slide the label a little along the arc,
                // never toward an end, where the end's own label sits.
                val orbHere = abs(sky.dayFraction - m.fraction) < 0.08f
                val slide = if (orbHere && !atEnd) (if (sky.dayFraction > m.fraction) -0.06f else 0.06f) else 0f
                val f = if (atEnd) m.fraction else (m.fraction + slide).coerceIn(0.12f, 0.88f)
                val top = !atEnd && abs(f - 0.5f) < 0.14f
                val (x, y) = when {
                    atEnd -> point(m.fraction, radius).first to (cy + (if (orbHere) 40.dp else 28.dp))
                    top -> point(f, radius + 16.dp).let { it.first to it.second - 6.dp }
                    else -> point(f, radius + (if (next) 46.dp else 34.dp))
                }
                AnchoredLabel(
                    x = x, y = y, side = 0,
                    text = if (ar) m.ar else m.name,
                    sub = m.time,
                    inline = top,
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
                Text(
                    text = io.github.sammynashed.emran.data.PrayerNames.name(nextName, ar),
                    color = sky.ink,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = if (ar) null else InterFontFamily,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                Text(
                    text = countdown,
                    color = sky.ink,
                    fontSize = 31.sp,
                    fontWeight = FontWeight.ExtraLight,
                    fontFamily = InterFontFamily,
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
    sub: String?,
    color: Color,
    bold: Boolean,
    inline: Boolean = false,
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
        val name: @Composable () -> Unit = {
            Text(
                text = text,
                color = color,
                fontSize = 13.sp,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                style = tight(18.sp),
            )
        }
        val time: @Composable () -> Unit = {
            if (sub != null) {
                Text(
                    text = sub,
                    style = tight(13.sp),
                    color = color.copy(alpha = color.alpha * 0.85f),
                    fontSize = 11.sp,
                    fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                    fontFamily = InterFontFamily,
                    maxLines = 1,
                    softWrap = false,
                    bypassNumerals = true,
                )
            }
        }
        val pill = if (bold) Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 9.dp, vertical = 3.dp) else Modifier
        if (inline) {
            androidx.compose.foundation.layout.Row(
                modifier = pill,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
            ) { name(); time() }
        } else {
            Column(pill, horizontalAlignment = Alignment.CenterHorizontally) { name(); time() }
        }
    }
}

/** Line box trimmed to the glyphs, so a name and its time sit close (Arabic fonts carry tall line gaps). */
private fun tight(lineHeight: androidx.compose.ui.unit.TextUnit) = TextStyle(
    lineHeight = lineHeight,
    lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
        alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
        trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
    ),
)
