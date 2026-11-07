package io.github.sammynashed.emran.glance.art

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import org.json.JSONArray
import org.json.JSONObject

/**
 * Widget text in the thmanyah typeface, drawn from pre-rendered word images (assets/widget_type, made by
 * tools/build_widget_type.py). The font's license forbids shipping its files in an extractable form, so the app
 * holds only pictures of the words and digits the widgets show, never the font. A text with a word that has no
 * picture returns false and the caller falls back to a bundled font.
 */
object TypeArt {
    private const val DIR = "widget_type"
    private class Entry(val file: String, val adv: Float, val ox: Float, val oy: Float, val w: Float, val h: Float)
    private class Style(val cap: Float, val space: Float, val table: Map<String, Entry>)
    class Cut(val file: String, val ox: Float, val oy: Float, val w: Float, val h: Float, val adv: Float, val strokeMid: Float)

    private var styles: Map<String, Style> = emptyMap()
    private var cuts: Map<String, Cut> = emptyMap()
    private val bitmaps = object : LinkedHashMap<String, Bitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) = size > 120
    }
    private lateinit var appContext: Context
    /** Words asked for with no picture: the debug dump writes them out, so the word list can be completed. */
    val missing = java.util.Collections.synchronizedSet(LinkedHashSet<String>())

    @Synchronized
    fun init(context: Context) {
        if (styles.isNotEmpty()) return
        appContext = context.applicationContext
        val root = runCatching {
            JSONObject(context.assets.open("$DIR/index.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
        }.getOrNull() ?: return
        val st = root.getJSONObject("styles")
        styles = st.keys().asSequence().associateWith { k ->
            val o = st.getJSONObject(k)
            val t = o.getJSONObject("t")
            Style(o.getDouble("cap").toFloat(), o.getDouble("space").toFloat(), t.keys().asSequence().associateWith { w -> entry(t.getJSONArray(w)) })
        }
        val cs = root.getJSONObject("cuts")
        cuts = cs.keys().asSequence().associateWith { k ->
            val a = cs.getJSONArray(k)
            Cut(a.getString(0), a.f(2), a.f(3), a.f(4), a.f(5), a.f(1), a.f(6))
        }
    }

    private fun JSONArray.f(i: Int) = getDouble(i).toFloat()
    private fun entry(a: JSONArray) = Entry(a.getString(0), a.f(1), a.f(2), a.f(3), a.f(4), a.f(5))

    @Synchronized
    private fun mask(file: String): Bitmap? = bitmaps[file] ?: runCatching {
        appContext.assets.open("$DIR/$file").use { BitmapFactory.decodeStream(it) }?.let { full ->
            full.extractAlpha().also { full.recycle(); bitmaps[file] = it }
        }
    }.getOrNull()

    private val NUMBER = Regex("[0-9٠-٩:%./]+")

    /** Lays [text] into visual left-to-right order. Null entries are word spaces. Returns null when a word has no picture. */
    private fun layout(text: String, style: Style): List<Entry?>? {
        val rtl = text.any { it in '؀'..'ۿ' && it !in '٠'..'٩' }
        val segments = text.trim().split(Regex("\\s+")).map { seg ->
            val pieces = ArrayList<List<String>>()
            var rest = seg
            while (rest.isNotEmpty()) {
                val n = NUMBER.matchAt(rest, 0)
                if (n != null) {
                    pieces += n.value.map { it.toString() }   // digits always read left to right
                    rest = rest.substring(n.value.length)
                    continue
                }
                val c = rest[0]
                if (c in ",،·()") { pieces += listOf(c.toString()); rest = rest.substring(1); continue }
                val end = rest.indexOfFirst { it in ",،·()" || NUMBER.matches(it.toString()) }.let { if (it < 0) rest.length else it }
                pieces += listOf(rest.substring(0, end))
                rest = rest.substring(end)
            }
            if (rtl) pieces.reversed().flatten() else pieces.flatten()
        }
        val out = ArrayList<Entry?>()
        (if (rtl) segments.reversed() else segments).forEachIndexed { i, toks ->
            if (i > 0) out += null
            toks.forEach { t ->
                val e = style.table[t]
                if (e == null) { missing += t; return null }
                out += e
            }
        }
        return out
    }

    private fun widthOf(line: List<Entry?>, style: Style, size: Float) = line.sumOf { ((it?.adv ?: style.space) * size).toDouble() }.toFloat()

    /** The width [text] will take at [size], or null when it can't be drawn from pictures. */
    fun measure(text: String, styleKey: String, size: Float): Float? {
        val st = styles[styleKey] ?: return null
        return widthOf(layout(text, st) ?: return null, st, size)
    }

    /** Draws [text] with its capitals' middle at [cy]; [p] gives colour, shader and alignment. False when it can't. */
    fun draw(c: Canvas, text: String, x: Float, cy: Float, styleKey: String, size: Float, p: Paint): Boolean {
        val st = styles[styleKey] ?: return false
        val line = layout(text, st) ?: return false
        val width = widthOf(line, st, size)
        var pen = when (p.textAlign) { Paint.Align.CENTER -> x - width / 2f; Paint.Align.RIGHT -> x - width; else -> x }
        val base = cy + st.cap * size / 2f
        val ink = Paint(p).apply { style = Paint.Style.FILL; isFilterBitmap = true; isAntiAlias = true }
        for (e in line) {
            if (e == null) { pen += st.space * size; continue }
            val m = mask(e.file) ?: return false
            c.drawBitmap(m, null, RectF(pen + e.ox * size, base + e.oy * size, pen + (e.ox + e.w) * size, base + (e.oy + e.h) * size), ink)
            pen += e.adv * size
        }
        return true
    }

    /** Draws [text] (display style) at the largest size whose ink fits [box]. [edge]: 0 centres, -1 and 1 hold it left or right. */
    fun drawInBox(c: Canvas, text: String, box: RectF, p: Paint, edge: Int): Boolean {
        val st = styles["D"] ?: return false
        val line = layout(text, st) ?: return false
        var pen = 0f; var l = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var t = Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (e in line) {
            if (e == null) { pen += st.space; continue }
            l = minOf(l, pen + e.ox); r = maxOf(r, pen + e.ox + e.w); t = minOf(t, e.oy); b = maxOf(b, e.oy + e.h)
            pen += e.adv
        }
        if (r <= l || b <= t) return false
        val size = minOf(box.width() / (r - l), box.height() / (b - t))
        val left = when { edge < 0 -> box.left; edge > 0 -> box.right - (r - l) * size; else -> box.centerX() - (r - l) * size / 2f }
        val top = box.centerY() - (b - t) * size / 2f
        val ink = Paint(p).apply { style = Paint.Style.FILL; isFilterBitmap = true; isAntiAlias = true; textAlign = Paint.Align.LEFT }
        pen = left - l * size
        val base = top - t * size
        for (e in line) {
            if (e == null) { pen += st.space * size; continue }
            val m = mask(e.file) ?: return false
            c.drawBitmap(m, null, RectF(pen + e.ox * size, base + e.oy * size, pen + (e.ox + e.w) * size, base + (e.oy + e.h) * size), ink)
            pen += e.adv * size
        }
        return true
    }

    fun cut(key: String): Cut? = cuts[key]
    fun cutMask(cut: Cut): Bitmap? = mask(cut.file)
    fun digitBounds(text: String): FloatArray? {
        val st = styles["D"] ?: return null
        var adv = 0f; var top = 0f
        for (ch in text) { val e = st.table[ch.toString()] ?: return null; adv += e.adv; top = minOf(top, e.oy) }
        return floatArrayOf(adv, -top)
    }
}
