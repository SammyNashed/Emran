package io.github.sammynashed.emran.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sammynashed.emran.data.QuranVerse
import io.github.sammynashed.emran.tajweed.MushafLayout
import io.github.sammynashed.emran.tajweed.MushafText
import io.github.sammynashed.emran.tajweed.mushafLine

/**
 * One page of the muṣḥaf, laid out as it is printed.
 *
 * Fifteen lines, the same fifteen lines as the Madinah muṣḥaf, with every ayah
 * beginning on the line it begins on there. That fixed geometry is what makes a
 * printed muṣḥaf usable for hifz - a passage lives at a place on a page, and
 * recall leans on the place as much as the words. Text reflowed to whatever
 * width a phone happens to have has no places in it.
 *
 * Each line is built as its own page model, so the tajweed colouring and Hifz
 * mode operate on it exactly as they do on flowing text: both address a word by
 * its ayah and its position within that ayah, and neither cares how the page is
 * divided.
 */
@Composable
fun MushafPageLines(
    pageNumber: Int,
    verses: List<QuranVerse>,
    fontSizeMultiplier: Float,
    textColor: Color,
    darkTheme: Boolean,
    tajweed: Boolean,
    hifz: Boolean,
    revealed: Set<Int>,
    fading: Map<Int, Float>,
    current: Int?,
    highlight: Color,
    arabicDigits: List<Char>,
    currentTheme: QuranThemeMode,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lines = remember(pageNumber) { MushafLayout.linesOf(context, pageNumber) }

    // Words, cleaned the same way the flowing renderer cleans them, so a word
    // index means the same thing in both.
    val wordsByAyah = remember(verses) {
        verses.associate { v ->
            (v.chapterId to v.id) to clean(v.text).split(Regex("\\s+")).filter { it.isNotBlank() }
        }
    }
    val surahOfAyahOne = remember(verses) {
        verses.filter { it.id == 1 }.associateBy { it.chapterId }
    }

    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(modifier.fillMaxWidth()) {
      val available = maxWidth
      // The page is fitted to the screen at 100%. Enlarging magnifies that
      // page and lets the reader pan across it, which is how a muṣḥaf behaves
      // on paper and in every app that renders one: the lines do not re-break
      // because the reader leaned in. Re-fitting them to the new size would
      // put different words on every line at every zoom level and destroy the
      // fixed geometry the layout exists to keep.
      val zoom = fontSizeMultiplier.coerceAtLeast(1f)
      val base = 32f

      // One size for the whole page, chosen so the widest line fits.
      //
      // Sizing each line on its own filled the width nicely and looked wrong:
      // lines that needed more shrinking came out visibly smaller and thinner
      // than their neighbours, and any line still too wide at the floor was
      // simply cut off, because a single line that may not wrap has nowhere to
      // put the overflow. A printed muṣḥaf uses one size throughout and varies
      // the *script*, not the type - so the page picks a size that the longest
      // line can live at, and every line shares it.
      val pageFont = remember(lines, available, base, wordsByAyah, tajweed, darkTheme) {
          val target = with(density) { available.toPx() }
          var factor = 1f
          lines.forEach { runs ->
              if (runs.isEmpty()) return@forEach
              val built = mushafLine(
                  runs = runs,
                  wordsOf = { sr, a -> wordsByAyah[sr to a].orEmpty() },
                  markerOf = { _, a -> " ﴿${a.toArabicDigits(arabicDigits)}﴾" },
                  darkTheme = darkTheme,
                  tajweed = false,
              )
              val w = measurer.measure(
                  text = built.text,
                  style = TextStyle(fontFamily = HafsFontFamily, fontSize = base.sp),
                  softWrap = false,
              ).size.width
              if (w > 0) factor = minOf(factor, target / w)
          }
          // Only ever shrink to fit; the page is never blown up to fill a gap.
          (base * factor).coerceIn(base * 0.45f, base)
      }

      Column(
          Modifier
              .then(
                  if (zoom > 1f) {
                      Modifier
                          .horizontalScroll(rememberScrollState())
                          .width(available * zoom)
                  } else {
                      Modifier.fillMaxWidth()
                  },
              ),
      ) {
        lines.forEachIndexed { index, runs ->
            if (runs.isEmpty()) {
                // A blank line on the page is still a line; keeping its height
                // is what stops the rest of the page shifting up.
                Spacer(Modifier.height((pageFont * zoom * 1.95f).dp))
                return@forEachIndexed
            }

            // A surah that begins on this line gets its heading above it, the
            // way the printed page carries one.
            runs.firstOrNull()?.let { first ->
                if (first.from == 0 && first.ayah == 1) {
                    surahOfAyahOne[first.surah]?.let { v ->
                        if (index > 0) Spacer(Modifier.height(12.dp))
                        SurahHeaderBanner(
                            name = v.chapterName,
                            fontSizeMultiplier = fontSizeMultiplier,
                            currentTheme = currentTheme,
                        )
                        Spacer(Modifier.height(10.dp))
                        if (first.surah != 1 && first.surah != 9) {
                            BismillahText(
                                fontSizeMultiplier = fontSizeMultiplier,
                                currentTheme = currentTheme,
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }

            val line = remember(runs, tajweed, darkTheme, wordsByAyah) {
                mushafLine(
                    runs = runs,
                    wordsOf = { s, a -> wordsByAyah[s to a].orEmpty() },
                    markerOf = { _, a -> " ﴿${a.toArabicDigits(arabicDigits)}﴾" },
                    darkTheme = darkTheme,
                    tajweed = tajweed,
                )
            }

            MushafText(
                page = line,
                fontFamily = HafsFontFamily,
                fontSize = (pageFont * zoom).sp,
                // Line spacing stays put whatever the size, so the fifteen
                // lines sit on an even grid instead of drifting.
                lineHeight = (pageFont * zoom * 1.95f).sp,
                color = textColor,
                hifz = hifz,
                revealed = revealed,
                fading = fading,
                current = current,
                highlight = highlight,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
      }
    }
}

/** True when the printed layout is available; callers fall back if not. */
@Composable
fun mushafLayoutAvailable(): Boolean {
    val context = LocalContext.current
    return remember { MushafLayout.isLoaded(context) }
}

private fun Int.toArabicDigits(digits: List<Char>): String =
    toString().map { c -> if (c.isDigit()) digits[c.digitToInt()] else c }.joinToString("")

/**
 * Strips the ornaments the layout supplies itself.
 *
 * Verse numbers, sajdah and hizb marks are drawn by the renderer at the point
 * the layout puts them, so leaving copies inside the text would double them.
 */
private fun clean(text: String): String =
    text.replace(Regex("[\\{\\}\\(\\)\\[\\]﴿﴾۝۞۩\\u06DD\\u06DE\\uFD3E\\uFD3F]"), "")
        .replace(Regex("[\\s\\d٠-٩\\u200E\\u200F\\u200C]+$"), "")
        .trim()
