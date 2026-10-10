package com.example.signtalk.ui.common

import android.graphics.Matrix
import android.view.TextureView
import androidx.core.view.doOnNextLayout

/**
 * A [TextureView] stretches video to fill the whole view. The bundled sign
 * clips come in two shapes (640x360 FSL clips, 640x480 ASL clips), so without
 * this the signer looks squashed or stretched. This scales the picture back to
 * its real aspect ratio, centred, leaving dark bars (the card background)
 * around it instead of distorting it.
 */
fun TextureView.fitVideo(videoWidth: Int, videoHeight: Int) {
    if (videoWidth <= 0 || videoHeight <= 0) return
    if (width == 0 || height == 0) {
        doOnNextLayout { applyVideoFit(videoWidth, videoHeight) }
        return
    }
    applyVideoFit(videoWidth, videoHeight)
}

private fun TextureView.applyVideoFit(videoWidth: Int, videoHeight: Int) {
    if (width == 0 || height == 0) return
    val viewWidth = width.toFloat()
    val viewHeight = height.toFloat()
    val scale = minOf(viewWidth / videoWidth, viewHeight / videoHeight)
    val matrix = Matrix().apply {
        setScale(
            videoWidth * scale / viewWidth,
            videoHeight * scale / viewHeight,
            viewWidth / 2f,
            viewHeight / 2f
        )
    }
    setTransform(matrix)
}
