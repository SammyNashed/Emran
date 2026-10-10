package io.github.sammynashed.emran.data.hadith

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import kotlin.coroutines.coroutineContext

/** One entry of `assets/hadith/manifest.json`: a book, bundled or downloadable. */
data class HadithBook(
    val id: String,
    val titleEn: String,
    val titleAr: String,
    /** Shipped inside the APK. Everything else is an optional download. */
    val bundled: Boolean,
    val chapters: Int,
    val hadiths: Int,
    /** Size of the pack file, which is also what a download costs. */
    val bytes: Long,
    val sha256: String
) {
    fun title(lang: String) = if (lang == "ar") titleAr else titleEn
}

class HadithHit(val bookId: String, val chapter: HadithChapter, val hadith: Hadith)

/**
 * Entry point for reading hadith.
 *
 * Memory: only chapter tables stay resident. At most [CHAPTER_CACHE] decoded
 * chapters are kept (a chapter is tens of KB of text), so opening every book and
 * scrolling around costs a few MB, not the tens of MB the raw JSON would.
 */
object HadithLibrary {
    private const val CHAPTER_CACHE = 4
    private const val ASSET_DIR = "hadith"

    @Volatile private var books: List<HadithBook>? = null
    private val packs = HashMap<String, HadithPack>()

