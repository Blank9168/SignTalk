package com.example.signtalk.ui.translate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.signtalk.domain.repository.DictionaryRepository
import com.example.signtalk.domain.translate.TextToSignMatcher
import com.example.signtalk.domain.translate.TranslationToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class TranslateUiState {
    /** Nothing typed/spoken yet. */
    data object Idle : TranslateUiState()

    /** Matching is running (near-instant, but the dictionary lookup is a suspend call). */
    data object Translating : TranslateUiState()

    /** At least one word/phrase resolved to a real sign. */
    data class Result(
        val tokens: List<TranslationToken>,
        val currentIndex: Int
    ) : TranslateUiState()

    /** Everything typed/spoken failed to match any sign in the dictionary. */
    data class NoSignsFound(val text: String) : TranslateUiState()
}

/**
 * Backs the "Text/Speech to Sign" screen (ui/translate). Holds the matched
 * sign sequence for whatever the user last typed or spoke, and which sign
 * in that sequence is currently being shown -- the Fragment advances
 * playback by calling [advance] each time the current clip finishes.
 */
class TranslateViewModel(
    private val dictionaryRepository: DictionaryRepository
) : ViewModel() {

    private val matcher = TextToSignMatcher(dictionaryRepository)

    private val _uiState = MutableStateFlow<TranslateUiState>(TranslateUiState.Idle)
    val uiState: StateFlow<TranslateUiState> = _uiState.asStateFlow()

    fun translate(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _uiState.value = TranslateUiState.Translating
            val tokens = matcher.match(text)
            val firstMatchedIndex = tokens.indexOfFirst { it is TranslationToken.Matched }
            _uiState.value = if (firstMatchedIndex == -1) {
                TranslateUiState.NoSignsFound(text)
            } else {
                TranslateUiState.Result(tokens, firstMatchedIndex)
            }
        }
    }

    /**
     * Moves to the next matched sign after the current one (skipping over
     * any unmatched words in between). Called by the Fragment when the
     * current sign's video finishes. No-op once the last matched sign has
     * been reached -- the Fragment leaves that clip on its final frame and
     * shows a Replay button instead of looping automatically, so it's clear
     * the sequence is done.
     */
    fun advance() {
        val state = _uiState.value
        if (state !is TranslateUiState.Result) return
        val nextIndex = (state.currentIndex + 1 until state.tokens.size)
            .firstOrNull { state.tokens[it] is TranslationToken.Matched }
        if (nextIndex != null) {
            _uiState.value = state.copy(currentIndex = nextIndex)
        }
    }

    /** Restarts the current sequence from its first matched sign. */
    fun replay() {
        val state = _uiState.value
        if (state !is TranslateUiState.Result) return
        val firstIndex = state.tokens.indexOfFirst { it is TranslationToken.Matched }
        if (firstIndex != -1) {
            _uiState.value = state.copy(currentIndex = firstIndex)
        }
    }

    fun reset() {
        _uiState.value = TranslateUiState.Idle
    }
}
