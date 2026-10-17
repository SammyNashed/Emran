package io.github.sammynashed.emran.data.repository

import android.content.Context
import io.github.sammynashed.emran.data.QuranChapter
import io.github.sammynashed.emran.data.QuranVersesResponse
import io.github.sammynashed.emran.data.QuranVerse
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class QuranRepository(private val context: Context) {

    // Using a class structure matching the single quran.json response.
    // The response is a list of Chapter objects that contain a 'verses' list.
    data class LocalQuranChapter(
        val id: Int,
        val name: String,
        val transliteration: String,
        val type: String,
        val total_verses: Int,
        val verses: List<QuranVerse>
    )

    companion object {
        @Volatile
        private var cachedData: List<LocalQuranChapter>? = null
        
        @Volatile
        private var pageMap: Map<String, Int>? = null

        @Volatile
        private var isPrewarming = false

        private val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        suspend fun prewarm(context: Context) {
            if (cachedData != null) return
            if (isPrewarming) return
            isPrewarming = true
            withContext(Dispatchers.IO) {
                try {
                    val repo = QuranRepository(context.applicationContext)
                    repo.loadData()
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isPrewarming = false
                }
            }
        }
    }

    private suspend fun loadPageMap(): Map<String, Int> {
        val cached = pageMap
        if (cached != null) return cached
        return withContext(Dispatchers.IO) {
            try {
                val type = Types.newParameterizedType(Map::class.java, java.lang.String::class.java, java.lang.Object::class.java)
                val adapter = moshi.adapter<Map<String, Any>>(type)
                val inputStream = context.assets.open("pages.json")
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                val rawMap = adapter.fromJson(jsonString) ?: emptyMap()
                
                val map = rawMap.mapValues { (_, value) ->
                    when (value) {
                        is Number -> value.toInt()
                        is String -> value.toIntOrNull() ?: 1
                        else -> 1
                    }
                }
                pageMap = map
                map
            } catch (e: Exception) {
                e.printStackTrace()
                emptyMap()
            }
        }
    }

    private suspend fun loadData(): List<LocalQuranChapter> {
        val cached = cachedData
        if (cached != null) return cached
        return withContext(Dispatchers.IO) {
            val type = Types.newParameterizedType(List::class.java, LocalQuranChapter::class.java)
            val adapter = moshi.adapter<List<LocalQuranChapter>>(type)
            val inputStream = context.assets.open("quran.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val data = adapter.fromJson(jsonString) ?: emptyList()
            
            val pMap = loadPageMap()
            val enrichedData = data.map { chapter ->
                chapter.copy(
                    verses = chapter.verses.map { verse ->
                        val key = "${chapter.id}_${verse.id}"
                        val pageNum = pMap[key] ?: 1
                        verse.copy(page = pageNum)
                    }
                )
            }
            cachedData = enrichedData
            enrichedData
        }
    }

    suspend fun getChapters(): List<QuranChapter> {
        return loadData().map { localChap ->
            QuranChapter(
                id = localChap.id,
                name = localChap.name,
                transliteration = localChap.transliteration,
                type = localChap.type,
                total_verses = localChap.total_verses,
                link = ""
            )
        }
    }

    suspend fun getSurahVerses(surahId: Int): QuranVersesResponse {
        val chapter = loadData().firstOrNull { it.id == surahId }
            ?: throw Exception("Surah not found")
        
        return QuranVersesResponse(
            id = chapter.id,
            name = chapter.name,
            verses = chapter.verses
        )
    }
}
