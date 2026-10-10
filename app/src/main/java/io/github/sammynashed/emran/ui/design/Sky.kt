package io.github.sammynashed.emran.ui.design

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.layer.drawLayer
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

/** Seconds, ticking about 30 times a second while the app is on screen and motion is allowed. */
/** The sky, rendered once per tick at low resolution; the window and every glass pane draw this. */
val LocalSkyLayer = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.ui.graphics.layer.GraphicsLayer?> { null }

/** The sky texture is drawn this many times smaller than the screen and scaled up. */
const val SKY_DOWNSCALE = 4

/** Paints the sky texture into the window or a glass pane's coordinate space. */
fun DrawScope.drawSkyTexture(layer: androidx.compose.ui.graphics.layer.GraphicsLayer?, sky: SkyState, size: Size, time: Float) {
    if (layer != null) drawLayer(layer) else drawSky(sky, size, time, withStars = false)
    drawStars(sky, size, time)
}

val LocalSkyTime = androidx.compose.runtime.staticCompositionLocalOf<State<Float>> {
    object : State<Float> { override val value: Float = 0f }
}

/**
 * Paints the sky into [size] (the whole window); glass calls it with an offset.
 * This is the plain version used below API 33 (stars twinkle, the glow breathes).
 */
fun DrawScope.drawSky(sky: SkyState, size: Size, time: Float = 0f, withStars: Boolean = true) {
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
            radius = size.width * (1.15f + 0.03f * kotlin.math.sin(time * 0.35f)),
        ),
        size = size,
    )
    if (withStars) drawStars(sky, size, time)
}

/** Twinkling stars, drawn at full resolution (they would wash out in the low-res sky texture). */
fun DrawScope.drawStars(sky: SkyState, size: Size, time: Float) {
    if (size.width <= 0f || size.height <= 0f) return
    if (sky.stars > 0.01f) {
        STARS.forEachIndexed { i, s ->
            val twinkle = 0.78f + 0.22f * kotlin.math.sin(time * (0.8f + (i % 5) * 0.35f) + i * 1.7f)
            drawCircle(
                color = Color.White.copy(alpha = (sky.stars * s.a * twinkle).coerceIn(0f, 1f)),
                radius = s.r * density,
                center = Offset(s.x * size.width, s.y * size.height),
            )
        }
    }
}


