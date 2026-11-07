package io.github.sammynashed.emran.ui.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import io.github.sammynashed.emran.R
import io.github.sammynashed.emran.data.hadith.Hadith
import io.github.sammynashed.emran.data.hadith.HadithBook
import io.github.sammynashed.emran.data.hadith.HadithParts
import java.io.File

/**
 * The share pictures for hadith and adhkar: real text laid over artwork that holds no words.
 *
 * Each picture is in the app's language only. A hadith is an open book: the stamp and the chain of narrators on the
 * right page, the words on the left, and more spreads when it is long. A dhikr sits in the sky over an old city at the hour it is said. Nothing is ever cut off:
 * text that runs long continues on the next picture, and text too long for that many pictures goes as plain text.
 */
object PaperShare {
    private const val W = 1080
    private const val H = 1350
    private const val MAX_SPREADS = 8
    private const val MAX_SKIES = 6

    private const val STAMP = 150f
    private const val PAGE_TOP = 292f
    private const val PAGE_BOTTOM = 925f
    private val RIGHT = RectF(548f, PAGE_TOP, 938f, PAGE_BOTTOM)
    private val LEFT = RectF(146f, PAGE_TOP, 514f, PAGE_BOTTOM)
    private val INK = Color.parseColor("#231509")
    private val NAME_INK = Color.parseColor("#4A3520")
    private val RED = Color.parseColor("#8E2413")
    private val MULTIPLY = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)

    // ---- what the app calls ------------------------------------------------

    fun shareHadith(context: Context, book: HadithBook?, lang: String, hadith: Hadith, plainText: String) {
        val app = context.applicationContext
        val files = renderHadith(app, book, lang, hadith)
        if (files == null) sendText(context, plainText) else sendImages(context, files, ShareCard.pitchCaption(lang))
    }

    fun shareDhikr(context: Context, folderId: String?, title: String, arabic: String, english: String?, source: String?, lang: String, plainText: String) {
        val app = context.applicationContext
        val files = renderDhikr(app, folderId, title, arabic, english, source, lang)
        if (files == null) sendText(context, plainText) else sendImages(context, files, ShareCard.pitchCaption(lang))
    }

    private fun sendText(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun sendImages(context: Context, files: List<File>, caption: String) {
        val uris = ArrayList<Uri>(files.map { FileProvider.getUriForFile(context, "${context.packageName}.share", it) })
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]) }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris) }
        }.apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_TEXT, caption)
            clipData = ClipData.newRawUri("Emran", uris[0]).also { c -> uris.drop(1).forEach { c.addItem(ClipData.Item(it)) } }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // ---- shared drawing ------------------------------------------------------

    private fun art(context: Context, name: String): Bitmap {
        val raw = context.assets.open("share/$name.webp").use { BitmapFactory.decodeStream(it) }
        return Bitmap.createScaledBitmap(raw, W, H, true).copy(Bitmap.Config.ARGB_8888, true)
            .also { if (raw.width != W) raw.recycle() }
    }

    private fun readex(context: Context) = ResourcesCompat.getFont(context, R.font.readex_pro) ?: Typeface.SANS_SERIF
    private fun amiriBold(context: Context) = ResourcesCompat.getFont(context, R.font.amiri_bold) ?: Typeface.SERIF
    private fun amiri(context: Context) = ResourcesCompat.getFont(context, R.font.amiri_regular) ?: Typeface.SERIF

    private fun layout(text: String, paint: TextPaint, width: Int, lineMult: Float, rtl: Boolean = true): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setLineSpacing(0f, lineMult)
            .setIncludePad(false)
            .setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_BALANCED)
            .build()

    /** One page's worth of a longer layout: the lines from [first] up to, not including, [end]. */
    private class Slice(val layout: StaticLayout, val first: Int, val end: Int) {
        val height get() = layout.getLineTop(end) - layout.getLineTop(first)
    }

    /** Cuts [l] into pages at whole lines. The first page may be shorter, when something sits above it. */
    private fun slices(l: StaticLayout, pageHeight: Int, firstPage: Int = pageHeight): List<Slice> {
        val out = ArrayList<Slice>()
        var first = 0
        while (first < l.lineCount) {
            val room = if (out.isEmpty()) firstPage else pageHeight
            var end = first + 1
            while (end < l.lineCount && l.getLineTop(end + 1) - l.getLineTop(first) <= room) end++
            out += Slice(l, first, end)
            first = end
        }
        return out
    }

    private fun Canvas.drawSlice(s: Slice, area: RectF) {
        val x = area.left + (area.width() - s.layout.width) / 2f
        val y = area.top + ((area.height() - s.height) / 2f).coerceAtLeast(0f)
        save()
        translate(x, y)
        clipRect(0f, 0f, s.layout.width.toFloat(), s.height.toFloat() + 2f)
        translate(0f, -s.layout.getLineTop(s.first).toFloat())
        s.layout.draw(this)
        restore()
    }

    /** Emran's own icon and name, top middle of every picture. */
    private fun Canvas.drawBrand(ui: Typeface, ar: Boolean) {
        ShareCard.drawLauncherIcon(this, W / 2f, 70f, 92f)
        val name = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; typeface = ui; textSize = 28f; textAlign = Paint.Align.CENTER
            setShadowLayer(10f, 0f, 1f, Color.argb(200, 0, 0, 0))
        }
        drawText(if (ar) "عِمران" else "Emran", W / 2f, 150f, name)
    }

    private fun save(context: Context, bmp: Bitmap, index: Int): File {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val cutoff = System.currentTimeMillis() - 60 * 60 * 1000
        dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        val out = File(dir, "emran-${System.currentTimeMillis()}-${index + 1}.jpg")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bmp.recycle()
        return out
    }

    private fun digits(ref: String, ar: Boolean): String =
        if (ar && ref.all { it.isDigit() }) ref.map { '٠' + (it - '0') }.joinToString("") else ref

    // ---- the book --------------------------------------------------------------

    /** The pictures for a hadith, or null when it needs more than [MAX_SPREADS] and so goes as text. */
    fun renderHadith(context: Context, book: HadithBook?, lang: String, hadith: Hadith): List<File>? {
        val ar = lang == "ar"
        // The picture speaks the app's language. A hadith with no translation can only be shown in Arabic.
        val english = hadith.english.takeIf { !ar && it.isNotBlank() }
        return book(context, book, lang, hadith, english)
    }

    /** The lines under the stamp: the book's full name and its grade, as far as there is anything to say. */
    private fun titleLines(book: HadithBook?, lang: String, hadith: Hadith): List<String> =
        listOfNotNull(
            book?.title(lang)?.takeIf { hadith.grade.isNotEmpty() },
            hadith.gradeLabel(lang),
        )

    /** Draws the stamp and its lines at the top of [page], and returns where the text below them may start. */
    private fun Canvas.drawHeader(context: Context, page: RectF, book: HadithBook?, lang: String, hadith: Hadith, ui: Typeface, stamp: Float = STAMP, top: Float = page.top): Float {
        val name = if (lang == "ar") book?.titleAr.orEmpty().removePrefix("مختارات صحيحة من ")
        else book?.titleEn.orEmpty().removePrefix("Sahih selection from ")
        drawStamp(this, page.centerX(), top + stamp / 2f, stamp, name, digits(hadith.ref, lang == "ar"), amiriBold(context))
        var y = top + stamp + 14f
        val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = NAME_INK; typeface = ui; textSize = 20f; textAlign = Paint.Align.CENTER; xfermode = MULTIPLY
        }
        for (line in titleLines(book, lang, hadith)) {
            y += 24f
            drawText(android.text.TextUtils.ellipsize(line, small, page.width(), android.text.TextUtils.TruncateAt.END).toString(), page.centerX(), y, small)
            y += 6f
        }
        return y + 14f
    }

    private fun headerHeight(book: HadithBook?, lang: String, hadith: Hadith) =
        STAMP + 14f + titleLines(book, lang, hadith).size * 30f + 14f

    private fun Canvas.drawPageNumber(s: Int, spreads: Int, ar: Boolean, ui: Typeface) {
        if (spreads < 2) return // Sits under the page that ends the spread.
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = NAME_INK; typeface = ui; textSize = 22f; textAlign = Paint.Align.CENTER; xfermode = MULTIPLY
        }
        drawText("${digits("${s + 1}", ar)} / ${digits("$spreads", ar)}", (if (ar) LEFT else RIGHT).centerX(), PAGE_BOTTOM + 18f, p)
    }

    /**
     * One language throughout. With a chain: stamp and chain on the right page, the words on the left. Without one: a
     * short hadith faces a large stamp, and a longer one starts under the stamp, so no page sits empty. [english] is
     * the translation, which names its own narrator, so it has no separate chain.
     */
    private fun book(context: Context, book: HadithBook?, lang: String, hadith: Hadith, english: String?): List<File>? {
        val ar = lang == "ar"
        val ui = readex(context)
        val rtl = english == null
        val parts = if (english != null) HadithParts(emptyList(), "", "", english) else HadithParts.of(hadith.arabic)
        val lineMult = if (rtl) 1.55f else 1.38f
        val ink = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; typeface = ui; xfermode = MULTIPLY }
        val width = LEFT.width().toInt()
        val pageH = (PAGE_BOTTOM - PAGE_TOP).toInt()
        val head = headerHeight(book, lang, hadith)
        val room = (pageH - head).toInt()

        val note = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = NAME_INK; typeface = ui; textSize = 23f; xfermode = MULTIPLY }
        val isnad = parts.isnad.takeIf { it.isNotBlank() }?.let { layout(it, note, RIGHT.width().toInt(), 1.5f) }
        val rightIsFull = parts.chain.isNotEmpty() || (isnad != null && isnad.height <= room)

        // Everything that is not on the right page already.
        val body = if (rightIsFull || parts.isnad.isBlank()) parts.words else parts.isnad + "\n" + parts.words

        // Large, and broken at its clauses, when it all fits on one page.
        val clauses = if (rtl) body.replace(Regex("،\\s+"), "،\n") else body
        val largest = if (rtl) 38 else 34
        val smallest = if (rtl) 31 else 28
        var single: StaticLayout? = null
        for (size in largest downTo smallest) {
            ink.textSize = size.toFloat()
            val l = layout(clauses, ink, width, lineMult, rtl)
            if (l.height <= pageH) { single = l; break }
        }
        val fits = single != null
        if (!fits) ink.textSize = smallest.toFloat()
        val flow = single?.let { slices(it, pageH) }
            ?: if (rightIsFull) slices(layout(body, ink, width, lineMult, rtl), pageH)
            else slices(layout(body, ink, width, lineMult, rtl), pageH, room)
        // Where the text starts: on the left page, or on the right page under the stamp.
        val startRight = !rightIsFull && !fits
        val spreads = if (startRight) (flow.size + 1) / 2 else 1 + flow.size / 2
        if (spreads > MAX_SPREADS) return null

        // A book opens from the side its language starts on: the right page for Arabic, the left for English.
        val firstPage = if (rtl) RIGHT else LEFT
        val nextPage = if (rtl) LEFT else RIGHT
        return List(spreads) { s ->
            val bmp = art(context, "book")
            val c = Canvas(bmp)
            when {
                s == 0 && !rightIsFull && fits -> {
                    val big = 230f
                    val lines = titleLines(book, lang, hadith).size * 30f
                    c.drawHeader(context, firstPage, book, lang, hadith, ui, big, PAGE_TOP + (pageH - big - lines) / 2f)
                    c.drawSlice(flow[0], nextPage)
                }
                s == 0 -> {
                    val y = c.drawHeader(context, firstPage, book, lang, hadith, ui)
                    val below = RectF(firstPage.left, y, firstPage.right, PAGE_BOTTOM)
                    when {
                        parts.chain.isNotEmpty() -> drawChain(c, parts, below, ui)
                        rightIsFull -> c.drawSlice(Slice(isnad!!, 0, isnad.lineCount), below)
                        else -> c.drawSlice(flow[0], below)
                    }
                    flow.getOrNull(if (startRight) 1 else 0)?.let { c.drawSlice(it, nextPage) }
                }
                else -> {
                    val i = if (startRight) s * 2 else 1 + (s - 1) * 2
                    flow.getOrNull(i)?.let { c.drawSlice(it, firstPage) }
                    flow.getOrNull(i + 1)?.let { c.drawSlice(it, nextPage) }
                }
            }
            c.drawPageNumber(s, spreads, ar, ui)
            c.drawBrand(ui, ar)
            save(context, bmp, s)
        }
    }

    /** A scribe's round ink stamp: the book around the rim, the number in the middle. */
    private fun drawStamp(c: Canvas, cx: Float, cy: Float, size: Float, name: String, ref: String, face: Typeface) {
        val r = size / 2f
        c.save()
        c.rotate(-8f, cx, cy)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RED; style = Paint.Style.STROKE; xfermode = MULTIPLY }
        line.strokeWidth = size * 0.027f
        c.drawCircle(cx, cy, r * 0.94f, line)
        line.strokeWidth = size * 0.013f
        c.drawCircle(cx, cy, r * 0.55f, line)
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = RED; typeface = face; xfermode = MULTIPLY; textAlign = Paint.Align.CENTER }
        text.textSize = size * 0.135f
        val rim = Path().apply { addArc(RectF(cx - r * 0.66f, cy - r * 0.66f, cx + r * 0.66f, cy + r * 0.66f), 135f, 270f) }
        c.drawTextOnPath(name, rim, 0f, 0f, text)
        if (ref.isNotEmpty()) {
            text.textSize = size * (if (ref.length > 5) 0.13f else 0.21f)
            c.drawText(ref, cx, cy + text.textSize * 0.34f, text)
        }
        c.restore()
    }

    /** One thin thread down the page, name by name, ending where the Prophet ﷺ is named. */
    private fun drawChain(c: Canvas, parts: HadithParts, area: RectF, ui: Typeface) {
        val size = if (parts.chain.size > 6) 23f else 27f
        val gap = if (parts.chain.size > 6) 8f else 12f
        val name = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = NAME_INK; typeface = ui; textSize = size; textAlign = Paint.Align.CENTER; xfermode = MULTIPLY }
        val lead = TextPaint(name).apply { color = RED; textSize = 32f }
        val thread = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RED; strokeWidth = 1.6f; alpha = 180; xfermode = MULTIPLY }
        var y = area.top + size
        val cx = area.centerX()
        for (n in parts.chain) {
            c.drawText(n, cx, y, name)
            c.drawLine(cx, y + size * 0.36f, cx, y + size * 0.36f + gap, thread)
            y += size * 1.26f + gap
        }
        c.drawText(parts.lead, cx, y + 8f, lead)
    }

    // ---- the sky ---------------------------------------------------------------

    private fun skyFor(folderId: String?) = when (folderId) {
        "evening" -> "dusk"
        "prayer", "sleep" -> "night"
        else -> "dawn"
    }

    /** The pictures for a dhikr, or null when it needs more than [MAX_SKIES] and so goes as text. */
    fun renderDhikr(context: Context, folderId: String?, title: String, arabic: String, english: String?, source: String?, lang: String): List<File>? {
        val ar = lang == "ar"
        val sky = skyFor(folderId)
        val onLight = sky == "dawn"
        val inkColor = if (onLight) Color.parseColor("#13223F") else Color.WHITE
        val ui = readex(context)
        // The words sit on a glass card; the title has its own room below the brand.
        val card = RectF(56f, 300f, W - 56f, 840f)
        val area = RectF(card.left + 48f, card.top + 40f, card.right - 48f, card.bottom - 40f)
        val pageH = area.height().toInt()
        val width = area.width().toInt()
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor; typeface = amiri(context)
            
        }
        // The picture speaks the app's language. A dhikr with no translation can only be shown in Arabic.
        val translation = english?.takeIf { !ar && it.isNotBlank() }
        val rtl = translation == null
        val text = (translation ?: arabic).replace(Regex("\\s+"), " ").trim()
        if (!rtl) p.typeface = ui
        val lineMult = if (rtl) 1.55f else 1.38f
        val smallest = if (rtl) 36 else 30
        var together: StaticLayout? = null
        for (size in (if (rtl) 70 else 58) downTo smallest step 2) {
            p.textSize = size.toFloat()
            val l = layout(text, p, width, lineMult, rtl)
            if (l.height <= pageH) { together = l; break }
        }
        val flow: List<Slice> = if (together != null) listOf(Slice(together, 0, together.lineCount)) else {
            p.textSize = smallest.toFloat()
            slices(layout(text, p, width, lineMult, rtl), pageH)
        }
        if (flow.size > MAX_SKIES) return null

        val files = ArrayList<File>()
        for ((i, page) in flow.withIndex()) {
            val bmp = art(context, sky)
            val c = Canvas(bmp)
            val head = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = inkColor; typeface = ui; textSize = 28f; textAlign = Paint.Align.CENTER }
            val label = if (flow.size > 1) "$title · ${digits("${i + 1}", ar)} / ${digits("${flow.size}", ar)}" else title
            c.drawText(android.text.TextUtils.ellipsize(label, head, W - 120f, android.text.TextUtils.TruncateAt.END).toString(), W / 2f, 252f, head)
            val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (onLight) Color.argb(120, 255, 255, 255) else Color.argb(110, 8, 14, 32)
            }
            c.drawRoundRect(card, 56f, 56f, body)
            c.drawRoundRect(card, 56f, 56f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 2.5f
                color = if (onLight) Color.argb(200, 255, 255, 255) else Color.argb(70, 255, 255, 255)
            })
            c.drawSlice(page, area)
            c.drawBrand(ui, ar)
            source?.takeIf { it.isNotBlank() }?.let {
                val s = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE; typeface = ui; textSize = 24f; textAlign = Paint.Align.CENTER
                    setShadowLayer(8f, 0f, 1f, Color.argb(160, 0, 0, 0))
                }
                c.drawText(android.text.TextUtils.ellipsize(it, s, W - 120f, android.text.TextUtils.TruncateAt.END).toString(), W / 2f, H - 44f, s)
            }
            files += save(context, bmp, i)
        }
        return files
    }
}