    private val chapterCache = object : LinkedHashMap<String, List<Hadith>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Hadith>>?) =
            size > CHAPTER_CACHE
    }

    fun packDir(context: Context): File = File(context.filesDir, ASSET_DIR).apply { mkdirs() }
    fun packFile(context: Context, id: String) = File(packDir(context), "$id.hdp")

    fun books(context: Context): List<HadithBook> {
        books?.let { return it }
        synchronized(this) {
            books?.let { return it }
            val text = context.assets.open("$ASSET_DIR/manifest.json").bufferedReader().use { it.readText() }
            val arr = JSONArray(text)
            val list = List(arr.length()) {
                val o = arr.getJSONObject(it)
                HadithBook(
                    id = o.getString("id"),
                    titleEn = o.getString("titleEn"),
                    titleAr = o.getString("titleAr"),
                    bundled = o.getBoolean("bundled"),
                    chapters = o.getInt("chapters"),
                    hadiths = o.getInt("hadiths"),
                    bytes = o.getLong("bytes"),
                    sha256 = o.getString("sha256")
                )
            }
            books = list
            return list
        }
    }

    fun book(context: Context, id: String): HadithBook? = books(context).firstOrNull { it.id == id }

    fun isInstalled(context: Context, book: HadithBook): Boolean =
        book.bundled || packFile(context, book.id).let { it.isFile && it.length() == book.bytes }

    fun installedBooks(context: Context): List<HadithBook> = books(context).filter { isInstalled(context, it) }

    /** Opens (and keeps open) the pack for [id]; null when it is not installed or unreadable. */
    @Synchronized
    fun pack(context: Context, id: String): HadithPack? {
        packs[id]?.let { return it }
        val book = book(context, id) ?: return null
        if (!isInstalled(context, book)) return null
        return try {
            val source: PackSource = if (book.bundled) {
                AssetPackSource(context.applicationContext, "$ASSET_DIR/$id.hdp")
            } else {
                FilePackSource(packFile(context, id))
            }
            HadithPack.open(id, source).also { packs[id] = it }
        } catch (e: Exception) {
            android.util.Log.e("HadithLibrary", "cannot open pack $id", e)
            null
        }
    }

    /** Drops the open handle for [id]; call before deleting its file. */
    @Synchronized
    fun release(id: String) {
        packs.remove(id)?.close()
        chapterCache.keys.removeAll { it.startsWith("$id/") }
    }

    fun chapter(context: Context, bookId: String, chapterId: Int): List<Hadith> {
        val key = "$bookId/$chapterId"
        synchronized(chapterCache) { chapterCache[key]?.let { return it } }
        val pack = pack(context, bookId) ?: return emptyList()
        val ch = pack.chapter(chapterId) ?: return emptyList()
        val loaded = pack.load(ch)
        synchronized(chapterCache) { chapterCache[key] = loaded }
        return loaded
    }

    fun hadith(context: Context, bookId: String, chapterId: Int, number: Int): Hadith? =
        chapter(context, bookId, chapterId).firstOrNull { it.number == number }

    // ---- search ----------------------------------------------------------

    /**
     * Searches every installed book, calling [onProgress] with the hits so far and
     * how many books have been scanned. Runs chapter by chapter so memory stays
     * flat regardless of how many collections are installed, and checks for
     * cancellation between chapters so a new keystroke stops the old scan.
     *
     * - digits only: jumps to that hadith number in each installed book
     * - contains Arabic letters: matches on the Arabic with diacritics ignored
     * - otherwise: case-insensitive match on the English
     *
     * Every word must appear (in any order) in the same hadith.
     */
    suspend fun search(
        context: Context,
        rawQuery: String,
        limit: Int = 120,
        onProgress: (hits: List<HadithHit>, scanned: Int, total: Int) -> Unit
    ) = withContext(Dispatchers.Default) {
        val query = rawQuery.trim()
        val installed = installedBooks(context)
        val hits = ArrayList<HadithHit>()
        if (query.length < 2 && !query.all { it.isDigit() }) {
            onProgress(hits, 0, installed.size)
            return@withContext
        }

        if (query.all { it.isDigit() }) {
            val n = query.toIntOrNull()
            if (n != null) {
                installed.forEachIndexed { idx, b ->
                    val pack = pack(context, b.id)
                    val ch = pack?.chapterOf(n)
                    if (pack != null && ch != null) {
                        chapter(context, b.id, ch.id).firstOrNull { it.number == n }
                            ?.let { hits += HadithHit(b.id, ch, it) }
                    }
                    onProgress(hits.toList(), idx + 1, installed.size)
                }
            }
            return@withContext
        }

        val arabic = query.any { it in '؀'..'ۿ' }
        val words = (if (arabic) ArabicText.normalize(query) else query.lowercase())
            .split(' ', '\t', '\n').filter { it.isNotEmpty() }
        if (words.isEmpty()) return@withContext

        for ((idx, b) in installed.withIndex()) {
            val pack = pack(context, b.id) ?: continue
            for (ch in pack.chapters) {
                coroutineContext.ensureActive()
                // Search decodes chapters itself rather than going through the cache:
                // a full scan would evict whatever the reader has open.
                for (h in pack.load(ch)) {
                    val hay = if (arabic) ArabicText.normalize(h.arabic) else h.english.lowercase()
                    if (words.all { hay.contains(it) }) {
                        hits += HadithHit(b.id, ch, h)
                        if (hits.size >= limit) {
                            onProgress(hits.toList(), idx + 1, installed.size)
                            return@withContext
                        }
                    }
                }
            }
            onProgress(hits.toList(), idx + 1, installed.size)
        }
    }

    // ---- hadith of the day ----------------------------------------------

    private const val PREFS = "emran_hadith"

    /**
     * A short hadith from the bundled collections, the same one all day.
     *
     * Chosen by date so it needs no network and no stored list; the pick is
     * cached so relaunching does not decode a chapter again. Short ones only, so
     * the home card stays a card.
     */
    fun hadithOfTheDay(context: Context, epochDay: Long = java.time.LocalDate.now().toEpochDay()): HadithHit? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getLong("hotd_day", -1) == epochDay) {
            val bookId = prefs.getString("hotd_book", null)
            val ch = prefs.getInt("hotd_chapter", -1)
            val num = prefs.getInt("hotd_number", -1)
            if (bookId != null && ch >= 0) {
                val pack = pack(context, bookId)
                val chapter = pack?.chapter(ch)
                val h = hadith(context, bookId, ch, num)
                if (chapter != null && h != null) return HadithHit(bookId, chapter, h)
            }
        }
        val pool = listOf("nawawi40", "qudsi40", "riyad", "riyad")
        val bookId = pool[(epochDay % pool.size).toInt()]
        val pack = pack(context, bookId) ?: return null
        val chapter = pack.chapters[((epochDay * 7919) % pack.chapters.size).toInt()]
        val list = chapter(context, bookId, chapter.id)
        if (list.isEmpty()) return null
        val start = ((epochDay * 104729) % list.size).toInt()
        val pick = (0 until list.size).map { list[(start + it) % list.size] }
            .firstOrNull { it.arabic.length in 40..420 } ?: list[start]
        prefs.edit()
            .putLong("hotd_day", epochDay).putString("hotd_book", bookId)
            .putInt("hotd_chapter", chapter.id).putInt("hotd_number", pick.number).apply()
        return HadithHit(bookId, chapter, pick)
    }
}

object ArabicText {
    /**
     * Folds text so a search matches however the user typed it: harakat and
     * tatweel removed, the alef family collapsed to a bare alef, ya/alef-maqsura
     * and ta-marbuta/ha unified. Applied to both the query and the text.
     */
    fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            when {
                c in 'ً'..'ٟ' || c == 'ٰ' || c == 'ـ' || c == 'ۖ' -> Unit
                c == 'أ' || c == 'إ' || c == 'آ' || c == 'ٱ' -> sb.append('ا')
                c == 'ى' -> sb.append('ي')
                c == 'ة' -> sb.append('ه')
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}
