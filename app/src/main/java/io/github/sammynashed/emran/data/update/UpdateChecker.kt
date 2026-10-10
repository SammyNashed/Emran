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

    suspend fun check(isArabic: Boolean): Result = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = true
            }
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
