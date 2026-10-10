package io.github.sammynashed.emran.data.hadith

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

sealed class PackState {
    object Idle : PackState()
    data class Downloading(val fraction: Float) : PackState()
    data class Failed(val reason: String) : PackState()
}

/**
 * Downloads the optional hadith collections.
 *
 * The app ships only the small books; the large ones are fetched when the user
 * asks, so the install stays small and nobody pays storage for a collection they
 * will not read. A pack is stored exactly as downloaded (it is already
 * compressed), so what the download costs is what it occupies.
 *
 * The transfer goes over plain HttpURLConnection and only ever runs on an
 * explicit tap. A pack is verified against the SHA-256 in the bundled manifest
 * before it is moved into place, so a truncated or tampered file is discarded.
 */
object HadithPackStore {
    /** Where packs are published. Overridable for testing via the "pack_base_url" pref. */
    const val DEFAULT_BASE_URL = "https://github.com/SammyNashed/Emran/releases/download/hadith-packs-v1/"

    // App-scoped so leaving the Hadith screen does not abandon a download half way.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(context: Context, book: HadithBook) {
        val app = context.applicationContext
        scope.launch { download(app, book) }
    }

    private val _states = MutableStateFlow<Map<String, PackState>>(emptyMap())
    val states: StateFlow<Map<String, PackState>> = _states.asStateFlow()

    private fun set(id: String, state: PackState) {
        _states.value = _states.value + (id to state)
    }

    fun baseUrl(context: Context): String =
        context.getSharedPreferences("emran_hadith", Context.MODE_PRIVATE)
            .getString("pack_base_url", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL

    suspend fun download(context: Context, book: HadithBook): Boolean = withContext(Dispatchers.IO) {
        if (_states.value[book.id] is PackState.Downloading) return@withContext false
        val target = HadithLibrary.packFile(context, book.id)
        val part = File(target.parentFile, "${book.id}.hdp.part")
        set(book.id, PackState.Downloading(0f))
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(baseUrl(context) + "${book.id}.hdp").openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                instanceFollowRedirects = true
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                error("server answered ${conn.responseCode}")
            }
            val total = book.bytes.toFloat()
            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        set(book.id, PackState.Downloading((done / total).coerceIn(0f, 1f)))
                    }
                }
            }
            val hex = digest.digest().joinToString("") { "%02x".format(it) }
            if (done != book.bytes || !hex.equals(book.sha256, ignoreCase = true)) {
                error("the file did not match what was expected")
            }
            HadithLibrary.release(book.id)
            if (target.exists()) target.delete()
            check(part.renameTo(target)) { "could not store the file" }
            set(book.id, PackState.Idle)
            true
        } catch (e: Exception) {
            part.delete()
            set(book.id, PackState.Failed(e.message ?: e.javaClass.simpleName))
            false
        } finally {
            conn?.disconnect()
        }
    }

    fun delete(context: Context, book: HadithBook) {
        if (book.bundled) return
        HadithLibrary.release(book.id)
        HadithLibrary.packFile(context, book.id).delete()
        set(book.id, PackState.Idle)
    }
}
