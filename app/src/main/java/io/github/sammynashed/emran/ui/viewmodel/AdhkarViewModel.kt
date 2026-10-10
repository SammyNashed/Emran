package io.github.sammynashed.emran.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.sammynashed.emran.data.assets.AdhkarCategory
import io.github.sammynashed.emran.data.assets.AdhkarFolder
import io.github.sammynashed.emran.data.assets.AdhkarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AdhkarViewModel(application: Application) : AndroidViewModel(application) {
    private val _folders = MutableStateFlow<List<AdhkarFolder>>(emptyList())
    val folders: StateFlow<List<AdhkarFolder>> = _folders.asStateFlow()

    private val _categories = MutableStateFlow<List<AdhkarCategory>>(emptyList())
    val categories: StateFlow<List<AdhkarCategory>> = _categories.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadAdhkar()
    }

    private fun loadAdhkar() {
        viewModelScope.launch {
            _isLoading.value = true
            _folders.value = AdhkarRepository.loadAdhkar(getApplication())
            _categories.value = AdhkarRepository.loadCategories(getApplication())
            _isLoading.value = false
        }
    }
}
