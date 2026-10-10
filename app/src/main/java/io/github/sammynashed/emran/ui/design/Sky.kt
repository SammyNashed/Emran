package io.github.sammynashed.emran.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import io.github.sammynashed.emran.data.PrayerTimeCalculator.PrayerTimes
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.random.Random

/**
 * "The sky is the interface."
 *
 * The whole app sits on a sky that follows the REAL prayer times of the user's
 * location: pre-dawn at Fajr, a rosy sunrise, clear noon, golden Asr, burning dusk
 * at Maghrib, then night after Isha. Colours are interpolated continuously between
 * those moments, never switched.
 *
 * It is procedural (a gradient + a few stars), not an image: it costs no APK size,
 * follows the clock exactly, and is one cheap layer behind everything else.
 */
@Immutable
data class SkyState(
    val top: Color,
    val mid: Color,
    val low: Color,
    /** Horizon glow colour. */
    val glow: Color,
    /** 0..1 star visibility. */
    val stars: Float,
    /** Where "now" sits on the prayer-day arc: 0 at Fajr, 1 at Isha. */
    val dayFraction: Float,
    /** 1 while the sun is up, 0 at night (the orb becomes a moon). */
    val sun: Float,
    /**
     * 0 = ink is white (dark sky), 1 = ink is deep navy (bright sky). Chosen from the
     * sky's luminance in the middle of the screen, where text sits, so contrast
     * never depends on the hour.
     */
    val darkInk: Float,
) {
    /** Primary text colour for this sky. */
    val ink: Color get() = lerp(Color.White, INK_DARK, darkInk)

    /** Quieter text colour. */
    val inkSoft: Color get() = ink.copy(alpha = 0.66f)

    /** How strongly glass is tinted white: light on dark skies, stronger on bright ones. */
    val glassAlpha: Float get() = 0.12f + 0.30f * darkInk

    /** Colour of the sun/moon orb. */
    val orb: Color get() = lerp(MOON, lerp(glow, Color(0xFFFFF1CC), 0.55f), sun)

    companion object {
        val INK_DARK = Color(0xFF0B1B33)
        val MOON = Color(0xFFDCE6FF)
    }
}

private class Palette(val top: Color, val mid: Color, val low: Color, val glow: Color, val stars: Float)

object SkyEngine {

    // Sampled from the approved mood board (~/emran-design/mood), then tuned for contrast.
    private val NIGHT = Palette(Color(0xFF040A18), Color(0xFF071626), Color(0xFF123F55), Color(0xFF1F6B80), 1f)
    private val FAJR = Palette(Color(0xFF0C183A), Color(0xFF6A5A8E), Color(0xFFF2856A), Color(0xFFFFB38A), 0.5f)
    private val SUNRISE = Palette(Color(0xFF35588F), Color(0xFFD9A7A0), Color(0xFFFFC890), Color(0xFFFFD9A0), 0f)
    private val DAY = Palette(Color(0xFF7FB8EE), Color(0xFFAED9FB), Color(0xFFEAF6FF), Color(0xFFFFFFFF), 0f)
    private val ASR = Palette(Color(0xFF86B1D6), Color(0xFFE4CD9F), Color(0xFFE69F3F), Color(0xFFFFD27A), 0f)
    private val MAGHRIB = Palette(Color(0xFF0D1C3F), Color(0xFF5D3B59), Color(0xFFD84A2F), Color(0xFFFF8A4C), 0.35f)

    private fun minutes(t: LocalDateTime): Double = t.toEpochSecond(ZoneOffset.UTC) / 60.0

    private fun smooth(x: Double): Float {
        val t = x.coerceIn(0.0, 1.0)
        return (t * t * (3 - 2 * t)).toFloat()
    }

    private fun smoothBetween(x: Double, a: Double, b: Double): Float =
        if (b <= a) (if (x >= b) 1f else 0f) else smooth((x - a) / (b - a))

    private fun mix(a: Palette, b: Palette, t: Float) = Palette(
        lerp(a.top, b.top, t), lerp(a.mid, b.mid, t), lerp(a.low, b.low, t),
        lerp(a.glow, b.glow, t), a.stars + (b.stars - a.stars) * t,
    )

