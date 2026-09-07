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
 * Two latency-focused behaviors on top of a naive "wait for 30 full frames,
 * reset on any dropout" implementation (see [minFramesForPrediction] and
 * [handLostGraceFrames] below for why these existed as a real, reported "it
 * just sits there loading" problem):
 * - Classification starts as soon as [SequenceBuffer.toFlatArrayResampled]
 *   will produce a sequence ([minFramesForPrediction] real frames, not a
 *   full 30, uniformly resampled to fill the window -- see that method's
 *   doc for why resampling, not padding, matters for signs with motion in
 *   them), instead of blocking on a full buffer every time.
 * - A brief hand-tracking dropout (motion blur, hand grazing the frame
 *   edge) no longer throws away the whole buffer -- only a run of
 *   [handLostGraceFrames] consecutive no-hand frames does, so normal
 *   signing doesn't keep restarting the buffer from zero.
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

    /**
     * Start classifying once this many *real* frames are collected, instead of waiting for
     * a full [SignClassifier.SEQUENCE_LENGTH]. Until then, [SequenceBuffer.toFlatArrayResampled]
     * uniformly resamples whatever real frames exist to fill the window; the resampled
     * source points shift toward more real data (and away from repeats) every subsequent
     * frame, so the prediction keeps refining -- this only affects how soon the *first*
     * result can appear, not the steady-state window once it fills.
     *
     * 2/3 of the full window is a deliberately *not* pushed lower than the latency fix that
     * introduced it originally used, specifically because of signs with real motion in
     * them (a swipe, a two-part handshape change, etc.): at 2/3, the model is at least
     * seeing a resampled view of a real majority of whatever's happened so far, and a
     * partial/in-progress motion is far more likely to fall under the confidence threshold
     * (see [confidenceThreshold]) and correctly show as "not recognized" rather than lock
     * onto a wrong guess -- the 5-prediction majority vote then catches up to the right
     * answer once the window is genuinely complete a few frames later. Pushing this lower
     * would trade a bit more perceived speed for classifying on proportionally less of the
     * gesture, which is the wrong direction for motion signs specifically -- there's no
     * on-device signal for "is the sign being performed right now static or dynamic" to
     * lower it selectively, so this stays a single conservative threshold for all classes.
     */
    private val minFramesForPrediction = (SignClassifier.SEQUENCE_LENGTH * 2 / 3)

    /**
     * How many consecutive no-hand-detected frames are tolerated before the sequence buffer
     * is actually reset. Without this, a single missed detection (motion blur, hand briefly
     * at the frame edge) threw away up to 30 frames of correctly-forming context and forced
     * a full re-buffer -- the main source of the "waits/reloads before recognizing" behavior
     * reported during testing, more so than the initial fill time itself.
     */
    private val handLostGraceFrames = 8
    private var missedHandFrames = 0

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
            missedHandFrames++
            if (missedHandFrames > handLostGraceFrames) {
                sequenceBuffer.clear()
                voteWindow.clear()
                _state.value = RecognitionState.WaitingForHands
            }
            // Within the grace window: leave the buffer and displayed state alone rather
            // than resetting on what's likely a momentary tracking blip, not a real "hand
            // left the frame."
            return
        }
        missedHandFrames = 0

        var leftHand = LandmarkNormalizer.emptyHand()
        var rightHand = LandmarkNormalizer.emptyHand()

        if (result.landmarks().size == 1) {
            // Canonicalize: with exactly one hand visible, always place it in the LEFT
            // feature slot, regardless of what MediaPipe's own handedness classifier calls
            // it for this frame. This is deliberate, not an oversight -- a direct check of
            // every ai/dataset/raw/<class>/*.npy training sample (every class, not just
            // numbers) found ZERO samples with the visible hand in the right-only slot. The
            // training pipeline's single FSL-105 signer's one-handed signs all ended up
            // labeled "Left" by MediaPipe (see project notes: this lines up with MediaPipe's
            // own documented "handedness is determined assuming the input image is
            // mirrored" behavior -- ai/dataset/landmarks.py feeds unmirrored footage, same
            // as this app does, so if that assumption applies the same way to both APIs, a
            // real dominant hand can consistently come out labeled "Left" on both sides; if
            // it doesn't apply identically to both APIs, trusting the on-device label at all
            // would put correct-but-unswapped geometry in the slot the model has ~no
            // single-hand training exposure to). Either way, canonicalizing to the slot the
            // model actually has real one-handed data in is the safer choice: it can't do
            // worse than the previous label-trusting behavior, since the model was never
            // meaningfully trained on right-slot-only input, and it removes "which hand you
            // signed with" as a variable for one-handed signs entirely.
            leftHand = LandmarkNormalizer.normalizeHand(result.landmarks()[0])
        } else {
            // Two (or more) hands detected -- both feature slots are meaningfully in play
            // (e.g. two-handed signs like "married"/"bread"/"coffee" in this dataset), so
            // trust MediaPipe's own per-hand Left/Right label here same as before; there's
            // no evidence (unlike the one-hand case above) that this assignment is wrong.
            val handedness = result.handedness()
            for (i in result.landmarks().indices) {
                val label = handedness.getOrNull(i)?.firstOrNull()?.categoryName() ?: continue
                val normalized = LandmarkNormalizer.normalizeHand(result.landmarks()[i])
                if (label.equals("Left", ignoreCase = true)) leftHand = normalized else rightHand = normalized
            }
        }

        sequenceBuffer.push(leftHand, rightHand)

        val sequence = sequenceBuffer.toFlatArrayResampled(minFramesForPrediction)
        if (sequence == null) {
            _state.value = RecognitionState.Buffering(sequenceBuffer.size, minFramesForPrediction)
            return
        }

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
        missedHandFrames = 0
    }

    fun close() {
        handLandmarkerHelper.close()
        classifier.close()
    }
}
