package io.github.sammynashed.emran.data.hadith

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A saved place: a hadith, or a dhikr from the adhkar folders.
 *
 * Stored as a reference, never as text: the words stay in the pack or asset they
 * came from, so a bookmark costs bytes rather than kilobytes, and a corrected
 * text is corrected everywhere.
 */
sealed class Bookmark {
    abstract val savedAt: Long
    abstract val key: String

    data class HadithRef(
        val bookId: String,
        val chapterId: Int,
        val number: Int,
        override val savedAt: Long
    ) : Bookmark() {
        override val key get() = hadithKey(bookId, chapterId, number)
    }

    data class DhikrRef(
        val folderId: String,
        /** The item's `id`, or its position in the folder when it has none. */
        val itemKey: String,
        override val savedAt: Long
    ) : Bookmark() {
        override val key get() = dhikrKey(folderId, itemKey)
    }

    /** Where the reader stopped in the Qur'an: one ayah. Kept apart from the automatic "resume". */
    data class AyahRef(
        val surah: Int,
        val ayah: Int,
        override val savedAt: Long
    ) : Bookmark() {
        override val key get() = ayahKey(surah, ayah)
    }

    companion object {
        fun hadithKey(bookId: String, chapterId: Int, number: Int) = "h|$bookId|$chapterId|$number"
        fun dhikrKey(folderId: String, itemKey: String) = "a|$folderId|$itemKey"
        fun ayahKey(surah: Int, ayah: Int) = "q|$surah|$ayah"
    }
}

/** Bookmarks in SharedPreferences: one small string per entry, no schema to migrate. */
object HadithBookmarks {
    private const val PREFS = "emran_bookmarks"
    private const val KEY = "entries"

    private val _all = MutableStateFlow<List<Bookmark>>(emptyList())
    val all: StateFlow<List<Bookmark>> = _all.asStateFlow()
    @Volatile private var loaded = false

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val set = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(KEY, emptySet()).orEmpty()
            _all.value = set.mapNotNull(::parse).sortedByDescending { it.savedAt }
            loaded = true
        }
    }

    fun isBookmarked(key: String) = _all.value.any { it.key == key }

    fun toggleHadith(context: Context, bookId: String, chapterId: Int, number: Int) =
        toggle(context, Bookmark.hadithKey(bookId, chapterId, number)) {
            Bookmark.HadithRef(bookId, chapterId, number, System.currentTimeMillis())
        }

    fun toggleDhikr(context: Context, folderId: String, itemKey: String) =
        toggle(context, Bookmark.dhikrKey(folderId, itemKey)) {
            Bookmark.DhikrRef(folderId, itemKey, System.currentTimeMillis())
        }

    fun toggleAyah(context: Context, surah: Int, ayah: Int) =
        toggle(context, Bookmark.ayahKey(surah, ayah)) {
            Bookmark.AyahRef(surah, ayah, System.currentTimeMillis())
        }

    /** Drops a dhikr's bookmark if there is one (the user deleted one of their own adhkar). */
    fun removeDhikr(context: Context, folderId: String, itemKey: String) {
        ensureLoaded(context)
        val key = Bookmark.dhikrKey(folderId, itemKey)
        if (_all.value.any { it.key == key }) persist(context, _all.value.filterNot { it.key == key })
    }

    fun remove(context: Context, bookmark: Bookmark) {
        ensureLoaded(context)
        persist(context, _all.value.filterNot { it.key == bookmark.key })
    }

    private fun toggle(context: Context, key: String, create: () -> Bookmark) {
        ensureLoaded(context)
        val cur = _all.value
        persist(context, if (cur.any { it.key == key }) cur.filterNot { it.key == key } else listOf(create()) + cur)
    }

    @Synchronized
    private fun persist(context: Context, list: List<Bookmark>) {
        _all.value = list
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY, list.map(::serialize).toSet()).apply()
    }

    private fun serialize(b: Bookmark) = when (b) {
        is Bookmark.HadithRef -> "h|${b.bookId}|${b.chapterId}|${b.number}|${b.savedAt}"
        is Bookmark.DhikrRef -> "a|${b.folderId}|${b.itemKey}|${b.savedAt}"
        is Bookmark.AyahRef -> "q|${b.surah}|${b.ayah}|${b.savedAt}"
    }

    private fun parse(s: String): Bookmark? = try {
        val p = s.split('|')
        when (p[0]) {
            "h" -> Bookmark.HadithRef(p[1], p[2].toInt(), p[3].toInt(), p[4].toLong())
            "a" -> Bookmark.DhikrRef(p[1], p[2], p[3].toLong())
            "q" -> Bookmark.AyahRef(p[1].toInt(), p[2].toInt(), p[3].toLong())
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}
