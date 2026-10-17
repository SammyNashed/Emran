package io.github.sammynashed.emran.ui.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf

object Localizations {
    private val translations = mapOf(
        "dhikr_new_tasbih" to mapOf("en" to "New Tasbih Counter", "ar" to "عداد تسبيح جديد"),
        "dhikr_create" to mapOf("en" to "Create", "ar" to "إنشاء العداد"),
        "dhikr_cancel" to mapOf("en" to "Cancel", "ar" to "إلغاء"),
        "dhikr_arabic_req" to mapOf("en" to "Arabic Text (Required)", "ar" to "النص العربي (مطلوب)"),
        "dhikr_trans_opt" to mapOf("en" to "English Translation (Optional)", "ar" to "الترجمة أو التفسير (اختياري)"),
        "dhikr_target_goal" to mapOf("en" to "Target Goal", "ar" to "العدد المستهدف"),
        "quran_err_fmt" to mapOf("en" to "Error: %s", "ar" to "خطأ: %s"),
        "quran_retry" to mapOf("en" to "Retry", "ar" to "أعد المحاولة"),
    )

    fun string(key: String, lang: String, default: String = key): String {
        return translations[key]?.get(lang) ?: translations[key]?.get("en") ?: default
    }
}

val LocalAppLanguage = compositionLocalOf { "en" }

@Composable
@ReadOnlyComposable
fun String.toAppNumerals(): String {
    return this.map { char ->
        when (char) {
            '٠' -> '0'
            '١' -> '1'
            '٢' -> '2'
            '٣' -> '3'
            '٤' -> '4'
            '٥' -> '5'
            '٦' -> '6'
            '٧' -> '7'
            '٨' -> '8'
            '٩' -> '9'
            else -> char
        }
    }.joinToString("")
}

@Composable
@ReadOnlyComposable
fun Int.toAppNumerals(): String = this.toString().toAppNumerals()

@Composable
@ReadOnlyComposable
fun Long.toAppNumerals(): String = this.toString().toAppNumerals()

@Composable
fun Text(
    text: String,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    fontStyle: androidx.compose.ui.text.font.FontStyle? = null,
    fontWeight: androidx.compose.ui.text.font.FontWeight? = null,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    textDecoration: androidx.compose.ui.text.style.TextDecoration? = null,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (androidx.compose.ui.text.TextLayoutResult) -> Unit = {},
    style: androidx.compose.ui.text.TextStyle = androidx.compose.material3.LocalTextStyle.current,
    bypassNumerals: Boolean = false
) {
    androidx.compose.material3.Text(
        text = if (bypassNumerals) text else text.toAppNumerals(),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style
    )
}

@Composable
fun Text(
    text: androidx.compose.ui.text.AnnotatedString,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    fontStyle: androidx.compose.ui.text.font.FontStyle? = null,
    fontWeight: androidx.compose.ui.text.font.FontWeight? = null,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    textDecoration: androidx.compose.ui.text.style.TextDecoration? = null,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (androidx.compose.ui.text.TextLayoutResult) -> Unit = {},
    style: androidx.compose.ui.text.TextStyle = androidx.compose.material3.LocalTextStyle.current,
    bypassNumerals: Boolean = false
) {
    val formattedText = if (bypassNumerals) text.text else text.text.toAppNumerals()
    val builder = androidx.compose.ui.text.AnnotatedString.Builder(formattedText)
    text.spanStyles.forEach { builder.addStyle(it.item, it.start, it.end) }
    text.paragraphStyles.forEach { builder.addStyle(it.item, it.start, it.end) }
    text.getStringAnnotations(0, text.length).forEach { builder.addStringAnnotation(it.tag, it.item, it.start, it.end) }
    
    androidx.compose.material3.Text(
        text = builder.toAnnotatedString(),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style
    )
}

