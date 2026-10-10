package io.github.sammynashed.emran.ui.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
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
    private const val APP_URL_SHORT = "github.com/SammyNashed/Emran"

    private const val W = 1080
    private const val H = 1350

    private val INDIGO = Color.parseColor("#6366F1")
    private val ORANGE = Color.parseColor("#F97316")

    class Content(
        /** The Arabic text; the heart of the card. */
        val arabic: String,
        /** English translation (or transliteration); omitted for Arabic-language users. */
        val english: String? = null,
        /** Narrator line shown above the English, if any. */
        val narrator: String? = null,
        /** "Sahih Muslim · 1234", "Qur'an, Al-Baqarah 2:255". */
        val source: String
    )

    fun share(context: Context, content: Content, lang: String) {
        val file = render(context, content, lang)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, caption(content, lang))
            clipData = ClipData.newRawUri("Emran", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

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
        val amiri = ResourcesCompat.getFont(context, R.font.amiri_regular) ?: Typeface.SERIF
        val amiriBold = ResourcesCompat.getFont(context, R.font.amiri_bold) ?: Typeface.SERIF
        val outfit = ResourcesCompat.getFont(context, R.font.outfit) ?: Typeface.SANS_SERIF
        val readex = ResourcesCompat.getFont(context, R.font.readex_pro) ?: Typeface.SANS_SERIF
        val ui = if (arabicUi) readex else outfit

        drawBackground(c)
        drawHeader(c, context, ui, arabicUi)

        // The panel that holds the words.
        val panel = RectF(60f, 300f, W - 60f, 1000f)
        c.drawRoundRect(panel, 48f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(30, 255, 255, 255) })
        c.drawRoundRect(panel, 48f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.argb(60, 255, 255, 255)
        })
        drawWords(c, panel, content, arabicUi, amiri, amiriBold, ui)
        drawFooter(c, ui, arabicUi)

        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val cutoff = System.currentTimeMillis() - 60 * 60 * 1000
        dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        val out = File(dir, "emran-${System.currentTimeMillis()}.png")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return out
    }

    private fun drawBackground(c: Canvas) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, H.toFloat(), intArrayOf(Color.parseColor("#0A0919"), Color.parseColor("#17123A"), Color.parseColor("#26112C")), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        // Two soft glows in the app's own indigo and orange.
        val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        glow.shader = RadialGradient(120f, 160f, 700f, Color.argb(120, 99, 102, 241), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), glow)
        glow.shader = RadialGradient(W - 100f, H - 120f, 640f, Color.argb(95, 249, 115, 22), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), glow)
        // Astrolabe-style rings, the motif of the home screen's prayer wheel.
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.argb(28, 255, 255, 255) }
        for (r in listOf(430f, 520f, 610f)) c.drawCircle(W / 2f, 170f, r, ring)
    }

    private fun drawHeader(c: Canvas, context: Context, ui: Typeface, arabicUi: Boolean) {
        val size = 128
        val icon = context.packageManager.getApplicationIcon(context.packageName)
        val ib = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, size, size)
        icon.draw(Canvas(ib))
        val left = (W - size) / 2f
        val clip = Path().apply { addRoundRect(RectF(left, 72f, left + size, 72f + size), 34f, 34f, Path.Direction.CW) }
        c.save(); c.clipPath(clip); c.drawBitmap(ib, left, 72f, null); c.restore()
        ib.recycle()

        val name = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; typeface = ui; textSize = 50f; textAlign = Paint.Align.CENTER
            if (!arabicUi) letterSpacing = 0.28f
            isFakeBoldText = true
        }
        c.drawText(if (arabicUi) "عِمران" else "EMRAN", W / 2f, 250f, name)
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
        val english = if (arabicUi) null else listOfNotNull(content.narrator, content.english).joinToString(" ").replace(Regex("\\s+"), " ").trim().ifBlank { null }

        val arPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = amiri }
        val enPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(225, 255, 255, 255); typeface = ui }

        // Largest type that lets everything fit; if even the smallest does not, cut with an ellipsis.
        var arLayout: StaticLayout? = null
        var enLayout: StaticLayout? = null
        val gap = if (english != null) 34f else 0f
        var size = 62f
        while (true) {
            arPaint.textSize = size
            enPaint.textSize = size * 0.56f
            val a = layout(arabic, arPaint, width, rtl = true, spacingAdd = size * 0.32f)
            val e = english?.let { layout(it, enPaint, width, rtl = false, spacingAdd = 6f) }
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
                enLayout = layout(english, enPaint, width, rtl = false, maxLines = ((avail - arLayout.height - gap) / enLine).toInt().coerceAtLeast(1), spacingAdd = 6f)
            }
        }

        val total = arLayout.height + gap + (enLayout?.height ?: 0)
        var y = panel.top + pad * 0.8f + (avail - total).coerceAtLeast(0f) / 2f
        c.save(); c.translate(panel.left + pad, y); arLayout.draw(c); c.restore()
        y += arLayout.height + gap
        if (enLayout != null) { c.save(); c.translate(panel.left + pad, y); enLayout.draw(c); c.restore() }

        // Where it comes from, in the orange of the app's accents.
        val src = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ORANGE; typeface = ui; textSize = 30f; textAlign = Paint.Align.CENTER; isFakeBoldText = true
        }
        c.drawText(TextUtils.ellipsize(content.source, src, width.toFloat(), TextUtils.TruncateAt.END).toString(), panel.centerX(), panel.bottom - 34f, src)
    }

    private fun drawFooter(c: Canvas, ui: Typeface, arabicUi: Boolean) {
        val centerX = W / 2f
        val pitch = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = ui; textSize = 40f; isFakeBoldText = true }
        val sub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 255, 255, 255); typeface = ui; textSize = 30f }
        val line1 = if (arabicUi) "مواقيت الصلاة، القبلة، القرآن، الأذكار والأحاديث" else "Prayer times, Qibla, Quran, adhkar & hadith"
        val line2 = if (arabicUi) "مجاني · مفتوح المصدر · يعمل دون إنترنت" else "Free  ·  Open source  ·  Fully offline"
        val w = W - 2 * 80
        val l1 = layout(line1, pitch, w, rtl = arabicUi, maxLines = 2)
        c.save(); c.translate(80f, 1042f); l1.draw(c); c.restore()
        val l2 = layout(line2, sub, w, rtl = arabicUi, maxLines = 1)
        c.save(); c.translate(80f, 1042f + l1.height + 6f); l2.draw(c); c.restore()

        // The call to action.
        val cta = (if (arabicUi) "حمّل عِمران  ·  " else "Get Emran  ·  ") + APP_URL_SHORT
        val ctaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = ui; textSize = 34f; isFakeBoldText = true }
        while (ctaPaint.measureText(cta) > W - 2 * 96 && ctaPaint.textSize > 20f) ctaPaint.textSize -= 1f
        val tw = ctaPaint.measureText(cta)
        val pill = RectF(centerX - tw / 2 - 44f, 1214f, centerX + tw / 2 + 44f, 1214f + 84f)
        val pp = Paint(Paint.ANTI_ALIAS_FLAG)
        pp.shader = LinearGradient(pill.left, 0f, pill.right, 0f, INDIGO, ORANGE, Shader.TileMode.CLAMP)
        c.drawRoundRect(pill, 42f, 42f, pp)
        val fm = ctaPaint.fontMetrics
        c.drawText(cta, centerX - tw / 2, pill.centerY() - (fm.ascent + fm.descent) / 2, ctaPaint)
    }
}
