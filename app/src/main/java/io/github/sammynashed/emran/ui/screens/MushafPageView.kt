package io.github.sammynashed.emran.ui.screens

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sammynashed.emran.R
import io.github.sammynashed.emran.data.QuranVerse
import io.github.sammynashed.emran.tajweed.MushafLayout
import io.github.sammynashed.emran.tajweed.Tajweed
import io.github.sammynashed.emran.tajweed.wordKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * KFGQPC Uthmanic Script HAFS (v22), the King Fahd Complex's own font for the
 * Madinah muṣḥaf, paired with the Complex's text in `quran.json`. It draws the
 * āyah rosette itself from the Arabic-Indic number alone (no U+06DD needed).
 */
val MushafFontFamily = FontFamily(Font(R.font.kfgqpc_hafs))

/**
 * One page of the Madinah muṣḥaf, as it is printed.
 *
 * - The real page: the same fifteen lines, the same words on each line, every page
 *   ending on the āyah the printed muṣḥaf ends on, in the Complex's own font.
 * - Each line is DRAWN, word by word, on one canvas: words are measured whole and
 *   placed from the right edge, so no layout box can ever cut a word short (the old
 *   per-word Text boxes showed "أَلَآ" as "أ" and "أُولَٰٓئِكَ" without its last letter).
 * - Lines are set edge to edge. One type size for the page; a line wider than the
 *   page is first condensed slightly (up to 10%, which the eye does not notice) and
 *   only then set smaller, so the page keeps the largest size the screen allows.
 * - Empty lines the layout reserves become the surah's ornamented title and the
 *   basmala. ۞ opens each quarter-ḥizb; ۩ marks a prostration.
 *
 * Hifz and tajweed are unchanged: words are addressed by āyah and position
 * ([wordKey]); hiding, fading in and the current-word highlight are applied when
 * drawing, so they never re-measure a line.
 */
