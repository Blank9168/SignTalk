package com.example.signtalk.recognition

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker

/** Where the signer's head/shoulders are, in pixels of the analyzed frame. */
data class BodyReference(
    val noseX: Float,
    val noseY: Float,
    val shoulderCenterX: Float,
    val shoulderCenterY: Float,
    val shoulderWidth: Float
)

/**
 * Tracks a stable reference point on the signer's body (nose + shoulders) with MediaPipe's
 * PoseLandmarker so the classifier can tell WHERE a hand is (forehead vs chin, chest vs waist),
 * which the wrist-centered hand features throw away. Training used a per-clip median of the
 * same three points (see ai/training location features); live we keep a smoothed running value,
 * refreshed every [everyNthFrame] frames because the pose model is heavier than the hand model.
 *
 * Needs `pose_landmarker.task` in app/src/main/assets/ (MediaPipe "pose_landmarker_full"). If
 * it is missing, [isAvailable] stays false and the engine falls back to the shape-only model.
 */
class BodyReferenceTracker(private val context: Context, private val everyNthFrame: Int = 5) {

    private var landmarker: PoseLandmarker? = null
    var isAvailable: Boolean = false
        private set

    @Volatile
    var reference: BodyReference? = null
        private set

    private var frameCounter = 0

    fun setup() {
        isAvailable = try {
            context.assets.open(ASSET).close()
            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder().setModelAssetPath(ASSET).setDelegate(Delegate.CPU).build()
                )
                .setRunningMode(RunningMode.IMAGE)
                .setNumPoses(1)
                .setMinPoseDetectionConfidence(0.5f)
                .setMinPosePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .build()
            landmarker = PoseLandmarker.createFromOptions(context, options)
            true
        } catch (e: Exception) {
            landmarker?.close()
            landmarker = null
            false
        }
    }

    /** Call for every analyzed frame (upright, unmirrored bitmap); runs pose detection only every Nth. */
    fun update(bitmap: Bitmap) {
        val lm = landmarker ?: return
        val due = reference == null || frameCounter % everyNthFrame == 0
        frameCounter++
        if (!due) return
        try {
            val result = lm.detect(BitmapImageBuilder(bitmap).build())
            val pose = result.landmarks().firstOrNull() ?: return
            if (pose.size <= 12) return
            val nose = pose[0]
            val ls = pose[11]
            val rs = pose[12]
            val vis = minOf(
                nose.visibility().orElse(1f), ls.visibility().orElse(1f), rs.visibility().orElse(1f)
            )
            if (vis < 0.5f) return
            val w = bitmap.width.toFloat()
            val h = bitmap.height.toFloat()
            val lsx = ls.x() * w
            val lsy = ls.y() * h
            val rsx = rs.x() * w
            val rsy = rs.y() * h
            val width = kotlin.math.hypot(lsx - rsx, lsy - rsy)
            if (width < 1f) return
            val fresh = BodyReference(
                noseX = nose.x() * w,
                noseY = nose.y() * h,
                shoulderCenterX = (lsx + rsx) / 2f,
                shoulderCenterY = (lsy + rsy) / 2f,
                shoulderWidth = width
            )
            val old = reference
            reference = if (old == null) fresh else BodyReference(
                noseX = lerp(old.noseX, fresh.noseX),
                noseY = lerp(old.noseY, fresh.noseY),
                shoulderCenterX = lerp(old.shoulderCenterX, fresh.shoulderCenterX),
                shoulderCenterY = lerp(old.shoulderCenterY, fresh.shoulderCenterY),
                shoulderWidth = lerp(old.shoulderWidth, fresh.shoulderWidth)
            )
        } catch (_: Exception) {
            // Best effort: keep the last known reference.
        }
    }

    fun reset() {
        reference = null
        frameCounter = 0
    }

    fun close() {
        landmarker?.close()
        landmarker = null
    }

    private fun lerp(old: Float, new: Float, alpha: Float = 0.3f) = old + alpha * (new - old)

    companion object {
        const val ASSET = "pose_landmarker.task"
    }
}
