package com.example.signtalk.recognition

import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONObject

/**
 * Loads `sign_landmarks.bin`: one real, hand-visible 30-frame landmark
 * sequence per FSL-105 class, in the same 126-feature-per-frame layout
 * (63 left-hand + 63 right-hand floats, each hand centered on its own
 * wrist and scaled -- see [LandmarkNormalizer] / `ai/dataset/landmarks.py`)
 * that the live recognition pipeline itself uses. Each sequence was sourced
 * directly from a real `ai/dataset/raw/<label>/ *.npy` training clip and
 * resampled to a clean, continuous 30-frame loop (no dead/no-hand frames --
 * see the asset-generation notes in docs/MOBILE_APP_NOTES.md).
 *
 * This is what powers the Text -> Sign direction: the mirror image of
 * on-device recognition, using the exact representation the model was
 * trained on instead of a bundled video per sign.
 */
class SignLandmarksProvider(private val context: Context) {

    companion object {
        private const val BIN_ASSET = "sign_landmarks.bin"
        private const val LABELS_ASSET = "labels.json"
        const val SEQUENCE_LENGTH = 30
        const val FEATURES_PER_FRAME = 126
    }

    private var sequencesByLabel: Map<String, Array<FloatArray>> = emptyMap()
    var isAvailable: Boolean = false
        private set

    fun setup() {
        isAvailable = try {
            val labels = loadLabelOrder()
            val bytes = context.assets.open(BIN_ASSET).use { it.readBytes() }
            val expectedFloats = labels.size * SEQUENCE_LENGTH * FEATURES_PER_FRAME
            require(bytes.size / 4 == expectedFloats) {
                "sign_landmarks.bin size mismatch: expected $expectedFloats floats, got ${bytes.size / 4}"
            }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val map = HashMap<String, Array<FloatArray>>(labels.size)
            for (label in labels) {
                val frames = Array(SEQUENCE_LENGTH) { FloatArray(FEATURES_PER_FRAME) }
                for (t in 0 until SEQUENCE_LENGTH) {
                    for (f in 0 until FEATURES_PER_FRAME) {
                        frames[t][f] = buffer.float
                    }
                }
                map[label] = frames
            }
            sequencesByLabel = map
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun loadLabelOrder(): List<String> {
        val json = context.assets.open(LABELS_ASSET).bufferedReader().use { it.readText() }
        val arr = JSONObject(json).getJSONArray("labels")
        return (0 until arr.length()).map { arr.getJSONObject(it).getString("label") }
    }

    /** The 30-frame landmark sequence for [label] (matching [com.example.signtalk.domain.model.DictionaryEntry.label]), or null if unavailable. */
    fun sequenceFor(label: String): Array<FloatArray>? = sequencesByLabel[label]
}