@Composable
fun MushafPageView(
    pageNumber: Int,
    /** Verses already loaded for this page; used until the full text is ready. */
    loadedVerses: List<QuranVerse>,
    theme: QuranThemeMode,
    tajweed: Boolean,
    /** The surah Hifz is running over, or null when not in Hifz. */
    hifzSurah: Int?,
    revealed: Set<Int>,
    fading: Map<Int, Float>,
    current: Int?,
    highlight: Color,
    arabicDigits: List<Char>,
    zoom: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val source by produceState<MushafSource.Data?>(MushafSource.cached(), Unit) {
        if (value == null) value = withContext(Dispatchers.Default) { MushafSource.load(context) }
    }
    val lines = remember(pageNumber) { MushafLayout.linesOf(context, pageNumber) }
    val nextFirst = remember(pageNumber) { MushafLayout.linesOf(context, pageNumber + 1).firstOrNull { it.isNotEmpty() }?.firstOrNull() }

    // Until the full text is parsed (a fraction of a second, once), draw from what the
    // reader already loaded; lines of other surahs simply stay empty for that moment.
    val loadedWords = remember(loadedVerses) {
        loadedVerses.associate { (it.chapterId to it.id) to MushafSource.words(it.text) }
    }
    val slots = remember(lines, source, nextFirst, loadedWords) {
        val data = source
        buildSlots(
            lines, nextFirst,
            wordsOf = { s, a -> data?.wordsOf(s, a) ?: loadedWords[s to a].orEmpty() },
            rub = { s, a -> data?.isRub(s, a) == true },
            sajda = { s, a -> data?.sajdaWord(s, a) ?: -1 },
        )
    }
    val special = pageNumber <= 2
    val dark = theme != QuranThemeMode.DAY
    val ink = theme.textColor
    val ornament = theme.ornament
    // Odd pages are right-hand pages in a muṣḥaf (it opens from the right), so their
    // binding is on the left; even pages are bound on the right.
    val spineLeft = pageNumber % 2 == 1

    // Geometry is plain left-to-right; the words are placed right to left by hand.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(
            modifier
                .fillMaxSize()
                .drawBehind {
                    if (theme != QuranThemeMode.AMOLED) {
                        // The binding: the page curves away into the spine.
                        val w = 26.dp.toPx()
                        val shade = Color.Black.copy(alpha = if (dark) 0.30f else 0.075f)
                        drawRect(
                            Brush.horizontalGradient(
                                if (spineLeft) listOf(shade, Color.Transparent) else listOf(Color.Transparent, shade),
                                startX = if (spineLeft) 0f else size.width - w,
                                endX = if (spineLeft) w else size.width,
                            ),
                            topLeft = Offset(if (spineLeft) 0f else size.width - w, 0f),
                            size = Size(w, size.height),
                        )
                    }
                },
        ) {
            val density = LocalDensity.current
            val measurer = rememberTextMeasurer(cacheSize = 0)
            val sideMargin = 15.dp
            val stripH = 26.dp
            val areaW = maxWidth - sideMargin * 2
            val areaH = maxHeight - stripH * 2
            val areaWPx = with(density) { areaW.toPx() }

            // Vertical rhythm: fifteen equal lines; a surah title takes a little more.
            val weights = slots.map { if (it is Slot.Header) HEADER_WEIGHT else 1f }
            val unitH = if (special) areaH / 9.5f else areaH / weights.sum().coerceAtLeast(15f)

            // Page type size, in sp: as wide as the page allows (FIT_EMS covers 89% of all
            // lines without any condensing), and never so tall that diacritics touch.
            val fontSp = remember(areaW, unitH, slots, special) {
                with(density) {
                    val byHeight = unitH.toPx() / LINE_HEIGHT_EMS
                    val byWidth = if (special) {
                        // Pages 1-2: short centred lines, set larger, inside a narrower column.
                        val widest = slots.filterIsInstance<Slot.Line>()
                            .maxOfOrNull { naturalEms(it.items, measurer, density) } ?: 8f
                        areaWPx * 0.80f / widest
                    } else {
                        areaWPx / FIT_EMS
                    }
                    minOf(byWidth, byHeight).toSp().value
                }
            }

            val largePrint = zoom > 1.05f
            val printSp = fontSp * (1f + (zoom - 1f) * 2f).coerceAtMost(2.6f)
            val draw = DrawParams(ink, ornament, dark, tajweed, hifzSurah, revealed, fading, current, highlight)

            Column(Modifier.fillMaxSize()) {
                // Running head: the surah on the right, the juzʾ on the left.
                RunningHead(
                    right = source?.nameOf(firstSurahOf(lines))?.let { "سورة $it" } ?: "",
                    left = "الجزء ${juzOf(pageNumber).toArabic(arabicDigits)}",
                    color = ornament,
                    height = stripH,
                    sideMargin = sideMargin,
                )

                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .drawBehind {
                            if (!largePrint) {
                                // A fine double rule around the text block, as on the printed page.
                                val i1 = 5.dp.toPx()
                                val i2 = 8.dp.toPx()
                                drawRoundRect(
                                    ornament.copy(alpha = 0.55f),
                                    topLeft = Offset(i1, 0f), size = Size(size.width - i1 * 2, size.height),
                                    cornerRadius = CornerRadius(3.dp.toPx()), style = Stroke(1.2.dp.toPx()),
                                )
                                drawRoundRect(
                                    ornament.copy(alpha = 0.35f),
                                    topLeft = Offset(i2, 3.dp.toPx()), size = Size(size.width - i2 * 2, size.height - 6.dp.toPx()),
                                    cornerRadius = CornerRadius(2.dp.toPx()), style = Stroke(0.7.dp.toPx()),
                                )
                            }
                        },
                ) {
                    if (largePrint) {
                        val flow = remember(slots, printSp, areaWPx) { reflow(slots, areaWPx, printSp, density, measurer) }
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = sideMargin)
                                .padding(top = 8.dp, bottom = 120.dp),
                        ) {
                            flow.forEach { piece ->
                                when (piece) {
                                    is Flow.Header -> Box(Modifier.fillMaxWidth().height((printSp * 3.2f).dp)) {
                                        SurahHeader(source?.nameOf(piece.surah).orEmpty(), ink, ornament)
                                    }
                                    Flow.Basmala -> Box(Modifier.fillMaxWidth().height((printSp * LINE_HEIGHT_EMS).dp), contentAlignment = Alignment.Center) {
                                        Basmala(ink, printSp)
                                    }
                                    is Flow.Row -> MushafLine(
                                        items = piece.items, fontSp = printSp, mode = if (piece.justify) LineMode.Justify else LineMode.Start,
                                        params = draw, measurer = measurer,
                                        modifier = Modifier.fillMaxWidth().height((printSp * LINE_HEIGHT_EMS).dp),
                                    )
                                }
                            }
                        }
                    } else {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = sideMargin, vertical = 4.dp),
                            verticalArrangement = if (special) Arrangement.Center else Arrangement.Top,
                        ) {
                            slots.forEachIndexed { i, slot ->
                                val slotModifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (special) Modifier.height(unitH * weights[i]) else Modifier.weight(weights[i]))
                                when (slot) {
                                    is Slot.Header -> Box(slotModifier) {
                                        SurahHeader(source?.nameOf(slot.surah).orEmpty(), ink, ornament)
                                    }
                                    Slot.Basmala -> Box(slotModifier, contentAlignment = Alignment.Center) { Basmala(ink, fontSp) }
                                    Slot.Blank -> Spacer(slotModifier)
                                    is Slot.Line -> MushafLine(
                                        items = slot.items,
                                        fontSp = fontSp,
                                        mode = if (special) LineMode.Centre else LineMode.Justify,
                                        params = draw,
                                        measurer = measurer,
                                        modifier = slotModifier,
                                    )
                                }
                            }
                        }
                    }
                }

                PageNumber(pageNumber.toArabic(arabicDigits), ornament, stripH)
            }
        }
    }
}

