package io.github.sammynashed.emran.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import io.github.sammynashed.emran.R
import io.github.sammynashed.emran.data.PrayerNames
import java.text.DateFormat
import java.util.Date

/**
 * The quiet "next prayer in mm:ss" notification, shown a few minutes before each adhan (not Sunrise).
 * The timer is a native countdown Chronometer inside the notification, so it ticks live with nothing
 * running and no repeated updates; it is cancelled the moment the adhan itself fires.
 * Lead time lives in SharedPreferences "countdown_lead_min" (0 = off), read by the schedule worker.
 */
object PrayerCountdown {
    const val DEFAULT_LEAD_MIN = 15
    val LEAD_CHOICES = listOf(0, 5, 10, 15, 20, 30)
    private const val CHANNEL = "emran_countdown_v1"
    const val ACTION_DISMISSED = "io.github.sammynashed.emran.COUNTDOWN_DISMISSED"

    fun leadMinutes(context: Context): Int =
        context.getSharedPreferences("emran_settings", Context.MODE_PRIVATE).getInt("countdown_lead_min", DEFAULT_LEAD_MIN)

    fun setLeadMinutes(context: Context, minutes: Int) {
        context.getSharedPreferences("emran_settings", Context.MODE_PRIVATE).edit().putInt("countdown_lead_min", minutes).apply()
        // Re-arm every alarm so the new lead time applies right away (and an old countdown is dropped when off).
        androidx.work.WorkManager.getInstance(context.applicationContext)
            .enqueue(androidx.work.OneTimeWorkRequestBuilder<PrayerScheduleWorker>().build())
    }

    private fun notificationId(prayer: String) = prayer.hashCode() + 5000

    private fun alarmIntent(context: Context, prayer: String, requestCode: Int, adhanAt: Long, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, PrayerAlarmReceiver::class.java).putExtra("PRAYER_NAME", prayer)
                .putExtra("COUNTDOWN", true).putExtra("ADHAN_AT", adhanAt),
            flags or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Arms (or, with the feature off / adhan silenced, clears) the countdown for one prayer. Called by the schedule worker. */
    fun schedule(context: Context, prayer: String, requestCode: Int, adhanAt: Long, enabled: Boolean) {
        if (prayer == "Sunrise") return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val lead = leadMinutes(context)
        val now = System.currentTimeMillis()
        val pi = alarmIntent(context, prayer, requestCode, adhanAt, PendingIntent.FLAG_UPDATE_CURRENT)!!
        if (!enabled || lead <= 0 || adhanAt <= now) {
            am.cancel(pi)
            if (!enabled || lead <= 0) cancel(context, prayer)
            return
        }
        val at = adhanAt - lead * 60_000L
        if (at <= now) {
            // Already inside the window (app opened late, or the lead was just raised): show it now, unless it is up.
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val up = runCatching { nm.activeNotifications.any { it.id == notificationId(prayer) } }.getOrDefault(false)
            if (!up) show(context, prayer, adhanAt)
            return
        }
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        runCatching {
            if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(context: Context, prayer: String) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(notificationId(prayer))
    }

    fun show(context: Context, prayer: String, adhanAt: Long) {
        val remaining = adhanAt - System.currentTimeMillis()
        if (prayer == "Sunrise" || remaining <= 0) return
        val prefs = context.getSharedPreferences("emran_settings", Context.MODE_PRIVATE)
        val ar = (prefs.getString("current_language", "en") ?: "en") == "ar"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, if (ar) "العدّ التنازلي للأذان" else "Adhan countdown", NotificationManager.IMPORTANCE_LOW).apply {
                    description = if (ar) "مؤقّت حيّ قبل الأذان" else "A live timer before the adhan"
                    setSound(null, null); enableVibration(false)
                },
            )
        }
        val name = PrayerNames.name(prayer, ar)
        val clock = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(adhanAt))
        val title = if (ar) "$name بعد" else "$name in"
        val sub = if (ar) "الأذان الساعة $clock" else "Adhan at $clock"

        val views = RemoteViews(context.packageName, R.layout.notif_countdown).apply {
            setTextViewText(R.id.countdown_title, title)
            setTextViewText(R.id.countdown_sub, sub)
            setChronometerCountDown(R.id.countdown_timer, true)
            setChronometer(R.id.countdown_timer, SystemClock.elapsedRealtime() + remaining, null, true)
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, io.github.sammynashed.emran.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_emran)
            .setContentTitle("$title · $sub")
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(views)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            // Newer Android lets users swipe "ongoing" notifications away. This one is a timer that must stay, so a swipe
            // just puts it back (the receiver only does so while time is left and the feature is still on).
            .setDeleteIntent(
                PendingIntent.getBroadcast(
                    context, notificationId(prayer) + 1,
                    Intent(context, PrayerAlarmReceiver::class.java).setAction(ACTION_DISMISSED)
                        .putExtra("PRAYER_NAME", prayer).putExtra("COUNTDOWN", true).putExtra("ADHAN_AT", adhanAt),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setTimeoutAfter(remaining) // gone by itself the moment the adhan is due
            .build()
        nm.notify(notificationId(prayer), n)
    }
}
