package io.github.sammynashed.emran.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Daily reminders for the morning and evening adhkar (7:00 and 21:00 unless the user picks other
 * times in Settings), and the weekly one to read Surat al-Kahf on Friday (14:00, after Jumu'ah). Each one is a single exact alarm for its next occurrence; when it fires, the
 * receiver posts the reminder and arms the next day. Everything that re-arms the prayer alarms
 * (boot, update, time change, the daily job, opening the app) re-arms these too.
 */
object AdhkarReminders {
    enum class Kind(val key: String, val defaultMinute: Int, val requestCode: Int, val fridayOnly: Boolean = false) {
        MORNING("morning", 7 * 60, 7101),
        EVENING("evening", 21 * 60, 7102),
        KAHF("kahf", 14 * 60, 7103, fridayOnly = true);

        fun title(ar: Boolean): String = when (this) {
            MORNING -> if (ar) "أذكار الصباح" else "Morning adhkar"
            EVENING -> if (ar) "أذكار المساء" else "Evening adhkar"
            KAHF -> if (ar) "سورة الكهف يوم الجمعة" else "Al-Kahf on Friday"
        }
    }

    private const val KAHF_CHANNEL = "emran_kahf_reminder"

    /** The hadith the Friday reminder carries, instead of a generic "time to read". */
    private const val KAHF_HADITH_AR = "«مَن قرأ سورةَ الكهفِ في يومِ الجمعةِ، أضاءَ له مِن النورِ ما بينَ الجمعتَين»"
    private const val KAHF_SOURCE_AR = "رواه الحاكم والبيهقي، وصححه الألباني (صحيح الجامع 6470)"
    private const val KAHF_HADITH_EN = "“Whoever reads Surat al-Kahf on Friday, a light will shine for him between the two Fridays.”"
    private const val KAHF_SOURCE_EN = "Al-Hakim and al-Bayhaqi; graded sahih by al-Albani (Sahih al-Jami' 6470)"

    private const val PREFS = "emran_settings"
    private const val CHANNEL = "emran_adhkar_reminders"
    private const val EXTRA_KIND = "ADHKAR_KIND"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isOn(context: Context, kind: Kind): Boolean = prefs(context).getBoolean("adhkar_${kind.key}_on", true)

    /** Minute of the day, 0..1439. */
    fun minute(context: Context, kind: Kind): Int =
        prefs(context).getInt("adhkar_${kind.key}_min", kind.defaultMinute).coerceIn(0, 1439)

    fun set(context: Context, kind: Kind, on: Boolean, minute: Int) {
        prefs(context).edit()
            .putBoolean("adhkar_${kind.key}_on", on)
            .putInt("adhkar_${kind.key}_min", Math.floorMod(minute, 1440))
            .commit()
        scheduleAll(context)
    }

    private fun pending(context: Context, kind: Kind): PendingIntent = PendingIntent.getBroadcast(
        context,
        kind.requestCode,
        Intent(context, AdhkarReminderReceiver::class.java).putExtra(EXTRA_KIND, kind.key),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** The next moment this reminder should ring, in the phone's own time zone, strictly after [nowMillis]. */
    fun nextTrigger(minute: Int, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault(), fridayOnly: Boolean = false): Long {
        val at = LocalTime.of(minute / 60, minute % 60)
        val today = LocalDate.now(java.time.Clock.fixed(java.time.Instant.ofEpochMilli(nowMillis), zone))
        // The first day from today (up to a week on) that is allowed and still ahead.
        for (i in 0..7) {
            val day = today.plusDays(i.toLong())
            if (fridayOnly && day.dayOfWeek != java.time.DayOfWeek.FRIDAY) continue
            val t = day.atTime(at).atZone(zone).toInstant().toEpochMilli()
            if (t > nowMillis) return t
        }
        return today.plusDays(8).atTime(at).atZone(zone).toInstant().toEpochMilli()
    }

    fun scheduleAll(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val onboarded = prefs(app).getBoolean("onboarding_completed", false)
        for (kind in Kind.values()) {
            val pi = pending(app, kind)
            am.cancel(pi)
            if (!onboarded || !isOn(app, kind)) continue
            val at = nextTrigger(minute(app, kind), System.currentTimeMillis(), fridayOnly = kind.fridayOnly)
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                }
                Log.d("AdhkarReminders", "${kind.key} reminder armed for ${java.time.Instant.ofEpochMilli(at)}")
            } catch (e: Exception) {
                Log.e("AdhkarReminders", "could not arm ${kind.key} reminder", e)
            }
        }
    }

    internal fun kindOf(intent: Intent): Kind? = Kind.values().firstOrNull { it.key == intent.getStringExtra(EXTRA_KIND) }

    internal fun notify(context: Context, kind: Kind) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ar = (prefs(context).getString("current_language", "en") ?: "en") == "ar"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, if (ar) "تذكير الأذكار" else "Adhkar reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = if (ar) "تذكير يومي بأذكار الصباح والمساء" else "A daily reminder for the morning and evening adhkar"
                },
            )
        }
        if (kind == Kind.KAHF && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(KAHF_CHANNEL, if (ar) "سورة الكهف يوم الجمعة" else "Al-Kahf on Friday", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = if (ar) "تذكير أسبوعي بقراءة سورة الكهف بعد صلاة الجمعة" else "A weekly reminder to read Surat al-Kahf after Jumu'ah"
                },
            )
        }
        val morning = kind == Kind.MORNING
        val title: String
        val text: String
        val big: String
        if (kind == Kind.KAHF) {
            title = if (ar) "سورة الكهف · يوم الجمعة" else "Surat al-Kahf · Friday"
            text = if (ar) KAHF_HADITH_AR else KAHF_HADITH_EN
            big = if (ar) "$KAHF_HADITH_AR\n$KAHF_SOURCE_AR\n\nاضغط لتقرأها الآن." else "$KAHF_HADITH_EN\n$KAHF_SOURCE_EN\n\nTap to read it now."
        } else {
            title = kind.title(ar)
            text = if (ar) "حان وقت ${if (morning) "أذكار الصباح" else "أذكار المساء"}، اضغط لتقرأها الآن"
            else "Time for your ${if (morning) "morning" else "evening"} adhkar. Tap to read them now."
            big = text
        }
        val open = Intent(context, io.github.sammynashed.emran.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // Al-Kahf opens straight into the surah, in Tilawah.
            putExtra("ROUTE", if (kind == Kind.KAHF) "quran_surah/18" else "adhkar_detail/${kind.key}")
        }
        val contentIntent = PendingIntent.getActivity(
            context, kind.requestCode, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, if (kind == Kind.KAHF) KAHF_CHANNEL else CHANNEL)
            .setSmallIcon(io.github.sammynashed.emran.R.drawable.ic_stat_emran)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(big))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        try {
            nm.notify(kind.requestCode, n)
        } catch (e: SecurityException) {
            Log.w("AdhkarReminders", "notifications not allowed", e)
        }
    }
}

class AdhkarReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = AdhkarReminders.kindOf(intent) ?: return
        // Checked again at fire time, so a reminder switched off after it was armed stays quiet.
        if (AdhkarReminders.isOn(context, kind)) AdhkarReminders.notify(context, kind)
        AdhkarReminders.scheduleAll(context)
    }
}
