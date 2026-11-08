package io.github.sammynashed.emran.service

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import io.github.sammynashed.emran.data.assets.MyAdhkar
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * "الذكر العائم": every so often a small Emran card floats in over whatever app is open, shows one
 * dhikr the user picked, and leaves on its own (or is dragged onto an X to dismiss it).
 *
 * Opt-in and off by default. It only appears while the screen is on and unlocked, never during a
 * call, never in the user's quiet hours. The alarm is a plain RTC one (not a wake-up), so it
 * never wakes a sleeping phone just to show a card nobody sees.
 */
object FloatingDhikr {
    /**
     * A dhikr the card can show, from the app's own Adhkar library (assets/adhkar.json, the same texts and
     * sources as Home ▸ Adhkar) or from the user's "أذكاري". [folderId] is its section there, so a tap on
     * the card opens that very dhikr.
     */
    data class Dhikr(val id: String, val ar: String, val en: String, val sourceAr: String?, val sourceEn: String?, val folderId: String)

    /** A section of the library as the picker lists it. */
    class Section(val id: String, val titleAr: String, val titleEn: String, val items: List<Dhikr>)

    @Volatile private var library: List<Section>? = null

    /** The bundled library, parsed once (a few hundred KB; plain org.json so a receiver can call it directly). */
    private fun bundled(c: Context): List<Section> = library ?: runCatching {
        val root = org.json.JSONObject(c.applicationContext.assets.open("adhkar.json").bufferedReader().use { it.readText() })
        val folders = root.getJSONArray("folders")
        (0 until folders.length()).map { fi ->
            val f = folders.getJSONObject(fi)
            val items = f.getJSONArray("items")
            val list = (0 until items.length()).mapNotNull { ii ->
                val it = items.getJSONObject(ii)
                val id = it.optString("id").ifBlank { return@mapNotNull null }
                val ar = it.optString("textAr")
                if (ar.isBlank()) return@mapNotNull null
                Dhikr(id, ar, it.optString("textEn").ifBlank { ar },
                    it.optString("sourceAr").ifBlank { it.optString("source") }.ifBlank { null },
                    it.optString("source").ifBlank { null }, f.getString("id"))
            }
            Section(f.getString("id"), f.optString("titleAr"), f.optString("titleEn"), list)
        }.filter { it.items.isNotEmpty() }
    }.getOrElse { android.util.Log.e("FloatingDhikr", "adhkar.json", it); emptyList() }.also { library = it }

    val EVERY_CHOICES = listOf(15, 30, 60, 120, 180)
    val SHOW_CHOICES = listOf(5, 8, 12, 20)

    private const val PREFS = "emran_settings"
    private const val REQ = 7201
    private const val CHANNEL = "emran_floating_dhikr"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isOn(c: Context) = prefs(c).getBoolean("float_dhikr_on", false)
    fun everyMinutes(c: Context) = prefs(c).getInt("float_dhikr_every", 60).let { if (it in EVERY_CHOICES) it else 60 }
    fun showSeconds(c: Context) = prefs(c).getInt("float_dhikr_show", 8).let { if (it in SHOW_CHOICES) it else 8 }
    fun quietOn(c: Context) = prefs(c).getBoolean("float_dhikr_quiet_on", true)
    fun quietStart(c: Context) = prefs(c).getInt("float_dhikr_quiet_start", 23 * 60)
    fun quietEnd(c: Context) = prefs(c).getInt("float_dhikr_quiet_end", 7 * 60)
    /** Only what the person picked; nothing is chosen for them. */
    fun selectedIds(c: Context): Set<String> = prefs(c).getStringSet("float_dhikr_ids", null)?.toSet() ?: emptySet()

    fun setOn(c: Context, on: Boolean) { prefs(c).edit().putBoolean("float_dhikr_on", on).apply(); schedule(c) }
    fun setEvery(c: Context, m: Int) { prefs(c).edit().putInt("float_dhikr_every", m).apply(); schedule(c) }
    fun setShow(c: Context, s: Int) { prefs(c).edit().putInt("float_dhikr_show", s).apply() }
    fun setQuiet(c: Context, on: Boolean, start: Int, end: Int) {
        prefs(c).edit().putBoolean("float_dhikr_quiet_on", on).putInt("float_dhikr_quiet_start", start).putInt("float_dhikr_quiet_end", end).apply()
        schedule(c)
    }
    fun setSelected(c: Context, ids: Set<String>) { prefs(c).edit().putStringSet("float_dhikr_ids", ids).apply() }

    /** Where the user last left the card (screen px of its top-left), or null for the default spot. */
    fun savedPosition(c: Context): Pair<Int, Int>? {
        val p = prefs(c); if (!p.contains("float_dhikr_x")) return null
        return p.getInt("float_dhikr_x", 0) to p.getInt("float_dhikr_y", 0)
    }
    fun savePosition(c: Context, x: Int, y: Int) { prefs(c).edit().putInt("float_dhikr_x", x).putInt("float_dhikr_y", y).apply() }

