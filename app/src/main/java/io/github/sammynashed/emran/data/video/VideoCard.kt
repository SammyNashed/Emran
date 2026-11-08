package io.github.sammynashed.emran.data.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import io.github.sammynashed.emran.R

/**
 * Draws what sits on top of the moving background: the surah name, the ayah in the muṣḥaf face, its explanation,
 * the reciter and Emran's name. One painter for both the live preview and the exported frames, so the screen shows
 * exactly what the MP4 will.
 */
class VideoCard(context: Context) {
    /** One ayah on screen. [ayah] 0 is the basmala before a surah's first ayah. */
    class Segment(val startUs: Long, val endUs: Long, val ayah: Int, val text: String, val explain: String?)

    class Style(
        val surahTitle: String,
        val rangeLine: String,
        /** e.g. "التفسير الميسر"; null hides the explanation. */
        val explainLabel: String?,
        val reciterLine: String?,
        /** "صُنع بتطبيق" / "Made with", then the app's name in bold, beside its mark, at the foot of every video. */
        val signaturePrefix: String,
        val signatureName: String,
    )

    private val hafs: Typeface = font(context, R.font.kfgqpc_hafs)
    private val amiri: Typeface = font(context, R.font.amiri_bold)
    private val readex: Typeface = font(context, R.font.readex_pro)

    private fun font(c: Context, id: Int) = runCatching { ResourcesCompat.getFont(c, id) }.getOrNull() ?: Typeface.SERIF

    private val gold = Color.rgb(0xE9, 0xCB, 0x86)

    /** [alpha] fades the ayah; [explainAlpha] the explanation (it stays put when neighbouring ayat share one). */
    fun draw(canvas: Canvas, w: Int, h: Int, style: Style, seg: Segment?, alpha: Float, explainAlpha: Float = alpha) {
        val u = minOf(w, h) / 1080f
        val portrait = h > w * 1.2f
        val landscape = w > h * 1.2f

        // Calm the picture so white text reads on any clip: a light veil, darker at the top and bottom.
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.argb(70, 0, 0, 0)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = LinearGradient(0f, 0f, 0f, h * 0.32f, Color.argb(150, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h * 0.32f, p)
        p.shader = LinearGradient(0f, h * 0.45f, 0f, h.toFloat(), Color.TRANSPARENT, Color.argb(185, 0, 0, 0), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h * 0.45f, w.toFloat(), h.toFloat(), p)
        p.shader = null

        val side = if (landscape) w * 0.12f else w * 0.08f
        val textW = (w - side * 2).toInt()

        // Header: the surah and which ayat.
        var y = if (portrait) h * 0.085f else h * 0.07f
        val arabicTitle = style.surahTitle.any { it in '\u0600'..'\u06FF' }
        val title = layout(style.surahTitle, if (arabicTitle) amiri else readex, if (arabicTitle) 52 * u else 44 * u, Color.WHITE, textW, 1f)
        y = drawAt(canvas, title, side, y)
        val range = layout(style.rangeLine, readex, 25 * u, Color.argb(190, 255, 255, 255), textW, 1f)
        y = drawAt(canvas, range, side, y + 6 * u)
        val headerBottom = y

        // Footer: reciter and Emran's name.
        var footTop = h - (if (portrait) h * 0.06f else h * 0.05f)
        val reciter = style.reciterLine?.let { layout(it, readex, 25 * u, Color.argb(200, 255, 255, 255), textW, 1f) }
        // Signature: the mark beside one line of text in one font ("صُنع بتطبيق عِمران" / "Made with Emran").
        val sigText = "${style.signaturePrefix} ${style.signatureName}"
        val arabicSig = sigText.any { it in '\u0600'..'\u06FF' }
        val sigPaint = paint(readex, 28 * u, Color.argb(235, 255, 255, 255))
        val markPx = 64 * u
        val gap = 14 * u
        val sigW = sigPaint.measureText(sigText)
        val rowH = maxOf(markPx, sigPaint.fontMetrics.run { descent - ascent })
        footTop -= rowH
        val groupW = markPx + gap + sigW
        val gx = (w - groupW) / 2f
        val midY = footTop + rowH / 2f
        val baseline = midY - (sigPaint.fontMetrics.ascent + sigPaint.fontMetrics.descent) / 2f
        // The mark leads the line: on the right in Arabic, on the left in English.
        if (arabicSig) {
            drawMark(canvas, gx + groupW - markPx / 2f, midY, markPx)
            canvas.drawText(sigText, gx, baseline, sigPaint)
        } else {
            drawMark(canvas, gx + markPx / 2f, midY, markPx)
            canvas.drawText(sigText, gx + markPx + gap, baseline, sigPaint)
        }
        reciter?.let { footTop -= it.height + 14 * u; drawAt(canvas, it, side, footTop) }

        if (seg == null) return
        val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
        val ea = (explainAlpha.coerceIn(0f, 1f) * 255).toInt()

        // The middle: the ayah, then its explanation, fitted into what the header and footer leave.
        val top = headerBottom + (if (portrait) h * 0.08f else h * 0.05f)
        val bottom = footTop - (if (portrait) h * 0.05f else h * 0.04f)
        val room = bottom - top
        val explain = seg.explain?.takeIf { style.explainLabel != null && it.isNotBlank() }
        val ayahRoom = if (explain != null) room * 0.56f else room
        // The muṣḥaf face draws the ornament around Arabic-Indic digits by itself.
        val ayahText = if (seg.ayah == 0) seg.text else "${seg.text} ${arabicDigits(seg.ayah)}"
        val ayah = fit(ayahText, hafs, if (portrait) 66 * u else 58 * u, 30 * u, Color.argb(a, 255, 255, 255), textW, ayahRoom, 1.55f)

        var exLabel: StaticLayout? = null
        var exBody: StaticLayout? = null
        var exHeight = 0f
        if (explain != null) {
            exLabel = layout(style.explainLabel!!, readex, 22 * u, Color.argb((ea * 0.9f).toInt(), Color.red(gold), Color.green(gold), Color.blue(gold)), textW, 1f)
            val bodyRoom = room - ayah.height - 34 * u - exLabel.height - 10 * u
            // A translation is what an English viewer actually reads, so it gets more size than an Arabic tafsir.
            val latin = explain.count { it in 'A'..'z' } > explain.length / 3
            exBody = fit(explain, readex, if (latin) 38 * u else 30 * u, 19 * u, Color.argb((ea * 0.92f).toInt(), 255, 255, 255), textW, bodyRoom, 1.45f)
            exHeight = 34 * u + exLabel.height + 10 * u + exBody.height
        }
        // Centre the block vertically in its room.
        var cy = top + ((room - ayah.height - exHeight) / 2f).coerceAtLeast(0f)
        cy = drawAt(canvas, ayah, side, cy)
        if (exLabel != null && exBody != null) {
            cy = drawAt(canvas, exLabel, side, cy + 34 * u)
            drawAt(canvas, exBody, side, cy + 10 * u)
        }
    }

    fun render(w: Int, h: Int, style: Style, seg: Segment?, alpha: Float, into: Bitmap? = null, explainAlpha: Float = alpha): Bitmap {
        val bmp = into?.takeIf { it.width == w && it.height == h && it.isMutable } ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.TRANSPARENT)
        draw(Canvas(bmp), w, h, style, seg, alpha, explainAlpha)
        return bmp
    }

