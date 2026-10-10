package com.example.signtalk.ui.recognition

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.signtalk.data.dictionary.signMetadata
import com.example.signtalk.data.remote.RetrofitClient
import com.example.signtalk.data.remote.dto.RecognitionLogRequestDto
import com.example.signtalk.domain.model.PerformanceStats
import com.example.signtalk.domain.model.RecognitionHint
import com.example.signtalk.domain.model.RecognitionResult
import com.example.signtalk.domain.model.RecognitionState
import com.example.signtalk.domain.repository.AppSettings
import com.example.signtalk.domain.repository.SettingsRepository
import com.example.signtalk.recognition.FeedbackPlayer
import com.example.signtalk.recognition.PerformanceLogger
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
 *
 * Speech, logging, the transcript and haptics only react to
 * [RecognitionState.Recognized], which the engine only emits after a sign passes both the
 * confidence threshold and the stability rule -- uncertain predictions are never spoken.
 */
class RecognitionViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    private val engine = RecognitionEngine(application)
    private val speechOutput = SpeechOutput(application)
    private val feedback = FeedbackPlayer(application)
    private val performanceLogger = PerformanceLogger(application)

    val recognitionState: StateFlow<RecognitionState> = engine.state
    val hint: StateFlow<RecognitionHint> = engine.hint
    val performance: StateFlow<PerformanceStats> = engine.performance
    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings()
    )

    /** Last accepted word, for the Replay button (empty until the first sign). */
    private val _lastWord = MutableStateFlow<RecognitionResult?>(null)
    val lastWord: StateFlow<RecognitionResult?> = _lastWord.asStateFlow()

    private var lastSpokenLabel: String? = null
    // Separate dedup key from lastSpokenLabel -- logging should happen
    // whenever a NEW sign is recognized regardless of the user's
    // auto-speak setting, so it can't share that gate.
    private var lastLoggedLabel: String? = null
    private var lastFeedbackLabel: String? = null
    private var lastWarnedAttempt = 0

    /**
     * Running transcript: every newly recognized sign is appended, so a
     * sequence of signs builds up a sentence on screen. Same "new sign" rule as
     * speaking/logging (holding a sign doesn't repeat it; lowering the hands
     * and signing it again does).
     */
    private val transcriptLabels = mutableListOf<String>()
    private val _transcript = MutableStateFlow<List<String>>(emptyList())
    val transcript: StateFlow<List<String>> = _transcript.asStateFlow()
    private var lastTranscriptLabel: String? = null

    /** Latency row being assembled for the most recently accepted sign. */
    private data class PendingLatency(
        val result: RecognitionResult,
        val utteranceId: String?,
        var textShownMs: Long = 0L,
        var speechStartMs: Long? = null
    )
    private var pendingLatency: PendingLatency? = null
    private var screenVisible = false

    init {
        viewModelScope.launch {
            settings.collect { s ->
                engine.setUseMockRecognition(s.useMockRecognition)
                engine.setConfidenceThreshold(s.confidenceThreshold)
                speechOutput.setRate(s.speechRate)
                feedback.vibrationEnabled = s.vibrationEnabled
                feedback.soundEffectsEnabled = s.soundEffectsEnabled
                val wasLogging = performanceLogger.enabled
                performanceLogger.enabled = s.performanceLogging
                if (!wasLogging && s.performanceLogging && screenVisible) performanceLogger.startSession()
                if (!s.autoSpeak) speechOutput.stop()
            }
        }
        viewModelScope.launch {
            recognitionState.collect { state -> onState(state) }
        }
        viewModelScope.launch {
            performance.collect { p -> performanceLogger.addSample(p.fps, p.inferenceMs) }
        }
        speechOutput.setOnStart { utteranceId, startedAtMs ->
            viewModelScope.launch(Dispatchers.Main) {
                val pending = pendingLatency ?: return@launch
                if (pending.utteranceId == utteranceId) {
                    pending.speechStartMs = startedAtMs
                    flushLatencyIfComplete()
                }
            }
        }
        // Model loading (TFLite + MediaPipe, GPU init) is heavy; doing it on the main thread froze
        // the screen on "Starting recognition...". Frames are ignored until setup finishes.
        viewModelScope.launch(Dispatchers.Default) { engine.setup() }
    }

    private fun onState(state: RecognitionState) {
        when (state) {
            is RecognitionState.Recognized -> {
                val result = state.result
                if (lastFeedbackLabel != result.label) {
                    lastFeedbackLabel = result.label
                    feedback.success()
                    _lastWord.value = result
                    // A new accepted sign: write out the previous latency row with what we have.
                    pendingLatency?.let { writeLatency(it) }
                    val utteranceId = maybeSpeak(result)
                    pendingLatency = PendingLatency(result, utteranceId)
                }
                lastWarnedAttempt = 0
                maybeLogRecognition(result)
                appendToTranscript(result)
            }
            is RecognitionState.NotSure -> {
                clearNewSignKeys()
                warnOnce(state.failedAttempts)
            }
            is RecognitionState.Unknown -> {
                clearNewSignKeys()
                warnOnce(state.failedAttempts)
            }
            // Hands left the frame: signing the same word again is a new sign.
            is RecognitionState.WaitingForHands -> clearNewSignKeys()
            else -> Unit
        }
    }

    /**
     * After the accepted word has gone (hands left, or the screen moved to Not sure / Unknown),
     * signing the same word again counts as a new sign ("isa, isa" is two words).
     */
    private fun clearNewSignKeys() {
        lastSpokenLabel = null
        lastLoggedLabel = null
        lastTranscriptLabel = null
        lastFeedbackLabel = null
    }

    /** One warning buzz per failed attempt, not one per camera frame. */
    private fun warnOnce(attempt: Int) {
        if (attempt > lastWarnedAttempt) {
            lastWarnedAttempt = attempt
            feedback.warning()
        }
    }

    private fun languageOf(label: String): SpeechOutput.Language =
        if (signMetadata[label]?.language == "FSL") SpeechOutput.Language.FILIPINO else SpeechOutput.Language.ENGLISH

    /** Speaks a newly accepted sign once (holding the same sign never repeats it). */
    private fun maybeSpeak(result: RecognitionResult): String? {
        if (!settings.value.autoSpeak) return null
        if (lastSpokenLabel == result.label) return null
        lastSpokenLabel = result.label
        return speechOutput.speak(result.displayName, languageOf(result.label))
    }

    /** Replay button: speaks the last accepted word again (works even when auto-speak is muted). */
    fun replayLastWord() {
        val word = _lastWord.value ?: return
        speechOutput.speak(word.displayName, languageOf(word.label))
    }

    /** Called by the screen right after the accepted word has been drawn. */
    fun onResultShown(result: RecognitionResult, shownAtMs: Long = SystemClock.elapsedRealtime()) {
        val pending = pendingLatency ?: return
        if (pending.result.acceptedAtMs != result.acceptedAtMs || pending.textShownMs != 0L) return
        pending.textShownMs = shownAtMs
        flushLatencyIfComplete()
    }

    private fun flushLatencyIfComplete() {
        val pending = pendingLatency ?: return
        val speechDone = pending.utteranceId == null || pending.speechStartMs != null
        if (pending.textShownMs != 0L && speechDone) {
            writeLatency(pending)
            pendingLatency = null
        }
    }

    private fun writeLatency(p: PendingLatency) {
        if (p.textShownMs == 0L) return
        performanceLogger.logLatency(
            label = p.result.label,
            confidence = p.result.confidence,
            firstHandMs = p.result.firstHandFrameMs,
            acceptedMs = p.result.acceptedAtMs,
            textShownMs = p.textShownMs,
            speechStartMs = p.speechStartMs
        )
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
        transcriptLabels += result.label
        while (transcriptLabels.size > MAX_TRANSCRIPT_WORDS) transcriptLabels.removeAt(0)
        _transcript.value = (_transcript.value + result.displayName).takeLast(MAX_TRANSCRIPT_WORDS)
    }

    fun clearTranscript() {
        _transcript.value = emptyList()
        transcriptLabels.clear()
        lastTranscriptLabel = null
        feedback.tap()
    }

    /** Reads the whole transcript aloud, each word in its own sign's language. */
    fun speakTranscript() {
        val words = _transcript.value
        if (words.isEmpty()) return
        val parts = words.mapIndexed { i, word ->
            word to languageOf(transcriptLabels.getOrNull(i).orEmpty())
        }
        speechOutput.speakSequence(parts)
    }

    fun onFrame(bitmapProvider: () -> android.graphics.Bitmap, timestampMs: Long) {
        engine.processFrame(bitmapProvider, timestampMs)
    }

    /** "Try again": drops the current attempt so the user can sign from the start. */
    fun tryAgain() {
        engine.reset()
        lastSpokenLabel = null
        lastLoggedLabel = null
        lastFeedbackLabel = null
        lastTranscriptLabel = null
        lastWarnedAttempt = 0
        feedback.tap()
    }

    fun toggleMute() = viewModelScope.launch {
        settingsRepository.setAutoSpeak(!settings.value.autoSpeak)
    }

    fun flipCamera() = viewModelScope.launch {
        engine.reset()
        settingsRepository.setUseFrontCamera(!settings.value.useFrontCamera)
    }

    /** Start/stop of a visible recognition session, for battery/temperature/FPS logging. */
    fun onScreenVisible(visible: Boolean) {
        screenVisible = visible
        if (visible) performanceLogger.startSession() else performanceLogger.endSession()
    }

    private companion object {
        const val MAX_TRANSCRIPT_WORDS = 20
    }

    override fun onCleared() {
        super.onCleared()
        pendingLatency?.let { writeLatency(it) }
        engine.close()
        speechOutput.shutdown()
        feedback.release()
        performanceLogger.shutdown()
    }
}
