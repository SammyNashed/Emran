package io.github.sammynashed.emran.glance.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.background
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import io.github.sammynashed.emran.glance.state.EmranWidgetState
import io.github.sammynashed.emran.glance.theme.EmranGlanceTheme
import io.github.sammynashed.emran.glance.theme.Glass
import io.github.sammynashed.emran.glance.theme.GlassPane

/** Today's Hijri date, with the month name spelled out. */
@Composable
fun HijriWidgetUi(state: EmranWidgetState) {
    val c = EmranGlanceTheme.colors
    val lang = state.language
    val rtl = WidgetText.isRtl(lang)
    val roomy = LocalSize.current.height >= 130.dp

    val day = state.hijriDay
    val month = WidgetText.hijriMonth(state.hijriMonth, lang)
    val year = state.hijriYear

    GlassPane(
        modifier = GlanceModifier.fillMaxSize(),
        contentPadding = if (roomy) 14.dp else 10.dp,
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The full date -- day, month *and* year -- is the whole point of the
            // widget, so all three survive at every size. Only the "HIJRI DATE"
            // caption is dropped when space is tight: it is the one line that
            // repeats what the numbers already say.
            if (roomy) {
                Text(
                    text = WidgetText.hijriToday(lang),
                    style = Glass.Type.label(c.textPrimaryRaw, rtl),
                    maxLines = 1
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
            }
            FontText(
                text = day?.toString() ?: "--",
                color = c.textPrimaryRaw,
                fontSizeSp = if (roomy) 38f else 28f,
                font = WidgetFont.OUTFIT,
                align = WidgetAlign.CENTER
            )
            Text(
                text = month,
                style = Glass.Type.value(c.accentRaw, if (roomy) 15 else 13, rtl),
                maxLines = 1
            )
            if (year != null) {
                Text(
                    text = "$year ${WidgetText.hijriYearSuffix(lang)}",
                    style = Glass.Type.label(c.textPrimaryRaw, rtl),
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Progress through the Quran, counted in surahs.
 *
 * Surahs rather than pages because a surah is a thing you finish; a page is a
 * thing you pass. And because the underlying store is a set of surah ids, the
 * count cannot be inflated by re-reading the same one.
 */
@Composable
fun QuranProgressWidgetUi(state: EmranWidgetState) {
    val c = EmranGlanceTheme.colors
    val lang = state.language
    val rtl = WidgetText.isRtl(lang)
    val size = LocalSize.current

    val total = 114
    val done = state.surahsCompleted.coerceIn(0, total)
    val fraction = done.toFloat() / total
    val percent = (fraction * 100).toInt()
    val khatmah = state.khatmahCount

    val compact = size.height < 130.dp

    GlassPane(modifier = GlanceModifier.fillMaxSize(), contentPadding = if (compact) 12.dp else 16.dp) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            horizontalAlignment = Glass.columnAlignment(rtl)
        ) {
            HeaderRow(start = WidgetText.quran(lang), end = "$percent%", rtl = rtl)
            Spacer(modifier = GlanceModifier.defaultWeight())

            FontText(
                text = "$done / $total",
                color = c.textPrimaryRaw,
                fontSizeSp = if (compact) 22f else 28f,
                font = WidgetFont.OUTFIT,
                align = if (rtl) WidgetAlign.END else WidgetAlign.START
            )
            Text(
                text = WidgetText.surahs(lang),
                style = Glass.Type.label(c.textPrimaryRaw, rtl),
                maxLines = 1
            )

            Spacer(modifier = GlanceModifier.height(8.dp))
            LinearProgressIndicator(
                progress = fraction,
                modifier = GlanceModifier.fillMaxWidth().height(5.dp),
                color = ColorProvider(c.accentRaw),
                backgroundColor = ColorProvider(c.textPrimaryRaw.copy(alpha = Glass.Alpha.TRACK))
            )

            // Finished readings are never lost when the cycle resets.
            if (!compact && khatmah > 0) {
                Spacer(modifier = GlanceModifier.height(6.dp))
                Text(
                    text = WidgetText.khatmat(khatmah, lang),
                    style = Glass.Type.label(c.accentRaw, rtl),
                    maxLines = 1
                )
            }
        }
    }
}
