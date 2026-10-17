package io.github.sammynashed.emran.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.sammynashed.emran.data.QuranChapter
import io.github.sammynashed.emran.data.QuranVerse
import io.github.sammynashed.emran.data.repository.QuranRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class QuranUiState {
    object Loading : QuranUiState()
    data class SuccessChapters(val chapters: List<QuranChapter>) : QuranUiState()
    data class SuccessVerses(val chapter: QuranChapter, val verses: List<QuranVerse>) : QuranUiState()
    data class Error(val message: String) : QuranUiState()
}

class QuranViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = QuranRepository(application)

    private val _uiState = MutableStateFlow<QuranUiState>(QuranUiState.Loading)
    val uiState: StateFlow<QuranUiState> = _uiState

    private var cachedChapters: List<QuranChapter>? = null

    private val prefs = application.getSharedPreferences("quran_prefs", Context.MODE_PRIVATE)

    private val _bookmarkedChapterId = MutableStateFlow<Int>(prefs.getInt("bookmark_chapter_id", -1))
    val bookmarkedChapterId: StateFlow<Int> = _bookmarkedChapterId

    private val _bookmarkedScrollPos = MutableStateFlow<Int>(prefs.getInt("bookmark_scroll_pos", 0))
    val bookmarkedScrollPos: StateFlow<Int> = _bookmarkedScrollPos

    private val _isPaginatedMode = MutableStateFlow<Boolean>(prefs.getBoolean("is_paginated_mode", true))
    val isPaginatedMode: StateFlow<Boolean> = _isPaginatedMode

    private val _isContinuousMode = MutableStateFlow<Boolean>(prefs.getBoolean("is_continuous_mode", false))
    val isContinuousMode: StateFlow<Boolean> = _isContinuousMode

    private val _allChapters = MutableStateFlow<List<QuranChapter>>(emptyList())
    val allChapters: StateFlow<List<QuranChapter>> = _allChapters

    init {
        loadChapters()
    }

    fun setPaginatedMode(isPaginated: Boolean) {
        prefs.edit().putBoolean("is_paginated_mode", isPaginated).apply()
        _isPaginatedMode.value = isPaginated
    }

    fun setContinuousMode(enabled: Boolean) {
        prefs.edit().putBoolean("is_continuous_mode", enabled).apply()
        _isContinuousMode.value = enabled
        // If we are currently reading a Surah, reload it with the new mode
        val state = _uiState.value
        if (state is QuranUiState.SuccessVerses) {
            loadSurah(state.chapter)
        }
    }

    /** The first chapter-list load; opening a surah from outside waits for it so it can't land after (and replace) the surah. */
    private var chaptersJob: kotlinx.coroutines.Job? = null

    fun loadChapters() {
        if (cachedChapters != null) {
            _uiState.value = QuranUiState.SuccessChapters(cachedChapters!!)
            return
        }

        chaptersJob = viewModelScope.launch {
            _uiState.value = QuranUiState.Loading
            try {
                val chapters = repository.getChapters()
                cachedChapters = chapters
                _allChapters.value = chapters
                _uiState.value = QuranUiState.SuccessChapters(chapters)
            } catch (e: Exception) {
                _uiState.value = QuranUiState.Error("Failed to fetch data: ${e.message}")
            }
        }
    }

    /** An ayah the reader should open on (set by ayah search); consumed once the reader has jumped. */
    private val _jumpToAyah = MutableStateFlow<Int?>(null)
    val jumpToAyah: StateFlow<Int?> = _jumpToAyah
    fun consumeJump() { _jumpToAyah.value = null }

    /** (surah, ayah) the reader marks after a search jump, until the reader turns away from it. */
    private val _focusAyah = MutableStateFlow<Pair<Int, Int>?>(null)
    val focusAyah: StateFlow<Pair<Int, Int>?> = _focusAyah
    fun clearFocus() { _focusAyah.value = null }

    fun loadSurah(chapter: QuranChapter, ayah: Int? = null) {
        _jumpToAyah.value = ayah
        _focusAyah.value = ayah?.let { chapter.id to it }
        viewModelScope.launch {
            _uiState.value = QuranUiState.Loading
            try {
                val chapters = cachedChapters ?: repository.getChapters()
                if (cachedChapters == null) {
                    cachedChapters = chapters
                    _allChapters.value = chapters
                }

                val isContinuous = _isContinuousMode.value
                val versesList = mutableListOf<QuranVerse>()

                if (isContinuous) {
                    // Load all chapters for continuous reading across the whole Quran
                    for (chap in chapters) {
                        val response = repository.getSurahVerses(chap.id)
                        val enriched = response.verses.map { verse ->
                            verse.copy(chapterId = chap.id, chapterName = chap.name)
                        }
                        versesList.addAll(enriched)
                    }
                } else {
                    val response = repository.getSurahVerses(chapter.id)
                    val enriched = response.verses.map { verse ->
                        verse.copy(chapterId = chapter.id, chapterName = chapter.name)
                    }
                    versesList.addAll(enriched)
                }

                _uiState.value = QuranUiState.SuccessVerses(chapter, versesList)
            } catch (e: Exception) {
                _uiState.value = QuranUiState.Error("Failed to fetch surah: ${e.message}")
            }
        }
    }

    /** Opens the reader on one ayah (from a bookmark), marked until the reader turns away from it. */
    fun openAyah(surah: Int, ayah: Int?) {
        viewModelScope.launch {
            chaptersJob?.join()
            val chapters = cachedChapters ?: runCatching { repository.getChapters() }.getOrNull() ?: return@launch
            if (cachedChapters == null) {
                cachedChapters = chapters
                _allChapters.value = chapters
            }
            chapters.firstOrNull { it.id == surah }?.let { loadSurah(it, ayah) }
        }
    }

    /** Opens a whole surah (the Friday al-Kahf reminder): where the reader left it, or its start. */
    fun openSurah(surah: Int) = openAyah(surah, null)

    fun saveBookmark(chapterId: Int, scrollPos: Int) {
        prefs.edit()
            .putInt("bookmark_chapter_id", chapterId)
            .putInt("bookmark_scroll_pos", scrollPos)
            .apply()
        _bookmarkedChapterId.value = chapterId
        _bookmarkedScrollPos.value = scrollPos
    }

    /**
     * Marks a surah as read, once its final page has been reached.
     *
     * Progress is a *set* of surah ids rather than a counter, which is what makes
     * re-reading honest: opening al-Fatihah twenty times still counts once,
     * because adding an id already in the set changes nothing.
     *
     * Reaching all 114 completes a khatmah. The set is then cleared and
     * [KEY_KHATMAH_COUNT] incremented, so the next reading starts from zero
     * while the finished ones are never lost -- someone on their third khatmah
     * sees "3 completed" alongside a fresh 0/114, rather than a bar stuck at
     * full forever or a count that silently resets to nothing.
     */
    /** This khatmah so far: surahs finished, the muṣḥaf pages they cover, and khatmas completed before. */
    data class Khatmah(val surahs: Int, val pages: Int, val totalPages: Int, val completed: Int)

    private val _khatmah = MutableStateFlow(Khatmah(0, 0, 604, prefs.getInt(KEY_KHATMAH_COUNT, 0)))
    val khatmah: StateFlow<Khatmah> = _khatmah

    /** Recounts from the saved set; pages shared by two finished surahs count once. */
    fun refreshKhatmah() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val done = prefs.getStringSet(KEY_SURAHS_DONE, emptySet())?.mapNotNull { it.toIntOrNull() } ?: emptyList()
            val pages = HashSet<Int>()
            var total = 604
            runCatching {
                val all = HashSet<Int>()
                for (id in 1..TOTAL_SURAHS) {
                    val ps = repository.getSurahVerses(id).verses.map { it.page }
                    all += ps
                    if (id in done) pages += ps
                }
                total = all.size.coerceAtLeast(1)
            }
            _khatmah.value = Khatmah(done.size, pages.size, total, prefs.getInt(KEY_KHATMAH_COUNT, 0))
        }
    }

    fun recordSurahCompleted(chapterId: Int) {
        if (chapterId !in 1..TOTAL_SURAHS) return

        val current = prefs.getStringSet(KEY_SURAHS_DONE, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (!current.add(chapterId.toString())) return  // already read this cycle

        val editor = prefs.edit()
        if (current.size >= TOTAL_SURAHS) {
            editor.putStringSet(KEY_SURAHS_DONE, emptySet())
            editor.putInt(KEY_KHATMAH_COUNT, prefs.getInt(KEY_KHATMAH_COUNT, 0) + 1)
        } else {
            editor.putStringSet(KEY_SURAHS_DONE, current)
        }
        editor.apply()
        refreshKhatmah()

        viewModelScope.launch {
            try {
                io.github.sammynashed.emran.glance.WidgetUpdater.updateAllWidgets(getApplication())
            } catch (e: Exception) {
                android.util.Log.e("QuranViewModel", "Failed to refresh widgets after Quran progress", e)
            }
        }
    }

    companion object {
        const val TOTAL_SURAHS = 114
        const val KEY_SURAHS_DONE = "surahs_completed"
        const val KEY_KHATMAH_COUNT = "khatmah_count"
    }
}
