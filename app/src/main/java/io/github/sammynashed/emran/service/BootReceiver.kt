package io.github.sammynashed.emran.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Re-arms the prayer alarms whenever something could have wiped or shifted them: reboot, app update,
 * the clock being changed, or the time zone changing (travel). Also keeps a daily safety-net job.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                reschedule(context)
                io.github.sammynashed.emran.data.update.PatchUpdate.announceIfUpdated(context)
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> reschedule(context)
        }
    }

    companion object {
        fun reschedule(context: Context) {
            try {
                val wm = WorkManager.getInstance(context)
                wm.enqueue(OneTimeWorkRequestBuilder<PrayerScheduleWorker>().build())
                ensureDaily(context)
            } catch (e: Exception) {
                android.util.Log.e("BootReceiver", "WorkManager schedule failed", e)
            }
        }

        /** Once a day, top up the alarms even if no receiver ever fired. */
        fun ensureDaily(context: Context) {
            val req = PeriodicWorkRequestBuilder<PrayerScheduleWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("prayer_daily_topup", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
