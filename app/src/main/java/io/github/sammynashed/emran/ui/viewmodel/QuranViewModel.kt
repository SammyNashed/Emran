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

    fun loadChapters() {
        if (cachedChapters != null) {
            _uiState.value = QuranUiState.SuccessChapters(cachedChapters!!)
            return
        }

        viewModelScope.launch {
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

    fun loadSurah(chapter: QuranChapter) {
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
