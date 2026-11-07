package io.github.sammynashed.emran.service

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import java.io.File
import java.time.LocalDateTime

/**
 * Decides HOW an adhan reaches the user, so the phone's own settings win by default:
 * silent, vibrate and Do Not Disturb are respected, and the adhan follows the notification volume.
 * The only thing that overrides all of that is the user's own "Wake me for Fajr" switch.
 */
object AdhanPolicy {

    /** What the user picked for one prayer. On/off is the bell on the Prayers screen (`notif_<Prayer>`), not this. */
    enum class Mode { ADHAN, VIBRATE }

    /** What actually happens right now, once the phone's state is taken into account. */
    enum class Play { WAKE, ADHAN, VIBRATE, SILENT }

    /** Days are a bitmask, Monday = bit 0; times are minutes after midnight; end < start runs past midnight. */
    data class QuietWindow(val days: Int, val startMin: Int, val endMin: Int)

    private const val PREFS = "emran_settings"
    private const val KEY_LOUDNESS = "adhan_loudness"
    private const val KEY_WAKE = "adhan_wake_fajr"
    private const val KEY_WAKE_LOUDNESS = "adhan_wake_loudness"
    private const val KEY_FULL = "adhan_full"
    private const val KEY_FAJR_SOUND = "fajr_sound"
    private const val KEY_QUIET = "adhan_quiet_windows"

    const val DEFAULT_LOUDNESS = 45
    const val DEFAULT_WAKE_LOUDNESS = 40

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun mode(context: Context, prayer: String): Mode =
        if (prefs(context).getString("adhan_mode_$prayer", null) == "VIBRATE") Mode.VIBRATE else Mode.ADHAN

    fun setMode(context: Context, prayer: String, mode: Mode) {
        prefs(context).edit().putString("adhan_mode_$prayer", mode.name).commit()
    }

    /** 20..100: the adhan's own loudness, as a share of whatever the phone's volume already is. */
    fun loudness(context: Context): Int = prefs(context).getInt(KEY_LOUDNESS, DEFAULT_LOUDNESS).coerceIn(20, 100)

