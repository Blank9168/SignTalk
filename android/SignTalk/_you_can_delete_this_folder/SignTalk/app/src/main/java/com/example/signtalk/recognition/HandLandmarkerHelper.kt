package com.example.signtalk.recognition

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.WorkerThread
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

/**
 * Wraps MediaPipe Tasks' HandLandmarker in LIVE_STREAM mode, mirroring the
 * extraction step of `ai/dataset/landmarks.py` on-device.
 *
 * Requires `hand_landmarker.task` in app/src/main/assets/. That file is a
 * small (~8-10MB) pretrained MediaPipe model downloaded from Google's model
 * host, which is not reachable from every build/dev environment -- it is
 * intentionally NOT bundled in this scaffold. See docs/MOBILE_APP_NOTES.md
 * for the download link and where to drop it. Until it's present,
 * [isModelAvailable] stays false and [Listener.onError] fires once.
 */
class HandLandmarkerHelper(
    private val context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onResult(result: HandLandmarkerResult)
        fun onError(message: String)
    }

    private var handLandmarker: HandLandmarker? = null
    var isModelAvailable: Boolean = false
        private set

    fun setup() {
        val modelAsset = "hand_landmarker.task"
        if (!assetExists(modelAsset)) {
            isModelAvailable = false
            listener.onError("hand_landmarker.task not found in assets/. See docs/MOBILE_APP_NOTES.md.")
            return
        }
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(modelAsset)
                .setDelegate(Delegate.CPU)
                .build()
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(2)
                .setMinHandDetectionConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setResultListener { result, _ -> listener.onResult(result) }
                .setErrorListener { e -> listener.onError(e.message ?: "Hand landmarker error") }
                .build()
            handLandmarker = HandLandmarker.createFromOptions(context, options)
            isModelAvailable = true
        } catch (e: Exception) {
            isModelAvailable = false
            listener.onError("Failed to load hand landmark model: ${e.message}")
        }
    }

    /**
     * [bitmap] must be the raw (unmirrored) camera frame -- mirror only a
     * display copy for the on-screen preview, never the frame fed here.
     * Feeding a mirrored frame trained left/right hands backwards in an
     * earlier version of this pipeline (see project notes).
     */
    @WorkerThread
    fun detectAsync(bitmap: Bitmap, timestampMs: Long) {
        val mpImage: MPImage = BitmapImageBuilder(bitmap).build()
        handLandmarker?.detectAsync(mpImage, timestampMs)
    }

    private fun assetExists(name: String): Boolean = try {
        context.assets.open(name).use { true }
    } catch (e: Exception) {
        false
    }

    fun close() {
        handLandmarker?.close()
        handLandmarker = null
    }
}
