package io.github.sammynashed.emran.glance.art

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import io.github.sammynashed.emran.R
import io.github.sammynashed.emran.glance.state.EmranWidgetState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.util.Locale

/**
 * Draws Emran's widgets as pictures, Glassify-style: frosted glass panels, real calligraphy
 * (hand-cleaned artwork in res/drawable-nodpi/cal_*), Bodoni Moda for day names, Inter for
 * everything else. Widgets can't use an app's own fonts directly, so the design is drawn here
 * and shown as one image; only the live countdown is a real (system-ticked) view on top.
 */
object WidgetArt {

    enum class Kind { DAY_ARABIC, DAY_ENGLISH, PRAYER_PROGRESS, PRAYER_TABLE, NEXT_PRAYER, HIJRI_DATE, HIJRI_MONTH, AYAH, CLOCK, OCCASION }

    private const val WHITE = Color.WHITE
    private val SOFT = Color.argb(178, 255, 255, 255)
    private val FAINT = Color.argb(105, 255, 255, 255)

    private val faces = HashMap<String, Typeface>()

    /**
     * A weight (and optical size) of a bundled variable font from res/font, shared with the app.
     * Resources are stored compressed, so the font is unpacked to the cache once and loaded from
     * there with its variation (API 26+); older phones get the default weight.
     */
    private fun face(context: Context, res: Int, axes: String): Typeface = faces.getOrPut("$res|$axes") {
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) {
                val file = java.io.File(context.cacheDir, "fonts/$res.ttf")
                if (!file.exists()) {
                    file.parentFile?.mkdirs()
                    context.resources.openRawResource(res).use { input -> file.outputStream().use { input.copyTo(it) } }
                }
                Typeface.Builder(file).setFontVariationSettings(axes).build()
            } else androidx.core.content.res.ResourcesCompat.getFont(context, res) ?: Typeface.DEFAULT
        }.getOrDefault(Typeface.DEFAULT)
    }

    private fun inter(c: Context, w: Int) = face(c, R.font.inter, "'wght' $w, 'opsz' 14")
    private fun bodoni(c: Context) = face(c, R.font.bodoni_moda, "'wght' 400, 'opsz' 24")

    /** Which panels are clear glass (wallpaper shows through) and which are smoky, by default. */
    private fun dark(kind: Kind) = kind !in setOf(Kind.DAY_ARABIC, Kind.DAY_ENGLISH, Kind.AYAH)

    /**
     * The glass tints a widget can wear. Every one is deep enough for white text. [WALLPAPER]
     * takes the system's Material You colour (Android 12+), else falls back to Midnight.
     */
    enum class Tint(val label: String, val labelAr: String) {
        AUTO("Auto", "تلقائي"), CLEAR("Clear", "شفاف"), SMOKY("Smoky", "دخاني"), MIDNIGHT("Midnight", "ليلي"),
        ROSE("Rose", "وردي"), EMERALD("Emerald", "زمردي"), SAND("Sand", "رملي"), WALLPAPER("Wallpaper", "من الخلفية");
    }

    /** The glass body colour (with its alpha) for a tint; null means clear glass. */
    fun body(context: Context, tint: Tint, kind: Kind): Int? = when (tint) {
        Tint.AUTO -> if (dark(kind)) Color.argb(150, 14, 16, 22) else null
        Tint.CLEAR -> null
        Tint.SMOKY -> Color.argb(150, 14, 16, 22)
        Tint.MIDNIGHT -> Color.argb(160, 18, 30, 72)
        Tint.ROSE -> Color.argb(150, 112, 34, 66)
        Tint.EMERALD -> Color.argb(150, 12, 78, 62)
        Tint.SAND -> Color.argb(150, 118, 86, 46)
        Tint.WALLPAPER -> if (Build.VERSION.SDK_INT >= 31) {
            val c = context.resources.getColor(android.R.color.system_accent1_700, context.theme)
            Color.argb(165, Color.red(c), Color.green(c), Color.blue(c))
        } else Color.argb(160, 18, 30, 72)
    }

    fun render(context: Context, kind: Kind, state: EmranWidgetState, wDp: Float, hDp: Float, tint: Tint = Tint.AUTO): Bitmap {
        val density = context.resources.displayMetrics.density.coerceAtMost(2.75f)
        val w = (wDp * density).toInt().coerceAtLeast(1)
        val h = (hDp * density).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val d = density
        glass(c, w.toFloat(), h.toFloat(), d, body(context, tint, kind))
        val ar = state.language == "ar"
        when (kind) {
            Kind.DAY_ARABIC -> dayArabic(context, c, state, w, h, d, ar)
            Kind.DAY_ENGLISH -> dayEnglish(context, c, state, w, h, d)
            Kind.PRAYER_PROGRESS -> prayerProgress(context, c, state, w, h, d, ar)
            Kind.PRAYER_TABLE -> prayerTable(context, c, state, w, h, d, ar)
            Kind.NEXT_PRAYER -> nextPrayer(context, c, state, w, h, d, ar)
            Kind.HIJRI_DATE -> hijriDate(context, c, state, w, h, d, ar)
            Kind.HIJRI_MONTH -> hijriMonth(context, c, state, w, h, d, ar)
            Kind.AYAH -> ayah(context, c, state, w, h, d, ar)
            Kind.CLOCK -> clock(context, c, state, w, h, d, ar)
            Kind.OCCASION -> occasion(context, c, state, w, h, d, ar)
        }
        return bmp
    }

    // ---- glass ----------------------------------------------------------------------------

    /**
     * Frosted glass without a real backdrop blur (widgets can't blur the wallpaper): a body tint,
     * light gathering at the top, a bright hairline along the top edge that fades down the sides,
     * a faint rim, and a soft inner glow.
     */
    private fun glass(c: Canvas, w: Float, h: Float, d: Float, body: Int?) {
        val dark = body != null
        val r = 24f * d
        val inset = 1.5f * d
        val rect = RectF(inset, inset, w - inset, h - inset)
        c.drawRoundRect(rect, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = body ?: Color.argb(46, 255, 255, 255)
        })
        c.drawRoundRect(rect, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, h, Color.argb(if (dark) 34 else 52, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        })
        c.drawRoundRect(rect, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(w * 0.85f, 0f, w * 0.9f, Color.argb(if (dark) 18 else 30, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        })
        c.drawRoundRect(rect, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = d; color = Color.argb(38, 255, 255, 255)
        })
        c.drawRoundRect(rect, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = d
            shader = LinearGradient(0f, 0f, 0f, h * 0.4f, Color.argb(130, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        })
    }

    // ---- text helpers -----------------------------------------------------------------------

    private fun paint(face: Typeface, sizePx: Float, color: Int = WHITE, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textSize = sizePx; this.color = color; textAlign = align }

    /** Draws [text] with its vertical centre at [cy]. */
    private fun Canvas.text(text: String, x: Float, cy: Float, p: Paint) {
        val fm = p.fontMetrics
        drawText(text, x, cy - (fm.ascent + fm.descent) / 2f, p)
    }

    /** Shrinks the paint until [text] fits [maxW]. */
    private fun Paint.fit(text: String, maxW: Float): Paint {
        while (measureText(text) > maxW && textSize > 6f) textSize *= 0.95f
        return this
    }

    /** One of the calligraphy pieces, scaled to fit the box and centred in it. */
    private fun calligraphy(context: Context, c: Canvas, res: Int, box: RectF) {
        val art = BitmapFactory.decodeResource(context.resources, res) ?: return
        val s = minOf(box.width() / art.width, box.height() / art.height)
        val dw = art.width * s; val dh = art.height * s
        val dst = RectF(box.centerX() - dw / 2, box.centerY() - dh / 2, box.centerX() + dw / 2, box.centerY() + dh / 2)
        c.drawBitmap(art, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        art.recycle()
    }

    private fun today(state: EmranWidgetState): LocalDate = LocalDate.now(ZoneId.systemDefault())

    private val DAY_RES = intArrayOf(R.drawable.cal_day1, R.drawable.cal_day2, R.drawable.cal_day3, R.drawable.cal_day4, R.drawable.cal_day5, R.drawable.cal_day6, R.drawable.cal_day7)
    private val MONTH_RES = intArrayOf(
        R.drawable.cal_month_1, R.drawable.cal_month_2, R.drawable.cal_month_3, R.drawable.cal_month_4,
        R.drawable.cal_month_5, R.drawable.cal_month_6, R.drawable.cal_month_7, R.drawable.cal_month_8,
        R.drawable.cal_month_9, R.drawable.cal_month_10, R.drawable.cal_month_11, R.drawable.cal_month_12,
    )
    private val MONTHS_EN = listOf("Muharram", "Safar", "Rabiʿ I", "Rabiʿ II", "Jumada I", "Jumada II", "Rajab", "Shaʿban", "Ramadan", "Shawwal", "Dhu al-Qaʿdah", "Dhu al-Hijjah")
    private val MONTHS_AR = listOf("محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة")

    /** Numbers are always Western digits (Sammy's call); Arabic text keeps its words. */
    private fun digits(s: String, @Suppress("UNUSED_PARAMETER") ar: Boolean) = s

    /** Arabic dates with Western digits. */
    private val AR = Locale.forLanguageTag("ar-u-nu-latn")

    private fun hijri(state: EmranWidgetState): Triple<Int, Int, Int> {
        if (state.hijriDay != null && state.hijriMonth != null && state.hijriYear != null) return Triple(state.hijriDay, state.hijriMonth, state.hijriYear)
        val h = HijrahDate.from(today(state))
        return Triple(h.get(ChronoField.DAY_OF_MONTH), h.get(ChronoField.MONTH_OF_YEAR), h.get(ChronoField.YEAR))
    }

    /** Friday's Dhuhr is shown as Jumu'ah (see [PrayerNames]); the widget is redrawn at midnight. */
    private fun prayerName(n: String, ar: Boolean): String = io.github.sammynashed.emran.data.PrayerNames.name(n, ar, LocalDate.now())

    private fun hm(epoch: Long, ar: Boolean, use24: Boolean, withSuffix: Boolean = false): String {
        val t = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).toLocalTime()
        val tf = io.github.sammynashed.emran.data.TimeFormat
        return digits(if (withSuffix) tf.full(t, ar, use24) else tf.clock(t, use24), ar)
    }

    // ---- the widgets ---------------------------------------------------------------------------

    private fun dayArabic(ctx: Context, c: Canvas, s: EmranWidgetState, w: Int, h: Int, d: Float, ar: Boolean) {
        val day = today(s)
        val idx = day.dayOfWeek.value % 7          // Sunday 0 ... Saturday 6
        val showLine = h > 96 * d
        val artBottom = if (showLine) h * 0.74f else h - 14 * d
        calligraphy(ctx, c, DAY_RES[idx], RectF(w * 0.12f, 16 * d, w * 0.88f, artBottom))
        if (showLine) {
            val (hd, hmn, hy) = hijri(s)
            val line = if (ar) digits("$hd ${MONTHS_AR[hmn - 1]} $hy", true)
            else "$hd ${MONTHS_EN[hmn - 1].uppercase()} $hy  ·  ${DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH).format(day).uppercase()}"
            val p = paint(inter(ctx, 500), 11f * d, SOFT, Paint.Align.CENTER).apply { letterSpacing = if (ar) 0f else 0.12f }.fit(line, w - 32 * d)
            c.text(line, w / 2f, h - 22 * d, p)
        }
    }

    private fun dayEnglish(ctx: Context, c: Canvas, s: EmranWidgetState, w: Int, h: Int, d: Float) {
        val name = today(s).dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH).uppercase()
        val p = paint(bodoni(ctx), minOf(h * 0.46f, 48f * d), WHITE, Paint.Align.CENTER).apply { letterSpacing = 0.32f }
        p.fit(name, w - 40 * d)
        c.text(name, w / 2f, h / 2f, p)
    }

    /** Today's five, the next one lit, and a bar for how far through the day's prayers we are. */
    private fun prayerProgress(ctx: Context, c: Canvas, s: EmranWidgetState, w: Int, h: Int, d: Float, ar: Boolean) {
        val pad = 18 * d
        val day = today(s)
        val (hd, hmn, _) = hijri(s)
        val title = if (ar) digits("${day.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, AR)}، $hd ${MONTHS_AR[hmn - 1]}", true)
        else "${day.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)}, $hd ${MONTHS_EN[hmn - 1]}"
        val sub = if (ar) digits(DateTimeFormatter.ofPattern("d MMMM", AR).format(day), true)
        else DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH).format(day)
        val startX = if (ar) w - pad else pad
        val align = if (ar) Paint.Align.RIGHT else Paint.Align.LEFT
        c.text(title, startX, pad + 9 * d, paint(inter(ctx, 400), 14.5f * d, WHITE, align).fit(title, w * 0.6f))
        c.text(sub, startX, pad + 28 * d, paint(inter(ctx, 400), 12f * d, SOFT, align))
        // Next prayer name on the far side; its countdown is a live view laid over this spot.
        val next = s.nextPrayerName?.let { prayerName(it, ar) }.orEmpty()
        c.text(next, if (ar) pad else w - pad, pad + 9 * d, paint(inter(ctx, 600), 15f * d, WHITE, if (ar) Paint.Align.LEFT else Paint.Align.RIGHT))

        val names = s.todayPrayerNames; val times = s.todayPrayerEpochMillis
        if (names.isEmpty() || names.size != times.size) return
        val now = System.currentTimeMillis()
        val nextIdx = times.indexOfFirst { it > now }.let { if (it < 0) -1 else it }
        val rowY = maxOf(pad + 70 * d, h * 0.60f)
        val left = pad + 16 * d; val right = w - pad - 16 * d
        names.forEachIndexed { i, n ->
            val k = if (ar) names.size - 1 - i else i
            val x = left + (right - left) * k / (names.size - 1).coerceAtLeast(1)
            val lit = i == nextIdx
            c.drawCircle(x, rowY - 20 * d, (if (lit) 3f else 2.5f) * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (lit) WHITE else FAINT })
            c.text(prayerName(n, ar), x, rowY, paint(inter(ctx, if (lit) 600 else 400), 11.5f * d, if (lit) WHITE else SOFT, Paint.Align.CENTER))
            c.text(hm(times[i], ar, s.use24Hour), x, rowY + 16 * d, paint(inter(ctx, 400), 11f * d, if (lit) WHITE else SOFT, Paint.Align.CENTER))
        }
        // Progress: from the first prayer to the last, where are we now.
        val frac = ((now - times.first()).toFloat() / (times.last() - times.first()).coerceAtLeast(1)).coerceIn(0f, 1f)
        val by = h - 14 * d
        val track = RectF(pad, by - 1.5f * d, w - pad, by + 1.5f * d)
        c.drawRoundRect(track, 2 * d, 2 * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(46, 255, 255, 255) })
        val fill = if (ar) RectF(track.right - track.width() * frac, track.top, track.right, track.bottom) else RectF(track.left, track.top, track.left + track.width() * frac, track.bottom)
        c.drawRoundRect(fill, 2 * d, 2 * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WHITE })
    }

    /** A small drawn mark for each prayer: dawn crescent, sunrise, sun, afternoon sun, sunset, moon. */
    private fun prayerIcon(c: Canvas, which: String, cx: Float, cy: Float, r: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = r * 0.22f; strokeCap = Paint.Cap.ROUND }
        val f = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        fun crescent() {
            val path = android.graphics.Path().apply {
                addCircle(cx, cy, r, android.graphics.Path.Direction.CW)
                val cut = android.graphics.Path().apply { addCircle(cx + r * 0.45f, cy - r * 0.3f, r * 0.85f, android.graphics.Path.Direction.CW) }
                op(cut, android.graphics.Path.Op.DIFFERENCE)
            }
            c.drawPath(path, f)
        }
        fun rays(n: Int, from: Float, to: Float, startDeg: Float, spanDeg: Float) {
            for (i in 0 until n) {
                val a = Math.toRadians((startDeg + spanDeg * i / (n - 1).coerceAtLeast(1)).toDouble())
                c.drawLine(cx + (from * Math.cos(a)).toFloat(), cy - (from * Math.sin(a)).toFloat(), cx + (to * Math.cos(a)).toFloat(), cy - (to * Math.sin(a)).toFloat(), p)
            }
        }
        when (which.removeSuffix(" (Tomorrow)")) {
            "Fajr" -> { crescent(); c.drawCircle(cx + r * 0.95f, cy - r * 0.85f, r * 0.16f, f) }
            "Isha" -> crescent()
            "Sunrise", "Maghrib" -> {
                val base = cy + r * 0.35f
                c.drawArc(RectF(cx - r * 0.7f, base - r * 0.7f, cx + r * 0.7f, base + r * 0.7f), 180f, 180f, false, p)
                c.drawLine(cx - r * 1.1f, base, cx + r * 1.1f, base, p)
                if (which.startsWith("Sunrise")) rays(3, r * 0.95f, r * 1.25f, 45f, 90f)
            }
            "Dhuhr" -> { c.drawCircle(cx, cy, r * 0.5f, f); rays(8, r * 0.78f, r * 1.1f, 0f, 315f) }
            else -> { c.drawCircle(cx, cy, r * 0.55f, p); rays(5, r * 0.85f, r * 1.12f, 20f, 140f) } // Asr: the sun, lower, rays above
        }
    }

    private fun prayerTable(ctx: Context, c: Canvas, s: EmranWidgetState, w: Int, h: Int, d: Float, ar: Boolean) {
        val names = s.todayPrayerNames; val times = s.todayPrayerEpochMillis
        if (names.isEmpty() || names.size != times.size) return
        val now = System.currentTimeMillis()
        val rawNext = times.indexOfFirst { it > now }
        // After Isha nothing in today's list is still ahead: the next prayer is TOMORROW's Fajr. Without this
        // the header went blank and no row was lit from Isha until midnight.
        val tomorrowFajr = rawNext < 0 && s.nextPrayerEpochMillis != null
        val nextIdx = if (tomorrowFajr) names.indexOfFirst { it.startsWith("Fajr") }.coerceAtLeast(0) else rawNext
        fun timeAt(i: Int): Long = if (tomorrowFajr && i == nextIdx) s.nextPrayerEpochMillis!! else times[i]
        val pad = 16 * d
        val wide = w / d >= 250f
        val dim = Color.argb(120, 255, 255, 255)

        fun row(i: Int, left: Float, right: Float, cy: Float, rowH: Float) {
            val lit = i == nextIdx; val done = !tomorrowFajr && (nextIdx < 0 || i < nextIdx)
            if (lit) {
                val r = RectF(left - 8 * d, cy - rowH / 2 + 2 * d, right + 8 * d, cy + rowH / 2 - 2 * d)
                c.drawRoundRect(r, rowH / 2, rowH / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(44, 255, 255, 255) })
                c.drawRoundRect(r, rowH / 2, rowH / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = d; color = Color.argb(90, 255, 255, 255) })
            }
            val col = when { lit -> WHITE; done -> dim; else -> Color.argb(225, 255, 255, 255) }
            val size = (rowH * 0.5f).coerceAtMost(14.5f * d)
            val iconR = size * 0.42f
            val iconX = if (ar) right - iconR else left + iconR
            prayerIcon(c, names[i], iconX, cy, iconR, col)
            val nameX = if (ar) right - iconR * 2 - 8 * d else left + iconR * 2 + 8 * d
            c.text(prayerName(names[i], ar), nameX, cy, paint(inter(ctx, if (lit) 600 else 400), size, col, if (ar) Paint.Align.RIGHT else Paint.Align.LEFT))
            c.text(hm(timeAt(i), ar, s.use24Hour), if (ar) left else right, cy, paint(inter(ctx, if (lit) 600 else 400), size, col, if (ar) Paint.Align.LEFT else Paint.Align.RIGHT))
        }

        if (!wide) {
            val top = pad + 4 * d
            val rowH = ((h - top - pad + 4 * d) / names.size).coerceAtMost(26 * d)
            val y0 = top + ((h - top - pad) - rowH * names.size) / 2
            names.indices.forEach { i -> row(i, pad, w - pad, y0 + rowH * i + rowH / 2, rowH) }
            return
        }
        // Wide: the next prayer as a header, then two columns.
        val headY = pad + 12 * d
        if (nextIdx >= 0) {
            val nm = prayerName(names[nextIdx], ar)
            c.text(nm, if (ar) w - pad else pad, headY, paint(inter(ctx, 600), 18f * d, WHITE, if (ar) Paint.Align.RIGHT else Paint.Align.LEFT))
            c.text(hm(timeAt(nextIdx), ar, s.use24Hour, withSuffix = true), if (ar) pad else w - pad, headY, paint(inter(ctx, 300), 22f * d, WHITE, if (ar) Paint.Align.LEFT else Paint.Align.RIGHT))
        }
        val top = headY + 22 * d
        c.drawRect(pad, top - 6 * d, w - pad, top - 5 * d, Paint().apply { color = Color.argb(40, 255, 255, 255) })
        val rows = (names.size + 1) / 2
        val rowH = ((h - top - pad + 4 * d) / rows).coerceAtMost(28 * d)
        val gap = 22 * d
        val colW = (w - 2 * pad - gap) / 2
        names.indices.forEach { i ->
            val colIdx = i / rows; val r = i % rows
            val visual = if (ar) 1 - colIdx else colIdx
            val left = pad + visual * (colW + gap)
            row(i, left, left + colW, top + rowH * r + rowH / 2, rowH)
        }
    }

    /** Short widgets put the time beside the name; the live countdown sits under the name. */
    fun isShort(hDp: Float) = hDp < 125f

    private fun nextPrayer(ctx: Context, c: Canvas, s: EmranWidgetState, w: Int, h: Int, d: Float, ar: Boolean) {
        val name = s.nextPrayerName?.let { prayerName(it, ar) } ?: return
        val time = s.nextPrayerEpochMillis?.let { hm(it, ar, s.use24Hour, withSuffix = true) } ?: return
        val label = if (ar) "الصلاة القادمة" else "Next prayer"
        if (isShort(h / d)) {
            val pad = 18 * d
            val start = if (ar) w - pad else pad
            val align = if (ar) Paint.Align.RIGHT else Paint.Align.LEFT
            c.text(label, start, h * 0.26f, paint(inter(ctx, 400), 11.5f * d, SOFT, align))
            c.text(name, start, h * 0.50f, paint(inter(ctx, 600), 19f * d, WHITE, align).fit(name, w * 0.42f))
            val tp = paint(inter(ctx, 300), minOf(h * 0.42f, 40f * d), WHITE, if (ar) Paint.Align.LEFT else Paint.Align.RIGHT).fit(time, w * 0.5f)
            c.text(time, if (ar) pad else w - pad, h / 2f, tp)
            return
        }
        c.text(label, w / 2f, h * 0.18f, paint(inter(ctx, 400), 12f * d, SOFT, Paint.Align.CENTER))
        c.text(name, w / 2f, h * 0.34f, paint(inter(ctx, 600), 19f * d, WHITE, Paint.Align.CENTER).fit(name, w - 24 * d))
        c.text(time, w / 2f, h * 0.56f, paint(inter(ctx, 300), minOf(h * 0.24f, 44f * d), WHITE, Paint.Align.CENTER).fit(time, w - 24 * d))
        // The countdown under it is a live view laid over the bitmap.
    }

    private fun hijriDate(ctx: Context, c: Canvas, s: EmranWidgetState, w: Int, h: Int, d: Float, ar: Boolean) {
        val (hd, hmn, _) = hijri(s)
        val num = hd.toString()
        val line = if (ar) DateTimeFormatter.ofPattern("d MMMM y", AR).format(today(s))
        else DateTimeFormatter.ofPattern("d MMM y", Locale.ENGLISH).format(today(s)).uppercase()
        val lineP = paint(inter(ctx, 500), 10.5f * d, SOFT, Paint.Align.CENTER).apply { letterSpacing = if (ar) 0f else 0.1f }
        if (isShort(h / d)) {
            // The day number on one side, the month in calligraphy on the other.
            val numX = if (ar) w * 0.82f else w * 0.18f
            c.text(num, numX, h * 0.46f, paint(inter(ctx, 300), minOf(h * 0.52f, 52f * d), WHITE, Paint.Align.CENTER))
            val box = if (ar) RectF(w * 0.06f, h * 0.14f, w * 0.64f, h * 0.70f) else RectF(w * 0.36f, h * 0.14f, w * 0.94f, h * 0.70f)
            calligraphy(ctx, c, MONTH_RES[hmn - 1], box)
            c.text(line, box.centerX(), h * 0.84f, lineP.fit(line, box.width()))
            return
        }
        c.text(num, w / 2f, h * 0.25f, paint(inter(ctx, 300), minOf(h * 0.28f, 56f * d), WHITE, Paint.Align.CENTER))
        calligraphy(ctx, c, MONTH_RES[hmn - 1], RectF(w * 0.12f, h * 0.42f, w * 0.88f, h * 0.72f))
        c.text(line, w / 2f, h * 0.86f, lineP.fit(line, w - 24 * d))
    }

    /** The Hijri month as dots, today glowing, and how far into the month we are. */
    private fun hijriMonth(ctx: Context, c: Canvas, s: EmranWidgetState, w: Int, h: Int, d: Float, ar: Boolean) {
        val (hd, hmn, _) = hijri(s)
        val len = runCatching { HijrahDate.from(today(s)).lengthOfMonth() }.getOrDefault(30)
        val pad = 16 * d
        val title = if (ar) MONTHS_AR[hmn - 1] else MONTHS_EN[hmn - 1]
        val pct = digits("${(hd * 100 / len)}%", ar)
        c.text(title, if (ar) w - pad else pad, pad + 8 * d, paint(inter(ctx, 400), 14f * d, WHITE, if (ar) Paint.Align.RIGHT else Paint.Align.LEFT).fit(title, w * 0.6f))
        c.text(pct, if (ar) pad else w - pad, pad + 8 * d, paint(inter(ctx, 400), 14f * d, WHITE, if (ar) Paint.Align.LEFT else Paint.Align.RIGHT))
        val cols = 7
        val gx = (w - 2 * pad - 8 * d) / (cols - 1)
        val top = pad + 36 * d
        val gy = ((h - top - pad) / 4.6f).coerceAtMost(gx)
        for (k in 0 until len) {
            val r = k / cols; val col = k % cols
            val x = if (ar) w - pad - 4 * d - col * gx else pad + 4 * d + col * gx
            val y = top + r * gy
            val isToday = k + 1 == hd; val past = k + 1 < hd
            if (isToday) {
                c.drawCircle(x, y, 9 * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 255, 255, 255); maskFilter = BlurMaskFilter(6 * d, BlurMaskFilter.Blur.NORMAL) })
            }
            c.drawCircle(x, y, (if (isToday) 3.4f else 2.3f) * d, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = when { isToday -> WHITE; past -> Color.argb(200, 255, 255, 255); else -> FAINT }
            })
        }
    }

    // ---- ayah of the day -------------------------------------------------------------------

    private fun hafs(c: Context): Typeface =
        faces.getOrPut("hafs") { runCatching { androidx.core.content.res.ResourcesCompat.getFont(c, R.font.kfgqpc_hafs) }.getOrNull() ?: Typeface.SERIF }

    /** Centred, wrapped text at the largest size (down to [minPx]) that fits the box. */
    private fun fitBlock(c: Canvas, text: String, p: android.text.TextPaint, box: RectF, maxPx: Float, minPx: Float, rtl: Boolean, spacing: Float) {
        var size = maxPx
        var layout: android.text.StaticLayout
        while (true) {
            p.textSize = size
            layout = android.text.StaticLayout.Builder.obtain(text, 0, text.length, p, box.width().toInt())
                .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
                .setTextDirection(if (rtl) android.text.TextDirectionHeuristics.RTL else android.text.TextDirectionHeuristics.LTR)
                .setLineSpacing(0f, spacing).setIncludePad(true).build()
            if (layout.height <= box.height() || size <= minPx) break
            size -= 1f
        }
        c.save(); c.translate(box.left, box.top + (box.height() - layout.height).coerceAtLeast(0f) / 2f); layout.draw(c); c.restore()
    }

    private fun ayah(ctx: Context, c: Canvas, s: EmranWidgetState, w: Int, h: Int, d: Float, ar: Boolean) {
        val text = s.ayahText ?: return
        val pad = 20 * d
        val ref = if (ar) "سورة ${s.ayahSurahAr} · ${s.ayahSurah}:${s.ayahNumber}"
        else "${s.ayahSurahEn?.uppercase()}  ·  ${s.ayahSurah}:${s.ayahNumber}"
        val showEn = !ar && s.ayahEnglish != null && h / d >= 150
        val arBottom = if (showEn) h * 0.50f else h - 34 * d
        fitBlock(c, text, android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = hafs(ctx); color = WHITE },
            RectF(pad, pad, w - pad, arBottom), 28f * d, 13f * d, true, 1.25f)
        if (showEn) {
            fitBlock(c, "\u201C${s.ayahEnglish}\u201D", android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = inter(ctx, 400); color = SOFT },
                RectF(pad, arBottom + 4 * d, w - pad, h - 34 * d), 14f * d, 10f * d, false, 1.15f)
        }
        c.text(ref, w / 2f, h - 18 * d, paint(inter(ctx, 500), 10f * d, SOFT, Paint.Align.CENTER).apply { letterSpacing = if (ar) 0f else 0.1f }.fit(ref, w - 2 * pad))
    }

    // ---- clock -------------------------------------------------------------------------------

    /** The clock's own digits are a live TextClock on top; the picture holds the glass and the date. */
    private fun clock(ctx: Context, c: Canvas, s: EmranWidgetState, w: Int, h: Int, d: Float, ar: Boolean) {
        val day = today(s)
        val (hd, hmn, _) = hijri(s)
        val line = if (ar) "${day.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, AR)} · $hd ${MONTHS_AR[hmn - 1]}"
        else "${day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH).uppercase()}, ${DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH).format(day).uppercase()}  ·  $hd ${MONTHS_EN[hmn - 1].uppercase()}"
        c.text(line, w / 2f, h * 0.76f, paint(inter(ctx, 500), 10.5f * d, SOFT, Paint.Align.CENTER).apply { letterSpacing = if (ar) 0f else 0.12f }.fit(line, w - 28 * d))
    }

    // ---- next occasion ---------------------------------------------------------------------

    private class Occasion(val month: Int, val day: Int, val en: String, val ar: String, val art: Int?)
    private val OCCASIONS = listOf(
        Occasion(9, 1, "Ramadan", "رمضان", R.drawable.cal_month_9),
        Occasion(10, 1, "Eid al-Fitr", "عيد الفطر", null),
        Occasion(12, 9, "Day of Arafah", "يوم عرفة", null),
        Occasion(12, 10, "Eid al-Adha", "عيد الأضحى", null),
        Occasion(1, 1, "Islamic New Year", "رأس السنة الهجرية", R.drawable.cal_month_1),
    )

    /** The nearest occasion from today (today included), and how many days until it. */
    private fun nextOccasion(today: LocalDate): Pair<Occasion, Long> {
        val h = HijrahDate.from(today)
        val y = h.get(ChronoField.YEAR)
        return OCCASIONS.flatMap { o ->
            listOf(y, y + 1).mapNotNull { yy -> runCatching { LocalDate.from(HijrahDate.of(yy, o.month, o.day)) }.getOrNull()?.let { o to it } }
        }.filter { !it.second.isBefore(today) }
            .minByOrNull { it.second }
            ?.let { (o, date) -> o to java.time.temporal.ChronoUnit.DAYS.between(today, date) }
            ?: (OCCASIONS.first() to 0L)
    }

    private fun occasion(ctx: Context, c: Canvas, s: EmranWidgetState, w: Int, h: Int, d: Float, ar: Boolean) {
        val (o, days) = nextOccasion(today(s))
        val pad = 16 * d
        c.text(if (ar) "المناسبة القادمة" else "Next occasion", if (ar) w - pad else pad, pad + 6 * d,
            paint(inter(ctx, 400), 11f * d, SOFT, if (ar) Paint.Align.RIGHT else Paint.Align.LEFT))
        val nameBox = RectF(w * 0.1f, pad + 18 * d, w * 0.9f, h * 0.56f)
        if (o.art != null) calligraphy(ctx, c, o.art, nameBox)
        else fitBlock(c, o.ar, android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = hafs(ctx); color = WHITE }, nameBox, 34f * d, 14f * d, true, 1f)
        val big = if (days == 0L) (if (ar) "اليوم" else "Today") else days.toString()
        c.text(big, w / 2f, h * 0.72f, paint(inter(ctx, 300), minOf(h * 0.2f, 36f * d), WHITE, Paint.Align.CENTER))
        val sub = when {
            days == 0L -> if (ar) o.ar else o.en
            ar -> if (days == 1L) "يوم واحد" else if (days in 2..10) "أيام" else "يومًا"
            else -> (if (days == 1L) "day" else "days") + " until " + o.en
        }
        c.text(sub, w / 2f, h * 0.88f, paint(inter(ctx, 400), 11f * d, SOFT, Paint.Align.CENTER).fit(sub, w - 2 * pad))
    }
}
