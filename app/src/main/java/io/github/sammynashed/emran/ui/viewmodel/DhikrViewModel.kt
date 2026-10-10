package io.github.sammynashed.emran.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.sammynashed.emran.data.local.db.AppDatabase
import io.github.sammynashed.emran.data.local.model.CustomTasbeeh
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

class DhikrViewModel(application: Application) : AndroidViewModel(application) {
    private val tasbeehDao = AppDatabase.getDatabase(application).tasbeehDao()

    init {
        viewModelScope.launch {
            tasbeehDao.resetAllCounts()
        }
    }

    // Session-persisted states that reset on app exit
    val presetCounts = mutableStateListOf(0, 0, 0, 0)
    val fridayCount = mutableStateOf(0)
    val selectedIndex = mutableStateOf(0)
    val activePresetPhraseIndex = mutableStateOf(0)

    val customTasbeehs: StateFlow<List<CustomTasbeeh>> = tasbeehDao.getAllTasbeehs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _activeCustomTasbeeh = MutableStateFlow<CustomTasbeeh?>(null)
    val activeCustomTasbeeh: StateFlow<CustomTasbeeh?> = _activeCustomTasbeeh

    fun selectCustomTasbeeh(tasbeeh: CustomTasbeeh?) {
        _activeCustomTasbeeh.value = tasbeeh
    }

    fun addCustomTasbeeh(arabic: String, transliteration: String, target: Int, colorHex: String) {
        viewModelScope.launch {
            val newTasbeeh = CustomTasbeeh(
                textArabic = arabic.trim(),
                textTransliteration = transliteration.trim(),
                count = 0,
                target = if (target > 0) target else 33,
                colorHex = colorHex
            )
            val insertedId = tasbeehDao.insertTasbeeh(newTasbeeh)
            // Auto-select the newly created one!
            _activeCustomTasbeeh.value = newTasbeeh.copy(id = insertedId.toInt())
        }
    }

    fun incrementCount(tasbeeh: CustomTasbeeh) {
        viewModelScope.launch {
            val nextCount = tasbeeh.count + 1
            val updated = tasbeeh.copy(count = nextCount)
            tasbeehDao.updateCount(tasbeeh.id, nextCount)
            if (_activeCustomTasbeeh.value?.id == tasbeeh.id) {
                _activeCustomTasbeeh.value = updated
            }
        }
    }

    fun resetCount(tasbeeh: CustomTasbeeh) {
        viewModelScope.launch {
            val updated = tasbeeh.copy(count = 0)
            tasbeehDao.updateCount(tasbeeh.id, 0)
            if (_activeCustomTasbeeh.value?.id == tasbeeh.id) {
                _activeCustomTasbeeh.value = updated
            }
        }
    }

    fun deleteTasbeeh(tasbeeh: CustomTasbeeh) {
        viewModelScope.launch {
            tasbeehDao.deleteTasbeeh(tasbeeh)
            if (_activeCustomTasbeeh.value?.id == tasbeeh.id) {
                _activeCustomTasbeeh.value = null
            }
        }
    }
}