/** Ems of line width the page size is chosen for (89% of lines fit at this size untouched). */
private const val FIT_EMS = 16.6f

/** A line's height, in ems: the font's ascent + descent (1.76) and a breath. */
private const val LINE_HEIGHT_EMS = 1.95f

/** How much taller than a text line the surah title is. */
private const val HEADER_WEIGHT = 1.35f

/** Smallest space between words, in ems. */
private const val MIN_GAP_EM = 0.12f

/** Space between words on a centred or start-aligned line, in ems. */
private const val NATURAL_GAP_EM = 0.28f

/** Lines may be condensed this much before they are set smaller. */
private const val MAX_CONDENSE = 0.90f

private const val BASMALA = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ"

private val INDIC = "٠١٢٣٤٥٦٧٨٩".toList()

private val JUZ_STARTS = intArrayOf(
    1, 22, 42, 62, 82, 102, 121, 142, 162, 182, 201, 222, 242, 262, 282,
    302, 322, 342, 362, 382, 402, 422, 442, 462, 482, 502, 522, 542, 562, 582,
)

private fun juzOf(page: Int): Int = JUZ_STARTS.indexOfLast { page >= it } + 1

private fun firstSurahOf(lines: List<List<MushafLayout.Run>>): Int =
    lines.firstOrNull { it.isNotEmpty() }?.first()?.surah ?: 1

private fun Int.toArabic(digits: List<Char>): String =
    toString().map { c -> if (c.isDigit()) digits[c.digitToInt()] else c }.joinToString("")

/** One printed word or ornament on a line. [word] is the word's index in its āyah, or one of the KIND_ values. */
private data class Item(val text: String, val surah: Int, val ayah: Int, val word: Int) {
    val isWord get() = word >= 0
}

private const val KIND_MARKER = -1
private const val KIND_RUB = -2
private const val KIND_SAJDA = -3

private sealed interface Slot {
    data class Header(val surah: Int) : Slot
    data object Basmala : Slot
    data object Blank : Slot
    data class Line(val items: List<Item>) : Slot
}

