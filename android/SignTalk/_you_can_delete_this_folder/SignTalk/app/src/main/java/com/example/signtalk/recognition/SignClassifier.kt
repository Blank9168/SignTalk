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
        private const val LABELS_ASSET = "labels.json"
        const val SEQUENCE_LENGTH = 30
        const val FEATURES_PER_FRAME = 126
    }

    private var interpreter: Interpreter? = null
    var labelEntries: List<LabelEntry> = emptyList()
        private set
    var isModelAvailable: Boolean = false
        private set

    fun setup(): Boolean {
        labelEntries = loadLabels()
        isModelAvailable = try {
            interpreter = Interpreter(loadModelFile(MODEL_ASSET))
            true
        } catch (e: Exception) {
            false
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
        require(sequence.size == SEQUENCE_LENGTH * FEATURES_PER_FRAME) {
            "Expected ${SEQUENCE_LENGTH * FEATURES_PER_FRAME} floats, got ${sequence.size}"
        }

        val input = Array(1) { Array(SEQUENCE_LENGTH) { FloatArray(FEATURES_PER_FRAME) } }
        for (t in 0 until SEQUENCE_LENGTH) {
            for (f in 0 until FEATURES_PER_FRAME) {
                input[0][t][f] = sequence[t * FEATURES_PER_FRAME + f]
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
