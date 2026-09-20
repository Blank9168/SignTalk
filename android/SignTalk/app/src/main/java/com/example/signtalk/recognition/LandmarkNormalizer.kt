package com.example.signtalk.recognition

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.sqrt

/**
 * Ports `normalize_hand()` from the Python pipeline (ai/dataset/landmarks.py)
 * so the model sees the same feature space on-device that it was trained on:
 * each hand is re-centered on its own wrist (landmark 0) and scaled by its
 * own wrist-to-middle-finger-MCP (landmark 9) distance. This makes the
 * features translation- and scale-invariant -- critical, because an earlier
 * version of this pipeline learned "where on screen the hand was" instead of
 * "what shape the hand made" and fell apart outside the training camera's
 * framing (see the project notes on this bug).
 *
 * Output: 21 landmarks * (x, y, z) = 63 floats per hand.
 */
object LandmarkNormalizer {

    private const val WRIST_INDEX = 0
    private const val MIDDLE_MCP_INDEX = 9
    private const val MIN_SCALE = 1e-6f

    /**
     * [aspect] = frame width / frame height. MediaPipe normalizes x by width and y by height,
     * so raw hand shapes are squashed differently for landscape vs portrait frames (~3x between
     * 16:9 and 9:16). Pass the real aspect for the aspect-corrected model
     * (`sign_lstm_iso.tflite`, see SignClassifier.usesAspectCorrection); leave it at 1f for the
     * legacy models, which were trained on the uncorrected features. Must mirror
     * `normalize_hand(coords, aspect)` in ai/dataset/landmarks.py.
     */
    fun normalizeHand(landmarks: List<NormalizedLandmark>, aspect: Float = 1f): FloatArray {
        require(landmarks.size == 21) { "Expected 21 hand landmarks, got ${landmarks.size}" }

        val wrist = landmarks[WRIST_INDEX]
        val middleMcp = landmarks[MIDDLE_MCP_INDEX]

        val dx = (middleMcp.x() - wrist.x()) * aspect
        val dy = middleMcp.y() - wrist.y()
        // Scale uses only x/y (matches ai/dataset/landmarks.py's normalize_hand(), which
        // takes np.linalg.norm(centered[MIDDLE_MCP_IDX, :2]) -- MediaPipe's single-camera z
        // depth estimate is noisier and was deliberately excluded from the scale reference
        // at training time. z IS still included in the output features below, divided by
        // this xy-only scale -- only the scale computation itself must exclude it.
        val scale = sqrt(dx * dx + dy * dy).coerceAtLeast(MIN_SCALE)

        val out = FloatArray(21 * 3)
        for (i in landmarks.indices) {
            val lm = landmarks[i]
            out[i * 3] = (lm.x() - wrist.x()) * aspect / scale
            out[i * 3 + 1] = (lm.y() - wrist.y()) / scale
            out[i * 3 + 2] = (lm.z() - wrist.z()) * aspect / scale
        }
        return out
    }

    /**
     * Where a hand is relative to the signer's body, in shoulder-widths: 4 floats =
     * [(centroid - shoulderCenter) / width (x, y), (centroid - nose) / width (x, y)].
     * Ports the location features the training pipeline computes from the same three body
     * points; the hand centroid is the mean of the 21 landmarks in pixel coordinates.
     */
    fun handLocation(
        landmarks: List<NormalizedLandmark>,
        frameWidth: Int,
        frameHeight: Int,
        body: BodyReference
    ): FloatArray {
        var sx = 0f
        var sy = 0f
        for (lm in landmarks) {
            sx += lm.x()
            sy += lm.y()
        }
        val cx = sx / landmarks.size * frameWidth
        val cy = sy / landmarks.size * frameHeight
        val w = body.shoulderWidth
        return floatArrayOf(
            (cx - body.shoulderCenterX) / w,
            (cy - body.shoulderCenterY) / w,
            (cx - body.noseX) / w,
            (cy - body.noseY) / w
        )
    }

    fun emptyLocation(): FloatArray = FloatArray(4)

    /** A hand slot with no detection this frame -- all zeros, same shape as a real hand. */
    fun emptyHand(): FloatArray = FloatArray(21 * 3)
}
