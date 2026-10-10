package com.example.signtalk.recognition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import com.example.signtalk.domain.model.PerformanceStats
import com.example.signtalk.domain.model.RecognitionHint
import com.example.signtalk.domain.model.RecognitionResult
import com.example.signtalk.domain.model.RecognitionState
import com.example.signtalk.domain.model.TopGuess
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
 * Acceptance rules (expert review, 2026-10-10): a sign is only *accepted* -- shown as
 * Recognized, spoken and logged -- after [requiredAgreeingPredictions] confident
 * predictions of the same class in a row (the stability period). A confident-but-not-yet-
 * stable sign keeps the screen on "Detecting". Below the threshold, the result is
 * "Not sure, please repeat" (top guess >= [notSureFloor]) or "Unknown sign" (below the
 * floor, or the optional `none` class won), so random hand movement is never forced into
 * one of the 50 signs. The thresholds are justified by ai/evaluation/threshold_sweep.py.
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

    /**
     * Stability period: this many confident predictions of the SAME class in a row are needed
     * before a sign is accepted (about 0.1-0.2 s at the camera's analysis rate). Replaces the
     * old 3-vote majority window, where a single confident frame was enough to show and
     * speak a word.
     */
    private val requiredAgreeingPredictions = 3

    /**
     * Below the confidence threshold but at or above this, the screen says "Not sure, please
     * repeat the sign" and shows the top guess; below it, "Unknown sign".
     */
    private val notSureFloor = 0.35f

    /**
     * After a sign is accepted, this many low-confidence frames in a row are tolerated before
     * the accepted word is dropped (the end of a sign is often less confident than its middle;
     * without this the word would flicker away as the hand relaxes).
     */
    private val holdAcceptedFrames = 6

    /** Frames of continuous doubt that count as one more failed attempt (about half a second). */
    private val framesPerFailedAttempt = 15

    private var candidateIdx = -1
    private var candidateCount = 0
    private var candidateConfidenceSum = 0f
    private var accepted: RecognitionResult? = null
    private var acceptedIdx = -1
    private var lowConfidenceStreak = 0
    private var framesInDoubt = 0
    private var failedAttempts = 0
    /** elapsedRealtime when the current sign attempt started (latency start). */
    private var attemptStartMs = 0L
    /**
     * Set by [reset] (main thread) and applied at the start of the next [onResult] (MediaPipe
     * thread), so the buffer and decision state are only ever touched from one thread.
     */
    @Volatile private var resetRequested = false

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

    private val _hint = MutableStateFlow(RecognitionHint.NONE)
    /** What the user should change (light, distance, framing), shown under the status. */
    val hint: StateFlow<RecognitionHint> = _hint.asStateFlow()

    private val _performance = MutableStateFlow(PerformanceStats(0f, 0f))
    /** Analysis FPS and per-inference time, for the optional performance overlay. */
    val performance: StateFlow<PerformanceStats> = _performance.asStateFlow()

    @Volatile private var lowLight = false
    private var frameCounter = 0
    private var lastResultMs = 0L
    private var fpsEma = 0f
    private var inferenceEma = 0f

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
        if (frameCounter++ % LIGHT_CHECK_EVERY_N_FRAMES == 0) lowLight = isLowLight(bitmap)
        if (classifier.usesLocation) bodyTracker.update(bitmap)
        handLandmarkerHelper.detectAsync(bitmap, timestampMs)
    }

    override fun onResult(result: HandLandmarkerResult) {
        if (_state.value is RecognitionState.ModelUnavailable) return

        if (resetRequested) {
            resetRequested = false
            sequenceBuffer.clear()
            resetDecision(keepFailedAttempts = false)
            missedHandFrames = 0
            attemptStartMs = 0L
        }
        updatePerformance()
        if (result.landmarks().isEmpty()) {
            missedHandFrames++
            _hint.value = if (lowLight) RecognitionHint.LOW_LIGHT else RecognitionHint.NONE
            if (missedHandFrames > handLostGraceFrames) {
                sequenceBuffer.clear()
                resetDecision(keepFailedAttempts = true)
                attemptStartMs = 0L
                _state.value = RecognitionState.WaitingForHands
            }
            // Within the grace window: leave the buffer and displayed state alone rather
            // than resetting on what's likely a momentary tracking blip, not a real "hand
            // left the frame."
            return
        }
        missedHandFrames = 0
        if (attemptStartMs == 0L) attemptStartMs = SystemClock.elapsedRealtime()
        _hint.value = framingHint(result)

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

        val inferenceStart = SystemClock.elapsedRealtimeNanos()
        val prediction = if (useMockRecognition) mockClassify() else classifier.classify(sequence)
        val inferenceMs = (SystemClock.elapsedRealtimeNanos() - inferenceStart) / 1_000_000f
        inferenceEma = if (inferenceEma == 0f) inferenceMs else inferenceEma * 0.9f + inferenceMs * 0.1f
        if (prediction == null) {
            _state.value = RecognitionState.Unknown(failedAttempts)
            return
        }
        decide(prediction.first, prediction.second)
    }

    /** Applies the confidence + stability rules to one prediction and updates [state]. */
    private fun decide(classIndex: Int, confidence: Float) {
        val isNone = classIndex == classifier.noneIndex
        val entry = classifier.labelEntries.getOrNull(classIndex)

        if (!isNone && entry != null && confidence >= confidenceThreshold) {
            // Only a confident frame of the word on screen keeps it alive; alternating confident
            // guesses of other classes must not hold an old word forever.
            if (classIndex == acceptedIdx) lowConfidenceStreak = 0 else lowConfidenceStreak++
            if (classIndex == candidateIdx) {
                candidateCount++
                candidateConfidenceSum += confidence
            } else {
                candidateIdx = classIndex
                candidateCount = 1
                candidateConfidenceSum = confidence
            }
            if (candidateCount >= requiredAgreeingPredictions) {
                val meanConfidence = candidateConfidenceSum / candidateCount
                val current = accepted
                val result = if (current != null && acceptedIdx == classIndex) {
                    current.copy(confidence = meanConfidence)
                } else {
                    val now = SystemClock.elapsedRealtime()
                    RecognitionResult(
                        label = entry.label,
                        displayName = entry.displayName,
                        confidence = meanConfidence,
                        timestampMs = System.currentTimeMillis(),
                        firstHandFrameMs = attemptStartMs,
                        acceptedAtMs = now
                    ).also { attemptStartMs = now } // the next sign's attempt starts here
                }
                accepted = result
                acceptedIdx = classIndex
                lowConfidenceStreak = 0
                failedAttempts = 0
                framesInDoubt = 0
                _state.value = RecognitionState.Recognized(result)
            } else {
                // Confident, but not stable yet: keep the previous accepted word on screen, or
                // keep "Detecting" -- never show or speak a sign after a single frame.
                val held = accepted
                _state.value = if (held != null && lowConfidenceStreak <= holdAcceptedFrames) {
                    RecognitionState.Recognized(held)
                } else {
                    if (held != null) dropAccepted()
                    RecognitionState.Buffering(sequenceBuffer.size, SignClassifier.SEQUENCE_LENGTH)
                }
            }
            return
        }

        // Low confidence, or the model's `none` class won.
        candidateIdx = -1
        candidateCount = 0
        candidateConfidenceSum = 0f
        lowConfidenceStreak++
        val held = accepted
        if (held != null && lowConfidenceStreak <= holdAcceptedFrames) {
            _state.value = RecognitionState.Recognized(held)
            return
        }
        if (held != null) dropAccepted()

        if (sequenceBuffer.size < SignClassifier.SEQUENCE_LENGTH) {
            // Still early in the sign and nothing is confident yet: keep "Detecting" instead of
            // flashing a failure before the model has seen the whole sign.
            _state.value = RecognitionState.Buffering(sequenceBuffer.size, SignClassifier.SEQUENCE_LENGTH)
            return
        }

        // A failed attempt is a full window of doubt, so the tail of a sign that was just
        // accepted (or a brief wobble) does not immediately count as a failure.
        framesInDoubt++
        if (framesInDoubt % framesPerFailedAttempt == 0) failedAttempts++
        if (failedAttempts == 0) {
            _state.value = RecognitionState.Buffering(framesInDoubt, framesPerFailedAttempt)
            return
        }
        _state.value = if (isNone || entry == null || confidence < notSureFloor) {
            RecognitionState.Unknown(failedAttempts)
        } else {
            RecognitionState.NotSure(TopGuess(entry.label, entry.displayName, confidence), failedAttempts)
        }
    }

    /**
     * The accepted word has expired. Clear the frame window too, so the next sign is read from
     * fresh frames instead of a window that still holds the end of the previous sign.
     */
    private fun dropAccepted() {
        accepted = null
        acceptedIdx = -1
        lowConfidenceStreak = 0
        sequenceBuffer.clear()
        attemptStartMs = SystemClock.elapsedRealtime()
    }

    private fun resetDecision(keepFailedAttempts: Boolean) {
        candidateIdx = -1
        candidateCount = 0
        candidateConfidenceSum = 0f
        accepted = null
        acceptedIdx = -1
        lowConfidenceStreak = 0
        framesInDoubt = 0
        if (!keepFailedAttempts) failedAttempts = 0
    }

    /** Hand framing hint from the landmark bounding box (coordinates are 0..1 of the frame). */
    private fun framingHint(result: HandLandmarkerResult): RecognitionHint {
        if (lowLight) return RecognitionHint.LOW_LIGHT
        var largest = 0f
        var atEdge = false
        for (hand in result.landmarks()) {
            var minX = 1f; var maxX = 0f; var minY = 1f; var maxY = 0f
            for (p in hand) {
                minX = minOf(minX, p.x()); maxX = maxOf(maxX, p.x())
                minY = minOf(minY, p.y()); maxY = maxOf(maxY, p.y())
            }
            if (minX < EDGE_MARGIN || maxX > 1f - EDGE_MARGIN || minY < EDGE_MARGIN || maxY > 1f - EDGE_MARGIN) atEdge = true
            largest = maxOf(largest, maxOf(maxX - minX, maxY - minY))
        }
        return when {
            atEdge -> RecognitionHint.HAND_AT_EDGE
            largest < HAND_TOO_FAR_SIZE -> RecognitionHint.HAND_TOO_FAR
            largest > HAND_TOO_CLOSE_SIZE -> RecognitionHint.HAND_TOO_CLOSE
            else -> RecognitionHint.NONE
        }
    }

    /** Mean luma of a coarse 16x16 sample grid; cheap enough to run every few frames. */
    private fun isLowLight(bitmap: Bitmap): Boolean {
        if (bitmap.width == 0 || bitmap.height == 0) return false
        var sum = 0.0
        val steps = 16
        for (i in 0 until steps) {
            for (j in 0 until steps) {
                val c = bitmap.getPixel(
                    (bitmap.width - 1) * i / (steps - 1),
                    (bitmap.height - 1) * j / (steps - 1)
                )
                sum += 0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)
            }
        }
        return sum / (steps * steps) < LOW_LIGHT_LUMA
    }

    private fun updatePerformance() {
        val now = SystemClock.elapsedRealtime()
        if (lastResultMs != 0L) {
            val dt = (now - lastResultMs).coerceAtLeast(1L)
            val fps = 1000f / dt
            fpsEma = if (fpsEma == 0f) fps else fpsEma * 0.9f + fps * 0.1f
        }
        lastResultMs = now
        if (frameCounter % 10 == 0) _performance.value = PerformanceStats(fpsEma, inferenceEma)
    }

    override fun onError(message: String) {
        _state.value = RecognitionState.Error(message)
    }

    private var mockIdx = -1
    private var mockFramesLeft = 0

    /**
     * Sample predictions so the screen is demoable without a trained model. Each fake sign is
     * held for ~20 frames so it passes the stability rule like a real one would.
     */
    private fun mockClassify(): Pair<Int, Float>? {
        val labels = classifier.labelEntries.ifEmpty { return null }
        if (mockFramesLeft <= 0 || mockIdx !in labels.indices) {
            mockIdx = Random.nextInt(labels.size)
            mockFramesLeft = 20
        }
        mockFramesLeft--
        return mockIdx to (0.7f + Random.nextFloat() * 0.29f)
    }

    fun labelEntries(): List<LabelEntry> = classifier.labelEntries

    /** "Try again": forget the current attempt (buffer, candidate, accepted word, failures). */
    fun reset() {
        bodyTracker.reset()
        resetRequested = true // applied on the recognition thread at the next frame
        if (_state.value !is RecognitionState.ModelUnavailable && _state.value !is RecognitionState.Initializing) {
            _state.value = RecognitionState.WaitingForHands
        }
    }

    private companion object {
        const val LIGHT_CHECK_EVERY_N_FRAMES = 10
        /** Mean luma (0-255) below which the "Improve lighting" hint is shown. */
        const val LOW_LIGHT_LUMA = 45.0
        const val EDGE_MARGIN = 0.02f
        /** Hand bounding box as a fraction of the frame. */
        const val HAND_TOO_FAR_SIZE = 0.12f
        const val HAND_TOO_CLOSE_SIZE = 0.85f
    }

    fun close() {
        handLandmarkerHelper.close()
        bodyTracker.close()
        classifier.close()
    }
}
