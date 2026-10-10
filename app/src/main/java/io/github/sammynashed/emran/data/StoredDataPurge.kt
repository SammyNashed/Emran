package io.github.sammynashed.emran.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Deletes what the removed profile, Qada, Stations and prayer-log features left
 * on the phone, once, on the first launch after updating.
 *
 * Emran no longer keeps a record of the person using it: no name or photo, no
 * prayer log, no streaks, points, badges or Qada counts. Removing the screens
 * alone would leave all of that sitting in storage, so it is erased here. The
 * two database tables go in AppDatabase's 8 -> 9 migration.
 *
 * Settings that only shape how the app behaves (location, method, Fine-Tune,
 * theme, notifications, language) are kept.
 */
object StoredDataPurge {

    private const val DONE_KEY = "personal_data_purged_v1"

    private val KEYS = listOf(
        // profile
        "user_name", "join_date", "profile_picture_uri",
        // points, ranks, badges, intentions
        "khushu_points", "last_seen_khushu_level", "equipped_badge", "active_niyyah",
        // stations (quests)
        "active_challenge_ids", "completed_challenges_ids",
        // qada
        "qada_onboarding_completed", "custom_forge_target", "max_qada_target", "last_missed_checked_date",
        // streak and its celebration
        "streak_count", "last_fire_animation_played_date", "last_streak_played_value",
        // lifetime tasbeeh tally (the counters themselves are kept)
        "total_tasbeeh_clicks",
    )

    fun run(context: Context, prefs: SharedPreferences) {
        if (prefs.getBoolean(DONE_KEY, false)) return
        val edit = prefs.edit()
        KEYS.forEach { edit.remove(it) }
        prefs.all.keys.filter { it.startsWith("reflection_note_") }.forEach { edit.remove(it) }
        edit.putBoolean(DONE_KEY, true).apply()

        // The profile photo was copied into app storage, not just referenced.
        context.filesDir.listFiles()
            ?.filter { it.name.startsWith("profile_picture") && it.name.endsWith(".jpg") }
            ?.forEach { runCatching { it.delete() } }
    }
}
