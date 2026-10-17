package io.github.sammynashed.emran.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
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

        // Cards, the dock and the lower half of every page sit on `low`/`glow`. Once the ink
        // turns white (before Maghrib the middle darkens while the horizon is still bright
        // orange), those bands must be dark enough for white text, or pages wash out.
        val whiteInk = 1f - darkInk
        fun tame(c: Color, maxLum: Float): Color {
            val l = c.luminance()
            if (l <= maxLum) return c
            val t = 1f - Math.pow((maxLum / l).toDouble(), 1 / 2.2).toFloat()
            return lerp(c, Color.Black, t * whiteInk)
        }

        // The mirror case, every morning: from mid-Fajr until well after sunrise the middle is
        // bright (so the ink turns dark) while the top is still deep navy, and the date, the
        // day arc and the prayer names up there vanished. Lift the top toward the middle's
        // colour until dark ink reads on it (luminance 0.30 gives the navy ink about 5.7:1).
        fun lift(c: Color, minLum: Float, toward: Color): Color {
            if (c.luminance() >= minLum || darkInk <= 0f) return c
            var lo = 0f; var hi = 1f
            repeat(12) {
                val m = (lo + hi) / 2
                if (lerp(c, toward, m).luminance() >= minLum) hi = m else lo = m
            }
            return lerp(c, toward, hi * darkInk)
        }
        val liftTarget = lerp(palette.mid, Color.White, 0.35f)

        return SkyState(
            top = lift(palette.top, 0.30f, liftTarget), mid = palette.mid, low = tame(palette.low, 0.12f), glow = tame(palette.glow, 0.20f),
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
const val SKY_DOWNSCALE = 5

/** Paints the sky texture into the window or a glass pane's coordinate space. */
fun DrawScope.drawSkyTexture(
    layer: androidx.compose.ui.graphics.layer.GraphicsLayer?,
    sky: SkyState,
    size: Size,
    time: Float,
    /** Stars. Only the window needs them: behind glass they are blurred away anyway. */
    detail: Boolean = true,
) {
    if (layer != null) drawLayer(layer) else drawSky(sky, size, time, withStars = false)
    if (detail) drawStars(sky, size, time)
}

/**
 * The sky texture's own, slower clock (a dozen updates a second). The clouds drift so slowly that this
 * looks identical, and every glass pane that redraws the texture only redraws that often.
 */
val LocalSkySlowTime = androidx.compose.runtime.staticCompositionLocalOf<State<Float>> {
    object : State<Float> { override val value: Float = 0f }
}
const val SKY_TEXTURE_FPS = 8f

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

/** The sky's three-colour gradient, eased like the old shader's smoothstep so the bands blend softly. */
fun DrawScope.drawSkyGradient(sky: SkyState, size: Size) {
    if (size.width <= 0f || size.height <= 0f) return
    fun ease(t: Float) = t * t * (3 - 2 * t)
    val stops = buildList {
        for (k in 0..4) add(0.55f * k / 4f to lerp(sky.top, sky.mid, ease(k / 4f)))
        for (k in 1..4) add(0.55f + 0.45f * k / 4f to lerp(sky.mid, sky.low, ease(k / 4f)))
    }.toTypedArray()
    drawRect(Brush.verticalGradient(*stops, startY = 0f, endY = size.height), size = size)
}

/** The horizon glow that breathes, added on top of the sky and clouds (exp falloff, as in the old shader). */
fun DrawScope.drawHorizonGlow(sky: SkyState, size: Size, time: Float, additive: Boolean) {
    if (size.width <= 0f || size.height <= 0f) return
    val pulse = 0.32f + 0.10f * kotlin.math.sin(time * 0.6f)
    val reach = 1.6f
    val stops = (0..8).map { k ->
        val f = k / 8f
        val d = f * reach
        f to sky.glow.copy(alpha = (pulse * kotlin.math.exp(-d * d * 3.2f)).coerceIn(0f, 1f))
    }.toTypedArray()
    drawRect(
        brush = Brush.radialGradient(*stops, center = Offset(size.width * 0.5f, size.height), radius = size.width * 1.15f * reach),
        size = size,
        blendMode = if (additive) androidx.compose.ui.graphics.BlendMode.Plus else androidx.compose.ui.graphics.BlendMode.SrcOver,
    )
}

/**
 * The clouds, drawn from one baked image (res/drawable-nodpi/sky_clouds.webp, made by
 * ~/emran-design/sky/bake_clouds.py from the same noise the old cloud shader computed live).
 *
 * Why an image: the live shader computed its noise on the GPU, and on many phones the GPU does that
 * maths at reduced precision, which turned every cloud into flat rectangles ("Minecraft clouds").
 * Scrolling a picture is something every GPU draws identically, so the sky now looks the same on
 * every phone, and phones below Android 13 (no custom shaders) get the moving clouds too.
 *
 * The image is a FIELD, not a picture of clouds: R = far layer, G = near layer, B = the near layer's
 * lit edge. Each layer is thresholded into cloud cover by a colour matrix AFTER the image is scaled
 * up, so the edges stay clean at any magnification. Drift offsets are worked out here in double
 * precision and wrapped to the tile, so no number ever grows with time.
 */
class SkyClouds private constructor(private val bitmap: android.graphics.Bitmap) {
    private val tile = bitmap.width.toFloat()
    private val shader = android.graphics.BitmapShader(bitmap, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
    private val matrix = android.graphics.Matrix()
    private val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.DITHER_FLAG).apply { shader = this@SkyClouds.shader }
    private val maskPaint = android.graphics.Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN) }
    private var maskH = -1f

    fun draw(canvas: android.graphics.Canvas, sky: SkyState, w: Float, h: Float, time: Double) {
        val cloudAmt = 0.55f + 0.45f * sky.darkInk
        val cloud = lerp(sky.glow, Color.White, 0.55f)
        val shade = lerp(cloud, Color(sky.mid.red * 0.72f, sky.mid.green * 0.72f, sky.mid.blue * 0.72f), 0.42f)
        canvas.saveLayer(0f, 0f, w, h, null)
        // Far layer: 3.2 cells per screen height, drifting right at 0.045 cells/s; 16 cells per tile.
        layer(canvas, w, h, cellPx = h / 3.2f, cellsPerTile = 16, dx = time * 0.045, dy = 0.0,
            channel = 0, lo = 0.43f, hi = 0.66f, color = shade, alpha = cloudAmt * 0.35f * 0.75f)
        // Near layer: 2.08 cells per screen height, drifting left (and a touch down); 8 cells per tile.
        layer(canvas, w, h, cellPx = h / 2.08f, cellsPerTile = 8, dx = 5.0 - time * 0.1105, dy = time * 0.02,
            channel = 1, lo = 0.50f, hi = 0.72f, color = cloud, alpha = cloudAmt * 0.55f * 0.92f)
        // The near clouds' edge that faces the horizon glow.
        layer(canvas, w, h, cellPx = h / 2.08f, cellsPerTile = 8, dx = 5.0 - time * 0.1105, dy = time * 0.02,
            channel = 2, lo = 0f, hi = 1f, color = sky.glow, alpha = cloudAmt * 0.55f * 0.22f)
        // Clouds live in a band: none at the very top, fading out just above the bottom edge.
        if (maskH != h) {
            maskH = h
            val y = floatArrayOf(0f, 0.06f, 0.15f, 0.24f, 0.33f, 0.42f, 0.92f, 0.96f, 1f)
            val a = floatArrayOf(0f, 0f, 0.156f, 0.5f, 0.844f, 1f, 1f, 0.5f, 0f)
            maskPaint.shader = android.graphics.LinearGradient(0f, 0f, 0f, h, IntArray(a.size) { android.graphics.Color.argb((a[it] * 255).toInt(), 0, 0, 0) }, y, android.graphics.Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w, h, maskPaint)
        canvas.restore()
    }

    private fun layer(
        canvas: android.graphics.Canvas, w: Float, h: Float, cellPx: Float, cellsPerTile: Int,
        dx: Double, dy: Double, channel: Int, lo: Float, hi: Float, color: Color, alpha: Float,
    ) {
        val scale = cellPx * cellsPerTile / tile
        // Offset in cells, wrapped to one tile in double precision, then turned into pixels.
        fun wrap(v: Double) = (Math.floorMod((v * 1_000_000.0).toLong(), cellsPerTile * 1_000_000L) / 1_000_000.0).toFloat()
        matrix.setScale(scale, scale)
        // The aspect-corrected shader coordinate is (x / h, y / h) * cells, so moving the noise by +d cells
        // moves the picture by -d cells on screen.
        matrix.postTranslate(-wrap(dx) * cellPx, -wrap(dy) * cellPx)
        shader.setLocalMatrix(matrix)
        // Field value v (0..255) in the chosen channel -> alpha = clamp((v/255 - lo) / (hi - lo)); colour fixed.
        val k = 1f / (hi - lo)
        val m = FloatArray(20)
        m[4] = color.red * 255f; m[9] = color.green * 255f; m[14] = color.blue * 255f
        m[15 + channel] = k
        m[19] = -lo * 255f * k
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(m)
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        canvas.drawRect(0f, 0f, w, h, paint)
    }

    companion object {
        @Volatile private var cached: SkyClouds? = null
        fun get(context: android.content.Context): SkyClouds? = cached ?: synchronized(this) {
            cached ?: runCatching {
                val opts = android.graphics.BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888 }
                SkyClouds(android.graphics.BitmapFactory.decodeResource(context.resources, io.github.sammynashed.emran.R.drawable.sky_clouds, opts))
            }.onFailure { android.util.Log.e("SkyClouds", "cloud image failed to load", it) }.getOrNull().also { cached = it }
        }
    }
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

