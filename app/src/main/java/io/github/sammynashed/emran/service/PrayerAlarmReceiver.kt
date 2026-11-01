package io.github.sammynashed.emran.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.github.sammynashed.emran.MainActivity

class PrayerAlarmReceiver : BroadcastReceiver() {

    companion object {
        private var mediaPlayer: android.media.MediaPlayer? = null
        private var volumeReceiver: BroadcastReceiver? = null
        private var focusRequest: android.media.AudioFocusRequest? = null
        private val fadeHandler = android.os.Handler(android.os.Looper.getMainLooper())
        private var fadeStep: Runnable? = null
        private var appContextForFocus: Context? = null
        /** The user's own alarm volume, put back when a wake-up adhan ends. */
        private var savedAlarmVolume: Int? = null
        const val ACTION_MUTE_ADHAN = "io.github.sammynashed.emran.IMAD_ACTION_MUTE_ADHAN"

        /** The user's switch for this prayer's adhan (Sunrise has its own; it used to borrow Fajr's). */
        fun isAdhanEnabled(context: Context, prayerName: String): Boolean {
            val prefs = context.getSharedPreferences("emran_settings", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("notifications_enabled_global", true)) return false
            return when (prayerName) {
                "Sunrise" -> prefs.getBoolean("notif_Sunrise", prefs.getBoolean("notif_Fajr", true))
                "Fajr", "Dhuhr", "Asr", "Maghrib", "Isha" -> prefs.getBoolean("notif_$prayerName", true)
                else -> true
            }
        }

        fun isAdhanPlaying(): Boolean {
            return try {
                mediaPlayer?.isPlaying ?: false
            } catch (e: Exception) {
                false
            }
        }

        fun muteAdhan(context: Context? = null) {
            fadeStep?.let { fadeHandler.removeCallbacks(it) }
            fadeStep = null
            focusRequest?.let { req ->
                val am = (context?.applicationContext ?: appContextForFocus)?.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                runCatching { am?.abandonAudioFocusRequest(req) }
            }
            focusRequest = null
            savedAlarmVolume?.let { prev ->
                val am = (context?.applicationContext ?: appContextForFocus)?.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                runCatching { am?.setStreamVolume(android.media.AudioManager.STREAM_ALARM, prev, 0) }
            }
            savedAlarmVolume = null
            // Always unregister, even when the caller has no Context (the app opening, for instance): a receiver that is
            // only forgotten stays registered and kills the NEXT adhan the moment the volume changes.
            volumeReceiver?.let { receiver ->
                (context?.applicationContext ?: appContextForFocus)?.let { appContext ->
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
        if (intent.getBooleanExtra("COUNTDOWN", false)) {
            // The pre-adhan timer: only while this prayer's adhan is on and the feature is not switched off meanwhile.
            if (PrayerCountdown.leadMinutes(context) > 0 && isAdhanEnabled(context, prayerName)) {
                PrayerCountdown.show(context, prayerName, intent.getLongExtra("ADHAN_AT", 0L))
            }
            return
        }
        // The bell is checked again HERE, at the moment the alarm fires, not only when it was scheduled:
        // an alarm armed before the user switched a prayer off (or by an older build) must stay silent.
        if (intent.getBooleanExtra("TEST", false) || isAdhanEnabled(context, prayerName)) {
            showNotification(context, prayerName)
        } else {
            android.util.Log.d("PrayerAlarmReceiver", "$prayerName adhan is switched off; staying silent.")
        }

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

        // What happens now depends on the phone (silent, vibrate, Do Not Disturb), the user's per-prayer
        // choice, their quiet hours and, for Fajr only, the opt-in "wake me" switch.
        val play = AdhanPolicy.resolve(context, prayerName)
        val wake = play == AdhanPolicy.Play.WAKE
        // Sound is always played by us (below), never by the channel. Only the wake channel may cut through
        // Do Not Disturb; the everyday one is an ordinary high-importance channel the user's settings govern.
        val channelId = if (wake) "emran_adhan_v3_wake" else "emran_adhan_v3"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        PrayerCountdown.cancel(context, prayerName) // the timer has run out: the adhan replaces it

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val existingChannels = notificationManager.notificationChannels
                for (oldChannel in existingChannels) {
                    if (oldChannel.id.startsWith("imad_prayer_alerts_") || oldChannel.id.startsWith("emran_prayer_alerts_")) {
                        notificationManager.deleteNotificationChannel(oldChannel.id)
                    }
                }
            } catch (e: Exception) {
                // Safe catch
            }

            val channel = NotificationChannel(
                channelId,
                if (wake) "Emran Fajr wake-up" else "Emran Prayer Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = if (wake) "Fajr adhan that wakes you, even on silent" else "Notification alerts for daily salah times"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setBypassDnd(wake)
                setSound(null, null) // We play the adhan ourselves so we can fade it in and cap it.
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
                val prayerAr = when (prayerName) {
                    "Fajr" -> "الفجر"
                    "Dhuhr" -> io.github.sammynashed.emran.data.PrayerNames.name("Dhuhr", true)
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
                val prayerEn = when (prayerName) {
                    "Fajr" -> "Fajr"
                    "Dhuhr" -> io.github.sammynashed.emran.data.PrayerNames.name("Dhuhr", false)
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
            .setSmallIcon(io.github.sammynashed.emran.R.drawable.ic_stat_emran)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Stop button reachable from the lock screen
            .setDeleteIntent(mutePendingIntent) // Swiping the notification away also stops the adhan
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

        playAdhan(context, prayerName, play, soundKey)
    }

    /** Plays the adhan for [play] (nothing for VIBRATE/SILENT: the channel's vibration is all they get). */
    private fun playAdhan(context: Context, prayerName: String, play: AdhanPolicy.Play, soundKey: String) {
        if (play == AdhanPolicy.Play.VIBRATE || play == AdhanPolicy.Play.SILENT) {
            muteAdhan(context)
            return
        }
        val wake = play == AdhanPolicy.Play.WAKE
        val voiceUri: Uri? = when (soundKey) {
            "ahmad_alnafees", "mishary_alafasy", "saad_alghamdi" ->
                Uri.parse("android.resource://${context.packageName}/raw/$soundKey")
            else -> null
        }
        val systemTone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val soundUri = when (play) {
            // Wake: the full Fajr adhan (with "الصلاة خير من النوم") if this voice has it on the phone.
            AdhanPolicy.Play.WAKE -> AdhanPolicy.fullFajrAdhan(context, soundKey) ?: voiceUri ?: systemTone
            else -> voiceUri ?: systemTone
        }
        // Ears hear loudness on a log scale: squaring the slider makes 60% about 9 dB below full, not 4 dB.
        // Everyday: the cap scales the player. Wake-up: the cap IS the alarm-stream level (set below), so the player
        // plays at full gain; scaling a low alarm volume down again would make a wake-up alarm inaudible.
        // The alarm stream has only ~7 steps, so the wake-up slider uses them for the coarse level (rounded up) and
        // the player's gain for what is left: every slider position then really changes the volume.
        val streamMax = (context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager)
            .getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
        val wakeWanted = AdhanPolicy.wakeLoudness(context) / 100f * streamMax // in stream steps, e.g. 2.8 of 7
        val wakeStep = Math.ceil(wakeWanted.toDouble()).toInt().coerceIn(1, streamMax)
        var gain = if (wake) (wakeWanted / wakeStep).let { it * it }.coerceIn(0.05f, 1f)
        else AdhanPolicy.loudness(context).let { (it / 100f) * (it / 100f) }

        try {
            muteAdhan(context) // Clear any existing playing audio first
            val appContext = context.applicationContext
            appContextForFocus = appContext

            val attrs = AudioAttributes.Builder()
                .setContentType(if (wake) AudioAttributes.CONTENT_TYPE_MUSIC else AudioAttributes.CONTENT_TYPE_SONIFICATION)
                // Everyday: the notification stream, so the user's notification volume, silent mode and DND all apply.
                // Wake: the alarm stream, on purpose, because the user asked to be woken.
                .setUsage(if (wake) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            if (wake) {
                // Like an alarm clock: play at the level the user chose for waking up (not whatever the alarm
                // slider was left at, which may be near zero), and put their volume back afterwards.
                runCatching {
                    savedAlarmVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_ALARM)
                    audioManager.setStreamVolume(android.media.AudioManager.STREAM_ALARM, wakeStep, 0)
                    // Android does not promise to honour the request (some phones refuse to LOWER the alarm volume), so
                    // read back where the stream really is and make up the difference with the player's gain.
                    val actual = audioManager.getStreamVolume(android.media.AudioManager.STREAM_ALARM).coerceAtLeast(1)
                    gain = (wakeWanted / actual).let { it * it }.coerceIn(0.03f, 1f)
                    android.util.Log.d("PrayerAlarmReceiver", "wake: alarm stream $savedAlarmVolume -> $actual (wanted ${"%.1f".format(wakeWanted)} of $streamMax), gain $gain")
                }.onFailure { android.util.Log.w("PrayerAlarmReceiver", "wake: could not set the alarm volume", it) }
                // Silent mode swallows the channel's vibration, so a wake-up vibrates on its own.
                runCatching {
                    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator
                    else @Suppress("DEPRECATION") appContext.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                    vibrator.vibrate(
                        android.os.VibrationEffect.createWaveform(longArrayOf(0, 600, 300, 600, 300, 600), -1),
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                    )
                }
            }
            val request = android.media.AudioFocusRequest.Builder(
                if (wake) android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT else android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            ).setAudioAttributes(attrs).build()
            audioManager.requestAudioFocus(request)
            focusRequest = request

            val player = android.media.MediaPlayer().apply {
                setDataSource(context, soundUri)
                setAudioAttributes(attrs)
                isLooping = false
                setVolume(0f, 0f)
                setOnCompletionListener { muteAdhan(appContext) }
                prepare()
                start()
            }
            mediaPlayer = player

            // Fade in, so the adhan can never be a jump-scare. Short clips (a system tone) fade over a quarter of
            // their length, otherwise they would end before ever reaching full volume.
            val clipMs = runCatching { player.duration }.getOrDefault(0).coerceAtLeast(1)
            // A chime under 3 s can't startle anyone and has no room to fade: it plays at its set loudness straight away.
            val fadeMs = if (clipMs < 3000) 0L else minOf(if (wake) 3000L else 4000L, clipMs / 3L)
            val started = android.os.SystemClock.uptimeMillis()
            val step = object : Runnable {
                override fun run() {
                    val t = if (fadeMs == 0L) 1f else ((android.os.SystemClock.uptimeMillis() - started).toFloat() / fadeMs).coerceIn(0f, 1f)
                    val v = gain * t * t // slow start: the first second is barely there
                    runCatching { mediaPlayer?.setVolume(v, v) }
                    if (t < 1f) fadeHandler.postDelayed(this, 50) else fadeStep = null
                }
            }
            fadeStep = step
            step.run()

            // Register background-compatible volume key press detection to instantly mute the Adhan
            val armedAt = android.os.SystemClock.uptimeMillis() + 2000L // our own volume change is announced a moment late
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(subContext: Context, subIntent: Intent) {
                    if (subIntent.action == "android.media.VOLUME_CHANGED_ACTION" && android.os.SystemClock.uptimeMillis() >= armedAt) {
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
            android.util.Log.e("PrayerAlarmReceiver", "Failed to play the adhan", e)
        }
    }
}
