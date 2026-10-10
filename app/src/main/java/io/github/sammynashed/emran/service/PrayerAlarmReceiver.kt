package io.github.sammynashed.emran.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.github.sammynashed.emran.MainActivity

class PrayerAlarmReceiver : BroadcastReceiver() {

    companion object {
        private var mediaPlayer: android.media.MediaPlayer? = null
        private var volumeReceiver: BroadcastReceiver? = null
        const val ACTION_MUTE_ADHAN = "io.github.sammynashed.emran.IMAD_ACTION_MUTE_ADHAN"

        fun isAdhanPlaying(): Boolean {
            return try {
                mediaPlayer?.isPlaying ?: false
            } catch (e: Exception) {
                false
            }
        }

        fun muteAdhan(context: Context? = null) {
            volumeReceiver?.let { receiver ->
                context?.applicationContext?.let { appContext ->
                    try {
                        appContext.unregisterReceiver(receiver)
                    } catch (e: Exception) {
                        // safe ignore if already unregistered or not found
                    }
                }
                volumeReceiver = null
            }

            try {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        player.stop()
                    }
                    player.release()
                }
            } catch (e: Exception) {
                android.util.Log.e("PrayerAlarmReceiver", "Error stopping MediaPlayer", e)
            } finally {
                mediaPlayer = null
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == ACTION_MUTE_ADHAN) {
            muteAdhan(context)
            val prayerName = intent.getStringExtra("PRAYER_NAME") ?: "Salah"
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(prayerName.hashCode())
            android.util.Log.d("PrayerAlarmReceiver", "Muted Adhan by user action.")
            return
        }

        val prayerName = intent.getStringExtra("PRAYER_NAME") ?: "Salah"
        showNotification(context, prayerName)