    /**
     * The sky at [now], given today's and tomorrow's prayer times.
     *
     * Everything is expressed in the location's own wall-clock minutes, which is
     * what [PrayerTimes] already uses, so no zone arithmetic is needed here.
     */
    fun compute(now: LocalDateTime, today: PrayerTimes, tomorrow: PrayerTimes): SkyState {
        val n = minutes(now)
        val fajr = minutes(today.fajr)
        val sunrise = minutes(today.sunrise)
        val maghrib = minutes(today.maghrib)
        val isha = minutes(today.isha)

        val keys = listOf(
            minutes(today.fajr) - 50 to NIGHT,
            fajr to FAJR,
            sunrise to SUNRISE,
            sunrise + 80 to DAY,
            minutes(today.dhuhr) to DAY,
            minutes(today.asr) to ASR,
            maghrib to MAGHRIB,
            isha to NIGHT,
            minutes(tomorrow.fajr) - 50 to NIGHT,
        ).sortedBy { it.first }

        val palette = when {
            n <= keys.first().first -> keys.first().second
            n >= keys.last().first -> keys.last().second
            else -> {
                val i = keys.indexOfLast { it.first <= n }
                val (ta, pa) = keys[i]
                val (tb, pb) = keys[i + 1]
                mix(pa, pb, smoothBetween(n, ta, tb))
            }
        }

        val dayFraction = if (isha > fajr) ((n - fajr) / (isha - fajr)).coerceIn(0.0, 1.0).toFloat() else 0f
        val sun = smoothBetween(n, sunrise - 20, sunrise + 10) * (1f - smoothBetween(n, maghrib - 10, maghrib + 25))

        // Text sits around the middle of the screen, so contrast is judged on `mid`.
        val darkInk = smoothBetween(palette.mid.luminance().toDouble(), 0.16, 0.32)

        return SkyState(
            top = palette.top, mid = palette.mid, low = palette.low, glow = palette.glow,
            stars = palette.stars, dayFraction = dayFraction, sun = sun, darkInk = darkInk,
        )
    }

    /** A believable default before the first real computation (late night). */
    val Default = SkyState(
        NIGHT.top, NIGHT.mid, NIGHT.low, NIGHT.glow, 1f, 0f, 0f, 0f,
    )
}

val LocalSky = staticCompositionLocalOf { SkyEngine.Default }

/** Size of the window the sky fills, so glass can draw the part of the sky behind it. */
val LocalSkySize = staticCompositionLocalOf { Size.Zero }

private class Star(val x: Float, val y: Float, val r: Float, val a: Float)

private val STARS: List<Star> = Random(7).let { rnd ->
    List(72) { Star(rnd.nextFloat(), rnd.nextFloat() * 0.62f, 0.5f + rnd.nextFloat() * 1.1f, 0.35f + rnd.nextFloat() * 0.65f) }
}

/** Paints the sky into [size] (the whole window); glass calls it with an offset. */
fun DrawScope.drawSky(sky: SkyState, size: Size) {
    // The first frame (before the window has been measured) has no size; a gradient
    // with a zero radius throws, so there is nothing to paint yet.
    if (size.width <= 0f || size.height <= 0f) return
    drawRect(
        brush = Brush.verticalGradient(
            0f to sky.top, 0.55f to sky.mid, 1f to sky.low,
            startY = 0f, endY = size.height,
        ),
        size = size,
    )
    // A soft glow where the sun last touched the horizon.
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(sky.glow.copy(alpha = 0.30f), Color.Transparent),
            center = Offset(size.width * 0.5f, size.height),
            radius = size.width * 1.15f,
        ),
        size = size,
    )
    if (sky.stars > 0.01f) {
        STARS.forEach { s ->
            drawCircle(
                color = Color.White.copy(alpha = (sky.stars * s.a).coerceIn(0f, 1f)),
                radius = s.r * density,
                center = Offset(s.x * size.width, s.y * size.height),
            )
        }
    }
}
