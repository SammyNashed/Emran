package io.github.sammynashed.emran.glance.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.text.Text
import io.github.sammynashed.emran.glance.state.EmranWidgetState
import io.github.sammynashed.emran.glance.theme.EmranGlanceTheme
import io.github.sammynashed.emran.glance.theme.Glass
import io.github.sammynashed.emran.glance.theme.GlassPane

/**
 * Countdown-first design: the ticking time remaining is the hero, with the
 * prayer it belongs to as supporting text.
 *
 * Everything is centred on one axis and the type sizes step in fixed pairs, so
 * the hero and its label stay in proportion at every widget size instead of the
 * label drifting relative to a hero that grows independently.
 */
@Composable
fun CountdownWidgetUi(state: EmranWidgetState) {
    val c = EmranGlanceTheme.colors
    val lang = state.language
    val rtl = WidgetText.isRtl(lang)
    val size = LocalSize.current

    val name = WidgetText.prayerName(state.nextPrayerName, lang)
    val time = WidgetText.clock(state.nextPrayerEpochMillis, state.use24Hour)
    val header = if (name.isBlank()) {
        WidgetText.loading(lang)
    } else if (rtl) {
        "حتى $name"
    } else {
        "UNTIL ${name.uppercase()}"
    }

    // One scale decision drives the whole composition.
    val tier = when {
        size.height < 110.dp || size.width < 160.dp -> 0
        size.height < 170.dp -> 1
        else -> 2
    }
    val heroSp = when (tier) { 0 -> 24f; 1 -> 34f; else -> 40f }
    val pane = when (tier) { 0 -> 12.dp; 1 -> 16.dp; else -> 20.dp }

    GlassPane(
        modifier = GlanceModifier.fillMaxSize(),
        contentPadding = pane,
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = header,
                style = Glass.Type.label(c.accentRaw, rtl),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            LiveCountdown(
                targetEpochMillis = state.nextPrayerEpochMillis,
                color = c.textPrimaryRaw,
                fontSizeSp = heroSp,
                centered = true
            )
            if (tier > 0) {
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = time,
                    style = Glass.Type.value(c.accentRaw, 14, rtl),
                    maxLines = 1
                )
            }
            if (tier == 2) {
                Spacer(modifier = GlanceModifier.height(12.dp))
                ElapsedBar(state)
            }
        }
    }
}

/**
 * One tile: the next prayer, its time and the countdown to it.
 *
 * For home screens with room for a single Emran widget rather than seven.
 */
@Composable
fun CombinedWidgetUi(state: EmranWidgetState) {
    val c = EmranGlanceTheme.colors
    val lang = state.language
    val rtl = WidgetText.isRtl(lang)
    val size = LocalSize.current

    val name = WidgetText.prayerName(state.nextPrayerName, lang)
    val time = WidgetText.clock(state.nextPrayerEpochMillis, state.use24Hour)
    val hijri = if (state.hijriDay != null) {
        "${state.hijriDay} ${WidgetText.hijriMonth(state.hijriMonth, lang)}"
    } else ""

    val compact = size.height < 160.dp

    GlassPane(modifier = GlanceModifier.fillMaxSize(), contentPadding = if (compact) 12.dp else 16.dp) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            horizontalAlignment = Glass.columnAlignment(rtl)
        ) {
            HeaderRow(start = WidgetText.nextPrayer(lang), end = hijri, rtl = rtl)
            Spacer(modifier = GlanceModifier.defaultWeight())

            FontText(
                text = if (name.isBlank()) WidgetText.loading(lang) else name,
                color = c.textPrimaryRaw,
                fontSizeSp = if (compact) 22f else 30f,
                font = WidgetFont.READEX,
                align = if (rtl) WidgetAlign.END else WidgetAlign.START
            )
            Text(text = time, style = Glass.Type.value(c.accentRaw, 16, rtl), maxLines = 1)
            if (!compact) {
                LiveCountdown(
                    targetEpochMillis = state.nextPrayerEpochMillis,
                    color = c.textPrimaryRaw.copy(alpha = Glass.Alpha.MUTED),
                    fontSizeSp = 13f
                )
            }

            Spacer(modifier = GlanceModifier.defaultWeight())
            ElapsedBar(state)
        }
    }
}
