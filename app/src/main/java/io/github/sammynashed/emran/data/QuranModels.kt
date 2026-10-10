package io.github.sammynashed.emran.data

/**
 * Data model for the offline Quran corpus in `assets/quran.json`.
 *
 * Emran does not perform any network I/O. These types used to double as Retrofit
 * response bodies for a remote Quran API; that path was removed along with the
 * INTERNET permission so the offline-first guarantee holds at the manifest level
 * rather than only by convention. [QuranRepository] parses the bundled asset into
 * these same shapes, so nothing downstream changed.
 */
data class QuranChapter(
    val id: Int,
    val name: String,
    val transliteration: String,
    val type: String,
    val total_verses: Int,
    val link: String
)

data class QuranVersesResponse(
    val id: Int,
    val name: String,
    val verses: List<QuranVerse>
)

data class QuranVerse(
    val id: Int,
    val text: String,
    val transliteration: String? = "",
    val page: Int = 0,
    val chapterId: Int = 0,
    val chapterName: String = ""
)
