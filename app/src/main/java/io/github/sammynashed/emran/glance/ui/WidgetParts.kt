package io.github.sammynashed.emran.glance.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import io.github.sammynashed.emran.glance.state.EmranWidgetState
import io.github.sammynashed.emran.glance.theme.EmranGlanceTheme
import io.github.sammynashed.emran.glance.theme.Glass

/**
 * Label on one edge, context on the other. The two are swapped for Arabic so the
 * primary label always sits on the reading-start side.
 */
@Composable
fun HeaderRow(start: String, end: String, rtl: Boolean) {
    val c = EmranGlanceTheme.colors
    val first = if (rtl) end else start
    val second = if (rtl) start else end
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        Text(text = first, style = Glass.Type.label(c.textPrimaryRaw, rtl), maxLines = 1)
        Spacer(modifier = GlanceModifier.defaultWeight())
        Text(text = second, style = Glass.Type.label(c.textPrimaryRaw, rtl), maxLines = 1)
    }
}

/**
 * Progress from the previous prayer to the next.
 *
 * Glance's own LinearProgressIndicator rather than two weighted boxes, because
 * `defaultWeight()` carries no weight *value* -- it can only split space evenly,
 * which would peg every bar at 50%.
 */
@Composable
fun ElapsedBar(state: EmranWidgetState) {
    val c = EmranGlanceTheme.colors
    val start = state.currentPrayerEpochMillis
    val end = state.nextPrayerEpochMillis
    if (start == null || end == null || end <= start) return

    val now = System.currentTimeMillis().coerceIn(start, end)
    val done = ((now - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f)

    LinearProgressIndicator(
        progress = done,
        modifier = GlanceModifier.fillMaxWidth().height(5.dp),
        color = ColorProvider(c.accentRaw),
        backgroundColor = ColorProvider(c.textPrimaryRaw.copy(alpha = Glass.Alpha.TRACK))
    )
}
