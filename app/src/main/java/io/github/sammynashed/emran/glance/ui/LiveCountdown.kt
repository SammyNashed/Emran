package io.github.sammynashed.emran.glance.ui

import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import io.github.sammynashed.emran.R

/**
 * A genuinely live countdown to the next prayer.
 *
 * Everything else in these widgets is a snapshot: Glance renders once per update
 * and app widgets are not redrawn on a timer, so a countdown composed from Text
 * would freeze the instant it was written. `Chronometer` is the exception -- the
 * platform ticks it once a second for as long as it is visible, with no worker
 * and no battery cost -- and it is reachable from Glance only through
 * [AndroidRemoteViews].
 *
 * The hosted view needs an explicit height. AndroidRemoteViews gives Glance no
 * intrinsic measurement to work with, so without one the row collapses or
 * overlaps whatever follows it.
 */
@Composable
fun LiveCountdown(
    targetEpochMillis: Long?,
    color: Color,
    fontSizeSp: Float = 20f,
    centered: Boolean = false,
    modifier: GlanceModifier = GlanceModifier
) {
    if (targetEpochMillis == null) return
    val context = LocalContext.current

    val remainingMillis = targetEpochMillis - System.currentTimeMillis()
    if (remainingMillis <= 0) return

    val layout = if (centered) {
        R.layout.widget_countdown_chronometer
    } else {
        R.layout.widget_countdown_chronometer_start
    }

    val views = RemoteViews(context.packageName, layout).apply {
        // Chronometer counts against elapsedRealtime, not wall-clock time, so the
        // target has to be expressed on that clock.
        setChronometer(
            R.id.widget_chronometer,
            SystemClock.elapsedRealtime() + remainingMillis,
            null,
            true
        )
        setChronometerCountDown(R.id.widget_chronometer, true)
        setTextColor(R.id.widget_chronometer, color.toArgb())
        setTextViewTextSize(R.id.widget_chronometer, TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
    }

    // Reserve a line box proportional to the type size: enough for ascenders and
    // descenders without leaving a gap.
    val lineHeight: Dp = (fontSizeSp * 1.35f).dp

    AndroidRemoteViews(
        remoteViews = views,
        modifier = modifier.fillMaxWidth().height(lineHeight)
    )
}
