package com.example.signtalk.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Live camera preview + frame feed for recognition (plain View-world
 * replacement for the old Compose `CameraPreview` composable -- same
 * CameraX wiring, just bound directly against a [PreviewView] instead of
 * through `AndroidView`). [onFrame] is called on a background thread with
 * each analyzed frame already rotated upright and NOT mirrored -- feed it
 * straight into [com.example.signtalk.recognition.RecognitionEngine]; only
 * the preview itself is mirrored for a natural "selfie" look when the front
 * camera is used (see the mirroring bug noted in project docs).
 */
class CameraController(private val context: Context) {

    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null

    /** Binds (or re-binds, e.g. after a front/back camera toggle) the preview + analyzer. */
    fun bind(
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner,
        useFrontCamera: Boolean,
        onFrame: (bitmap: Bitmap, timestampMs: Long) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            cameraProvider = provider

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                handleFrame(imageProxy, onFrame)
            }
            val selector = if (useFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            try {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            } catch (e: Exception) {
                Log.e("CameraController", "Failed to bind camera use cases", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun shutdown() {
        cameraProvider?.unbindAll()
        analysisExecutor.shutdown()
    }
}

private fun handleFrame(imageProxy: ImageProxy, onFrame: (Bitmap, Long) -> Unit) {
    try {
        val rawBitmap = imageProxy.toBitmap()
        val rotation = imageProxy.imageInfo.rotationDegrees
        val upright = if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
        } else {
            rawBitmap
        }
        onFrame(upright, imageProxy.imageInfo.timestamp)
    } catch (e: Exception) {
        Log.e("CameraController", "Failed to process frame", e)
    } finally {
        imageProxy.close()
    }
}
