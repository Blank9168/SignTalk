package com.example.signtalk.domain.model

/** One classified gesture, produced by the on-device recognition engine. */
data class RecognitionResult(
    val label: String,
    val displayName: String,
    val confidence: Float,
    val timestampMs: Long
)

/** Overall state of the live recognition screen. */
sealed interface RecognitionState {
    data object Initializing : RecognitionState
    data object WaitingForHands : RecognitionState
    data object WaitingForBody : RecognitionState
    data class Buffering(val framesCollected: Int, val framesNeeded: Int) : RecognitionState
    data class Recognized(val result: RecognitionResult) : RecognitionState
    data object NotRecognized : RecognitionState
    data class ModelUnavailable(val reason: String) : RecognitionState
    data class Error(val message: String) : RecognitionState
}
