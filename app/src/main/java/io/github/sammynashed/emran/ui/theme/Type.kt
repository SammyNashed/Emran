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
 * Scripture and supplication text.
 *
 * A traditional Naskh, chosen for its tashkeel: vowel marks are the difference
 * between reciting a du'a correctly and not, and a UI sans places them far less
 * carefully than a Naskh designed around them.
 */
@OptIn(ExperimentalTextApi::class)
val NaskhFontFamily = FontFamily(
    Font(R.font.noto_naskh_arabic, FontWeight.Normal, variationSettings = weightAxis(400)),
    Font(R.font.noto_naskh_arabic, FontWeight.Medium, variationSettings = weightAxis(500)),
    Font(R.font.noto_naskh_arabic, FontWeight.Bold, variationSettings = weightAxis(700))
)

/**
 * Display numerals -- countdowns, streak counts, prayer times.
 *
 * Geometric and even-width, so a ticking figure does not visibly reflow as its
 * digits change.
 */
@OptIn(ExperimentalTextApi::class)
val OutfitFontFamily = FontFamily(
    Font(R.font.outfit, FontWeight.Normal, variationSettings = weightAxis(400)),
    Font(R.font.outfit, FontWeight.Medium, variationSettings = weightAxis(500)),
    Font(R.font.outfit, FontWeight.SemiBold, variationSettings = weightAxis(600)),
    Font(R.font.outfit, FontWeight.Bold, variationSettings = weightAxis(700)),
    Font(R.font.outfit, FontWeight.ExtraBold, variationSettings = weightAxis(800))
)

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = OutfitFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp
    ),
    displayMedium = TextStyle(
        fontFamily = OutfitFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp
    ),
    displaySmall = TextStyle(
        fontFamily = OutfitFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 44.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = ReadexFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = ReadexFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = ReadexFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = ReadexFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = ReadexFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = ReadexFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = ReadexFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = ReadexFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = ReadexFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = ReadexFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = ReadexFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = ReadexFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
