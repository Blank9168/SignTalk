package com.example.signtalk.ui.dictionary

import android.content.Intent
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.MediaController
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.signtalk.SignTalkApp
import com.example.signtalk.databinding.FragmentDictionaryDetailBinding
import com.example.signtalk.domain.model.DictionaryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class DictionaryDetailFragment : Fragment() {

    private var _binding: FragmentDictionaryDetailBinding? = null
    private val binding get() = _binding!!

    private var entryId: Long = 0L

    // --- Video playback (TextureView + raw MediaPlayer) ---
    //
    // NOTE (fix, 2026-09-07 round 2): this used to be a plain android.widget.
    // VideoView, which draws via an internal SurfaceView. SurfaceView
    // composites through its own separate window-manager-positioned surface
    // rather than through the normal View drawing pass, and it's a
    // long-documented source of "shows a black box, no error, sometimes no
    // audio either" failures specifically when hosted inside a Fragment
    // (as opposed to directly in an Activity) -- the surface can end up
    // positioned/composited against a stale or not-yet-ready window frame.
    // Fixing the earlier file:///android_asset/ Uri bug (see git history /
    // project notes) made the video *loadable*, but this separate,
    // unrelated SurfaceView-in-Fragment issue is what was still blocking it
    // from actually being visible. TextureView draws through the normal
    // View hierarchy (like an ImageView would) and doesn't have this class
    // of bug, so it's used here instead, with a small hand-rolled
    // MediaController.MediaPlayerControl to keep the same tap-to-show
    // play/pause/seek UI VideoView used to provide for free.
    private var mediaPlayer: MediaPlayer? = null
    private var mediaController: MediaController? = null
    private var pendingUri: Uri? = null
    private var currentSurface: Surface? = null

    private val playerControl = object : MediaController.MediaPlayerControl {
        override fun start() { mediaPlayer?.start() }
        override fun pause() { mediaPlayer?.pause() }
        override fun getDuration(): Int = mediaPlayer?.duration ?: 0
        override fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0
        override fun seekTo(pos: Int) { mediaPlayer?.seekTo(pos) }
        override fun isPlaying(): Boolean = mediaPlayer?.isPlaying ?: false
        override fun getBufferPercentage(): Int = 0
        override fun canPause(): Boolean = true
        override fun canSeekBackward(): Boolean = true
        override fun canSeekForward(): Boolean = true
        override fun getAudioSessionId(): Int = mediaPlayer?.audioSessionId ?: 0
    }

    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            currentSurface = Surface(surface)
            pendingUri?.let { startPlayback(it) }
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            releasePlayer()
            currentSurface?.release()
            currentSurface = null
            return true
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }

    // Lets a *custom* entry (one with no bundled FSL-105 clip -- see bindVideo
    // below) get a video attached or replaced. Uses ACTION_OPEN_DOCUMENT (via
    // this contract) rather than GET_CONTENT specifically because it's the
    // one that lets us take a *persistable* read permission below --
    // GET_CONTENT's grant only lasts the current app process, so the URI
    // would stop resolving the next time the app is launched and this sign is
    // viewed again. Registered as a property (runs during the fragment's
    // construction) rather than inside onViewCreated, since
    // registerForActivityResult must be called before the fragment reaches
    // STARTED.
    private val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        requireContext().contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        val appContainer = (requireActivity().application as SignTalkApp).container
        viewLifecycleOwner.lifecycleScope.launch {
            appContainer.dictionaryRepository.setVideoUri(entryId, uri.toString())
            showVideo(uri)
            binding.addVideoButton.visibility = View.GONE
            binding.changeVideoButton.visibility = View.VISIBLE
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDictionaryDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val appContainer = (requireActivity().application as SignTalkApp).container
        entryId = arguments?.getLong("entryId") ?: 0L

        binding.entryVideoView.surfaceTextureListener = surfaceTextureListener

        binding.addVideoButton.setOnClickListener {
            pickVideoLauncher.launch(arrayOf("video/*"))
        }
        binding.changeVideoButton.setOnClickListener {
            pickVideoLauncher.launch(arrayOf("video/*"))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val entry = appContainer.dictionaryRepository.getEntry(entryId)
            if (entry == null) {
                binding.loadingText.text = "Sign not found."
                return@launch
            }
            (requireActivity() as? AppCompatActivity)?.supportActionBar?.title = entry.displayName

            binding.loadingText.visibility = View.GONE
            binding.contentGroup.visibility = View.VISIBLE
            binding.entryEmoji.text = entry.emoji
            binding.entryCategory.text = entry.category
            binding.entryDescription.text = entry.description
            bindVideo(entry)

            if (entry.isUserAdded) {
                binding.deleteButton.visibility = View.VISIBLE
                binding.deleteButton.setOnClickListener {
                    viewLifecycleOwner.lifecycleScope.launch {
                        appContainer.dictionaryRepository.deleteEntry(entry.id)
                        findNavController().popBackStack()
                    }
                }
            }
        }
    }

    /**
     * Shows this sign's real FSL-105 training clip, bundled into the app as an
     * asset, if one exists for it. That covers all 50 seeded dictionary
     * signs -- for those there is nothing to "add" or "change", so neither
     * button is shown at all.
     *
     * Otherwise (a custom, user-added sign with no training footage) falls
     * back to the manual picker: shows whatever video the user has attached,
     * with a "Change video" button, or an "Add video" prompt if nothing has
     * been attached yet.
     */
    private suspend fun bindVideo(entry: DictionaryEntry) {
        val assetPath = "sign_videos/${entry.label}.mp4"
        if (hasAsset(assetPath)) {
            // A "file:///android_asset/..." Uri can't be handed to
            // MediaPlayer directly (that scheme only resolves in WebView --
            // see project notes for the full explanation), so the asset is
            // copied once into the app's cache dir and played from there via
            // a real file Uri.
            val realUri = copyAssetToCache(assetPath)
            showVideo(realUri)
            binding.addVideoButton.visibility = View.GONE
            binding.changeVideoButton.visibility = View.GONE
            return
        }

        val uriString = entry.videoUri
        if (uriString.isNullOrBlank()) {
            binding.addVideoButton.visibility = View.VISIBLE
            binding.entryVideoView.visibility = View.GONE
            binding.changeVideoButton.visibility = View.GONE
        } else {
            showVideo(Uri.parse(uriString))
            binding.addVideoButton.visibility = View.GONE
            binding.changeVideoButton.visibility = View.VISIBLE
        }
    }

    /** True if `assets/<path>` exists and can be opened. */
    private fun hasAsset(path: String): Boolean =
        try {
            requireContext().assets.openFd(path).close()
            true
        } catch (e: IOException) {
            false
        }

    /**
     * Copies a bundled asset video to this app's cache dir (once -- reused on
     * later opens) and returns a real file:// Uri pointing at that copy. See
     * the note in bindVideo() above for why a plain "file:///android_asset/"
     * Uri can't be handed to the player directly.
     */
    private suspend fun copyAssetToCache(assetPath: String): Uri = withContext(Dispatchers.IO) {
        val fileName = assetPath.substringAfterLast('/')
        val cacheDir = File(requireContext().cacheDir, "sign_videos").apply { mkdirs() }
        val outFile = File(cacheDir, fileName)
        if (!outFile.exists() || outFile.length() == 0L) {
            requireContext().assets.open(assetPath).use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        Uri.fromFile(outFile)
    }

    private fun showVideo(uri: Uri) {
        binding.entryVideoView.visibility = View.VISIBLE
        if (mediaController == null) {
            mediaController = MediaController(requireContext()).apply {
                setAnchorView(binding.entryVideoView)
            }
        }
        mediaController?.setMediaPlayer(playerControl)

        pendingUri = uri
        val surface = currentSurface
        if (surface != null) {
            // TextureView is already attached to a window and its
            // SurfaceTexture already exists (e.g. re-opening/changing the
            // video on a fragment that's already visible) -- start right
            // away rather than waiting for onSurfaceTextureAvailable, which
            // won't fire again for an already-available texture.
            startPlayback(uri)
        }
        // Otherwise onSurfaceTextureAvailable (registered in onViewCreated)
        // will call startPlayback() itself once the TextureView's surface is
        // ready -- this is the normal first-open path, since the view was
        // "gone" until the line above just made it visible.
    }

    private fun startPlayback(uri: Uri) {
        val surface = currentSurface ?: return
        releasePlayer()
        try {
            mediaPlayer = MediaPlayer().apply {
                setSurface(surface)
                setDataSource(requireContext(), uri)
                // Loop the clip -- these are short (~4s) reference signs, and the
                // whole point of watching one is to be able to rewatch it as many
                // times as needed to learn the movement, without having to dig up
                // the MediaController and tap play again every few seconds.
                isLooping = true
                setOnPreparedListener { mp ->
                    mp.start()
                    mediaController?.let {
                        it.setEnabled(true)
                        it.show(3000)
                    }
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("DictionaryDetail", "Video playback error: what=$what extra=$extra uri=$uri")
                    true // consume -- don't let the framework pop its own dialog
                }
                prepareAsync()
            }
        } catch (e: IOException) {
            Log.e("DictionaryDetail", "Failed to set video data source: $uri", e)
        }
    }

    private fun releasePlayer() {
        mediaPlayer?.apply {
            setOnPreparedListener(null)
            setOnErrorListener(null)
            try {
                reset()
            } catch (e: IllegalStateException) {
                // already in an error/released state -- fine to ignore, we're tearing it down anyway
            }
            release()
        }
        mediaPlayer = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        releasePlayer()
        currentSurface?.release()
        currentSurface = null
        mediaController = null
        pendingUri = null
        _binding = null
    }
}
