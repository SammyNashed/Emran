package io.github.sammynashed.emran.tajweed

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.TextUnit

/**
 * The muṣḥaf page, optionally with unrecited words hidden.
 *
 * Hifz mode does not need a second layer or a blur. Painting an unrecited word
 * transparent leaves it laid out but unseen, so the page holds its shape while
 * words appear in place - and unlike a blur, it behaves identically on every
 * Android version.
 */
@Composable
fun MushafText(
    page: MushafPage,
    fontFamily: FontFamily,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    /** Off renders the plain page; on hides everything not yet recited. */
    hifz: Boolean = false,
    revealed: Set<Int> = emptySet(),
    fading: Map<Int, Float> = emptyMap(),
    /** The word being recited now, or null. */
    current: Int? = null,
    highlight: Color = Color.Unspecified,
    /**
     * Keep the text on a single line, however wide it is.
     *
     * A printed muṣḥaf line is one line. Letting it wrap turns one line of the
     * page into two on screen, which destroys the fifteen-line geometry the
     * whole layout exists to preserve - so a line that will not fit is scaled
     * down by the caller rather than broken.
     */
    singleLine: Boolean = false,
) {
    val style = TextStyle(
        textDirection = TextDirection.Rtl,
        lineBreak = LineBreak.Paragraph,
    )

    androidx.compose.material3.Text(
        text = if (hifz) {
            hifzLayer(page, revealed, fading, current, highlight, color)
        } else {
            page.text
        },
        fontFamily = fontFamily,
        fontSize = fontSize,
        lineHeight = lineHeight,
        color = color,
        textAlign = if (singleLine) TextAlign.Center else TextAlign.Justify,
        maxLines = if (singleLine) 1 else Int.MAX_VALUE,
        softWrap = !singleLine,
        style = style,
        modifier = modifier.fillMaxWidth(),
    )
}
