package com.example.signtalk.ui.recognition

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.signtalk.data.remote.RetrofitClient
import com.example.signtalk.data.remote.dto.RecognitionLogRequestDto
import com.example.signtalk.domain.model.RecognitionResult
import com.example.signtalk.domain.model.RecognitionState
import com.example.signtalk.domain.repository.AppSettings
import com.example.signtalk.domain.repository.SettingsRepository
import com.example.signtalk.recognition.RecognitionEngine
import com.example.signtalk.recognition.SpeechOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * [AndroidViewModel] (not the usual factory-injected ViewModel) because the
 * MediaPipe/TFLite engine and TextToSpeech both need a real Context to load
 * assets and start the speech engine.
 */
class RecognitionViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    private val engine = RecognitionEngine(application)
    private val speechOutput = SpeechOutput(application)

    val recognitionState: StateFlow<RecognitionState> = engine.state
    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings()
    )

    private val _lastSpokenText = MutableStateFlow("")
    val lastSpokenText: StateFlow<String> = _lastSpokenText.asStateFlow()

    private var lastSpokenLabel: String? = null
    // Separate dedup key from lastSpokenLabel -- logging should happen
    // whenever a NEW sign is recognized regardless of the user's
    // auto-speak setting, so it can't share that gate.
    private var lastLoggedLabel: String? = null

    /**
     * Running transcript: every newly recognized sign is appended, so a
     * sequence of signs builds up a sentence on screen. Same "new sign" rule as
     * speaking/logging (holding a sign doesn't repeat it; lowering the hands
     * and signing it again does).
     */
    private val _transcript = MutableStateFlow<List<String>>(emptyList())
    val transcript: StateFlow<List<String>> = _transcript.asStateFlow()
    private var lastTranscriptLabel: String? = null

    init {
        viewModelScope.launch {
            settings.collect { s ->
                engine.setUseMockRecognition(s.useMockRecognition)
                engine.setConfidenceThreshold(s.confidenceThreshold)
                speechOutput.setRate(s.speechRate)
            }
        }
        viewModelScope.launch {
            recognitionState.collect { state ->
                if (state is RecognitionState.Recognized) {
                    maybeSpeak(state.result.displayName, state.result.label)
                    maybeLogRecognition(state.result)
                    appendToTranscript(state.result)
                } else if (state is RecognitionState.WaitingForHands) {
                    // Hands left the frame: signing the same word again is a new sign.
                    // (Previously it was silently ignored until the screen was reset.)
                    lastSpokenLabel = null
                    lastLoggedLabel = null
                    lastTranscriptLabel = null
                }
            }
        }
        // Model loading (TFLite + MediaPipe, GPU init) is heavy; doing it on the main thread froze
        // the screen on "Starting recognition...". Frames are ignored until setup finishes.
        viewModelScope.launch(Dispatchers.Default) { engine.setup() }
    }

    private fun maybeSpeak(displayName: String, label: String) {
        if (!settings.value.autoSpeak) return
        if (lastSpokenLabel == label) return // avoid repeating the same word every frame
        lastSpokenLabel = label
        _lastSpokenText.value = displayName
        speechOutput.speak(displayName)
    }

    /**
     * Best-effort POST to /api/logs/recognition (backend/sign-talk-api --
     * see RecognitionLogRequestDto) so the "Logs/statistics" part of the
     * tech stack has real data to aggregate, instead of nothing. Fires once
     * per newly-recognized sign (not every frame the sign stays on screen),
     * same dedup shape as [maybeSpeak] but tracked separately since logging
     * shouldn't be silenced by the user's auto-speak setting. Never throws
     * into the caller -- an unreachable/offline backend should never affect
     * the recognition UI itself.
     */
    private fun maybeLogRecognition(result: RecognitionResult) {
        if (lastLoggedLabel == result.label) return
        lastLoggedLabel = result.label
        viewModelScope.launch {
            try {
                RetrofitClient.apiService.logRecognition(
                    RecognitionLogRequestDto(
                        predictedSlug = result.label,
                        confidence = result.confidence.toDouble()
                    )
                )
            } catch (e: Exception) {
                Log.w("RecognitionViewModel", "Recognition log skipped (backend unreachable or offline?): ${e.message}")
            }
        }
    }

    private fun appendToTranscript(result: RecognitionResult) {
        if (lastTranscriptLabel == result.label) return
        lastTranscriptLabel = result.label
        _transcript.value = (_transcript.value + result.displayName).takeLast(MAX_TRANSCRIPT_WORDS)
    }

    fun clearTranscript() {
        _transcript.value = emptyList()
        lastTranscriptLabel = null
    }

    /** Reads the whole transcript aloud as one sentence. */
    fun speakTranscript() {
        val sentence = _transcript.value.joinToString(" ")
        if (sentence.isNotBlank()) speechOutput.speak(sentence)
    }

    fun onFrame(bitmapProvider: () -> android.graphics.Bitmap, timestampMs: Long) {
        engine.processFrame(bitmapProvider, timestampMs)
    }

    fun resetBuffer() {
        engine.reset()
        lastSpokenLabel = null
        lastLoggedLabel = null
    }

    private companion object {
        const val MAX_TRANSCRIPT_WORDS = 20
    }

    override fun onCleared() {
        super.onCleared()
        engine.close()
        speechOutput.shutdown()
    }
}
