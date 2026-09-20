package com.example.signtalk.recognition

import android.content.Context
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp
import org.json.JSONObject
import org.tensorflow.lite.Interpreter

data class LabelEntry(val label: String, val displayName: String, val emoji: String)

/**
 * Wraps the TFLite export of `ai/models/model.py`'s SignLSTM. Requires
 * `sign_lstm.tflite` in app/src/main/assets/, converted from the trained
 * `sign_lstm.pt` -- see docs/MOBILE_APP_NOTES.md for the conversion steps.
 * That weights file isn't produced or shipped by this scaffold; until it's
 * added, [isModelAvailable] is false and the recognition screen shows a
 * clear "model not found" state instead of guessing.
 */
class SignClassifier(private val context: Context) {

    companion object {
        private const val MODEL_ASSET = "sign_lstm.tflite"
        // Hands-only model trained on aspect-corrected features (ai/dataset/fix_aspect.py).
        // Optional: when this file is in assets/ it wins over every other model and the engine
        // switches on aspect correction with it; delete it to fall back to the legacy models.
        private const val ISO_MODEL_ASSET = "sign_lstm_iso.tflite"
        private const val LOCATION_MODEL_ASSET = "sign_lstm_loc.tflite"
        private const val LABELS_ASSET = "labels.json"
        const val SEQUENCE_LENGTH = 30
        const val FEATURES_PER_FRAME = 126
        const val FEATURES_WITH_LOCATION = 134
    }

    /** Per-frame feature count of the loaded model: 126 (hands only) or 134 (hands + body location). */
    var featuresPerFrame: Int = FEATURES_PER_FRAME
        private set
    val usesLocation: Boolean get() = featuresPerFrame == FEATURES_WITH_LOCATION

    /** True when the loaded model expects aspect-corrected hand features (see LandmarkNormalizer). */
    var usesAspectCorrection: Boolean = false
        private set

    private var interpreter: Interpreter? = null
    var labelEntries: List<LabelEntry> = emptyList()
        private set
    var isModelAvailable: Boolean = false
        private set

    /**
     * Loads the classifier. When [preferLocation] is true (the pose model is available) and
     * `sign_lstm_loc.tflite` exists, that body-location-aware model is used; otherwise falls
     * back to the original hands-only `sign_lstm.tflite`.
     */
    fun setup(preferLocation: Boolean = false): Boolean {
        interpreter?.close()
        interpreter = null
        labelEntries = loadLabels()
        val candidates = buildList {
            add(ISO_MODEL_ASSET)
            if (preferLocation) add(LOCATION_MODEL_ASSET)
            add(MODEL_ASSET)
        }
        isModelAvailable = false
        for (asset in candidates) {
            try {
                val interp = Interpreter(loadModelFile(asset))
                val shape = interp.getInputTensor(0).shape() // [1, 30, F]
                interpreter = interp
                featuresPerFrame = shape[2]
                usesAspectCorrection = asset == ISO_MODEL_ASSET
                isModelAvailable = true
                break
            } catch (e: Exception) {
                // try the next candidate
            }
        }
        return isModelAvailable
    }

    private fun loadLabels(): List<LabelEntry> = try {
        val json = context.assets.open(LABELS_ASSET).bufferedReader().use { it.readText() }
        val arr = JSONObject(json).getJSONArray("labels")
        (0 until arr.length()).map { i ->
            val item = arr.getJSONObject(i)
            LabelEntry(item.getString("label"), item.getString("displayName"), item.getString("emoji"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun loadModelFile(assetName: String): MappedByteBuffer {
        val afd = context.assets.openFd(assetName)
        FileInputStream(afd.fileDescriptor).use { input ->
            return input.channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
        }
    }

    /** Classifies a flattened (SEQUENCE_LENGTH * FEATURES_PER_FRAME) sequence. Null if model/labels aren't loaded. */
    fun classify(sequence: FloatArray): Pair<Int, Float>? {
        val interp = interpreter ?: return null
        val numClasses = labelEntries.size
        if (numClasses == 0) return null
        require(sequence.size == SEQUENCE_LENGTH * featuresPerFrame) {
            "Expected ${SEQUENCE_LENGTH * featuresPerFrame} floats, got ${sequence.size}"
        }

        val input = Array(1) { Array(SEQUENCE_LENGTH) { FloatArray(featuresPerFrame) } }
        for (t in 0 until SEQUENCE_LENGTH) {
            for (f in 0 until featuresPerFrame) {
                input[0][t][f] = sequence[t * featuresPerFrame + f]
            }
        }
        val output = Array(1) { FloatArray(numClasses) }
        interp.run(input, output)

        val probs = softmax(output[0])
        var bestIdx = 0
        for (i in probs.indices) if (probs[i] > probs[bestIdx]) bestIdx = i
        return bestIdx to probs[bestIdx]
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.maxOrNull() ?: 0f
        val exps = FloatArray(logits.size) { exp((logits[it] - max).toDouble()).toFloat() }
        val sum = exps.sum().coerceAtLeast(1e-9f)
        return FloatArray(exps.size) { exps[it] / sum }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