private sealed interface Flow {
    data class Header(val surah: Int) : Flow
    data object Basmala : Flow
    data class Row(val items: List<Item>, val justify: Boolean) : Flow
}

private enum class LineMode { Justify, Centre, Start }

private class DrawParams(
    val ink: Color,
    val ornament: Color,
    val dark: Boolean,
    val tajweed: Boolean,
    val hifzSurah: Int?,
    val revealed: Set<Int>,
    val fading: Map<Int, Float>,
    val current: Int?,
    val highlight: Color,
)

private fun mushafStyle(sp: Float) = TextStyle(
    fontFamily = MushafFontFamily,
    fontSize = sp.sp,
    textDirection = TextDirection.Rtl,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/**
 * A word laid out for drawing. [width] is the shaped width of the whole word and
 * [left] where its ink starts inside [layout].
 *
 * Why not just `layout.size.width`: a single-line Text is sized from Compose's width
 * estimate, and for an Arabic word split into tajweed colour runs that estimate is too
 * small. The word wrapped inside its own box and the tail was dropped: "وَمِمَّا" drew
 * as "وَمِمَّ", "قَبۡلِكَ" as "قَبۡلِ", "أَلَآ" as "أ". Laying the word out in a fixed,
 * very wide box, left-aligned, and asking the line for its real extent is exact.
 */
private class Laid(val layout: TextLayoutResult, val left: Float, val width: Float)

private fun measureItem(item: Item, sp: Float, params: DrawParams?, measurer: TextMeasurer): Laid {
    val text: AnnotatedString = if (params != null && params.tajweed && item.isWord) {
        buildAnnotatedString {
            append(item.text)
            Tajweed.annotate(item.text).forEach { span ->
                addStyle(SpanStyle(color = span.rule.colorFor(params.dark)), span.start, span.end)
            }
        }
    } else AnnotatedString(item.text)
    val color = when {
        params == null -> Color.Unspecified
        item.isWord -> params.ink
        else -> params.ornament
    }
    val layout = measurer.measure(
        text = text,
        style = mushafStyle(sp).copy(color = color, textAlign = TextAlign.Left),
        overflow = TextOverflow.Visible,
        softWrap = true,
        maxLines = 1,
        // A FIXED width. Given any freedom, Compose sizes the box from its width estimate,
        // which for coloured Arabic runs is too short; the word then wraps inside its own
        // box and maxLines = 1 throws the wrapped tail away.
        constraints = Constraints.fixedWidth(WIDE_BOX_PX),
    )
    val left = layout.getLineLeft(0)
    val right = layout.getLineRight(0)
    return Laid(layout, left, (right - left).coerceAtLeast(0f))
}

/** Room for any single word at any size, so a word is never broken or squeezed while measured. */
private const val WIDE_BOX_PX = 20_000

/** Width of a line at 1 em, with minimum gaps. */
private fun naturalEms(items: List<Item>, measurer: TextMeasurer, density: Density): Float {
    val ref = 20f
    val px = with(density) { ref.sp.toPx() }
    val words = items.sumOf { measureItem(it, ref, null, measurer).width.toDouble() }.toFloat()
    return words / px + MIN_GAP_EM * (items.size - 1).coerceAtLeast(0)
}

/**
 * One line of the page, drawn: every word measured whole, then placed from the right.
 * Justified lines spread their words to both edges; a line too wide for the page is
 * condensed (then, if it must, set smaller) so nothing is ever cut.
 */
@Composable
private fun MushafLine(
    items: List<Item>,
    fontSp: Float,
    mode: LineMode,
    params: DrawParams,
    measurer: TextMeasurer,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    // Measured once per line and colour scheme. Hifz state is applied in the draw pass.
    val laid = remember(items, fontSp, params.tajweed, params.dark, params.ink, params.ornament) {
        items.map { measureItem(it, fontSp, params, measurer) }
    }
    Canvas(modifier) {
        if (laid.isEmpty()) return@Canvas
        val em = with(density) { fontSp.sp.toPx() }
        val avail = size.width
        val wordsW = laid.sumOf { it.width.toDouble() }.toFloat()
        val gaps = (laid.size - 1).coerceAtLeast(0)
        val natural = wordsW + gaps * MIN_GAP_EM * em
        // Squeeze factor across the line: condense first, shrink after.
        val fit = if (natural > avail) avail / natural else 1f
        val sx = maxOf(fit, MAX_CONDENSE)
        val shrink = if (fit < MAX_CONDENSE) fit / MAX_CONDENSE else 1f
        val scaleX = sx * shrink
        val scaleY = shrink
        val inkW = wordsW * scaleX

        val shortLine = mode == LineMode.Justify && natural < avail * 0.70f
        val gap = when {
            mode == LineMode.Justify && !shortLine && gaps > 0 -> (avail - inkW) / gaps
            else -> NATURAL_GAP_EM * em * scaleX
        }
        val lineW = inkW + gap * gaps
        var right = when {
            mode == LineMode.Centre || shortLine -> (avail + lineW) / 2f
            else -> avail
        }
        val cy = size.height / 2f
        val p = params
        laid.forEachIndexed { i, word ->
            val item = items[i]
            val layout = word.layout
            val w = word.width * scaleX
            val x = right - w
            right = x - gap

            val inHifz = p.hifzSurah != null && item.surah == p.hifzSurah
            var alpha = if (p.hifzSurah != null && !inHifz) 0.32f else 1f
            if (inHifz && item.word != KIND_RUB) {
                // An āyah's rosette shows once its last word does; words hide until recited.
                val key = if (item.isWord) wordKey(item.ayah, item.word) else null
                val shown = if (key != null) key in p.revealed else true
                if (!shown) return@forEachIndexed
                val remaining = key?.let { p.fading[it] }
                if (remaining != null && remaining > 0f) alpha *= (1f - remaining)
                if (key != null && key == p.current) {
                    val pad = 0.12f * em
                    drawRoundRect(
                        p.highlight,
                        topLeft = Offset(x - pad, cy - layout.size.height * scaleY / 2f),
                        size = Size(w + pad * 2, layout.size.height * scaleY),
                        cornerRadius = CornerRadius(0.25f * em),
                    )
                }
            }
            val top = cy - layout.size.height / 2f
            withTransform({ scale(scaleX, scaleY, pivot = Offset(x, cy)) }) {
                drawText(layout, topLeft = Offset(x - word.left, top), alpha = alpha)
            }
        }
    }
}

/**
 * Large print: the page's words poured into lines as wide as the screen at [sp],
 * breaking only between words, with titles and basmala kept as their own lines.
 * The page's content does not change; only how many words share a line.
 */
private fun reflow(
    slots: List<Slot>,
    availPx: Float,
    sp: Float,
    density: Density,
    measurer: TextMeasurer,
): List<Flow> {
    val gap = with(density) { (sp * NATURAL_GAP_EM).sp.toPx() }
    val out = mutableListOf<Flow>()
    val line = mutableListOf<Item>()
    var used = 0f
    fun flush(justify: Boolean) {
        if (line.isNotEmpty()) out += Flow.Row(line.toList(), justify)
        line.clear(); used = 0f
    }
    slots.forEach { slot ->
        when (slot) {
            is Slot.Header -> { flush(false); out += Flow.Header(slot.surah) }
            Slot.Basmala -> { flush(false); out += Flow.Basmala }
            Slot.Blank -> Unit
            is Slot.Line -> slot.items.forEach { item ->
                val w = measureItem(item, sp, null, measurer).width
                val need = if (line.isEmpty()) w else used + gap + w
                if (need > availPx && line.isNotEmpty()) {
                    flush(true)
                    line += item; used = w
                } else {
                    line += item; used = need
                }
            }
        }
    }
    flush(false)
    return out
}

/**
 * Turns the page's fifteen lines into what each one shows.
 *
 * An empty line in the layout is not empty on paper: before a surah it holds the
 * surah's title frame, then (except for al-Fātiḥah and at-Tawbah) the basmala. Empty
 * lines at the foot of a page introduce the surah that begins on the next page.
 */
private fun buildSlots(
    lines: List<List<MushafLayout.Run>>,
    nextPageFirst: MushafLayout.Run?,
    wordsOf: (Int, Int) -> List<String>,
    rub: (Int, Int) -> Boolean,
    sajda: (Int, Int) -> Int,
): List<Slot> = lines.mapIndexed { i, runs ->
    if (runs.isNotEmpty()) {
        val items = buildList {
            runs.forEach { r ->
                val words = wordsOf(r.surah, r.ayah)
                val from = r.from.coerceIn(0, words.size)
                val until = r.until.coerceIn(from, words.size)
                if (from == 0 && until > 0 && rub(r.surah, r.ayah)) add(Item("۞", r.surah, r.ayah, KIND_RUB))
                val sajdaAt = sajda(r.surah, r.ayah)
                for (w in from until until) {
                    add(Item(words[w], r.surah, r.ayah, w))
                    if (w == sajdaAt) add(Item("۩", r.surah, r.ayah, KIND_SAJDA))
                }
                if (until == words.size && words.isNotEmpty()) add(Item(r.ayah.toArabic(INDIC), r.surah, r.ayah, KIND_MARKER))
            }
        }
        if (items.isEmpty()) Slot.Blank else Slot.Line(items)
    } else {
        val j = (i + 1 until lines.size).firstOrNull { lines[it].isNotEmpty() }
        if (j != null) {
            // Empty lines just before a surah's first āyah: title, then basmala.
            val first = lines[j].first()
            val remaining = j - i
            when {
                first.ayah != 1 || first.from != 0 -> Slot.Blank
                first.surah == 1 || first.surah == 9 -> if (remaining == 1) Slot.Header(first.surah) else Slot.Blank
                remaining == 2 -> Slot.Header(first.surah)
                remaining == 1 -> Slot.Basmala
                else -> Slot.Blank
            }
        } else {
            // Empty lines at the foot of the page belong to the surah opening the next one.
            val firstTrailing = lines.indexOfLast { it.isNotEmpty() } + 1
            val next = nextPageFirst
            when {
                next == null || next.ayah != 1 || next.from != 0 -> Slot.Blank
                i == firstTrailing -> Slot.Header(next.surah)
                i == firstTrailing + 1 && next.surah != 1 && next.surah != 9 -> Slot.Basmala
                else -> Slot.Blank
            }
        }
    }
}

@Composable
private fun Basmala(color: Color, fontSp: Float) {
    androidx.compose.material3.Text(
        text = BASMALA,
        style = mushafStyle(fontSp),
        color = color,
        maxLines = 1,
        softWrap = false,
    )
}

/**
 * The surah's title: the illuminated cartouche (an arabesque frame, tinted to the
 * page's ornament colour) with the name in its medallion.
 */
@Composable
private fun SurahHeader(name: String, ink: Color, ornament: Color) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val h = maxHeight * 0.94f
        // The art is 4.2:1; it may stretch to 5.4:1 to span the line, no further.
        val w = minOf(maxWidth, h * 5.4f)
        Box(Modifier.width(w).height(h), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.surah_header),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ornament),
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
            val density = LocalDensity.current
            val measurer = rememberTextMeasurer()
            val title = "سورة $name"
            // Sized to sit inside the medallion (about a third of the frame's width).
            val sp = remember(title, w, h) {
                with(density) {
                    val ref = measurer.measure(
                        title,
                        TextStyle(fontFamily = SurahNameFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp),
                        softWrap = false, maxLines = 1,
                    ).size.width.coerceAtLeast(1)
                    val byWidth = 20f * (w * 0.33f).toPx() / ref
                    val byHeight = (h * 0.42f).toSp().value
                    minOf(byWidth, byHeight)
                }
            }
            androidx.compose.material3.Text(
                text = title,
                fontFamily = SurahNameFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = sp.sp,
                color = ink,
                maxLines = 1,
                softWrap = false,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
            )
        }
    }
}

