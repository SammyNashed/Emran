package io.github.sammynashed.emran.glance.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws the home screen's astrolabe compass into a Bitmap.
 *
 * The in-app original is a stack of Compose `Canvas` layers, and Glance has no
 * equivalent: it compiles to RemoteViews, which cannot draw arcs, dashes or
 * anything else procedural. Rendering to a Bitmap with the platform Canvas and
 * handing Glance an Image is the only way to reproduce the design faithfully
 * rather than approximating it with boxes.
 *
 * Geometry is copied from AstrolabeCentralCompass so the two stay recognisably
 * the same object: prayers sit at fixed 60-degree intervals starting from Fajr,
 * and the progress arc sweeps through them in order.
 */
object AstrolabeRenderer {

    /** Prayer order around the ring, matching the in-app compass. */
    private val ORDER = listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha")

    /** Canvas size in pixels. Kept modest: the whole RemoteViews must stay well
     *  inside the binder transaction limit, and this is scaled to fit anyway. */
    const val SIZE_PX = 360

    /**
     * @param progress 0..1 around the whole ring.
     * @param highlightIndex index into [ORDER] of the next prayer, or -1.
     */
    fun render(
        context: Context,
        accent: Color,
        onSurface: Color,
        progress: Float,
        labels: List<String>,
        highlightIndex: Int,
        rtl: Boolean
    ): Bitmap {
        val bmp = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val cx = SIZE_PX / 2f
        val cy = SIZE_PX / 2f
        val accentArgb = accent.toArgb()
        val textArgb = onSurface.toArgb()

        // Mirror the sweep for Arabic, exactly as the in-app compass does.
        val startAngle = if (rtl) 30f else -210f
        val sweepMax = if (rtl) -360f else 360f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // ---- 1a. Graduated ticks around the outer border -------------------
        val tickOuter = SIZE_PX / 2f - 2f
        val tickLen = 5f
        for (a in 0 until 360 step 3) {
            val rad = Math.toRadians(a.toDouble())
            val c = cos(rad).toFloat()
            val s = sin(rad).toFloat()
            val major = a % 30 == 0
            paint.color = onSurface.copy(alpha = if (major) 0.45f else 0.18f).toArgb()
            paint.strokeWidth = if (major) 2f else 1f
            canvas.drawLine(
                cx + (tickOuter - tickLen) * c, cy + (tickOuter - tickLen) * s,
                cx + tickOuter * c, cy + tickOuter * s,
                paint
            )
        }

        // ---- 1b. Track, glow and progress arcs -----------------------------
        val ringRadius = SIZE_PX * 0.40f
        val ring = RectF(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius)

        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.pathEffect = null

        paint.color = onSurface.copy(alpha = 0.08f).toArgb()
        paint.strokeWidth = 2f
        canvas.drawArc(ring, startAngle, sweepMax, false, paint)

        val swept = sweepMax * progress.coerceIn(0f, 1f)

        paint.color = accent.copy(alpha = 0.28f).toArgb()
        paint.strokeWidth = 10f
        canvas.drawArc(ring, startAngle, swept, false, paint)

        paint.color = accentArgb
        paint.strokeWidth = 4f
        canvas.drawArc(ring, startAngle, swept, false, paint)

        // ---- 1c. Dashed inner ring -----------------------------------------
        paint.color = onSurface.copy(alpha = 0.10f).toArgb()
        paint.strokeWidth = 1f
        paint.pathEffect = DashPathEffect(floatArrayOf(5f, 6f), 0f)
        canvas.drawCircle(cx, cy, SIZE_PX * 0.33f, paint)
        paint.pathEffect = null

        // ---- Prayer dots and labels ----------------------------------------
        // The dial is drawn, not composed, so it is the one place a widget can
        // use a bundled font: Glance maps FontFamily to a *system* family name,
        // which app resources can never be.
        val labelTypeface = try {
            ResourcesCompat.getFont(context, io.github.sammynashed.emran.R.font.readex_pro)
        } catch (e: Exception) {
            null
        } ?: Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = 17f
            typeface = labelTypeface
        }

        for (i in ORDER.indices) {
            // Even 60-degree spacing, in ring order, mirrored for Arabic.
            val angle = startAngle + (sweepMax / ORDER.size) * i
            val rad = Math.toRadians(angle.toDouble())
            val c = cos(rad).toFloat()
            val s = sin(rad).toFloat()

            val dotX = cx + ringRadius * c
            val dotY = cy + ringRadius * s
            val isNext = i == highlightIndex

            paint.style = Paint.Style.FILL
            paint.color = if (isNext) accentArgb else onSurface.copy(alpha = 0.35f).toArgb()
            canvas.drawCircle(dotX, dotY, if (isNext) 6f else 3.5f, paint)

            if (isNext) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                paint.color = accent.copy(alpha = 0.45f).toArgb()
                canvas.drawCircle(dotX, dotY, 11f, paint)
                paint.style = Paint.Style.FILL
            }

            // Label sits inside the ring, on the same radial line.
            val labelRadius = ringRadius - 26f
            labelPaint.color = if (isNext) accentArgb else onSurface.copy(alpha = 0.55f).toArgb()
            labels.getOrNull(i)?.let { text ->
                canvas.drawText(
                    text,
                    cx + labelRadius * c,
                    cy + labelRadius * s + labelPaint.textSize / 3f,
                    labelPaint
                )
            }
        }

        // ---- 1d. Thumb knob on the progress head ---------------------------
        val thumbAngle = Math.toRadians((startAngle + swept).toDouble())
        val tx = cx + ringRadius * cos(thumbAngle).toFloat()
        val ty = cy + ringRadius * sin(thumbAngle).toFloat()

        paint.style = Paint.Style.FILL
        paint.color = onSurface.copy(alpha = 0.35f).toArgb()
        canvas.drawCircle(tx, ty, 11f, paint)
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(tx, ty, 7f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = accentArgb
        canvas.drawCircle(tx, ty, 7f, paint)

        return bmp
    }

    /**
     * How far through the prayer cycle "now" is, as a fraction of the ring.
     *
     * Deliberately *not* linear in time: the dots sit at even 60-degree
     * intervals, so the head has to advance one sixth per prayer regardless of
     * how long that interval actually lasts. Otherwise the marker would drift
     * away from the dot it is supposed to be approaching.
     */
    fun progressFor(times: List<Long>, now: Long): Float {
        if (times.size < ORDER.size) return 0f
        val segments = ORDER.size

        if (now <= times.first()) return 0f
        for (i in 0 until segments - 1) {
            val from = times[i]
            val to = times[i + 1]
            if (now in from..to && to > from) {
                val within = (now - from).toFloat() / (to - from).toFloat()
                return (i + within) / segments
            }
        }
        // After Isha: run the last segment out over the remainder of the night.
        val isha = times.last()
        val nextFajr = times.first() + 24L * 60 * 60 * 1000
        val within = ((now - isha).toFloat() / (nextFajr - isha).toFloat()).coerceIn(0f, 1f)
        return ((segments - 1) + within) / segments
    }

    fun indexOf(prayer: String?): Int = ORDER.indexOf(prayer ?: "")
}