    fun canDraw(c: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(c)

    fun overlaySettingsIntent(c: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${c.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Everything the user can pick: the built-in list, then their own adhkar from "أذكاري". */
    /** The user's own first, then every section of the library in the app's order. */
    fun sections(c: Context): List<Section> {
        val mine = MyAdhkar.load(c).mapNotNull { item ->
            val id = item.id ?: return@mapNotNull null
            Dhikr(id, item.textAr, MyAdhkar.translationOf(item).ifBlank { item.textAr }, item.sourceAr, item.source, MyAdhkar.FOLDER_ID)
        }
        val own = if (mine.isEmpty()) emptyList() else listOf(Section(MyAdhkar.FOLDER_ID, "أذكاري", "My adhkar", mine))
        return own + bundled(c)
    }

    fun choices(c: Context): List<Dhikr> = sections(c).flatMap { it.items }

    fun next(c: Context): Dhikr? {
        val chosen = selectedIds(c)
        val all = choices(c)
        val list = all.filter { it.id in chosen }
        if (list.isEmpty()) return null
        val i = prefs(c).getInt("float_dhikr_turn", 0)
        prefs(c).edit().putInt("float_dhikr_turn", i + 1).apply()
        return list[Math.floorMod(i, list.size)]
    }

    fun inQuiet(c: Context, minuteOfDay: Int): Boolean {
        if (!quietOn(c)) return false
        val s = quietStart(c); val e = quietEnd(c)
        return if (s == e) false else if (s < e) minuteOfDay in s until e else minuteOfDay >= s || minuteOfDay < e
    }

    /** Next slot strictly after now, on the clock's grid (every 30 min = :00 and :30), skipping quiet hours. */
    fun nextTrigger(c: Context, now: LocalDateTime = LocalDateTime.now()): LocalDateTime {
        val every = everyMinutes(c)
        val dayStart = now.toLocalDate().atStartOfDay()
        val minute = now.hour * 60 + now.minute
        var t = dayStart.plusMinutes(((minute / every) + 1L) * every)
        repeat(48 * 60 / every + 2) {
            if (!inQuiet(c, t.hour * 60 + t.minute)) return t
            t = t.plusMinutes(every.toLong())
        }
        return t
    }

    private fun pending(c: Context) = PendingIntent.getBroadcast(
        c, REQ, Intent(c, FloatingDhikrReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun schedule(context: Context) {
        val c = context.applicationContext
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(c)
        am.cancel(pi)
        if (!isOn(c) || !prefs(c).getBoolean("onboarding_completed", false)) return
        val at = nextTrigger(c).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            // RTC, not RTC_WAKEUP: a sleeping phone stays asleep; the card is only for a screen someone is looking at.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) am.setExact(AlarmManager.RTC, at, pi)
            else am.set(AlarmManager.RTC, at, pi)
            Log.d("FloatingDhikr", "next card at ${java.time.Instant.ofEpochMilli(at)}")
        } catch (e: Exception) {
            Log.e("FloatingDhikr", "could not arm", e)
        }
    }

    /** True when someone is actually looking at an unlocked screen and not on a call. */
    fun goodMoment(c: Context): Boolean {
        val pm = c.getSystemService(Context.POWER_SERVICE) as PowerManager
        val km = c.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val audio = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val inCall = audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION
        return pm.isInteractive && !km.isKeyguardLocked && !inCall
    }

    fun language(c: Context) = prefs(c).getString("current_language", "en") ?: "en"

    /** Without the overlay permission the same dhikr arrives as a quiet notification instead. */
    fun notifyInstead(c: Context, d: Dhikr) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ar = language(c) == "ar"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, if (ar) "الذكر العائم" else "Floating dhikr", NotificationManager.IMPORTANCE_LOW).apply {
                    description = if (ar) "ذكر قصير يظهر كل فترة" else "A short dhikr every so often"
                    setShowBadge(false)
                },
            )
        }
        val open = PendingIntent.getActivity(
            c, REQ, Intent(c, io.github.sammynashed.emran.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("ROUTE", "floating_dhikr"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(io.github.sammynashed.emran.R.drawable.ic_stat_emran)
            .setContentTitle(d.ar)
            .setContentText(if (ar) d.sourceAr ?: "" else d.en)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setTimeoutAfter(showSeconds(c) * 1000L + 60_000L)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { nm.notify(REQ, n) }
    }
}

class FloatingDhikrReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = context.applicationContext
        FloatingDhikr.schedule(c)                       // arm the next one first, whatever happens below
        if (!FloatingDhikr.isOn(c)) return
        val now = LocalDateTime.now()
        if (FloatingDhikr.inQuiet(c, now.hour * 60 + now.minute)) return
        if (!FloatingDhikr.goodMoment(c)) return
        val d = FloatingDhikr.next(c) ?: return
        if (FloatingDhikr.canDraw(c)) FloatingDhikrOverlay.show(c, d) else FloatingDhikr.notifyInstead(c, d)
    }
}
