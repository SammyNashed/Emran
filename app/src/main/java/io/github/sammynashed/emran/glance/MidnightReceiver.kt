package io.github.sammynashed.emran.glance

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.github.sammynashed.emran.service.EmranWidgetUpdateWorker
import java.time.LocalDate
import java.time.ZoneId

/**
 * Redraws the widgets just after midnight, so the day, the Hijri date and the month dots change
 * when the day does (prayer alarms alone would leave them on yesterday until Fajr).
 */
class MidnightReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<EmranWidgetUpdateWorker>().build())
    }

    companion object {
        fun schedule(context: Context) {
            runCatching {
                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val at = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 60_000L
                val pi = PendingIntent.getBroadcast(
                    context, 4401, Intent(context, MidnightReceiver::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                // Inexact is fine: a minute or two after midnight, no exact-alarm permission needed.
                am.setWindow(AlarmManager.RTC, at, 5 * 60_000L, pi)
            }
        }
    }
}
