package io.github.sammynashed.emran.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Keeps Emran's process in the foreground while a whole adhan plays (two to four and a half minutes).
 * The adhan itself is played by [PrayerAlarmReceiver]; this service only holds the adhan's own
 * notification as its foreground notice, so the system can't freeze or kill the app half-way through.
 * When the adhan ends or is stopped the notification is detached and left as it was.
 */
class AdhanKeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground must be called whatever happens, or Android ends the app for breaking its promise.
        val n = pending ?: fallback(this)
        val id = if (pending != null) pendingId else FALLBACK_ID
        runCatching {
            ServiceCompat.startForeground(
                this, id, n,
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
            )
        }.onFailure { android.util.Log.w(TAG, "could not go foreground", it) }
        if (!PrayerAlarmReceiver.isAdhanPlaying()) stopSelf() // stopped before we got here
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // Keep the prayer's notification (it says which prayer it is); only drop the foreground hold.
        // Stopped by the user ("Stop adhan" or a swipe): the notification goes too, as it did before this service.
        val how = if (remove) ServiceCompat.STOP_FOREGROUND_REMOVE else ServiceCompat.STOP_FOREGROUND_DETACH
        runCatching { ServiceCompat.stopForeground(this, how) }
        // [pending] is left alone: a new adhan may already have handed over its notification for the next start.
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AdhanKeepAlive"
        private const val FALLBACK_ID = 7411
        @Volatile private var pending: Notification? = null
        @Volatile private var pendingId = 0
        @Volatile private var remove = false

        /** The next stop also removes the notification (while foreground, a plain cancel() is ignored). */
        fun removeNotificationOnStop() { remove = true }

        fun start(context: Context, notificationId: Int, notification: Notification) {
            pending = notification
            pendingId = notificationId
            remove = false
            runCatching { ContextCompat.startForegroundService(context, Intent(context, AdhanKeepAliveService::class.java)) }
                .onFailure { android.util.Log.w(TAG, "foreground start not allowed; the adhan plays without it", it) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, AdhanKeepAliveService::class.java)) }
        }

        private fun fallback(context: Context): Notification =
            NotificationCompat.Builder(context, "emran_adhan_v3")
                .setSmallIcon(io.github.sammynashed.emran.R.drawable.ic_stat_emran)
                .setContentTitle("Emran")
                .setSilent(true)
                .build()
    }
}
