package com.example.signtalk.recognition

/**
 * Rolling buffer of the last [sequenceLength] frames, each frame being
 * `[leftHand(63) + rightHand(63)] = 126` floats -- matches the (30, 126)
 * input shape `ai/training/train.py` trains the BiLSTM against. Hand order
 * (left features first, then right) must match whatever convention
 * `ai/dataset/landmarks.py` used when building training sequences.
 */
class SequenceBuffer(
    private val sequenceLength: Int = 30,
    private val featuresPerFrame: Int = 126
) {
    private val frames = ArrayDeque<FloatArray>()

    val isFull: Boolean get() = frames.size >= sequenceLength
    val size: Int get() = frames.size

    fun push(leftHand: FloatArray, rightHand: FloatArray) {
        val frame = FloatArray(featuresPerFrame)
        leftHand.copyInto(frame, destinationOffset = 0)
        rightHand.copyInto(frame, destinationOffset = leftHand.size)
        frames.addLast(frame)
        while (frames.size > sequenceLength) {
            frames.removeFirst()
        }
    }

    /** Pushes a fully built frame (already [featuresPerFrame] floats), e.g. hands + body-location features. */
    fun pushFrame(frame: FloatArray) {
        require(frame.size == featuresPerFrame) { "Expected $featuresPerFrame floats, got ${frame.size}" }
        frames.addLast(frame)
        while (frames.size > sequenceLength) {
            frames.removeFirst()
        }
    }

    fun clear() = frames.clear()

    /** Flattened (sequenceLength * featuresPerFrame) array, oldest frame first, or null if not full. */
    fun toFlatArrayOrNull(): FloatArray? {
        if (!isFull) return null
        val out = FloatArray(sequenceLength * featuresPerFrame)
        frames.forEachIndexed { i, frame -> frame.copyInto(out, destinationOffset = i * featuresPerFrame) }
        return out
    }

    /**
     * Same as [toFlatArrayOrNull], but allows classifying before the buffer is fully
     * populated: once at least [minFrames] real frames have been collected, the available
     * frames are **uniformly resampled** to fill all [sequenceLength] slots, rather than
     * making the caller wait for a full [sequenceLength] of real frames.
     *
     * This deliberately mirrors `ai/dataset/landmarks.py`'s `sample_to_fixed_length()` --
     * the exact function training used to turn FSL-105's variable-length clips into fixed
     * 30-frame sequences (`indices = round(linspace(0, t-1, target_len))`). Using the same
     * resampling here, instead of e.g. repeating the oldest frame to fill the gap, matters
     * most for signs with real motion in them: stretching the T real frames collected *so
     * far* proportionally across all 30 slots represents "the gesture as captured so far,
     * sped up to fill the window" -- much closer to what a genuinely shorter training clip
     * looked like after resampling -- rather than "the gesture's first instant, frozen and
     * held for several extra frames," which is what naive front-padding would produce and
     * which has no real analog in how the model was trained. It's still an approximation
     * (a still-in-progress motion sign genuinely hasn't happened yet, no resampling trick
     * changes that) -- see [minFrames]'s caller for how that residual risk is bounded.
     *
     * As more real frames keep arriving each call, T grows and the resampled points shift
     * to draw from a larger, more complete window, converging on [toFlatArrayOrNull]'s
     * exact behavior once T reaches [sequenceLength].
     *
     * Returns null if fewer than [minFrames] frames are available yet.
     */
    fun toFlatArrayResampled(minFrames: Int): FloatArray? {
        val t = frames.size
        if (t < minFrames) return null
        val out = FloatArray(sequenceLength * featuresPerFrame)
        if (t == sequenceLength) {
            frames.forEachIndexed { i, frame -> frame.copyInto(out, destinationOffset = i * featuresPerFrame) }
            return out
        }
        for (i in 0 until sequenceLength) {
            val srcIndex = if (sequenceLength == 1) {
                0
            } else {
                Math.round(i.toFloat() * (t - 1) / (sequenceLength - 1).toFloat())
            }
            frames[srcIndex].copyInto(out, destinationOffset = i * featuresPerFrame)
        }
        return out
    }
}
