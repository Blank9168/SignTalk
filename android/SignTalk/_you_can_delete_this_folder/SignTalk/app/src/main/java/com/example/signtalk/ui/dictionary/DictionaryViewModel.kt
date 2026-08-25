package com.example.signtalk.ui.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.signtalk.domain.model.DictionaryEntry
import com.example.signtalk.domain.repository.DictionaryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DictionaryViewModel(private val repository: DictionaryRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val entries: StateFlow<List<DictionaryEntry>> = _query
        .flatMapLatest { repository.observeEntries(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    fun addEntry(displayName: String, category: String, description: String, emoji: String, onAdded: (Long) -> Unit) {
        if (displayName.isBlank()) return
        viewModelScope.launch {
            val id = repository.addEntry(
                DictionaryEntry(
                    label = displayName.trim().lowercase().replace(Regex("\\s+"), "_"),
                    displayName = displayName.trim(),
                    category = category.ifBlank { "Custom" },
                    description = description,
                    emoji = emoji.ifBlank { "🤟" },
                    isUserAdded = true
                )
            )
            onAdded(id)
        }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch { repository.deleteEntry(id) }
    }
}
