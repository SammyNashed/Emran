package io.github.sammynashed.emran.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.sammynashed.emran.R

/*
 * Emran's typefaces. All three are SIL Open Font License 1.1, which permits
 * bundling in a published app with no user-facing credit; the licence texts
 * ship in assets/licenses/ to satisfy the one obligation the OFL does impose.
 *
 * All three are *variable* fonts, so a single file covers every weight. The
 * weight axis needs API 26; on 24-25 each falls back to its default instance,
 * which is a slightly flatter hierarchy rather than a broken one.
 */

@OptIn(ExperimentalTextApi::class)
private fun weightAxis(weight: Int) = FontVariation.Settings(FontVariation.weight(weight))

/**
 * The interface family. Readex Pro covers Arabic and Latin in one design with
 * harmonised metrics, which is why it is here rather than a prettier pairing:
 * in a bilingual RTL app, two families means Arabic and Latin sitting at
 * visibly different optical sizes on the same line, and a lifetime of
 * per-language overrides chasing it.
 */
@OptIn(ExperimentalTextApi::class)
val ReadexFontFamily = FontFamily(
    Font(R.font.readex_pro, FontWeight.Light, variationSettings = weightAxis(300)),
    Font(R.font.readex_pro, FontWeight.Normal, variationSettings = weightAxis(400)),
    Font(R.font.readex_pro, FontWeight.Medium, variationSettings = weightAxis(500)),
    Font(R.font.readex_pro, FontWeight.SemiBold, variationSettings = weightAxis(600)),
    Font(R.font.readex_pro, FontWeight.Bold, variationSettings = weightAxis(700))
)

/**
 * English text and every number: Inter, a calm neo-grotesque with even (tabular) figures, so
 * a ticking time never reflows. Readex Pro stays the Arabic face, where it is at its best;
 * its Latin is wide and airy, which made English screens look busy.
 */
@OptIn(ExperimentalTextApi::class)
val InterFontFamily = FontFamily(
    Font(R.font.inter, FontWeight.Light, variationSettings = weightAxis(300)),
    Font(R.font.inter, FontWeight.Normal, variationSettings = weightAxis(400)),
    Font(R.font.inter, FontWeight.Medium, variationSettings = weightAxis(500)),
    Font(R.font.inter, FontWeight.SemiBold, variationSettings = weightAxis(600)),
    Font(R.font.inter, FontWeight.Bold, variationSettings = weightAxis(700)),
)

/**
 * One scale for the whole app: titles semibold, body regular, labels medium, and no added
 * letter-spacing (Inter is spaced for screens already; spacing Arabic breaks its joins).
 */
fun emranTypography(arabic: Boolean): Typography {
    val ui = if (arabic) ReadexFontFamily else InterFontFamily
    fun st(family: FontFamily, w: FontWeight, size: Int, line: Int) = TextStyle(fontFamily = family, fontWeight = w, fontSize = size.sp, lineHeight = line.sp)
    return Typography(
        displayLarge = st(InterFontFamily, FontWeight.Light, 57, 64),
        displayMedium = st(InterFontFamily, FontWeight.Light, 45, 52),
        displaySmall = st(InterFontFamily, FontWeight.Normal, 36, 44),
        headlineLarge = st(ui, FontWeight.SemiBold, 30, 38),
        headlineMedium = st(ui, FontWeight.SemiBold, 26, 34),
        headlineSmall = st(ui, FontWeight.SemiBold, 22, 30),
        titleLarge = st(ui, FontWeight.SemiBold, 20, 26),
        titleMedium = st(ui, FontWeight.Medium, 16, 22),
        titleSmall = st(ui, FontWeight.Medium, 14, 20),
        bodyLarge = st(ui, FontWeight.Normal, 16, 24),
        bodyMedium = st(ui, FontWeight.Normal, 14, 20),
        bodySmall = st(ui, FontWeight.Normal, 12, 16),
        labelLarge = st(ui, FontWeight.Medium, 14, 20),
        labelMedium = st(ui, FontWeight.Medium, 12, 16),
        labelSmall = st(ui, FontWeight.Medium, 11, 16),
    )
}
