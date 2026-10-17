package io.github.sammynashed.emran.data.assets

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * The user's own adhkar, kept as one more folder ("أذكاري") so they count, bookmark, copy, share
 * and search exactly like the bundled ones. Stored in `files/my_adhkar.json`, not Room: the
 * database is rebuilt from scratch whenever its shape changes, and these words must survive that.
 *
 * Each dhikr gets a permanent id when it is written ("u" + time), so a bookmark keeps pointing at
 * it after others are added, edited or deleted around it.
 */
object MyAdhkar {
    const val FOLDER_ID = "mine"

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter<List<AdhkarItem>>(Types.newParameterizedType(List::class.java, AdhkarItem::class.java))

    private val _items = MutableStateFlow<List<AdhkarItem>?>(null)
    /** Null until first read from disk. */
    val items: StateFlow<List<AdhkarItem>?> = _items

    private fun file(context: Context) = File(context.applicationContext.filesDir, "my_adhkar.json")

    @Synchronized
    fun load(context: Context): List<AdhkarItem> {
        _items.value?.let { return it }
        val list = runCatching { file(context).takeIf { it.exists() }?.readText()?.let(adapter::fromJson) }.getOrNull().orEmpty()
        _items.value = list
        return list
    }

    fun folder(context: Context) = AdhkarFolder(
        id = FOLDER_ID,
        titleEn = "My adhkar",
        titleAr = "أذكاري",
        icon = "Favorite",
        color = "#F48FB1",
        items = load(context),
        subtitleEn = "Your own words of remembrance",
        subtitleAr = "أذكار تكتبها بنفسك",
    )

    /**
     * Adds a new dhikr, or replaces the one with [id]. [translation] is optional: without it the
     * same words show in both languages.
     */
    @Synchronized
    fun save(context: Context, id: String?, text: String, translation: String, reps: Int, source: String) {
        val words = text.trim()
        val other = translation.trim()
        val note = source.trim().ifBlank { null }
        val item = AdhkarItem(
            textAr = words,
            textEn = other.ifBlank { words },
            reps = reps.coerceIn(1, 10_000),
            id = id ?: "u${System.currentTimeMillis()}",
            source = note,
            sourceAr = note,
        )
        val list = load(context)
        write(context, if (id != null && list.any { it.id == id }) list.map { if (it.id == id) item else it } else list + item)
    }

    @Synchronized
    fun delete(context: Context, id: String) {
        write(context, load(context).filterNot { it.id == id })
        // A deleted dhikr's bookmark would point at nothing.
        io.github.sammynashed.emran.data.hadith.HadithBookmarks.removeDhikr(context, FOLDER_ID, id)
    }

    private fun write(context: Context, list: List<AdhkarItem>) {
        val f = file(context)
        val tmp = File(f.path + ".tmp")
        tmp.writeText(adapter.toJson(list))
        tmp.renameTo(f)
        _items.value = list
    }

    /** The translation the user typed, if any (empty when they gave only one text). */
    fun translationOf(item: AdhkarItem) = if (item.textEn == item.textAr) "" else item.textEn
}
