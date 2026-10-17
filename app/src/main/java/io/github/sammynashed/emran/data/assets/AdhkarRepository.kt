package io.github.sammynashed.emran.data.assets

import android.content.Context
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A single supplication.
 *
 * Every field except [textAr], [textEn] and [reps] is optional so that older or
 * externally contributed `adhkar.json` files keep parsing. See
 * `docs/adhkar-format.md` for the contribution format.
 */
@JsonClass(generateAdapter = true)
data class AdhkarItem(
    val textAr: String,
    val textEn: String,
    val reps: Int,

    /** Stable key, used for per-item progress and deep links. */
    val id: String? = null,

    /**
     * Latin transliteration of [textAr], for users who do not read Arabic script
     * but want to recite rather than only read a translation.
     */
    val transliteration: String? = null,

    /**
     * Where the supplication comes from -- a surah and ayah reference, or the
     * hadith collection and number.
     *
     * This is not decoration. A du'a with no attribution is one the user cannot
     * verify, and for religious content that matters more than it would for
     * ordinary app copy.
     */
    val source: String? = null,
    val sourceAr: String? = null,

    /** The reward or benefit reported for this dhikr, where one is narrated. */
    val virtue: String? = null,
    val virtueAr: String? = null
) {
    fun sourceFor(lang: String): String? =
        if (lang == "ar") sourceAr ?: source else source

    fun virtueFor(lang: String): String? =
        if (lang == "ar") virtueAr ?: virtue else virtue
}

@JsonClass(generateAdapter = true)
data class AdhkarFolder(
    val id: String,
    val titleEn: String,
    val titleAr: String,
    val icon: String,
    val color: String,
    val items: List<AdhkarItem>,
    /** Optional short description shown under the folder title. */
    val subtitleEn: String? = null,
    val subtitleAr: String? = null,
    /**
     * `"hisn"` for chapters imported from Hisn al-Muslim, attributed to the book's
     * chapter rather than to a hadith number; null for the curated core. The
     * screen keeps the two apart so the looser attribution is never mistaken
     * for the verified kind.
     */
    val group: String? = null,
    /** For `group == "hisn"`: the [AdhkarCategory] this chapter is listed under. */
    val category: String? = null
) {
    val isHisn: Boolean get() = group == "hisn"
}

/** A themed bundle of Hisn al-Muslim chapters ("Food & Fasting", "Travel & Hajj", ...). */
@JsonClass(generateAdapter = true)
data class AdhkarCategory(
    val id: String,
    val titleEn: String,
    val titleAr: String,
    val icon: String,
    val color: String
)

@JsonClass(generateAdapter = true)
data class AdhkarRoot(
    val folders: List<AdhkarFolder>,
    val categories: List<AdhkarCategory> = emptyList(),
    /**
     * Bumped when the shape of the file changes so a future migration can tell
     * a v1 bundle from a v2 one. Absent in the original file, hence the default.
     */
    val schemaVersion: Int = 1
)

object AdhkarRepository {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    @Volatile
    private var cache: List<AdhkarFolder> = emptyList()

    @Volatile
    private var categoryCache: List<AdhkarCategory> = emptyList()

    suspend fun loadCategories(context: Context): List<AdhkarCategory> {
        loadAdhkar(context)
        return categoryCache
    }

    /**
     * Every folder: the user's own ("أذكاري", see [MyAdhkar]) first, then the bundled ones. The
     * user's folder is rebuilt on each call, so edits show everywhere (bookmarks, search) at once.
     */
    suspend fun loadAdhkar(context: Context): List<AdhkarFolder> = withContext(Dispatchers.IO) {
        listOf(MyAdhkar.folder(context)) + loadBundled(context)
    }

    private suspend fun loadBundled(context: Context): List<AdhkarFolder> = withContext(Dispatchers.IO) {
        if (cache.isNotEmpty()) return@withContext cache

        try {
            val jsonStr = context.assets.open("adhkar.json").bufferedReader().use { it.readText() }
            val adapter = moshi.adapter(AdhkarRoot::class.java)
            val root = adapter.fromJson(jsonStr)
            cache = root?.folders ?: emptyList()
            categoryCache = root?.categories ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.e("AdhkarRepository", "Failed to parse assets/adhkar.json", e)
            cache = emptyList()
        }
        cache
    }
}