// The living sky: the gradient, drifting clouds, twinkling stars and a breathing
// horizon glow, all in the colours of the current prayer time. Drawn on the GPU.
private const val SKY_AGSL = """
uniform float2 res;
uniform float time;
uniform float3 cTop;
uniform float3 cMid;
uniform float3 cLow;
uniform float3 cGlow;
uniform float3 cCloud;
uniform float night;
uniform float cloudAmt;

float hash(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}
float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + float2(1.0, 0.0));
    float c = hash(i + float2(0.0, 1.0));
    float d = hash(i + float2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}
float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p *= 2.02;
        a *= 0.5;
    }
    return v;
}
float segDist(float2 p, float2 a, float2 b) {
    float2 pa = p - a;
    float2 ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h);
}

half4 main(float2 frag) {
    float2 uv = frag / res;
    float y = uv.y;
    float aspect = res.x / res.y;
    float3 col = (y < 0.55)
        ? mix(cTop, cMid, smoothstep(0.0, 0.55, y))
        : mix(cMid, cLow, smoothstep(0.55, 1.0, y));

    // Two cloud layers drifting in opposite directions, so the sky has depth.
    float2 base = float2(uv.x * aspect, uv.y);
    float2 p1 = base * 3.2;
    p1.x += time * 0.045;
    float n1 = fbm(p1 + fbm(p1 * 0.6 + time * 0.03));
    float2 p2 = base * 1.6;
    p2.x -= time * 0.085;
    float n2 = fbm(p2 * 1.3 + float2(5.0, time * 0.02));
    float band = smoothstep(0.06, 0.42, y) * (1.0 - smoothstep(0.92, 1.0, y));
    float far = smoothstep(0.40, 0.72, n1) * band * cloudAmt * 0.60;
    float near = smoothstep(0.48, 0.78, n2) * band * cloudAmt;
    float3 shade = mix(cCloud, cMid * 0.78, 0.38);
    col = mix(col, shade, far * 0.70);
    col = mix(col, cCloud, near * 0.88);
    // Light catching the cloud edges.
    col += cGlow * near * (0.10 + 0.07 * sin(time * 0.5 + uv.x * 3.0));

    // Horizon glow that breathes.
    float2 gc = float2(res.x * 0.5, res.y);
    float d = length((frag - gc) / (res.x * 1.15));
    float g = exp(-d * d * 3.2) * (0.32 + 0.10 * sin(time * 0.6));
    col += cGlow * g;

    // Soft rays fanning up from the horizon in daylight and at dusk.
    float2 q = float2((uv.x - 0.5) * aspect, 1.12 - uv.y);
    float ang = atan(q.x, q.y);
    float ray = pow(noise(float2(ang * 6.5, time * 0.10)), 2.4);
    float fall = smoothstep(1.15, 0.0, length(q));
    col += cGlow * ray * fall * (1.0 - night) * 0.20;

    // Night: a slow aurora ribbon and, every so often, a shooting star.
    if (night > 0.15) {
        float w = sin(uv.x * 5.0 + time * 0.40) * 0.055 + sin(uv.x * 11.0 - time * 0.55) * 0.022;
        float yy = uv.y - (0.24 + w);
        float ribbon = exp(-yy * yy * 150.0);
        float flick = 0.55 + 0.45 * noise(float2(uv.x * 7.0 + time * 0.35, time * 0.25));
        float3 aur = mix(float3(0.12, 0.95, 0.70), float3(0.58, 0.36, 0.98), clamp(uv.x + 0.15 * sin(time * 0.3), 0.0, 1.0));
        col += aur * ribbon * flick * 0.34 * night;

        float cyc = 11.0;
        float id = floor(time / cyc);
        float k = fract(time / cyc) / 0.085;
        if (k < 1.0) {
            float2 s0 = float2((0.12 + 0.55 * hash(float2(id, 1.0))) * aspect, 0.06 + 0.16 * hash(float2(id, 2.0)));
            float2 dir = normalize(float2(1.0, 0.5));
            float2 head = s0 + dir * k * 0.42;
            float dd = segDist(base, head - dir * 0.11, head);
            col += float3(1.0) * smoothstep(0.0055, 0.0, dd) * (1.0 - k) * night;
        }
    }

    // A little noise so the long gradients never band.
    col += (hash(frag) - 0.5) * (1.4 / 255.0);
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

/**
 * Owns one sky shader. Each place that paints the sky (the window, and every glass
 * panel's own copy behind it) gets its own painter, so uniforms never clash.
 */
class SkyPainter {
    private val shader: RuntimeShader? =
        if (Build.VERSION.SDK_INT >= 33) runCatching { RuntimeShader(SKY_AGSL) }.getOrNull() else null

    fun paint(scope: DrawScope, sky: SkyState, size: Size, time: Float) {
        if (size.width <= 0f || size.height <= 0f) return
        val sh = shader
        if (sh != null && Build.VERSION.SDK_INT >= 33 && runCatching { paintShader(scope, sh, sky, size, time) }.isSuccess) return
        scope.drawSky(sky, size, time, withStars = false)
    }

    @RequiresApi(33)
    private fun paintShader(scope: DrawScope, sh: RuntimeShader, sky: SkyState, size: Size, time: Float) {
        fun rgb(name: String, c: Color) = sh.setFloatUniform(name, c.red, c.green, c.blue)
        sh.setFloatUniform("res", size.width, size.height)
        sh.setFloatUniform("time", time)
        rgb("cTop", sky.top); rgb("cMid", sky.mid); rgb("cLow", sky.low); rgb("cGlow", sky.glow)
        rgb("cCloud", lerp(sky.glow, Color.White, 0.55f))
        sh.setFloatUniform("night", sky.stars)
        sh.setFloatUniform("cloudAmt", 0.55f + 0.45f * sky.darkInk)
        scope.drawRect(ShaderBrush(sh), size = size)
    }
}
