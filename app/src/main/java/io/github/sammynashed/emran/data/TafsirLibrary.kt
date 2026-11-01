package io.github.sammynashed.emran.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Every tafsir Emran can show, all offline.
 *
 * Built in: Al-Mukhtasar (see [AyahTafsir]) and the small packs in assets/tafsir/. Added by the user: a pack
 * file (.emtafsir, a zip of meta.json and one <surah>.json per surah) picked from storage, kept in the app's own
 * files/tafsir/ and removable again. Nothing here touches the network; packs reach the phone as ordinary files
 * (the Telegram channel, Drop). The pack format is documented in tools/build_tafsir_pack.py.
 *
 * The user's choice is remembered per app language, so Arabic and English can differ.
 */
object TafsirLibrary {
    const val MUKHTASAR_AR = "mukhtasar-ar"
    const val MUKHTASAR_EN = "mukhtasar-en"

    class Edition(
        val id: String,
        val lang: String,
        val nameAr: String,
        val nameEn: String,
        /** Shipped inside the app: cannot be removed. */
        val builtIn: Boolean,
        val sizeBytes: Long,
    ) {
        fun name(ar: Boolean) = if (ar) nameAr else nameEn
    }

    class Text(val text: String, val group: IntRange?)

    /** Bumped whenever the installed set or the choice changes, so screens reading them recompose. */
    val version = mutableIntStateOf(0)

    private const val PREFS = "emran_tafsir"
    private val cache = object : android.util.LruCache<String, JSONObject>(4) {}

    private fun dir(context: Context) = File(context.filesDir, "tafsir")

    fun editions(context: Context): List<Edition> {
        val out = mutableListOf(
            Edition(MUKHTASAR_AR, "ar", "المختصر في تفسير القرآن الكريم", "Al-Mukhtasar", true, 0),
            Edition(MUKHTASAR_EN, "en", "المختصر (إنجليزي)", "Al-Mukhtasar (English)", true, 0),
        )
        val am = context.applicationContext.assets
        am.list("tafsir")?.sorted()?.forEach { id ->
            runCatching {
                val m = JSONObject(am.open("tafsir/$id/meta.json").bufferedReader().use { it.readText() })
                out += Edition(id, m.getString("lang"), m.getString("nameAr"), m.getString("nameEn"), true, 0)
            }
        }
        dir(context).listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.forEach { d ->
            runCatching {
                val m = JSONObject(File(d, "meta.json").readText())
                val size = d.listFiles()?.sumOf { it.length() } ?: 0L
                out += Edition(d.name, m.getString("lang"), m.getString("nameAr"), m.getString("nameEn"), false, size)
            }
        }
        return out
    }

    /** The editions in [lang] first, in their own order, then the rest: someone reading in English may still want Arabic. */
    fun editionsFor(context: Context, lang: String): List<Edition> =
        editions(context).let { all -> all.filter { it.lang == lang } + all.filter { it.lang != lang } }

    fun selectedId(context: Context, lang: String): String {
        val want = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("pick_$lang", null) ?: if (lang == "ar") MUKHTASAR_AR else MUKHTASAR_EN
        // A pack that was deleted falls back to Al-Mukhtasar.
        return if (editions(context).any { it.id == want }) want else if (lang == "ar") MUKHTASAR_AR else MUKHTASAR_EN
    }

    fun select(context: Context, lang: String, id: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("pick_$lang", id).apply()
        version.intValue++
    }

    /** The explanation of one ayah in edition [id], or null when that edition has nothing for it. Call off the main thread. */
    fun get(context: Context, id: String, surah: Int, ayah: Int): Text? {
        val app = context.applicationContext
        if (id == MUKHTASAR_AR || id == MUKHTASAR_EN) {
            val e = AyahTafsir.get(app, surah, ayah) ?: return null
            val t = (if (id == MUKHTASAR_AR) e.tafsirAr else e.tafsirEn).ifBlank { return null }
            return Text(t, e.group)
        }
        val o = surahFile(app, id, surah) ?: return null
        val ts = o.optJSONArray("t") ?: return null
        val i = ayah - 1
        if (i !in 0 until ts.length()) return null
        val g = o.optJSONObject("g")?.optJSONArray("$ayah")?.let { it.getInt(0)..it.getInt(1) }
        val text = ts.optString(i).ifBlank { g?.let { ts.optString(it.first - 1) }.orEmpty() }
        return if (text.isBlank()) null else Text(text, g)
    }

    private fun surahFile(context: Context, id: String, s: Int): JSONObject? {
        val key = "$id/$s"
        cache.get(key)?.let { return it }
        val file = File(File(dir(context), id), "$s.json")
        val raw = runCatching {
            if (file.exists()) file.readText()
            else context.assets.open("tafsir/$id/$s.json").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return null
        return runCatching { JSONObject(raw) }.getOrNull()?.also { cache.put(key, it) }
    }

    /**
     * Installs a pack file the user picked. Only meta.json and <number>.json entries are accepted, written under an
     * id made of [a-z0-9-], so a hostile zip cannot write elsewhere. Returns the edition, or null if the file is not
     * a usable pack. Call off the main thread.
     */
    fun import(context: Context, uri: Uri): Edition? {
        val app = context.applicationContext
        val tmp = File(dir(app), ".incoming-${System.nanoTime()}").apply { mkdirs() }
        try {
            app.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    var budget = 400L * 1024 * 1024
                    while (true) {
                        val e = zip.nextEntry ?: break
                        val name = e.name.substringAfterLast('/')
                        if (e.isDirectory || !(name == "meta.json" || Regex("""\d{1,3}\.json""").matches(name))) continue
                        val out = File(tmp, name)
                        out.outputStream().use { o ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = zip.read(buf)
                                if (n < 0) break
                                budget -= n
                                if (budget < 0) return null
                                o.write(buf, 0, n)
                            }
                        }
                    }
                }
            } ?: return null
            val meta = JSONObject(File(tmp, "meta.json").readText())
            val id = meta.getString("id").lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '-' }
            if (id.isEmpty() || id == MUKHTASAR_AR || id == MUKHTASAR_EN) return null
            // At least the first and last surah must be there and parse.
            JSONObject(File(tmp, "1.json").readText()).getJSONArray("t")
            JSONObject(File(tmp, "114.json").readText()).getJSONArray("t")
            // A pack with the id of a built-in one is not installed twice.
            if (app.assets.list("tafsir")?.contains(id) == true) return editions(app).firstOrNull { it.id == id }
            val target = File(dir(app), id)
            target.deleteRecursively()
            if (!tmp.renameTo(target)) return null
            (0..114).forEach { cache.remove("$id/$it") }
            version.intValue++
            return editions(app).firstOrNull { it.id == id }
        } catch (_: Exception) {
            return null
        } finally {
            tmp.deleteRecursively()
        }
    }

    fun delete(context: Context, id: String) {
        val app = context.applicationContext
        if (editions(app).firstOrNull { it.id == id }?.builtIn != false) return
        File(dir(app), id).deleteRecursively()
        (0..114).forEach { cache.remove("$id/$it") }
        version.intValue++
    }
}
