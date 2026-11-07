package io.github.sammynashed.emran.glance

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.sammynashed.emran.glance.provider.PrayerWidgetStateProvider
import java.time.LocalDate

/**
 * Wakes the widgets at the moment they change: each prayer time (the next prayer moves on, the
 * countdown would start counting up) and just after midnight (the day, the Hijri date and today's
 * times change). It draws them right here (DirectWidgets), not through a background job, so a phone
 * that holds jobs back can't leave a widget on Dhuhr at 17:53.
 *
 * (The name is historical: it used to fire at midnight only. The manifest entry keeps it.)
 */
class MidnightReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DirectWidgets.redrawAsync(this, context)
    }
}

object WidgetTicker {
    private const val REQUEST = 4401
    /** A little after the moment, so "now" is safely past it when the times are worked out again. */
    private const val LATE_MS = 3_000L

    private fun intent(context: Context) = PendingIntent.getBroadcast(
        context, REQUEST, Intent(context, MidnightReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * Arms one alarm for the next change, or none when no widget is placed. It does not wake a
     * sleeping phone (RTC, not RTC_WAKEUP): nobody sees a widget with the screen off, and the alarm
     * is delivered the moment the phone wakes, so the widget is right when it is looked at.
     */
    fun schedule(context: Context, anyWidget: Boolean) {
        runCatching {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = intent(context)
            if (!anyWidget) { am.cancel(pi); return }
            val at = nextChange(context) + LATE_MS
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExact(AlarmManager.RTC, at, pi)
            else am.setWindow(AlarmManager.RTC, at, 60_000L, pi)
            WidgetDebugLogger.d("WidgetTicker: next redraw at ${java.time.Instant.ofEpochMilli(at)}")
        }.onFailure { WidgetDebugLogger.e("WidgetTicker.schedule failed", it) }
    }

    /** The next prayer time today, tomorrow's Fajr, or midnight, whichever comes first. */
    private fun nextChange(context: Context): Long {
        val now = System.currentTimeMillis()
        val p = PrayerWidgetStateProvider(context).provideDomainData().prayerData
        val zone = p.zone
        val times = p.todayPrayerTimes.map { it.atZone(zone).toInstant().toEpochMilli() } +
            listOfNotNull(p.nextPrayerTime?.atZone(zone)?.toInstant()?.toEpochMilli())
        val midnight = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return (times + midnight).filter { it > now + 1_000L }.minOrNull() ?: midnight
    }
}
