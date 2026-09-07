package com.example.signtalk.ui.translate

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.signtalk.domain.repository.DictionaryRepository
import com.example.signtalk.recognition.SignLandmarksProvider
import com.example.signtalk.recognition.SpeechOutput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One resolved word/phrase from the typed input: the sign it matched (if any), ready to animate + speak. */
data class SignStep(
    val text: String,
    val label: String?,
    val displayName: String,
    val found: Boolean
)

data class TranslateUiState(
    val steps: List<SignStep> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val animationAvailable: Boolean = true
)

/**
 * Owns the Text -> Sign direction: resolves typed or spoken text into a
 * sequence of known FSL-105 signs (matched against the same dictionary the
 * Dictionary tab shows -- both by underscore label, e.g. "thank_you", and
 * by display name, e.g. "Thank You"), then drives playback of each sign's
 * landmark animation via [SignLandmarksProvider].
 *
 * This is the mirror image of
 * [com.example.signtalk.ui.recognition.RecognitionViewModel]'s
 * sign -> text -> speech direction, completing two-way communication.
 */
class TranslateViewModel(
    application: Application,
    private val dictionaryRepository: DictionaryRepository
) : AndroidViewModel(application) {

    private val landmarksProvider = SignLandmarksProvider(application)
    private val speechOutput = SpeechOutput(application)

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _uiState = MutableStateFlow(TranslateUiState())
    val uiState: StateFlow<TranslateUiState> = _uiState.asStateFlow()

    /** Normalized phrase (words lowercased, joined by single spaces) -> (label, displayName). */
    private var phraseIndex: Map<String, Pair<String, String>> = emptyMap()
    private var maxPhraseWords = 1
    private var lastSpokenIndex = -1

    init {
        landmarksProvider.setup()
        _uiState.value = TranslateUiState(animationAvailable = landmarksProvider.isAvailable)
        // Collect continuously (not a one-shot .first()) so this doesn't race the app-level
        // dictionary seed on first launch, and so a user-added dictionary entry becomes
        // signable immediately without restarting the app.
        viewModelScope.launch {
            dictionaryRepository.observeEntries("").collect { entries ->
                val map = mutableMapOf<String, Pair<String, String>>()
                for (entry in entries) {
                    val byLabel = entry.label.replace('_', ' ').lowercase()
                    val byName = entry.displayName.lowercase().replace("'", "").replace("’", "")
                    map[byLabel] = entry.label to entry.displayName
                    map[byName] = entry.label to entry.displayName
                }
                phraseIndex = map
                maxPhraseWords = map.keys.maxOfOrNull { it.split(" ").size } ?: 1
            }
        }
    }

    fun setInputText(text: String) {
        _inputText.value = text
    }

    /** Tokenizes [inputText], greedily matching the longest known phrase at each position (so "thank you" resolves as one sign, not two misses). */
    fun translate() {
        val words = inputText.value
            .lowercase()
            .replace(Regex("['’]"), "")
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }

        val steps = mutableListOf<SignStep>()
        var i = 0
        while (i < words.size) {
            var matched = false
            val maxSpan = minOf(maxPhraseWords, words.size - i)
            for (span in maxSpan downTo 1) {
                val phrase = words.subList(i, i + span).joinToString(" ")
                val hit = phraseIndex[phrase]
                if (hit != null) {
                    steps += SignStep(phrase, hit.first, hit.second, true)
                    i += span
                    matched = true
                    break
                }
            }
            if (!matched) {
                steps += SignStep(words[i], null, words[i], false)
                i += 1
            }
        }

        lastSpokenIndex = -1
        _uiState.value = _uiState.value.copy(
            steps = steps,
            currentIndex = if (steps.isNotEmpty()) 0 else -1,
            isPlaying = false
        )
        speakCurrentIfNeeded()
    }

    fun currentStep(): SignStep? = _uiState.value.let { it.steps.getOrNull(it.currentIndex) }

    fun sequenceForCurrent(): Array<FloatArray>? = currentStep()?.label?.let { landmarksProvider.sequenceFor(it) }

    fun setPlaying(playing: Boolean) {
        _uiState.value = _uiState.value.copy(isPlaying = playing)
    }

    fun next(): Boolean = move(1)
    fun previous(): Boolean = move(-1)

    private fun move(delta: Int): Boolean {
        val state = _uiState.value
        if (state.steps.isEmpty()) return false
        val newIndex = state.currentIndex + delta
        if (newIndex !in state.steps.indices) return false
        _uiState.value = state.copy(currentIndex = newIndex)
        speakCurrentIfNeeded()
        return true
    }

    private fun speakCurrentIfNeeded() {
        val state = _uiState.value
        if (state.currentIndex == lastSpokenIndex) return
        lastSpokenIndex = state.currentIndex
        val step = state.steps.getOrNull(state.currentIndex) ?: return
        if (step.found) speechOutput.speak(step.displayName)
    }

    override fun onCleared() {
        super.onCleared()
        speechOutput.shutdown()
    }
}
