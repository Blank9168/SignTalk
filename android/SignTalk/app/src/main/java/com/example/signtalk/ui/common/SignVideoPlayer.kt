package com.example.signtalk.ui.common

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.view.Surface
import android.view.TextureView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Plays one sign clip on a [TextureView] with a raw [MediaPlayer] (same approach as the
 * original dictionary detail screen: TextureView avoids the SurfaceView-in-Fragment black-box
 * bug). Adds what the expert review asked for: replay from the start and slow playback
 * (0.5x) for learning a sign.
 *
 * Call [release] from the owning Fragment's onDestroyView.
 */
class SignVideoPlayer(
    private val context: Context,
    private val textureView: TextureView,
    private val logTag: String = "SignVideoPlayer"
) {
    /** Loop the clip (dictionary, examples) or stop on the last frame (Translate sequence). */
    var looping: Boolean = true
        set(value) {
            field = value
            player?.isLooping = value
        }

    /** Playback speed; 1.0 = normal, 0.5 = slow. */
    var speed: Float = 1f
        set(value) {
            field = value
            applySpeed()
        }

    /** Called when a non-looping clip reaches its end. */
    var onCompletion: (() -> Unit)? = null

    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var pendingUri: Uri? = null
    private var prepared = false

    init {
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                surface = Surface(st)
                pendingUri?.let { start(it) }
            }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                releasePlayer()
                surface?.release()
                surface = null
                return true
            }
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
        }
        if (textureView.isAvailable) {
            textureView.surfaceTexture?.let { surface = Surface(it) }
        }
    }

    fun play(uri: Uri) {
        pendingUri = uri
        if (surface != null) start(uri)
    }

    /** Replay from the start. */
    fun restart() {
        val p = player
        if (p != null && prepared) {
            p.seekTo(0)
            p.start()
            applySpeed()
        } else {
            pendingUri?.let { play(it) }
        }
    }

    fun stop() {
        pendingUri = null
        releasePlayer()
    }

    private fun start(uri: Uri) {
        val s = surface ?: return
        releasePlayer()
        try {
            player = MediaPlayer().apply {
                setSurface(s)
                setDataSource(context, uri)
                isLooping = looping
                setOnPreparedListener { mp ->
                    prepared = true
                    textureView.fitVideo(mp.videoWidth, mp.videoHeight)
                    mp.start()
                    applySpeed()
                }
                setOnCompletionListener { if (!looping) onCompletion?.invoke() }
                setOnErrorListener { _, what, extra ->
                    Log.e(logTag, "Video playback error: what=$what extra=$extra uri=$uri")
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            // IOException, or SecurityException when access to a user-picked video was revoked.
            Log.e(logTag, "Failed to set video data source: $uri", e)
        }
    }

    private fun applySpeed() {
        val p = player ?: return
        if (!prepared) return
        try {
            val wasPlaying = p.isPlaying
            p.playbackParams = p.playbackParams.setSpeed(speed)
            if (!wasPlaying && !looping) p.pause()
        } catch (e: Exception) {
            // Some codecs/devices do not support speed changes; keep normal speed.
            Log.w(logTag, "Playback speed $speed not supported: ${e.message}")
        }
    }

    private fun releasePlayer() {
        prepared = false
        player?.apply {
            setOnPreparedListener(null)
            setOnErrorListener(null)
            setOnCompletionListener(null)
            try {
                reset()
            } catch (e: IllegalStateException) {
                // already released / in error state
            }
            release()
        }
        player = null
    }

    fun release() {
        releasePlayer()
        surface?.release()
        surface = null
        pendingUri = null
        onCompletion = null
    }

    companion object {
        /** True if a bundled reference clip exists for this sign label. */
        fun hasBundledClip(context: Context, label: String): Boolean = try {
            context.assets.openFd("sign_videos/$label.mp4").close()
            true
        } catch (e: IOException) {
            false
        }

        /**
         * Copies `assets/sign_videos/<label>.mp4` to the cache once and returns a file Uri
         * (MediaPlayer cannot open "file:///android_asset/" Uris). Null if there is no clip.
         */
        suspend fun bundledClipUri(context: Context, label: String): Uri? = withContext(Dispatchers.IO) {
            if (!hasBundledClip(context, label)) return@withContext null
            val dir = File(context.cacheDir, "sign_videos").apply { mkdirs() }
            val out = File(dir, "$label.mp4")
            if (!out.exists() || out.length() == 0L) {
                context.assets.open("sign_videos/$label.mp4").use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
            }
            Uri.fromFile(out)
        }
    }
}
