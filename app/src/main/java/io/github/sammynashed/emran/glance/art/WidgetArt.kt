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
 * Draws Emran's widgets as pictures, Glassify-style: frosted glass panels and thmanyah type, drawn from
 * pre-rendered word images (see [TypeArt]; Quran text stays in the Mushaf face). Widgets can't use an app's own
 * fonts directly, so the design is drawn here and shown as one image; only the live countdown and clocks are
 * real (system-ticked) views on top.
 */
object WidgetArt {

    enum class Kind { DAY_ARABIC, DAY_ENGLISH, PRAYER_PROGRESS, PRAYER_TABLE, NEXT_PRAYER, HIJRI_DATE, HIJRI_MONTH, AYAH, CLOCK, OCCASION,
        PRAYER_ROW, MONTH_CALLIGRAPHY, MONTH_ELEGANT, DATE_COMPACT, DATE_BANNER, DUAL_BANNER, DATE_CLOCK, CALENDAR, YEAR_DOTS, CLOCK_ARABIC }

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

    /** Which pre-rendered thmanyah style (see [TypeArt]) each face stands for. The real face is only the fallback. */
    private val typeStyle = java.util.IdentityHashMap<Typeface, String>()
    private fun inter(c: Context, w: Int) = face(c, R.font.inter, "'wght' $w, 'opsz' 14").also { typeStyle[it] = if (w >= 600) "M" else "R" }
    /** The display face (thmanyah Serif Display, as pictures). */
    private val DISPLAY: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD).also { typeStyle[it] = "D" }
    private fun bodoni(c: Context) = face(c, R.font.bodoni_moda, "'wght' 400, 'opsz' 24").also { typeStyle[it] = "T" }

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
        TypeArt.init(context)
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
            Kind.PRAYER_ROW -> prayerRow(context, c, state, w, h, d, ar)
            Kind.MONTH_CALLIGRAPHY -> monthCalligraphy(context, c, state, w, h, d)
            Kind.MONTH_ELEGANT -> monthElegant(context, c, state, w, h, d, ar)
            Kind.DATE_COMPACT -> dateCompact(context, c, state, w, h, d, ar)
            Kind.DATE_BANNER -> dateBanner(context, c, state, w, h, d, ar)
            Kind.DUAL_BANNER -> dualBanner(context, c, state, w, h, d, ar)
            Kind.DATE_CLOCK -> dateClock(context, c, state, w, h, d, ar)
            Kind.CALENDAR -> calendar(context, c, state, w, h, d, ar)
            Kind.YEAR_DOTS -> yearDots(context, c, state, w, h, d, ar)
            Kind.CLOCK_ARABIC -> Unit // only glass: the live digits are a view on top
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
        val st = typeStyle[p.typeface]
        if (st != null && TypeArt.draw(this, text, x, cy, st, p.textSize, p)) return
        val fm = p.fontMetrics
        drawText(text, x, cy - (fm.ascent + fm.descent) / 2f, p)
    }

    /** Shrinks the paint until [text] fits [maxW]. */
    private fun Paint.fit(text: String, maxW: Float): Paint {
        val st = typeStyle[typeface]
        while ((st?.let { TypeArt.measure(text, it, textSize) } ?: measureText(text)) > maxW && textSize > 6f) textSize *= 0.95f
        return this
    }

    private fun displayPaint(sizePx: Float, color: Int = WHITE): Paint = paint(DISPLAY, sizePx, color)

    /** [text] in the display face at the largest size that fits [box], centred by its actual ink. edge: -1 / 1 hold it left / right. */
    private fun calligraphy(ctx: Context, c: Canvas, text: String, box: RectF, color: Int = WHITE, edge: Int = 0) {
        if (TypeArt.drawInBox(c, text, box, displayPaint(100f, color), edge)) return
        val p = displayPaint(box.height() * 0.7f, color).apply { textAlign = Paint.Align.CENTER }.fit(text, box.width())
        val fm = p.fontMetrics
        c.drawText(text, box.centerX(), box.centerY() - (fm.ascent + fm.descent) / 2f, p)
    }

    /** One of the calligraphy images (Thuluth, with tashkeel), scaled to fit the box and centred in it. */
    private fun calligraphy(context: Context, c: Canvas, res: Int, box: RectF) {
        val art = BitmapFactory.decodeResource(context.resources, res) ?: return
        val s = minOf(box.width() / art.width, box.height() / art.height)
        val dw = art.width * s; val dh = art.height * s
        val dst = RectF(box.centerX() - dw / 2, box.centerY() - dh / 2, box.centerX() + dw / 2, box.centerY() + dh / 2)
        c.drawBitmap(art, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        art.recycle()
    }

    private val DAY_RES = intArrayOf(R.drawable.cal_day1, R.drawable.cal_day2, R.drawable.cal_day3, R.drawable.cal_day4, R.drawable.cal_day5, R.drawable.cal_day6, R.drawable.cal_day7)
    private val MONTH_RES = intArrayOf(
        R.drawable.cal_month_1, R.drawable.cal_month_2, R.drawable.cal_month_3, R.drawable.cal_month_4,
        R.drawable.cal_month_5, R.drawable.cal_month_6, R.drawable.cal_month_7, R.drawable.cal_month_8,
        R.drawable.cal_month_9, R.drawable.cal_month_10, R.drawable.cal_month_11, R.drawable.cal_month_12,
    )

    private fun today(state: EmranWidgetState): LocalDate = LocalDate.now(ZoneId.systemDefault())

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

    /** The day in calligraphy alone, filling the glass, square or wide. */
    private fun dayArabic(ctx: Context, c: Canvas, s: EmranWidgetState, w: Int, h: Int, d: Float, ar: Boolean) {
        val idx = today(s).dayOfWeek.value % 7          // Sunday 0 ... Saturday 6
        calligraphy(ctx, c, DAY_RES[idx], RectF(w * 0.1f, h * 0.1f, w * 0.9f, h * 0.9f))
    }

    /** Wide: the whole name, spaced out. Square: its first three letters, large. */
    private fun dayEnglish(ctx: Context, c: Canvas, s: EmranWidgetState, w: Int, h: Int, d: Float) {
        val small = w < h * 1.5f
        val style = if (small) java.time.format.TextStyle.SHORT else java.time.format.TextStyle.FULL
        val name = today(s).dayOfWeek.getDisplayName(style, Locale.ENGLISH).uppercase()
        val p = paint(bodoni(ctx), if (small) w * 0.25f else minOf(h * 0.46f, 48f * d), WHITE, Paint.Align.CENTER).apply { letterSpacing = 0.32f }
        p.fit(name, w - (if (small) 28 else 40) * d)
        c.text(name, w / 2f, h / 2f, p)
    }

    /** The next prayer: time and countdown on one side, its name large on the other; then today's five and the day's bar. */
    private fun prayerProgress(ctx: Context, c: Canvas, s: EmranWidgetState, w: Int, h: Int, d: Float, ar: Boolean) {
        val pad = 18 * d
        val startX = if (ar) w - pad else pad
        val align = if (ar) Paint.Align.RIGHT else Paint.Align.LEFT
        val next = s.nextPrayerName?.let { prayerName(it, ar) }.orEmpty()
        val time = s.nextPrayerEpochMillis?.let { hm(it, ar, s.use24Hour) }.orEmpty()
        c.text(time, startX, pad + 10 * d, paint(inter(ctx, 500), 17f * d, WHITE, align))
        // In Arabic the big name already says it; the small line says what it is instead.
        val small = if (ar) "الصلاة القادمة" else next
        c.text(small, startX, pad + 30 * d, paint(inter(ctx, 400), 12.5f * d, SOFT, align).fit(small, w * 0.4f))
        // The name in the app's Arabic script; only the countdown under the time is live (a view on top).
        val big = prayerName(s.nextPrayerName ?: "", true)
        calligraphy(ctx, c, big, if (ar) RectF(pad, pad - 4 * d, w * 0.5f, pad + 46 * d) else RectF(w * 0.5f, pad - 4 * d, w - pad, pad + 46 * d), edge = if (ar) -1 else 1)

        val names = s.todayPrayerNames; val times = s.todayPrayerEpochMillis
        if (names.isEmpty() || names.size != times.size) return
        val now = System.currentTimeMillis()
        val nextIdx = litPrayer(s)
        val rowY = maxOf(pad + 72 * d, h * 0.66f)
        fiveAcross(ctx, c, s, ar, nextIdx, pad + 16 * d, w - pad - 16 * d, rowY, d)
        val frac = ((now - times.first()).toFloat() / (times.last() - times.first()).coerceAtLeast(1)).coerceIn(0f, 1f)
        val by = h - 13 * d
        val track = RectF(pad, by - 1.5f * d, w - pad, by + 1.5f * d)
        c.drawRoundRect(track, 2 * d, 2 * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(46, 255, 255, 255) })
        val fill = if (ar) RectF(track.right - track.width() * frac, track.top, track.right, track.bottom) else RectF(track.left, track.top, track.left + track.width() * frac, track.bottom)
        c.drawRoundRect(fill, 2 * d, 2 * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WHITE })
    }

    /** Which of today's five to light: the next one, or Fajr once Isha has passed (that's tomorrow's). */
    private fun litPrayer(s: EmranWidgetState): Int {
        val now = System.currentTimeMillis()
        val i = s.todayPrayerEpochMillis.indexOfFirst { it > now }
        return if (i >= 0) i else s.todayPrayerNames.indexOfFirst { it.startsWith("Fajr") }.coerceAtLeast(0)
    }

    /** Today's five in a row, name over time, the lit one's time on a soft pill. */
    private fun fiveAcross(ctx: Context, c: Canvas, s: EmranWidgetState, ar: Boolean, lit: Int, left: Float, right: Float, cy: Float, d: Float) {
        val names = s.todayPrayerNames; val times = s.todayPrayerEpochMillis
        val nameP = (right - left) / (names.size - 1).coerceAtLeast(1)
        val size = (nameP * 0.17f).coerceIn(9f * d, 12f * d)
        names.forEachIndexed { i, n ->
            val k = if (ar) names.size - 1 - i else i
            val x = left + nameP * k
            val on = i == lit
            val t = if (on && s.nextPrayerEpochMillis != null) hm(s.nextPrayerEpochMillis, ar, s.use24Hour) else hm(times[i], ar, s.use24Hour)
            val tp = paint(inter(ctx, if (on) 600 else 400), size, if (on) WHITE else SOFT, Paint.Align.CENTER)
            if (on) {
                val half = (TypeArt.measure(t, "M", tp.textSize) ?: tp.measureText(t)) / 2 + 8 * d
                val r = RectF(x - half, cy + 8 * d, x + half, cy + 26 * d)
                c.drawRoundRect(r, 9 * d, 9 * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(50, 255, 255, 255) })
            }
            c.text(prayerName(n, ar), x, cy - 3 * d, paint(inter(ctx, if (on) 600 else 400), size, if (on) WHITE else SOFT, Paint.Align.CENTER).fit(prayerName(n, ar), nameP - 4 * d))
            c.text(t, x, cy + 17 * d, tp)
        }
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

    /** Square: a ring around the time, name and live countdown. Short: time and countdown one side, the name in script the other. */
    private fun nextPrayer(ctx: Context, c: Canvas, s: EmranWidgetState, w: Int, h: Int, d: Float, ar: Boolean) {
        val nextName = s.nextPrayerName ?: return
        val epoch = s.nextPrayerEpochMillis ?: return
        val time = hm(epoch, ar, s.use24Hour)
        val script = prayerName(nextName, true)
        if (isShort(h / d)) {
            val pad = 18 * d
            c.text(time, if (ar) w - pad else pad, h * 0.32f, paint(inter(ctx, 500), minOf(h * 0.26f, 20f * d), WHITE, if (ar) Paint.Align.RIGHT else Paint.Align.LEFT))
            calligraphy(ctx, c, script, if (ar) RectF(pad, h * 0.14f, w * 0.5f, h * 0.86f) else RectF(w * 0.5f, h * 0.14f, w - pad, h * 0.86f), edge = if (ar) -1 else 1)
            return
        }
        val cx = w / 2f; val cy = h / 2f
        val r = minOf(w, h) * 0.5f - 18 * d
        val sw = 5 * d
        c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = sw; color = Color.argb(60, 255, 255, 255) })
        // How much of the wait from the last prayer to the next has passed.
        val now = System.currentTimeMillis()
        val from = s.currentPrayerEpochMillis ?: s.todayPrayerEpochMillis.lastOrNull { it <= now }
        val frac = if (from != null && epoch > from) ((now - from).toFloat() / (epoch - from)).coerceIn(0f, 1f) else 0f
        c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), -90f, 360f * frac, false, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = sw; strokeCap = Paint.Cap.ROUND; color = WHITE })
        c.text(time, cx, cy - r * 0.48f, paint(inter(ctx, 400), r * 0.24f, SOFT, Paint.Align.CENTER))
        calligraphy(ctx, c, script, RectF(cx - r * 0.72f, cy - r * 0.34f, cx + r * 0.72f, cy + r * 0.26f))
        // The countdown under the name is a live view laid over the bitmap.
    }

    /** The month with its stroke stretched and the day's number standing on the stroke, fading down into it. */
    private fun monthOnStroke(c: Canvas, w: Int, h: Int, d: Float, day: Int, key: String) {
        val cut = TypeArt.cut(key) ?: return
        val m = TypeArt.cutMask(cut) ?: return
        val num = day.toString().map { '\u0660' + (it - '0') }.joinToString("")
        val size = minOf((w - 36 * d) / cut.w, h * 0.62f / cut.h * 0.9f)
        val left = w / 2f - cut.w * size / 2f
        val base = h * 0.60f + (cut.oy + cut.h) * size * -0.25f + size * 0.2f
        val penLeft = left - cut.ox * size
        c.drawBitmap(m, null, RectF(left, base + cut.oy * size, left + cut.w * size, base + (cut.oy + cut.h) * size), displayPaint(size).apply { isFilterBitmap = true })
        val digits = TypeArt.digitBounds(num) ?: return
        val numSize = size * 0.72f
        val mid = penLeft + cut.strokeMid * size
        val nBase = base - size * 0.2f
        val np = displayPaint(numSize).apply {
            textAlign = Paint.Align.CENTER
            shader = LinearGradient(0f, nBase - digits[1] * numSize, 0f, nBase, WHITE, Color.argb(70, 255, 255, 255), Shader.TileMode.CLAMP)
        }
        TypeArt.draw(c, num, mid, nBase - 0.35f * numSize, "D", numSize, np)
    }

    private fun hijriDate(ctx: Context, c: Canvas, s: EmranWidgetState, w: Int, h: Int, d: Float, ar: Boolean) {
        val (hd, hmn, _) = hijri(s)
        // The day's number stands faded behind the top of the month.
        val numSize = minOf(h * 0.5f, w * 0.26f)
        val digits = hd.toString().map { '\u0660' + (it - '0') }.joinToString("")
        val np = displayPaint(numSize).apply {
            textAlign = Paint.Align.CENTER
            shader = LinearGradient(0f, h * 0.06f, 0f, h * 0.55f, Color.argb(170, 255, 255, 255), Color.argb(10, 255, 255, 255), Shader.TileMode.CLAMP)
        }
        TypeArt.draw(c, digits, w / 2f, h * 0.27f, "D", numSize, np)
        calligraphy(ctx, c, MONTH_RES[hmn - 1], RectF(w * 0.1f, h * 0.3f, w * 0.9f, h * 0.94f))
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

    private class Occasion(val month: Int, val day: Int, val en: String, val ar: String, val art: Int? = null)
    private val OCCASIONS = listOf(
        Occasion(9, 1, "Ramadan", "رمضان", R.drawable.cal_month_9),
        Occasion(10, 1, "Eid al-Fitr", "عيد الفطر"),
        Occasion(12, 9, "Day of Arafah", "يوم عرفة"),
        Occasion(12, 10, "Eid al-Adha", "عيد الأضحى"),
        Occasion(1, 1, "Islamic New Year", "رأس السنة الهجرية"),
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
        if (o.art != null) calligraphy(ctx, c, o.art, nameBox) else calligraphy(ctx, c, o.ar, nameBox)
        val big = if (days == 0L) (if (ar) "اليوم" else "Today") else days.toString()
        c.text(big, w / 2f, h * 0.72f, paint(inter(ctx, 300), minOf(h * 0.2f, 36f * d), WHITE, Paint.Align.CENTER))
        val sub = when {
            days == 0L -> if (ar) o.ar else o.en
            ar -> if (days == 1L) "يوم واحد" else if (days in 2..10) "أيام" else "يومًا"
            else -> (if (days == 1L) "day" else "days") + " until " + o.en
        }
        c.text(sub, w / 2f, h * 0.88f, paint(inter(ctx, 400), 11f * d, SOFT, Paint.Align.CENTER).fit(sub, w - 2 * pad))
    }

    // ---- the date family --------------------------------------------------------------------

    private fun monthAr(day: LocalDate) = DateTimeFormatter.ofPattern("MMMM", AR).format(day)
    private fun monthEn(day: LocalDate) = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH).format(day)
    private fun weekday(day: LocalDate, ar: Boolean) = day.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, if (ar) AR else Locale.ENGLISH)

    /** All five prayers in one slim row. */
    private fun prayerRow(ctx: Context, c: Canvas, s: EmranWidgetState, w: Int, h: Int, d: Float, ar: Boolean) {
        if (s.todayPrayerNames.isEmpty() || s.todayPrayerNames.size != s.todayPrayerEpochMillis.size) return
        fiveAcross(ctx, c, s, ar, litPrayer(s), 34 * d, w - 34 * d, h / 2f - 6 * d, d)
    }

    /** The Gregorian month in Thuluth, the day's number on its stroke. */
    private fun monthCalligraphy(ctx: Context, c: Canvas, s: EmranWidgetState, w: Int, h: Int, d: Float) {
        val day = today(s)
        monthOnStroke(c, w, h, d, day.dayOfMonth, "g${day.monthValue}")
    }

    /** The month in spaced Bodoni, the day's number faint behind it. */
    private fun monthElegant(ctx: Context, c: Canvas, s: EmranWidgetState, w: Int, h: Int, d: Float, ar: Boolean) {
        val day = today(s)
        val num = paint(inter(ctx, 500), minOf(h * 0.5f, w * 0.26f), Color.argb(70, 255, 255, 255), Paint.Align.CENTER)
        c.text(day.dayOfMonth.toString(), w / 2f, h * 0.34f, num)
        val name = if (ar) monthAr(day) else monthEn(day).uppercase()
        val p = if (ar) displayPaint(h * 0.34f).apply { textAlign = Paint.Align.CENTER }
        else paint(bodoni(ctx), minOf(h * 0.28f, 40f * d), WHITE, Paint.Align.CENTER).apply { letterSpacing = 0.22f }
        c.text(name, w / 2f, h * 0.64f, p.fit(name, w - 36 * d))
    }

    private fun dateCompact(ctx: Context, c: Canvas, s: EmranWidgetState, w: Int, h: Int, d: Float, ar: Boolean) {
        val day = today(s)
        val unit = minOf(w, h).toFloat()
        val wd = weekday(day, ar); val mo = if (ar) monthAr(day) else monthEn(day)
        c.text(wd, w / 2f, h * 0.25f, paint(inter(ctx, 400), unit * 0.105f, SOFT, Paint.Align.CENTER).fit(wd, w - 24 * d))
        c.text(day.dayOfMonth.toString(), w / 2f, h * 0.5f, paint(inter(ctx, 500), unit * 0.36f, WHITE, Paint.Align.CENTER))
        c.text(mo, w / 2f, h * 0.75f, paint(inter(ctx, 400), unit * 0.105f, SOFT, Paint.Align.CENTER).fit(mo, w - 24 * d))
    }

    private fun dateBanner(ctx: Context, c: Canvas, s: EmranWidgetState, w: Int, h: Int, d: Float, ar: Boolean) {
        val day = today(s)
        val line = if (ar) DateTimeFormatter.ofPattern("EEEE، d MMMM y", AR).format(day)
        else DateTimeFormatter.ofPattern("EEEE, MMMM d, y", Locale.ENGLISH).format(day)
        c.text(line, w / 2f, h / 2f, paint(inter(ctx, 400), minOf(h * 0.3f, 18f * d), WHITE, Paint.Align.CENTER).fit(line, w - 36 * d))
    }

    /** Hijri day + month in calligraphy | Gregorian month + day, a hairline between. */
    private fun dualBanner(ctx: Context, c: Canvas, s: EmranWidgetState, w: Int, h: Int, d: Float, ar: Boolean) {
        val day = today(s)
        val (hd, hmn, _) = hijri(s)
        val pad = 16 * d; val mid = w / 2f
        val numP = { paint(inter(ctx, 500), minOf(h * 0.38f, 30f * d), WHITE, Paint.Align.CENTER) }
        val numW = minOf(h * 0.38f, 30f * d) * 1.3f
        // Each half reads number-then-word from its outer edge, so the two meet at the hairline.
        val left = pad; val right = w - pad
        c.text(hd.toString(), left + numW / 2, h / 2f, numP())
        calligraphy(ctx, c, MONTH_RES[hmn - 1], RectF(left + numW + 4 * d, h * 0.1f, mid - 8 * d, h * 0.9f))
        c.drawRect(mid - d / 2, h * 0.3f, mid + d / 2, h * 0.7f, Paint().apply { color = Color.argb(46, 255, 255, 255) })
        c.text(day.dayOfMonth.toString(), right - numW / 2, h / 2f, numP())
        calligraphy(ctx, c, monthAr(day), RectF(mid + 8 * d, h * 0.2f, right - numW - 4 * d, h * 0.8f))
    }

    /** The date on one side, the next prayer on the other; the clock between them is live, on top. */
    private fun dateClock(ctx: Context, c: Canvas, s: EmranWidgetState, w: Int, h: Int, d: Float, ar: Boolean) {
        val day = today(s); val pad = 18 * d
        val small = minOf(h * 0.17f, 13f * d)
        val dateA = if (ar) Paint.Align.RIGHT else Paint.Align.LEFT
        val nextA = if (ar) Paint.Align.LEFT else Paint.Align.RIGHT
        val dateX = if (ar) w - pad else pad; val nextX = if (ar) pad else w - pad
        val line2 = if (ar) "${day.dayOfMonth} ${monthAr(day)}" else "${day.dayOfMonth} ${monthEn(day)}"
        c.text(weekday(day, ar), dateX, h / 2f - small * 0.8f, paint(inter(ctx, 400), small, SOFT, dateA).fit(weekday(day, ar), w * 0.28f))
        c.text(line2, dateX, h / 2f + small * 0.8f, paint(inter(ctx, 400), small, WHITE, dateA).fit(line2, w * 0.28f))
        val name = s.nextPrayerName?.let { prayerName(it, ar) } ?: return
        val time = s.nextPrayerEpochMillis?.let { hm(it, ar, s.use24Hour) } ?: return
        c.text(name, nextX, h / 2f - small * 0.8f, paint(inter(ctx, 400), small, SOFT, nextA).fit(name, w * 0.28f))
        c.text(time, nextX, h / 2f + small * 0.8f, paint(inter(ctx, 400), small, WHITE, nextA))
    }

    /** This month, today on a soft pill. Wide gets full day names; a tight square gets letters. */
    private fun calendar(ctx: Context, c: Canvas, s: EmranWidgetState, w: Int, h: Int, d: Float, ar: Boolean) {
        val day = today(s)
        val pad = 12 * d
        val firstDow = java.time.temporal.WeekFields.of(Locale.getDefault()).firstDayOfWeek
        val start = day.withDayOfMonth(1).with(java.time.temporal.TemporalAdjusters.previousOrSame(firstDow))
        val cw = (w - 2 * pad) / 7f
        val rh = (h - 2 * pad) / 7f
        val size = minOf(rh * 0.5f, cw * 0.46f)
        fun colX(i: Int) = if (ar) w - pad - cw * (i + 0.5f) else pad + cw * (i + 0.5f)
        val short = cw < 34 * d
        for (i in 0 until 7) {
            val dow = firstDow.plus(i.toLong())
            val label = when {
                ar -> dow.getDisplayName(java.time.format.TextStyle.NARROW, AR)
                short -> dow.getDisplayName(java.time.format.TextStyle.NARROW, Locale.ENGLISH)
                else -> dow.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)
            }
            c.text(label, colX(i), pad + rh / 2, paint(inter(ctx, 400), size * 0.9f, SOFT, Paint.Align.CENTER))
        }
        for (r in 0 until 6) for (i in 0 until 7) {
            val date = start.plusDays((r * 7 + i).toLong())
            val cy = pad + rh * (r + 1.5f)
            val isToday = date == day
            val inMonth = date.month == day.month
            if (isToday) {
                val half = minOf(cw, rh) * 0.46f
                val box = RectF(colX(i) - half, cy - half, colX(i) + half, cy + half)
                c.drawRoundRect(box, half * 0.6f, half * 0.6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(56, 255, 255, 255) })
            }
            c.text(date.dayOfMonth.toString(), colX(i), cy, paint(inter(ctx, if (isToday) 600 else 400), size, if (isToday || inMonth) WHITE else Color.argb(80, 255, 255, 255), Paint.Align.CENTER))
        }
    }

    /** One dot for every day of the year: past bright, today glowing, the rest faint. */
    private fun yearDots(ctx: Context, c: Canvas, s: EmranWidgetState, w: Int, h: Int, d: Float, ar: Boolean) {
        val day = today(s); val pad = 16 * d
        val total = day.lengthOfYear(); val done = day.dayOfYear
        c.text(day.year.toString(), if (ar) w - pad else pad, pad + 6 * d, paint(inter(ctx, 400), 13f * d, WHITE, if (ar) Paint.Align.RIGHT else Paint.Align.LEFT))
        c.text("${done * 100 / total}%", if (ar) pad else w - pad, pad + 6 * d, paint(inter(ctx, 400), 13f * d, WHITE, if (ar) Paint.Align.LEFT else Paint.Align.RIGHT))
        val top = pad + 22 * d
        val aw = w - 2 * pad; val ah = h - top - pad
        // The largest even spacing at which every day still fits the area.
        var cols = Math.ceil(Math.sqrt(total * aw.toDouble() / ah)).toInt().coerceAtLeast(1)
        var rows = Math.ceil(total.toDouble() / cols).toInt()
        while (cols > 1 && Math.ceil(total.toDouble() / (cols - 1)).toInt() * (aw / (cols - 2).coerceAtLeast(1)) <= ah) { cols--; rows = Math.ceil(total.toDouble() / cols).toInt() }
        val gap = minOf(aw / (cols - 1).coerceAtLeast(1), ah / (rows - 1).coerceAtLeast(1))
        val r = (gap * 0.19f).coerceAtMost(3 * d)
        val past = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(225, 255, 255, 255) }
        val ahead = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 255, 255, 255) }
        val x0 = pad + (aw - gap * (cols - 1)) / 2
        for (k in 0 until total) {
            val col = k % cols
            val x = if (ar) w - x0 - col * gap else x0 + col * gap
            val y = top + (k / cols) * gap
            if (k + 1 == done) {
                c.drawCircle(x, y, r * 2.6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(80, 255, 255, 255); maskFilter = BlurMaskFilter(r * 2f, BlurMaskFilter.Blur.NORMAL) })
                c.drawCircle(x, y, r * 1.35f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WHITE })
            } else c.drawCircle(x, y, r, if (k + 1 < done) past else ahead)
        }
    }
}