@Composable
private fun RunningHead(right: String, left: String, color: Color, height: Dp, sideMargin: Dp) {
    Row(
        Modifier.fillMaxWidth().height(height).padding(horizontal = sideMargin + 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Text(left, color = color, fontSize = 13.sp, fontFamily = SurahNameFontFamily, fontWeight = FontWeight.Bold)
        androidx.compose.material3.Text(right, color = color, fontSize = 13.sp, fontFamily = SurahNameFontFamily, fontWeight = FontWeight.Bold)
    }
}

/** The folio number, between two short rules. */
@Composable
private fun PageNumber(text: String, color: Color, height: Dp) {
    Row(
        Modifier.fillMaxWidth().height(height),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.width(22.dp).height(1.dp)) { drawRect(color.copy(alpha = 0.6f)) }
        androidx.compose.material3.Text(
            text, color = color, fontSize = 14.sp, fontFamily = SurahNameFontFamily, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        Canvas(Modifier.width(22.dp).height(1.dp)) { drawRect(color.copy(alpha = 0.6f)) }
    }
}

/**
 * The whole Qur'an's words, by surah and āyah, plus where the quarter-ḥizb marks and
 * prostrations fall. Parsed once from `quran.json` and `mushaf_marks.json`, off the
 * main thread.
 */
object MushafSource {
    class Data(
        private val words: Map<Long, List<String>>,
        private val names: Map<Int, String>,
        private val rub: Set<Long>,
        private val sajda: Map<Long, Int>,
    ) {
        fun wordsOf(s: Int, a: Int): List<String> = words[key(s, a)].orEmpty()
        fun nameOf(s: Int): String = names[s].orEmpty()
        fun isRub(s: Int, a: Int): Boolean = key(s, a) in rub
        fun sajdaWord(s: Int, a: Int): Int = sajda[key(s, a)] ?: -1
    }

    @Volatile private var data: Data? = null

    fun cached(): Data? = data

    private fun key(s: Int, a: Int): Long = (s.toLong() shl 16) or a.toLong()

    @Synchronized
    fun load(context: Context): Data {
        data?.let { return it }
        val root = JSONArray(context.assets.open("quran.json").bufferedReader().use { it.readText() })
        val words = HashMap<Long, List<String>>(6300)
        val names = HashMap<Int, String>(114)
        for (i in 0 until root.length()) {
            val surah = root.getJSONObject(i)
            val id = surah.getInt("id")
            names[id] = surah.getString("name")
            val verses = surah.getJSONArray("verses")
            for (v in 0 until verses.length()) {
                val verse = verses.getJSONObject(v)
                words[key(id, verse.getInt("id"))] = words(verse.getString("text"))
            }
        }
        val rub = HashSet<Long>()
        val sajda = HashMap<Long, Int>()
        runCatching {
            val marks = JSONObject(context.assets.open("mushaf_marks.json").bufferedReader().use { it.readText() })
            val r = marks.getJSONArray("rub")
            for (i in 0 until r.length()) {
                val (s, a) = r.getString(i).split(":").map { it.toInt() }
                rub += key(s, a)
            }
            val sj = marks.getJSONArray("sajda")
            for (i in 0 until sj.length()) {
                val e = sj.getJSONArray(i)
                sajda[key(e.getInt(0), e.getInt(1))] = e.getInt(2)
            }
        }
        return Data(words, names, rub, sajda).also { data = it }
    }

    /** Ornaments the renderer draws itself are stripped, so they never appear twice. */
    fun words(text: String): List<String> =
        text.replace(Regex("[\\{\\}\\(\\)\\[\\]﴿﴾۝۞۩\\u06DD\\u06DE\\uFD3E\\uFD3F]"), "")
            .replace(Regex("[\\s\\d٠-٩\\u200E\\u200F\\u200C]+$"), "")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
}
