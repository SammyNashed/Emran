package io.github.sammynashed.emran.ui.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint

import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import io.github.sammynashed.emran.R
import java.io.File

/**
 * Turns a hadith or dhikr into a share-ready picture that also says what Emran is and
 * where to get it.
 *
 * Drawn straight onto a Canvas, so nothing is downloaded and no image ships in the APK.
 * Android's own text engine shapes the Arabic (joining, diacritics, right-to-left), which
 * a hand-rolled renderer would get wrong. The picture is written to the cache directory,
 * and old ones are deleted on every share, so it never accumulates.
 */
object ShareCard {
    const val APP_URL = "https://github.com/SammyNashed/Emran"

    private const val W = 1080
    private const val H = 1350

    class Content(
        /** The Arabic text; the heart of the card. */
        val arabic: String,
        /** English translation (or transliteration); omitted for Arabic-language users. */
        val english: String? = null,
        /** Narrator line shown above the English, if any. */
        val narrator: String? = null,
        /** "Sahih Muslim · 1234", "Qur'an, Al-Baqarah 2:255". */
        val source: String,
        /** Quran text is drawn in the Mushaf face, as in the app's reader. */
        val quran: Boolean = false,
    )

    /**
     * [withCaption] true sends the words as the caption too (hadith, adhkar). False sends the picture
     * with only a short "Get Emran + link" line as its caption; the words stay in the image.
     */
    fun share(context: Context, content: Content, lang: String, withCaption: Boolean = true) {
        val file = render(context, content, lang)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, if (withCaption) caption(content, lang) else downloadCaption(lang))
            clipData = ClipData.newRawUri("Emran", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Just the invitation; the picture already carries the words. */
    fun downloadCaption(lang: String): String =
        (if (lang == "ar") "حمّل عِمران" else "Get Emran") + "\n" + APP_URL

    /** The text that travels with the picture (WhatsApp and others show it as the caption). */
    fun caption(content: Content, lang: String): String {
        val snippet = (if (lang == "ar" || content.english.isNullOrBlank()) content.arabic else content.english)
            .replace(Regex("\\s+"), " ").trim().let { if (it.length > 220) it.take(217).trimEnd() + "…" else it }
        val pitch = if (lang == "ar")
            "مواقيت الصلاة والقبلة والقرآن والأذكار والأحاديث في تطبيق عِمران — مجاني ومفتوح المصدر ويعمل دون إنترنت."
        else
            "Prayer times, Qibla, Quran, adhkar and hadith in Emran — free, open source and fully offline."
        return "$snippet\n— ${content.source}\n\n$pitch\n$APP_URL"
    }

    // ---- drawing --------------------------------------------------------

    fun render(context: Context, content: Content, lang: String): File {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val arabicUi = lang == "ar"
        val amiri = if (content.quran) ResourcesCompat.getFont(context, R.font.kfgqpc_hafs) ?: Typeface.SERIF
        else ResourcesCompat.getFont(context, R.font.amiri_regular) ?: Typeface.SERIF
        val amiriBold = ResourcesCompat.getFont(context, R.font.amiri_bold) ?: Typeface.SERIF
        val outfit = ResourcesCompat.getFont(context, R.font.inter) ?: Typeface.SANS_SERIF
        val readex = ResourcesCompat.getFont(context, R.font.readex_pro) ?: Typeface.SANS_SERIF
        val ui = if (arabicUi) readex else outfit

        drawBackground(c)
        drawHeader(c, ui, arabicUi)

        // The glass panel that holds the words, as on the app's cards.
        val panel = RectF(64f, 346f, W - 64f, H - 110f)
        drawGlass(c, panel)
        drawWords(c, panel, content, arabicUi, amiri, amiriBold, ui)

        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val cutoff = System.currentTimeMillis() - 60 * 60 * 1000
        dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        val out = File(dir, "emran-${System.currentTimeMillis()}.png")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return out
    }

    /** The app's night sky: deep navy down to teal at the horizon, a moon glow, and a scatter of stars. */
    private fun drawBackground(c: Canvas) {
        val sky = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, H.toFloat(),
                intArrayOf(Color.parseColor("#040A18"), Color.parseColor("#071626"), Color.parseColor("#123F55"), Color.parseColor("#1F6B80")),
                floatArrayOf(0f, 0.38f, 0.74f, 1f), Shader.TileMode.CLAMP,
            )
        }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), sky)
        // Soft moon glow behind the header.
        val glow = Paint().apply {
            shader = RadialGradient(W / 2f, 170f, 560f, Color.argb(70, 220, 230, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, W.toFloat(), 800f, glow)
        // Stars: fixed seed, so the card never shimmers between shares.
        val rnd = java.util.Random(26)
        val star = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(90) {
            val x = rnd.nextFloat() * W
            val y = rnd.nextFloat() * H * 0.62f
            val r = 1.1f + rnd.nextFloat() * 2.2f
            star.color = Color.argb((60 + rnd.nextInt(150)), 255, 255, 255)
            c.drawCircle(x, y, r, star)
        }
    }

    /** Liquid glass: a pale translucent body, a brighter top edge, and a hairline rim. */
    private fun drawGlass(c: Canvas, r: RectF) {
        val radius = 60f
        c.drawRoundRect(r, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, r.top, 0f, r.bottom, Color.argb(54, 255, 255, 255), Color.argb(22, 255, 255, 255), Shader.TileMode.CLAMP)
        })
        c.drawRoundRect(r, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2.5f
            shader = LinearGradient(0f, r.top, 0f, r.bottom, Color.argb(130, 255, 255, 255), Color.argb(34, 255, 255, 255), Shader.TileMode.CLAMP)
        })
    }

    private fun drawHeader(c: Canvas, ui: Typeface, arabicUi: Boolean) {
        // The launcher icon itself: dark tile, five pillars fading indigo to orange.
        val cx = W / 2f
        val cy = 150f
        val k = 150f / 72f                       // the icon's visible 72-unit square -> 150 px
        val ox = cx - 75f
        val oy = cy - 75f
        fun X(v: Float) = ox + (v - 18f) * k
        fun Y(v: Float) = oy + (v - 18f) * k
        val tile = RectF(ox, oy, ox + 150f, oy + 150f)
        c.drawRoundRect(tile, 40f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0A0A0A") })
        c.drawRoundRect(tile, 40f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, Y(18f), 0f, Y(90f), Color.argb(38, 99, 102, 241), Color.argb(38, 249, 115, 22), Shader.TileMode.CLAMP)
        })
        c.drawRoundRect(tile, 40f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.argb(60, 255, 255, 255)
        })
        listOf(51f to 31f, 43f to 41f, 35f to 51f, 43f to 61f, 51f to 71f).forEach { (top, left) ->
            val r = 3f * k
            val rect = RectF(X(left), Y(top), X(left + 6f), Y(73f))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, Y(top), 0f, Y(73f), Color.parseColor("#6366F1"), Color.parseColor("#F97316"), Shader.TileMode.CLAMP)
            }
            c.drawRoundRect(rect, r, r, paint)
            c.drawRect(rect.left, rect.top + r, rect.right, rect.bottom, paint)
        }
        val name = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; typeface = ui; textSize = 46f; textAlign = Paint.Align.CENTER
            if (!arabicUi) letterSpacing = 0.28f
        }
        c.drawText(if (arabicUi) "عِمران" else "EMRAN", cx, 296f, name)
    }

    private fun layout(text: String, paint: TextPaint, width: Int, rtl: Boolean, maxLines: Int = Int.MAX_VALUE, spacingAdd: Float = 0f, mult: Float = 1f): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setLineSpacing(spacingAdd, mult)
            .setIncludePad(true)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

    private fun drawWords(c: Canvas, panel: RectF, content: Content, arabicUi: Boolean, amiri: Typeface, amiriBold: Typeface, ui: Typeface) {
        val pad = 56f
        val width = (panel.width() - pad * 2).toInt()
        val sourceH = 70f
        val avail = panel.height() - pad * 1.6f - sourceH
        val arabic = content.arabic.replace(Regex("\\s+"), " ").trim()
        val english = listOfNotNull(content.narrator, content.english).joinToString(" ").replace(Regex("\\s+"), " ").trim().ifBlank { null }

        val arPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = amiri }
        val enPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(225, 255, 255, 255); typeface = ui }

        // Largest type that lets everything fit; if even the smallest does not, cut with an ellipsis.
        var arLayout: StaticLayout? = null
        var enLayout: StaticLayout? = null
        val gap = if (english != null) 34f else 0f
        var size = 96f
        while (true) {
            arPaint.textSize = size
            enPaint.textSize = minOf(size * 0.56f, 40f)
            val a = layout(arabic, arPaint, width, rtl = true, spacingAdd = size * 0.32f)
            val e = english?.let { layout(it, enPaint, width, rtl = arabicUi, spacingAdd = if (arabicUi) 12f else 6f) }
            if (a.height + gap + (e?.height ?: 0) <= avail || size <= 30f) { arLayout = a; enLayout = e; break }
            size -= 2f
        }
        if (arLayout!!.height + gap + (enLayout?.height ?: 0) > avail) {
            // Too long even at the smallest size: give Arabic ~60% of the room (all of it when alone).
            val arRoom = if (english != null) avail * 0.6f else avail
            val arLine = arLayout.height / arLayout.lineCount.coerceAtLeast(1)
            arLayout = layout(arabic, arPaint, width, rtl = true, maxLines = (arRoom / arLine).toInt().coerceAtLeast(1), spacingAdd = size * 0.32f)
            if (english != null) {
                val enLine = enLayout!!.height / enLayout.lineCount.coerceAtLeast(1)
                enLayout = layout(english, enPaint, width, rtl = arabicUi, maxLines = ((avail - arLayout.height - gap) / enLine).toInt().coerceAtLeast(1), spacingAdd = 6f)
            }
        }

        val total = arLayout.height + gap + (enLayout?.height ?: 0)
        var y = panel.top + pad * 0.8f + (avail - total).coerceAtLeast(0f) / 2f
        c.save(); c.translate(panel.left + pad, y); arLayout.draw(c); c.restore()
        y += arLayout.height + gap
        if (enLayout != null) { c.save(); c.translate(panel.left + pad, y); enLayout.draw(c); c.restore() }

        // Where it comes from, in the orange of the app's accents.
        val src = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 255, 255, 255); typeface = ui; textSize = 30f; textAlign = Paint.Align.CENTER; isFakeBoldText = true
        }
        c.drawText(TextUtils.ellipsize(content.source, src, width.toFloat(), TextUtils.TruncateAt.END).toString(), panel.centerX(), panel.bottom - 34f, src)
    }
}