    /** Emran's icon (see ui/design/Brand.kt drawEmranMark): dark rounded tile, five pillars fading indigo to orange. */
    private fun drawMark(c: Canvas, cx: Float, cy: Float, size: Float) {
        val l = cx - size / 2f; val t = cy - size / 2f
        val r = size * 0.27f
        val rect = android.graphics.RectF(l, t, l + size, t + size)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.rgb(10, 10, 10)
        p.setShadowLayer(size * 0.18f, 0f, size * 0.05f, Color.argb(140, 0, 0, 0))
        c.drawRoundRect(rect, r, r, p)
        p.clearShadowLayer()
        p.shader = LinearGradient(0f, t, 0f, t + size, Color.argb(41, 0x63, 0x66, 0xF1), Color.argb(41, 0xF9, 0x73, 0x16), Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, r, r, p); p.shader = null
        val k = size / 72f
        fun x(v: Float) = l + (v - 18f) * k
        fun y(v: Float) = t + (v - 18f) * k
        p.shader = LinearGradient(0f, y(35f), 0f, y(73f), Color.rgb(0x63, 0x66, 0xF1), Color.rgb(0xF9, 0x73, 0x16), Shader.TileMode.CLAMP)
        for ((left, top) in PILLARS) {
            val pw = 6f * k
            val px = x(left + 3f)
            c.drawRoundRect(android.graphics.RectF(px - pw / 2f, y(top), px + pw / 2f, y(73f)), pw / 2f, pw / 2f, p)
        }
    }

    private fun drawAt(c: Canvas, l: StaticLayout, x: Float, y: Float): Float {
        c.save(); c.translate(x, y); l.draw(c); c.restore()
        return y + l.height
    }

    private fun paint(face: Typeface, size: Float, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face; textSize = size; this.color = color
        setShadowLayer(size * 0.18f, 0f, size * 0.05f, Color.argb(150, 0, 0, 0))
    }

    private fun layout(text: String, face: Typeface, size: Float, color: Int, width: Int, spacing: Float, maxLines: Int = Int.MAX_VALUE) =
        StaticLayout.Builder.obtain(text, 0, text.length, paint(face, size, color), width.coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
            .setLineSpacing(0f, spacing)
            .setIncludePad(true)
            .setMaxLines(maxLines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()

    /** The largest size from [max] down to [min] at which [text] fits [room]; at [min] it is cut with "…". */
    private fun fit(text: String, face: Typeface, max: Float, min: Float, color: Int, width: Int, room: Float, spacing: Float): StaticLayout {
        var size = max
        while (size > min) {
            val l = layout(text, face, size, color, width, spacing)
            if (l.height <= room) return l
            size *= 0.92f
        }
        val probe = layout(text, face, min, color, width, spacing)
        val perLine = probe.height.toFloat() / probe.lineCount.coerceAtLeast(1)
        return layout(text, face, min, color, width, spacing, maxLines = (room / perLine).toInt().coerceAtLeast(1))
    }

    companion object {
        private val PILLARS = listOf(31f to 51f, 41f to 43f, 51f to 35f, 61f to 43f, 71f to 51f)
        fun arabicDigits(n: Int) = n.toString().map { if (it.isDigit()) '٠' + (it - '0') else it }.joinToString("")
    }
}