// The living sky, kept calm so text reads on it: the gradient, soft slow clouds and a
// breathing horizon glow, all in the colours of the current prayer time. Drawn on the GPU.


/**
 * "Calm sky": the plain gradient with no clouds, for anyone who wants the quietest screen. Read inside draw lambdas, so flipping it redraws the sky at once.
 */
object SkySettings {
    private val calmState = androidx.compose.runtime.mutableStateOf(false)
    var calm: Boolean
        get() = calmState.value
        private set(v) { calmState.value = v }
    private var loaded = false

    private fun prefs(context: android.content.Context) =
        context.applicationContext.getSharedPreferences("emran_ui", android.content.Context.MODE_PRIVATE)

    fun ensureLoaded(context: android.content.Context) {
        if (loaded) return
        loaded = true
        calm = prefs(context).getBoolean("calm_sky", false)
    }

    fun set(context: android.content.Context, value: Boolean) {
        loaded = true
        calm = value
        prefs(context).edit().putBoolean("calm_sky", value).apply()
    }
}

/**
 * Owns one sky shader. Each place that paints the sky (the window, and every glass
 * panel's own copy behind it) gets its own painter, so uniforms never clash.
 */
class SkyPainter(context: android.content.Context) {
    private val clouds = SkyClouds.get(context)

    fun paint(scope: DrawScope, sky: SkyState, size: Size, time: Float) {
        if (size.width <= 0f || size.height <= 0f) return
        if (SkySettings.calm || clouds == null) {
            scope.drawSky(sky, size, time, withStars = false)
            return
        }
        scope.drawSkyGradient(sky, size)
        scope.drawIntoCanvas { clouds.draw(it.nativeCanvas, sky, size.width, size.height, time.toDouble()) }
        scope.drawHorizonGlow(sky, size, time, additive = true)
    }
}
