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

    fun clear() = frames.clear()

    /** Flattened (sequenceLength * featuresPerFrame) array, oldest frame first, or null if not full. */
    fun toFlatArrayOrNull(): FloatArray? {
        if (!isFull) return null
        val out = FloatArray(sequenceLength * featuresPerFrame)
        frames.forEachIndexed { i, frame -> frame.copyInto(out, destinationOffset = i * featuresPerFrame) }
        return out
    }
}
