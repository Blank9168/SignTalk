package com.example.signtalk.domain.model

/**
 * One accepted gesture, produced by the on-device recognition engine.
 *
 * [firstHandFrameMs] and [acceptedAtMs] are `SystemClock.elapsedRealtime()` values used to
 * measure latency (first hand seen -> sign accepted -> text shown -> speech started); see
 * recognition/PerformanceLogger.kt.
 */
data class RecognitionResult(
    val label: String,
    val displayName: String,
    val confidence: Float,
    val timestampMs: Long,
    val firstHandFrameMs: Long = 0L,
    val acceptedAtMs: Long = 0L
)

/** Best guess the model had while it was not confident enough to accept anything. */
data class TopGuess(val label: String, val displayName: String, val confidence: Float)

/**
 * Overall state of the live recognition screen. The status shown to the user maps to:
 * Detecting ([Buffering]), Recognized ([Recognized]), Not sure ([NotSure]) and
 * Unknown sign ([Unknown]).
 */
sealed interface RecognitionState {
    data object Initializing : RecognitionState
    data object WaitingForHands : RecognitionState
    data object WaitingForBody : RecognitionState
    /** Hands are visible and the engine is collecting frames / waiting for a stable result. */
    data class Buffering(val framesCollected: Int, val framesNeeded: Int) : RecognitionState
    /** Passed both the confidence threshold and the stability rule (N agreeing predictions in a row). */
    data class Recognized(val result: RecognitionResult) : RecognitionState
    /**
     * The model has a guess, but it is between the "not sure" floor and the confidence
     * threshold, or the last predictions disagree. Nothing is spoken. [failedAttempts] counts
     * consecutive failed attempts so the screen can offer an example of the expected sign.
     */
    data class NotSure(val guess: TopGuess?, val failedAttempts: Int) : RecognitionState
    /**
     * The movement does not look like any of the supported signs (confidence below the
     * "not sure" floor, or the model's `none` class won). The 50 signs are never forced.
     */
    data class Unknown(val failedAttempts: Int) : RecognitionState
    data class ModelUnavailable(val reason: String) : RecognitionState
    data class Error(val message: String) : RecognitionState
}

/** Camera-condition hint shown under the status, so errors say what to do next. */
enum class RecognitionHint {
    NONE,
    LOW_LIGHT,
    HAND_TOO_FAR,
    HAND_TOO_CLOSE,
    HAND_AT_EDGE
}

/** Live performance numbers for the optional on-screen overlay. */
data class PerformanceStats(val fps: Float, val inferenceMs: Float)
