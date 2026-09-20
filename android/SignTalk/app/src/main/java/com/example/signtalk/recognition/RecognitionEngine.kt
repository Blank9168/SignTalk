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
    // Tracks nose/shoulders so hand LOCATION (forehead vs chin, etc.) can be a model feature.
    // Only used when both pose_landmarker.task and sign_lstm_loc.tflite are present; otherwise
    // the engine behaves exactly like before (hands-only model).
    private val bodyTracker = BodyReferenceTracker(context)

    // Re-created in setup() once the loaded model's per-frame feature count (126 or 134) is known.
    private var sequenceBuffer = SequenceBuffer(
        sequenceLength = SignClassifier.SEQUENCE_LENGTH,
        featuresPerFrame = SignClassifier.FEATURES_PER_FRAME
    )

    @Volatile private var frameWidth = 0
    @Volatile private var frameHeight = 0

    // (class index, confidence) of the recent confident predictions
    private val voteWindow = ArrayDeque<Pair<Int, Float>>()
    // Small window = the label follows a new sign quickly (5 kept the old sign on screen for ~4 frames).
    private val voteWindowSize = 3

    /**
     * Start classifying as soon as this many *real* hand frames exist (~0.4 s), instead of
     * waiting for a fuller window. The frames collected so far are uniformly resampled to the
     * model's 30 slots ([SequenceBuffer.toFlatArrayResampled]) and the prediction keeps
     * refining as more frames arrive. The model is trained on partial signs (`_prefix`
     * augmentation in ai/training/dataset.py), so early predictions are usable: on held-out
     * phone clips 12 frames gave ~80% (15 -> ~91%, 20 -> ~95%, full window ~98%). Predictions
     * below [confidenceThreshold] never show, so the uncertain early ones stay hidden rather
     * than flashing a wrong word.
     */
    private val minFramesForPrediction = 12

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
    @Volatile private var isSetUp = false

    fun setConfidenceThreshold(threshold: Float) {
        confidenceThreshold = threshold
    }

    fun setUseMockRecognition(enabled: Boolean) {
        if (useMockRecognition == enabled) return
        useMockRecognition = enabled
        if (isSetUp) refreshAvailability()
    }

    fun setup() {
        // Load the classifier first. The pose model (body location) is only needed by the
        // legacy location model, so a hands-only model (e.g. sign_lstm_iso.tflite) skips
        // loading it -- that was a large part of the "Starting recognition..." wait.
        classifier.setup(preferLocation = true)
        if (classifier.usesLocation) {
            bodyTracker.setup()
            if (!bodyTracker.isAvailable) classifier.setup(preferLocation = false)
        }
        sequenceBuffer = SequenceBuffer(
            sequenceLength = SignClassifier.SEQUENCE_LENGTH,
            featuresPerFrame = classifier.featuresPerFrame
        )
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
        val bitmap = bitmapProvider()
        frameWidth = bitmap.width
        frameHeight = bitmap.height
        if (classifier.usesLocation) bodyTracker.update(bitmap)
        handLandmarkerHelper.detectAsync(bitmap, timestampMs)
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

        // Aspect-corrected model: hand shapes must be measured in isotropic units (see
        // LandmarkNormalizer.normalizeHand). Legacy models were trained without it.
        val aspect = if (classifier.usesAspectCorrection && frameHeight > 0) {
            frameWidth.toFloat() / frameHeight
        } else {
            1f
        }

        var leftHand = LandmarkNormalizer.emptyHand()
        var rightHand = LandmarkNormalizer.emptyHand()
        var leftLoc = LandmarkNormalizer.emptyLocation()
        var rightLoc = LandmarkNormalizer.emptyLocation()

        val useLocation = classifier.usesLocation
        val body = bodyTracker.reference
        if (useLocation && (body == null || frameWidth == 0 || frameHeight == 0)) {
            // Location-aware model needs the signer's head/shoulders in view first.
            _state.value = RecognitionState.WaitingForBody
            return
        }

        if (result.landmarks().size == 1) {
            // Canonicalize: with exactly one hand visible, always place it in the LEFT
            // feature slot, regardless of what MediaPipe's own handedness classifier calls
            // it for this frame. This is deliberate, not an oversight -- a direct check of
            // every ai/dataset/raw/<class>/*.npy training sample (every class, not just
            // numbers) found no FSL/user samples with the visible hand in the right-only slot
            // (2026-09-21 re-check: 20 ASL Citizen clips are right-slot-only -- the only exceptions). The
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
            leftHand = LandmarkNormalizer.normalizeHand(result.landmarks()[0], aspect)
            if (useLocation && body != null) {
                leftLoc = LandmarkNormalizer.handLocation(result.landmarks()[0], frameWidth, frameHeight, body)
            }
        } else {
            // Two (or more) hands detected -- both feature slots are meaningfully in play
            // (e.g. two-handed signs like "married"/"bread"/"coffee" in this dataset), so
            // trust MediaPipe's own per-hand Left/Right label here same as before; there's
            // no evidence (unlike the one-hand case above) that this assignment is wrong.
            val handedness = result.handedness()
            val labels = result.landmarks().indices.map { handedness.getOrNull(it)?.firstOrNull()?.categoryName() }
            // MediaPipe sometimes labels both hands the same; the loop below would then let the
            // second hand overwrite the first and the model would see one hand instead of two.
            // Fall back to image position (smaller wrist x -> "Left" slot, as in landmarks.py).
            val slotted: List<Pair<Int, String>> =
                if (result.landmarks().size == 2 && labels[0] != null && labels[0] == labels[1]) {
                    result.landmarks().indices.sortedBy { result.landmarks()[it][0].x() }
                        .mapIndexed { rank, i -> i to (if (rank == 0) "Left" else "Right") }
                } else {
                    result.landmarks().indices.mapNotNull { i -> labels[i]?.let { i to it } }
                }
            for ((i, label) in slotted) {
                val normalized = LandmarkNormalizer.normalizeHand(result.landmarks()[i], aspect)
                val loc = if (useLocation && body != null) {
                    LandmarkNormalizer.handLocation(result.landmarks()[i], frameWidth, frameHeight, body)
                } else {
                    LandmarkNormalizer.emptyLocation()
                }
                if (label.equals("Left", ignoreCase = true)) {
                    leftHand = normalized
                    leftLoc = loc
                } else {
                    rightHand = normalized
                    rightLoc = loc
                }
            }
        }

        if (useLocation) {
            // Frame layout must match training: [left hand 63][right hand 63][left loc 4][right loc 4].
            val frame = FloatArray(SignClassifier.FEATURES_WITH_LOCATION)
            leftHand.copyInto(frame, 0)
            rightHand.copyInto(frame, 63)
            leftLoc.copyInto(frame, 126)
            rightLoc.copyInto(frame, 130)
            sequenceBuffer.pushFrame(frame)
        } else {
            sequenceBuffer.push(leftHand, rightHand)
        }

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
        // Only confident predictions vote (same as ai/evaluation/predict.py); a low-confidence
        // frame lets the oldest vote expire instead of voting for a guess.
        if (confidence >= confidenceThreshold) {
            voteWindow.addLast(classIndex to confidence)
            while (voteWindow.size > voteWindowSize) voteWindow.removeFirst()
        } else if (voteWindow.isNotEmpty()) {
            voteWindow.removeFirst()
        }
        // Report the confidence of the label actually shown (mean over its votes). Before, the
        // label came from the vote but the confidence from the latest frame, which could be a
        // different class.
        val winner = voteWindow.groupBy { it.first }.maxByOrNull { it.value.size }
        val entry = winner?.let { classifier.labelEntries.getOrNull(it.key) }
        _state.value = if (winner != null && entry != null) {
            val votedConfidence = winner.value.map { it.second }.average().toFloat()
            RecognitionState.Recognized(
                RecognitionResult(entry.label, entry.displayName, votedConfidence, System.currentTimeMillis())
            )
        } else if (sequenceBuffer.size < SignClassifier.SEQUENCE_LENGTH) {
            // Still early in the sign and nothing is confident yet: keep "reading" instead of
            // flashing "Gesture not recognized" before the model has seen the whole sign.
            RecognitionState.Buffering(sequenceBuffer.size, SignClassifier.SEQUENCE_LENGTH)
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
        bodyTracker.reset()
        sequenceBuffer.clear()
        voteWindow.clear()
        missedHandFrames = 0
    }

    fun close() {
        handLandmarkerHelper.close()
        bodyTracker.close()
        classifier.close()
    }
}
