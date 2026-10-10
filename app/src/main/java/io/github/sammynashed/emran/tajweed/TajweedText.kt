package io.github.sammynashed.emran.tajweed

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

/**
 * One word of the page, and where it sits in the rendered string.
 *
 * Hifz mode has to obscure individual words inside a justified, flowing page.
 * Splitting the page into a composable per word would make that easy and would
 * also destroy the line breaking that makes it read like a muṣḥaf, so the page
 * stays a single text and the words are addressed by character range instead.
 */
data class MushafWord(
    /** The āyah's real number within its surah, not its index on this page. */
    val ayah: Int,
    /** Index of this word within its āyah, counting whitespace-separated words. */
    val word: Int,
    val start: Int,
    val end: Int,
) {
    /** Stable across pages, so a reveal survives paging and re-rendering. */
    val key: Int get() = wordKey(ayah, word)
}

/**
 * Identity of one word of the surah.
 *
 * Deliberately not a page ordinal. In paginated reading each page builds its
 * own model, so an ordinal means something different on every page - and Hifz
 * mode reveals across the whole surah. Keying on the āyah's own number and the
 * word's place within it is the same everywhere the surah is drawn.
 */
fun wordKey(ayah: Int, word: Int): Int = (ayah shl 12) or (word and 0xFFF)

/**
 * A rendered page: the text, and every word's position in it.
 *
 * [words] is in reading order, so its index doubles as a stable ordinal for the
 * whole page - which is what Hifz mode tracks as it reveals.
 */
data class MushafPage(
    val text: AnnotatedString,
    val words: List<MushafWord>,
) {
    /** Every word key on this page, in reading order. */
    val keys: List<Int> get() = words.map { it.key }
}

/**
 * Builds a page of the muṣḥaf with the tajweed rules coloured in.
 *
 * Each āyah is annotated **on its own**, never on the joined page. Annotating
 * the concatenation would be simpler and wrong: the rules for nūn sākinah and
 * tanwīn look at the following letter, and on a joined page that letter is the
 * first letter of the *next* āyah. The verse number sitting between them is
 * punctuation, which the annotator skips, so nothing would stop a tanwīn at the
 * end of one āyah from taking its rule from the beginning of another - a rule
 * the reader is not supposed to apply, since that is where they stop.
 *
 * @param verses each āyah as `text to trailing marker` (the marker being the
 *   ornamented verse number, which is never coloured).
 */
fun tajweedPage(
    /** Each āyah as `number, text, trailing marker`. */
    verses: List<Triple<Int, String, String>>,
    separator: String,
    darkTheme: Boolean,
    enabled: Boolean,
): MushafPage {
    val words = mutableListOf<MushafWord>()
    val text = buildAnnotatedString {
        verses.forEachIndexed { i, (ayahNumber, verseText, marker) ->
            if (i > 0) append(separator)
            val base = length
            append(verseText)

            // Record where each word landed, before any styling is applied -
            // spans do not move text, so these offsets stay valid.
            var w = 0
            var cursor = 0
            while (cursor < verseText.length) {
                while (cursor < verseText.length && verseText[cursor].isWhitespace()) cursor++
                if (cursor >= verseText.length) break
                val wordStart = cursor
                while (cursor < verseText.length && !verseText[cursor].isWhitespace()) cursor++
                words += MushafWord(ayahNumber, w++, base + wordStart, base + cursor)
            }

            if (enabled) {
                Tajweed.annotate(verseText).forEach { span ->
                    addStyle(
                        SpanStyle(color = span.rule.colorFor(darkTheme)),
                        base + span.start,
                        base + span.end,
                    )
                }
            }
            // The verse number is left unstyled, so it keeps whatever colour the
            // page is drawn in - including the theme's fade animation.
            append(marker)
        }
    }
    return MushafPage(text, words)
}

/**
 * The page as Hifz mode draws it.
 *
 * A word not yet recited is painted **transparent**, not blurred and not dimmed.
 * The glyphs are still laid out, so line breaks, justification and the position
 * of every other word are exactly what they are when reading normally - the
 * page does not reflow as you recite, words simply appear in the spaces they
 * already occupied.
 *
 * Transparency also removes a problem blurring had: `Modifier.blur` does nothing
 * below API 31, so on Android 11 and older a "blurred" surah was legible - the
 * whole answer, handed over. A colour applies everywhere.
 *
 * [current] marks the word being recited now, given a background rather than a
 * different text colour, so following along never changes how the script itself
 * looks.
 */
fun hifzLayer(
    page: MushafPage,
    revealed: Set<Int>,
    fading: Map<Int, Float>,
    current: Int?,
    highlight: Color,
    base: Color,
): AnnotatedString = buildAnnotatedString {
    append(page.text)
    page.words.forEach { w ->
        if (w.key in revealed) {
            // `fading` counts *down* from 1 as the animation runs, so a word
            // arriving is drawn at the inverse: from nothing to fully present.
            val remaining = fading[w.key]
            if (remaining != null && remaining > 0f) {
                addStyle(
                    SpanStyle(color = base.copy(alpha = 1f - remaining)),
                    w.start,
                    w.end,
                )
            }
            // Fully revealed words are left alone, so they keep whatever colour
            // the tajweed rules already gave them.
        } else {
            addStyle(SpanStyle(color = Color.Transparent), w.start, w.end)
        }
        if (w.key == current) {
            addStyle(SpanStyle(background = highlight), w.start, w.end)
        }
    }
}
