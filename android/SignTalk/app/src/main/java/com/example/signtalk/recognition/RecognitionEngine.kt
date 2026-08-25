package com.example.signtalk.recognition

import android.content.Context
import com.example.signtalk.domain.model.RecognitionResult
import com.example.signtalk.domain.model.RecognitionState
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the on-device recognition pipeline: MediaPipe hand landmarks ->
 * normalize -> 30-frame sequence buffer -> TFLite BiLSTM classifier ->
 * majority-vote smoothing over the last few predictions (same idea as
 * `ai/evaluation/predict.py`'s temporal smoothing, so a single noisy frame
 * doesn't flip the displayed result).
 *
 * Asset availability (hand_landmarker.task, sign_lstm.tflite) and the
 * "use sample predictions" setting are tracked separately from the derived
 * [state] so toggling mock mode on/off in Settings after [setup] has
 * already run still correctly flips between [RecognitionState.ModelUnavailable]
 * and a working state -- see [refreshAvailability].
 */
class RecognitionEngine(
    context: Context,
    private var confidenceThreshold: Float = 0.6f
) : HandLandmarkerHelper.Listener {

    private val handLandmarkerHelper = HandLandmarkerHelper(context, this)
    private val classifier = SignClassifier(context)
    private val sequenceBuffer = SequenceBuffer(
        sequenceLength = SignClassifier.SEQUENCE_LENGTH,
        featuresPerFrame = SignClassifier.FEATURES_PER_FRAME
    )

    private val voteWindow = ArrayDeque<Int>()
    private val voteWindowSize = 5

    private val _state = MutableStateFlow<RecognitionState>(RecognitionState.Initializing)
    val state: StateFlow<RecognitionState> = _state.asStateFlow()

    private var useMockRecognition: Boolean = false
    private var isSetUp = false

    fun setConfidenceThreshold(threshold: Float) {
        confidenceThreshold = threshold
    }

    fun setUseMockRecognition(enabled: Boolean) {
        if (useMockRecognition == enabled) return
        useMockRecognition = enabled
        if (isSetUp) refreshAvailability()
    }

    fun setup() {
        classifier.setup()
        handLandmarkerHelper.setup()
        isSetUp = true
        refreshAvailability()
    }

    /** Re-derives [state] from current asset availability + mock-mode setting; a no-op if already past this gate. */
    private fun refreshAvailability() {
        if (!handLandmarkerHelper.isModelAvailable) {
            _state.value = RecognitionState.ModelUnavailable(
                "hand_landmarker.task not found in app/src/main/assets/. See docs/MOBILE_APP_NOTES.md."
            )
            return
        }
        if (!classifier.isModelAvailable && !useMockRecognition) {
            _state.value = RecognitionState.ModelUnavailable(
                "sign_lstm.tflite not found in app/src/main/assets/. Enable \"Use sample predictions\" " +
                    "in Settings to try the app without it, or see docs/MOBILE_APP_NOTES.md."
            )
            return
        }
        if (_state.value is RecognitionState.ModelUnavailable || _state.value is RecognitionState.Initializing) {
            _state.value = RecognitionState.WaitingForHands
        }
    }

    fun processFrame(bitmapProvider: () -> android.graphics.Bitmap, timestampMs: Long) {
        if (!handLandmarkerHelper.isModelAvailable) return
        handLandmarkerHelper.detectAsync(bitmapProvider(), timestampMs)
    }

    override fun onResult(result: HandLandmarkerResult) {
        if (_state.value is RecognitionState.ModelUnavailable) return

        if (result.landmarks().isEmpty()) {
            sequenceBuffer.clear()
            voteWindow.clear()
            _state.value = RecognitionState.WaitingForHands
            return
        }

        var leftHand = LandmarkNormalizer.emptyHand()
        var rightHand = LandmarkNormalizer.emptyHand()
        val handedness = result.handedness()
        for (i in result.landmarks().indices) {
            val label = handedness.getOrNull(i)?.firstOrNull()?.categoryName() ?: continue
            val normalized = LandmarkNormalizer.normalizeHand(result.landmarks()[i])
            if (label.equals("Left", ignoreCase = true)) leftHand = normalized else rightHand = normalized
        }

        sequenceBuffer.push(leftHand, rightHand)

        if (!sequenceBuffer.isFull) {
            _state.value = RecognitionState.Buffering(sequenceBuffer.size, SignClassifier.SEQUENCE_LENGTH)
            return
        }

        val sequence = sequenceBuffer.toFlatArrayOrNull() ?: return
        val prediction = if (useMockRecognition) mockClassify() else classifier.classify(sequence)
        if (prediction == null) {
            _state.value = RecognitionState.NotRecognized
            return
        }

        val (classIndex, confidence) = prediction
        voteWindow.addLast(classIndex)
        while (voteWindow.size > voteWindowSize) voteWindow.removeFirst()
        val votedIndex = voteWindow.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: classIndex

        val entry = classifier.labelEntries.getOrNull(votedIndex)
        _state.value = if (entry != null && confidence >= confidenceThreshold) {
            RecognitionState.Recognized(
                RecognitionResult(entry.label, entry.displayName, confidence, System.currentTimeMillis())
            )
        } else {
            RecognitionState.NotRecognized
        }
    }

    override fun onError(message: String) {
        _state.value = RecognitionState.Error(message)
    }

    /** Randomized sample predictions so the screen is demoable before a real model is trained/exported. */
    private fun mockClassify(): Pair<Int, Float>? {
        val labels = classifier.labelEntries.ifEmpty { return null }
        val idx = Random.nextInt(labels.size)
        val confidence = 0.7f + Random.nextFloat() * 0.29f
        return idx to confidence
    }

    fun labelEntries(): List<LabelEntry> = classifier.labelEntries

    fun reset() {
        sequenceBuffer.clear()
        voteWindow.clear()
    }

    fun close() {
        handLandmarkerHelper.close()
        classifier.close()
    }
}
