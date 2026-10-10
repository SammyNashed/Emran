package io.github.sammynashed.emran.glance.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.background
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
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

/**
 * The day's schedule -- five prayers plus Sunrise -- with the next one picked out.
 *
 * The row metrics are derived from the reported height rather than fixed, because
 * six rows at a comfortable size do not fit a short widget and the last row (Isha)
 * was being clipped off the bottom. Below the point where even the compact metrics
 * stop fitting, the header is dropped rather than a prayer.
 */
@Composable
fun TodayFiveWidgetUi(state: EmranWidgetState) {
    val c = EmranGlanceTheme.colors
    val lang = state.language
    val rtl = WidgetText.isRtl(lang)
    val height = LocalSize.current.height

    val names = state.todayPrayerNames
    val times = state.todayPrayerEpochMillis

    // Space budget: 6 rows must always fit, so the row height, type size and
    // padding all scale together with the widget.
    val rows = names.size.coerceAtLeast(1)
    val showHeader = height >= 180.dp
    val pane = when {
        height < 150.dp -> 10.dp
        height < 220.dp -> 14.dp
        else -> 16.dp
    }
    val available = height - pane * 2 - (if (showHeader) 26.dp else 0.dp)
    val perRow = available / rows
    val fontSize = when {
        perRow < 20.dp -> 11
        perRow < 26.dp -> 12
        perRow < 32.dp -> 13
        else -> 14
    }
    val rowPadV = when {
        perRow < 22.dp -> 1.dp
        perRow < 30.dp -> 3.dp
        else -> 5.dp
    }

    GlassPane(modifier = GlanceModifier.fillMaxSize(), contentPadding = pane) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            horizontalAlignment = Glass.columnAlignment(rtl)
        ) {
            if (showHeader) {
                HeaderRow(
                    start = WidgetText.today(lang),
                    end = state.locationName.orEmpty(),
                    rtl = rtl
                )
                Spacer(modifier = GlanceModifier.height(6.dp))
            }

            if (names.isEmpty()) {
                Text(
                    text = WidgetText.loading(lang),
                    style = Glass.Type.body(c.textPrimaryRaw, 13, rtl)
                )
                return@Column
            }

            for (i in names.indices) {
                PrayerRow(
                    name = WidgetText.prayerName(names[i], lang),
                    time = WidgetText.clock(times.getOrNull(i), state.use24Hour),
                    highlighted = names[i] == state.nextPrayerName,
                    // Sunrise is a marker, not a prayer, so it is dimmed rather
                    // than presented as something to pray.
                    subdued = names[i] == "Sunrise",
                    rtl = rtl,
                    fontSize = fontSize,
                    paddingV = rowPadV
                )
            }
        }
    }
}

@Composable
private fun PrayerRow(
    name: String,
    time: String,
    highlighted: Boolean,
    subdued: Boolean,
    rtl: Boolean,
    fontSize: Int,
    paddingV: androidx.compose.ui.unit.Dp
) {
    val c = EmranGlanceTheme.colors

    val rowModifier = if (highlighted) {
        GlanceModifier
            .fillMaxWidth()
            .background(ColorProvider(c.accentRaw.copy(alpha = 0.20f)))
            .cornerRadius(Glass.Radius.Chip)
            .padding(horizontal = 8.dp, vertical = paddingV)
    } else {
        GlanceModifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = paddingV)
    }

    val nameColor = when {
        highlighted -> c.accentRaw
        subdued -> c.textPrimaryRaw.copy(alpha = 0.40f)
        else -> c.textPrimaryRaw
    }
    val timeColor = when {
        highlighted -> c.accentRaw
        subdued -> c.textPrimaryRaw.copy(alpha = 0.40f)
        else -> c.textPrimaryRaw.copy(alpha = Glass.Alpha.MUTED)
    }

    val nameStyle = if (highlighted) {
        Glass.Type.value(nameColor, fontSize, rtl)
    } else {
        Glass.Type.body(nameColor, fontSize, rtl)
    }
    val timeStyle = if (highlighted) {
        Glass.Type.value(timeColor, fontSize, rtl)
    } else {
        Glass.Type.body(timeColor, fontSize, rtl)
    }

    Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
        val first = if (rtl) time else name
        val second = if (rtl) name else time
        val firstStyle = if (rtl) timeStyle else nameStyle
        val secondStyle = if (rtl) nameStyle else timeStyle

        Text(text = first, style = firstStyle, maxLines = 1)
        Spacer(modifier = GlanceModifier.defaultWeight())
        Text(text = second, style = secondStyle, maxLines = 1)
    }
}
