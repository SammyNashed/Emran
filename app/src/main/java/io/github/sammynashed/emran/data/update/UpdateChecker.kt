package io.github.sammynashed.emran.data.update

import io.github.sammynashed.emran.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * "Is there a newer Emran?" - asked only when the user taps the row in Settings.
 * Nothing here runs on its own: no startup check, no background job, no banner
 * that needed the network to appear. That keeps the promise that the only thing
 * touching the network is something the user started.
 *
 * The answer comes from one tiny JSON file (`latest.json`, ~300 bytes) published
 * next to the releases:
 *   { "versionCode": 4, "versionName": "2.1.1",
 *     "notes_ar": "...", "notes_en": "..." }
 * The APK itself is never fetched by the app; the user is sent to the channel,
 * where the file is a normal Telegram download they already know how to install.
 */
object UpdateChecker {

    const val CHANNEL_URL = "https://t.me/emran_app"
    private const val CHANNEL_HANDLE = "emran_app"

    /**
     * Opens the channel in the Telegram app. `tg://` goes straight to the app without looking
     * up t.me, which some networks block; the https link is the fallback for a phone where
     * the app is missing. Returns false when neither could be opened.
     */
    fun openChannel(context: android.content.Context): Boolean {
        for (uri in listOf("tg://resolve?domain=$CHANNEL_HANDLE", CHANNEL_URL)) {
            try {
                context.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                return true
            } catch (_: Exception) { /* try the next one */ }
        }
        return false
    }
    private const val LATEST_URL =
        "https://github.com/SammyNashed/Emran/releases/download/update/latest.json"

    sealed interface Result {
        data object UpToDate : Result
        data class Available(val versionName: String, val notes: String) : Result
        /** Could not reach the server at all (no data, no wifi, DNS). */
        data object Offline : Result
        /** Reached the server but got no usable answer (404, bad JSON). */
        data object Failed : Result
    }

    /** Where the version file lives. Overridable for testing via the "update_url" pref (debug installs only can set it). */
    private fun latestUrl(context: android.content.Context): String =
        context.getSharedPreferences("emran_update", android.content.Context.MODE_PRIVATE)
            .getString("update_url", null)?.takeIf { it.isNotBlank() } ?: LATEST_URL

    suspend fun check(context: android.content.Context, isArabic: Boolean): Result = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(latestUrl(context)).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = true
            }
            // No latest.json published yet means no update exists, which is not an error to show.
            if (conn.responseCode == HttpURLConnection.HTTP_NOT_FOUND) return@withContext Result.UpToDate
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext Result.Failed
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val latestCode = json.getInt("versionCode")
            if (latestCode <= BuildConfig.VERSION_CODE) return@withContext Result.UpToDate
            val notes = json.optString(if (isArabic) "notes_ar" else "notes_en")
                .ifBlank { json.optString("notes_en") }
            Result.Available(json.optString("versionName", latestCode.toString()), notes)
        } catch (e: java.io.IOException) {
            Result.Offline
        } catch (e: Exception) {
            Result.Failed
        } finally {
            conn?.disconnect()
        }
    }
}
