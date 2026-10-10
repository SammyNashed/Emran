package io.github.sammynashed.emran.tajweed

import android.content.Context
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import org.json.JSONArray
import org.json.JSONObject

/**
 * Where every word of the Qur'an sits on the printed page.
 *
 * The Madinah muṣḥaf is 604 pages of exactly fifteen lines, and every page ends
 * on a complete ayah. That geometry is not decoration: it is fixed so that a
 * memoriser can anchor a passage to a position - third line down, left-hand
 * page - and recall it that way. Reflowing the text to whatever width a phone
 * happens to have throws that away, which is why a scrolling muṣḥaf reads as
 * scattered to anyone who learned from a printed one.
 *
 * `mushaf_lines.json` restores it. Each page holds its lines, each line holds
 * the runs of words that belong on it, and the runs index Emran's own text - so
 * the tajweed colouring and the recitation engine keep working on exactly the
 * words they already knew.
 */
object MushafLayout {

    /** One run of words from a single ayah, on one line. */
    data class Run(val surah: Int, val ayah: Int, val from: Int, val until: Int)

    private var pages: Map<Int, List<List<Run>>>? = null

    /** Lines of [page], or empty if the layout is unavailable. */
    fun linesOf(context: Context, page: Int): List<List<Run>> =
        load(context)[page].orEmpty()

    fun isLoaded(context: Context): Boolean = load(context).isNotEmpty()

    @Synchronized
    private fun load(context: Context): Map<Int, List<List<Run>>> {
        pages?.let { return it }
        val parsed = runCatching {
            val text = context.assets.open("mushaf_lines.json")
                .bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            val out = HashMap<Int, List<List<Run>>>(root.length())
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val lines = root.getJSONArray(key)
                out[key.toInt()] = (0 until lines.length()).map { i ->
                    lines.getJSONArray(i).toRuns()
                }
            }
            out
        }.getOrElse {
            // A missing or malformed layout must not take the muṣḥaf with it;
            // the caller falls back to flowing text.
            emptyMap()
        }
        pages = parsed
        return parsed
    }

    private fun JSONArray.toRuns(): List<Run> = (0 until length()).map { j ->
        val r = getJSONArray(j)
        Run(r.getInt(0), r.getInt(1), r.getInt(2), r.getInt(3))
    }
}

/**
 * Builds one printed line as its own page model.
 *
 * Each line becomes a [MushafPage] in its own right, which is what lets the
 * tajweed colouring and Hifz mode work on it untouched: both address words by
 * ayah number and position within the ayah, and neither has any notion of how
 * the page is broken up.
 *
 * @param wordsOf the words of an ayah, in order, from Emran's own text.
 */
fun mushafLine(
    runs: List<MushafLayout.Run>,
    wordsOf: (surah: Int, ayah: Int) -> List<String>,
    markerOf: (surah: Int, ayah: Int) -> String?,
    darkTheme: Boolean,
    tajweed: Boolean,
): MushafPage {
    val words = mutableListOf<MushafWord>()
    val text = buildAnnotatedString {
        runs.forEachIndexed { index, run ->
            if (index > 0) append(" ")
            val all = wordsOf(run.surah, run.ayah)
            if (all.isEmpty()) return@forEachIndexed

            val from = run.from.coerceIn(0, all.size)
            val until = run.until.coerceIn(from, all.size)
            for (w in from until until) {
                if (w > from) append(" ")
                val start = length
                append(all[w])
                words += MushafWord(run.ayah, w, start, length)
                if (tajweed) {
                    Tajweed.annotate(all[w]).forEach { span ->
                        addStyle(
                            SpanStyle(color = span.rule.colorFor(darkTheme)),
                            start + span.start,
                            start + span.end,
                        )
                    }
                }
            }
            // The ornamented ayah number belongs at the end of the ayah, which
            // is only on the line that actually finishes it.
            if (until == all.size) {
                markerOf(run.surah, run.ayah)?.let { append(it) }
            }
        }
    }
    return MushafPage(text, words)
}

/** Convenience for callers that only need the text. */
fun MushafPage.plain(): AnnotatedString = text
