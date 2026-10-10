package io.github.sammynashed.emran.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            try {
                val workRequest = OneTimeWorkRequestBuilder<PrayerScheduleWorker>().build()
                WorkManager.getInstance(context).enqueue(workRequest)
            } catch (e: Exception) {
                // Fail-safe logging
                android.util.Log.e("BootReceiver", "WorkManager boot schedule failed", e)
            }
        }
    }
}