    fun setLoudness(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_LOUDNESS, value.coerceIn(20, 100)).apply()
    }

    /** The wake-up adhan has its own cap, separate from the everyday one: loud enough to wake you, never "maximum". */
    fun wakeLoudness(context: Context): Int = prefs(context).getInt(KEY_WAKE_LOUDNESS, DEFAULT_WAKE_LOUDNESS).coerceIn(10, 100)

    fun setWakeLoudness(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_WAKE_LOUDNESS, value.coerceIn(10, 100)).apply()
    }

    fun wakeForFajr(context: Context): Boolean = prefs(context).getBoolean(KEY_WAKE, false)

    fun setWakeForFajr(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_WAKE, on).commit()
    }

    fun quietWindows(context: Context): List<QuietWindow> {
        val raw = prefs(context).getString(KEY_QUIET, "") ?: ""
        return raw.split(';').mapNotNull {
            val p = it.split(':')
            if (p.size != 3) return@mapNotNull null
            val d = p[0].toIntOrNull() ?: return@mapNotNull null
            val s = p[1].toIntOrNull() ?: return@mapNotNull null
            val e = p[2].toIntOrNull() ?: return@mapNotNull null
            QuietWindow(d and 0x7F, s.coerceIn(0, 1439), e.coerceIn(0, 1439))
        }
    }

    fun setQuietWindows(context: Context, windows: List<QuietWindow>) {
        prefs(context).edit().putString(KEY_QUIET, windows.joinToString(";") { "${it.days}:${it.startMin}:${it.endMin}" }).commit()
    }

    fun isQuietNow(context: Context, now: LocalDateTime = LocalDateTime.now()): Boolean {
        val minute = now.hour * 60 + now.minute
        val today = now.dayOfWeek.value - 1
        val yesterday = (today + 6) % 7
        return quietWindows(context).any { w ->
            if (w.startMin <= w.endMin) {
                (w.days shr today and 1) == 1 && minute >= w.startMin && minute < w.endMin
            } else {
                // Overnight: the evening part belongs to the day it started on, the morning part to the day after.
                ((w.days shr today and 1) == 1 && minute >= w.startMin) ||
                    ((w.days shr yesterday and 1) == 1 && minute < w.endMin)
            }
        }
    }

    /** Whether Android would let our channel through Do Not Disturb (the wake switch needs it). */
    fun canBypassDnd(context: Context): Boolean =
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted

    fun resolve(context: Context, prayer: String, now: LocalDateTime = LocalDateTime.now()): Play {
        // The user asked to be woken for Fajr: that is the one thing allowed to ignore silent, DND and quiet hours.
        if (prayer == "Fajr" && wakeForFajr(context)) return Play.WAKE

        val mode = mode(context, prayer)
        if (mode == Mode.VIBRATE || isQuietNow(context, now)) return Play.VIBRATE

        val ringer = (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).ringerMode
        if (ringer == AudioManager.RINGER_MODE_SILENT) return Play.SILENT
        if (ringer == AudioManager.RINGER_MODE_VIBRATE) return Play.VIBRATE

        val filter = (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).currentInterruptionFilter
        if (filter != NotificationManager.INTERRUPTION_FILTER_ALL && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
            return Play.SILENT
        }
        return Play.ADHAN
    }

    /** "The whole adhan" instead of its opening seconds, for every prayer (a friend asked for it, 2026-11). Off by default. */
    fun fullAdhan(context: Context): Boolean = prefs(context).getBoolean(KEY_FULL, false)

    fun setFullAdhan(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_FULL, on).commit()
    }

    private fun raw(context: Context, name: String): Uri? {
        val res = context.resources.getIdentifier(name, "raw", context.packageName)
        return if (res != 0) Uri.parse("android.resource://${context.packageName}/$res") else null
    }

    /**
     * The whole adhan of [voiceKey] for [prayer], or null when this voice has no full recording.
     * Fajr is ALWAYS the voice the user picked (his rule, 2026-11-07: never a stranger's recording):
     * its own Fajr recording with "الصلاة خير من النوم" when one exists from the same muezzin,
     * otherwise that same muezzin's everyday adhan.
     */
    fun fullAdhanUri(context: Context, voiceKey: String, prayer: String): Uri? = when (prayer) {
        "Fajr" -> fullFajrAdhan(context, voiceKey) ?: raw(context, "${voiceKey}_full")
        "Dhuhr", "Asr", "Maghrib", "Isha" -> raw(context, "${voiceKey}_full")
        else -> null // Sunrise is a reminder that Fajr's time is over, never an adhan
    }

    /**
     * Voices with a "الصلاة خير من النوم" clip (`res/raw/<voice>_tathweeb`), cut from that muezzin's own
     * Fajr adhan. Only these are offered for "Fajr sound" (his rule: show only what exists).
     */
    val TATHWEEB_VOICES = listOf("mishary_alafasy")

    /** The voice whose "الصلاة خير من النوم" plays at Fajr instead of the adhan, or null for the adhan. */
    fun fajrTathweebVoice(context: Context): String? =
        prefs(context).getString(KEY_FAJR_SOUND, null)?.takeIf { it in TATHWEEB_VOICES && raw(context, "${it}_tathweeb") != null }

    fun setFajrTathweebVoice(context: Context, voice: String?) {
        prefs(context).edit().putString(KEY_FAJR_SOUND, voice.orEmpty()).commit()
    }

    fun tathweebUri(context: Context, voice: String): Uri? = raw(context, "${voice}_tathweeb")

    /**
     * The full Fajr adhan (ending with "الصلاة خير من النوم") for a voice, if it is on the phone:
     * either bundled as `res/raw/<voice>_fajr_full` or downloaded to `files/adhan_full/<voice>.ogg`.
     */
    fun fullFajrAdhan(context: Context, voiceKey: String): Uri? {
        val file = File(context.filesDir, "adhan_full/$voiceKey.ogg")
        if (file.isFile && file.length() > 0) return Uri.fromFile(file)
        return raw(context, "${voiceKey}_fajr_full")
    }
}