        // Dynamically reschedule next batch of alarms to ensure alarms never expire if app is closed
        try {
            val workRequest = OneTimeWorkRequestBuilder<PrayerScheduleWorker>().build()
            WorkManager.getInstance(context.applicationContext).enqueue(workRequest)
            
            // Also trigger an immediate widget update so the widget shows the next prayer
            val widgetWorkRequest = OneTimeWorkRequestBuilder<io.github.sammynashed.emran.service.EmranWidgetUpdateWorker>().build()
            WorkManager.getInstance(context.applicationContext).enqueue(widgetWorkRequest)
            
            android.util.Log.d("PrayerAlarmReceiver", "Enqueued dynamic prayer reschedule work and widget update.")
        } catch (e: Exception) {
            android.util.Log.e("PrayerAlarmReceiver", "Failed to enqueue next alarm reschedule in onReceive", e)
        }
    }

    private fun showNotification(context: Context, prayerName: String) {
        val preferences = context.getSharedPreferences("emran_settings", Context.MODE_PRIVATE)
        val soundKey = preferences.getString("default_adhan_sound", "default") ?: "default"

        val soundUri = when (soundKey) {
            "ahmad_alnafees" -> android.net.Uri.parse("android.resource://${context.packageName}/raw/ahmad_alnafees")
            "mishary_alafasy" -> android.net.Uri.parse("android.resource://${context.packageName}/raw/mishary_alafasy")
            "saad_alghamdi" -> android.net.Uri.parse("android.resource://${context.packageName}/raw/saad_alghamdi")
            else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }

        val channelId = "emran_prayer_alerts_v2_silent"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val existingChannels = notificationManager.notificationChannels
                for (oldChannel in existingChannels) {
                    if ((oldChannel.id.startsWith("imad_prayer_alerts_") || oldChannel.id.startsWith("emran_prayer_alerts_")) && oldChannel.id != channelId) {
                        notificationManager.deleteNotificationChannel(oldChannel.id)
                    }
                }
            } catch (e: Exception) {
                // Safe catch
            }

            val channel = NotificationChannel(
                channelId,
                "Emran Prayer Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notification alerts for daily salah times"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setBypassDnd(true)
                setSound(null, null) // Silent! We play custom media manually on ALARM stream
            }
            notificationManager.createNotificationChannel(channel)
        }

        val lang = preferences.getString("current_language", "en") ?: "en"
        val fullLocName = preferences.getString("location_name", "Aleppo, Syria") ?: "Aleppo, Syria"
        val localizedFullLocName = io.github.sammynashed.emran.data.LocationPresets.getLocalizedName(fullLocName, lang)
        val locName = localizedFullLocName.split(",").first().trim()

        val title: String
        val content: String

        if (lang == "ar") {
            if (prayerName.equals("Sunrise", ignoreCase = true)) {
                title = "شروق الشمس - انتهاء وقت الفجر"
                content = "لقد حان وقت شروق الشمس في $locName وانتهى وقت صلاة الفجر."
            } else {
                val isFriday = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.FRIDAY
                val prayerAr = when (prayerName) {
                    "Fajr" -> "الفجر"
                    "Dhuhr" -> if (isFriday) "الجمعة" else "الظهر"
                    "Asr" -> "العصر"
                    "Maghrib" -> "المغرب"
                    "Isha" -> "العشاء"
                    else -> prayerName
                }
                title = "صلاة $prayerAr"
                content = "حان الآن وقت صلاة $prayerAr في $locName"
            }
        } else {
            if (prayerName.equals("Sunrise", ignoreCase = true)) {
                title = "Sunrise - Fajr Ended"
                content = "Sunrise time in $locName. Fajr prayer has ended."
            } else {
                val isFriday = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.FRIDAY
                val prayerEn = when (prayerName) {
                    "Fajr" -> "Fajr"
                    "Dhuhr" -> if (isFriday) "Jum'ah" else "Dhuhr"
                    "Asr" -> "Asr"
                    "Maghrib" -> "Maghrib"
                    "Isha" -> "Isha"
                    else -> prayerName
                }
                title = "$prayerEn Prayer"
                content = "It is now time for $prayerEn prayer in $locName"
            }
        }

        val openAppIntent = Intent(context, io.github.sammynashed.emran.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val muteIntent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_MUTE_ADHAN
            putExtra("PRAYER_NAME", prayerName)
        }
        val mutePendingIntent = PendingIntent.getBroadcast(
            context,
            prayerName.hashCode() + 1000,
            muteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val muteActionTitle = if (lang == "ar") "إيقاف الأذان" else "Stop Adhan"

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                muteActionTitle,
                mutePendingIntent
            )
            .build()

        notificationManager.notify(prayerName.hashCode(), notification)

        // Play the Adhan sound via MediaPlayer on STREAM_ALARM to bypass DND and silent mode
        try {
            muteAdhan(context) // Clear any existing playing audio first

            val player = android.media.MediaPlayer().apply {
                setDataSource(context, soundUri)
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_ALARM) // Bypasses silent mode!
                    .build()
                setAudioAttributes(audioAttributes)
                isLooping = false
                prepare()
                start()
            }
            mediaPlayer = player

            // Register background-compatible volume key press detection to instantly mute the Adhan
            val appContext = context.applicationContext
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(subContext: Context, subIntent: Intent) {
                    if (subIntent.action == "android.media.VOLUME_CHANGED_ACTION") {
                        android.util.Log.d("PrayerAlarmReceiver", "Volume keys changed detected in background. Stops/mutes Adhan playback.")
                        muteAdhan(appContext)
                    }
                }
            }
            volumeReceiver = receiver
            try {
                val filter = android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION")
                appContext.registerReceiver(receiver, filter)
            } catch (e: Exception) {
                android.util.Log.e("PrayerAlarmReceiver", "Failed to register volume muting receiver", e)
            }
        } catch (e: Exception) {
            android.util.Log.e("PrayerAlarmReceiver", "Failed to play Adhan sound via alarm-stream MediaPlayer", e)
        }
    }
}
