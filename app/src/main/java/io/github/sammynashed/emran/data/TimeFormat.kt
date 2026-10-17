package io.github.sammynashed.emran.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 12-hour or 24-hour clock, for every time the app shows (screens, widgets, notifications).
 *
 * Until the user picks one in Settings it follows the phone's own setting, so most people never
 * need to touch it. Compose reads [use24] inside composition, so switching redraws every screen.
 */
object TimeFormat {
    private const val PREFS = "emran_settings"
    private const val KEY = "time_format_24h"

    /** null = follow the phone. */
    private val choice = mutableStateOf<Boolean?>(null)
    private val phone = mutableStateOf(true)
    @Volatile private var loaded = false

    fun ensureLoaded(context: Context) {
        phone.value = android.text.format.DateFormat.is24HourFormat(context)
        if (loaded) return
        loaded = true
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        choice.value = if (p.contains(KEY)) p.getBoolean(KEY, true) else null
    }

    /** The clock in effect. Read in composition: a change recomposes. */
    val use24: Boolean get() = choice.value ?: phone.value

    fun is24(context: Context): Boolean {
        ensureLoaded(context)
        return use24
    }

    fun set(context: Context, use24: Boolean) {
        loaded = true
        choice.value = use24
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, use24).apply()
    }

    /** "19:18" or "7:18". Latin digits, as everywhere else in the app. */
    fun clock(t: LocalTime, use24: Boolean = this.use24): String =
        if (use24) "%02d:%02d".format(t.hour, t.minute)
        else "%d:%02d".format(if (t.hour % 12 == 0) 12 else t.hour % 12, t.minute)

    /** "م"/"ص" or "PM"/"AM" on the 12-hour clock, null on the 24-hour one. */
    fun suffix(t: LocalTime, ar: Boolean, use24: Boolean = this.use24): String? =
        if (use24) null else if (t.hour < 12) (if (ar) "ص" else "AM") else (if (ar) "م" else "PM")

    /** "19:18", "7:18 PM" or "7:18 م". */
    fun full(t: LocalTime, ar: Boolean, use24: Boolean = this.use24): String =
        clock(t, use24) + (suffix(t, ar, use24)?.let { " $it" } ?: "")

    fun full(t: LocalDateTime, ar: Boolean, use24: Boolean = this.use24): String = full(t.toLocalTime(), ar, use24)
}
