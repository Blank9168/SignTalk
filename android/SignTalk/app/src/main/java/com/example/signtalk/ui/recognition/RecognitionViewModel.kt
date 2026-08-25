package com.example.signtalk.ui.recognition

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.signtalk.domain.model.RecognitionState
import com.example.signtalk.domain.repository.AppSettings
import com.example.signtalk.domain.repository.SettingsRepository
import com.example.signtalk.recognition.RecognitionEngine
import com.example.signtalk.recognition.SpeechOutput
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
                }
            }
        }
        engine.setup()
    }

    private fun maybeSpeak(displayName: String, label: String) {
        if (!settings.value.autoSpeak) return
        if (lastSpokenLabel == label) return // avoid repeating the same word every frame
        lastSpokenLabel = label
        _lastSpokenText.value = displayName
        speechOutput.speak(displayName)
    }

    fun onFrame(bitmapProvider: () -> android.graphics.Bitmap, timestampMs: Long) {
        engine.processFrame(bitmapProvider, timestampMs)
    }

    fun resetBuffer() {
        engine.reset()
        lastSpokenLabel = null
    }

    override fun onCleared() {
        super.onCleared()
        engine.close()
        speechOutput.shutdown()
    }
}
