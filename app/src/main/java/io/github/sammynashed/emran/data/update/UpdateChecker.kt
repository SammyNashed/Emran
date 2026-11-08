package io.github.sammynashed.emran.data.update

import android.content.Context
import io.github.sammynashed.emran.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * "Is there a newer Emran?" - asked only when the user taps it in Settings, and the update is
 * downloaded only when they then tap "Update now". Nothing here runs on its own: no startup
 * check, no background job. That keeps the promise that only things the user started go online.
 *
 * Everything lives on Emran's site (GitHub Pages), which loads in Syria without a VPN, unlike
 * GitHub's release downloads. One small file says what is newest and how to get there:
 *   { "versionCode": 15, "versionName": "2.3.5", "notes_ar": "...", "notes_en": "...",
 *     "apks": { "arm64-v8a": { "url": "../download/Emran.apk", "size": 81000000, "sha256": "..." },
 *               "armeabi-v7a": { ... } },
 *     "patches": [ { "url": "Emran-2.3.4-to-2.3.5.emran", "size": 9000000,
 *                    "fromSha256": "...", "toSha256": "...", "toVersionCode": 15 } ] }
 * URLs are relative to latest.json. The app hashes its own installed APK and follows patches
 * from that exact file, one after another, up to the newest version (someone a few versions
 * behind gets the whole chain in one go). When no chain fits (a debug build, a phone that came
 * from another source), it offers the full APK instead.
 */
object UpdateChecker {

    const val CHANNEL_URL = "https://t.me/emran_app"
    private const val CHANNEL_HANDLE = "emran_app"
    const val SITE_URL = "https://sammynashed.github.io/Emran/"
    private const val LATEST_URL = SITE_URL + "update/latest.json"

    /**
     * Opens the channel in the Telegram app. `tg://` goes straight to the app without looking
     * up t.me, which some networks block; the https link is the fallback for a phone where
     * the app is missing. Returns false when neither could be opened.
     */
    fun openChannel(context: Context): Boolean {
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

    /** One file to fetch: where it is and how big. [sha256] is checked for full APKs (patches carry their own). */
    data class Remote(val url: String, val size: Long, val sha256: String? = null)

    /** How to get to the newest version: a chain of small patches, or the whole app. */
    sealed interface Plan {
        val size: Long
        data class Patches(val files: List<Remote>) : Plan { override val size get() = files.sumOf { it.size } }
        data class FullApk(val file: Remote) : Plan { override val size get() = file.size }
    }

    sealed interface Result {
        data object UpToDate : Result
        data class Available(val versionName: String, val notes: String, val plan: Plan?) : Result
        /** Could not reach the server at all (no data, no wifi, DNS). */
        data object Offline : Result
        /** Reached the server but got no usable answer (404, bad JSON). */
        data object Failed : Result
    }

    /** Where the version file lives. Overridable for testing via the "update_url" pref (debug installs only can set it). */
    private fun latestUrl(context: Context): String =
        context.getSharedPreferences("emran_update", Context.MODE_PRIVATE)
            .getString("update_url", null)?.takeIf { it.isNotBlank() } ?: LATEST_URL

    suspend fun check(context: Context, isArabic: Boolean): Result = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val base = latestUrl(context)
            conn = (URL(base).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                instanceFollowRedirects = true
                useCaches = false
            }
            // No latest.json published yet means no update exists, which is not an error to show.
            if (conn.responseCode == HttpURLConnection.HTTP_NOT_FOUND) return@withContext Result.UpToDate
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext Result.Failed
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val latestCode = json.getInt("versionCode")
            if (latestCode <= BuildConfig.VERSION_CODE) return@withContext Result.UpToDate
            val notes = json.optString(if (isArabic) "notes_ar" else "notes_en")
                .ifBlank { json.optString("notes_en") }
            Result.Available(json.optString("versionName", latestCode.toString()), notes, plan(context, base, json, latestCode))
        } catch (e: java.io.IOException) {
            Result.Offline
        } catch (e: Exception) {
            Result.Failed
        } finally {
            conn?.disconnect()
        }
    }

    private fun plan(context: Context, base: String, json: JSONObject, latestCode: Int): Plan? {
        fun resolve(u: String) = URL(URL(base), u).toString()
        val patches = json.optJSONArray("patches")
        if (patches != null && patches.length() > 0) {
            val bySource = (0 until patches.length()).map { patches.getJSONObject(it) }.associateBy { it.getString("fromSha256") }
            var at = runCatching { sha256(File(context.applicationInfo.sourceDir)) }.getOrNull()
            val chain = mutableListOf<Remote>()
            while (at != null && chain.size < 20) {
                val p = bySource[at] ?: break
                chain += Remote(resolve(p.getString("url")), p.optLong("size"))
                if (p.optInt("toVersionCode") >= latestCode) return Plan.Patches(chain)
                at = p.getString("toSha256")
            }
        }
        // One full APK per phone type: a 64-bit phone gets arm64-v8a, an older 32-bit one armeabi-v7a.
        val apks = json.optJSONObject("apks")
        val apk = android.os.Build.SUPPORTED_ABIS.firstNotNullOfOrNull { apks?.optJSONObject(it) }
            ?: json.optJSONObject("apk") ?: return null
        return Plan.FullApk(Remote(resolve(apk.getString("url")), apk.optLong("size"), apk.optString("sha256").ifBlank { null }))
    }

    /**
     * Downloads every file of [plan] into the app's cache. [progress] runs from 0 to 1 over the
     * whole plan. Returns the files in order, or null if anything failed (half files are removed).
     */
    suspend fun download(context: Context, plan: Plan, progress: (Float) -> Unit): List<File>? = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "update").apply { mkdirs() }
        val remotes = when (plan) { is Plan.Patches -> plan.files; is Plan.FullApk -> listOf(plan.file) }
        val total = plan.size.coerceAtLeast(1)
        var done = 0L
        val out = mutableListOf<File>()
        try {
            remotes.forEachIndexed { i, r ->
                val f = File(dir, if (plan is Plan.FullApk) "download.apk" else "step-$i.emran")
                val conn = (URL(r.url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                }
                try {
                    if (conn.responseCode != HttpURLConnection.HTTP_OK) error("HTTP ${conn.responseCode}")
                    val digest = MessageDigest.getInstance("SHA-256")
                    conn.inputStream.use { input ->
                        f.outputStream().buffered(1 shl 16).use { o ->
                            val buf = ByteArray(1 shl 16)
                            while (true) {
                                val n = input.read(buf); if (n < 0) break
                                o.write(buf, 0, n); digest.update(buf, 0, n); done += n
                                progress((done.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                    if (r.size > 0 && f.length() != r.size) error("size")
                    if (r.sha256 != null && digest.digest().toHex() != r.sha256) error("sha")
                } finally {
                    conn.disconnect()
                }
                out += f
            }
            out
        } catch (e: Exception) {
            android.util.Log.w("UpdateChecker", "download failed", e)
            out.forEach { it.delete() }
            dir.listFiles()?.filter { it.name.startsWith("step-") || it.name == "download.apk" }?.forEach { it.delete() }
            null
        }
    }

    internal fun sha256(file: File): String {
        val d = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { s ->
            val buf = ByteArray(1 shl 16)
            while (true) { val n = s.read(buf); if (n < 0) break; d.update(buf, 0, n) }
        }
        return d.digest().toHex()
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
