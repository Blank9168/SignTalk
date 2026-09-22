package com.example.signtalk.ui.translate

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.signtalk.SignTalkApp
import com.example.signtalk.databinding.FragmentTranslateBinding
import com.example.signtalk.domain.model.DictionaryEntry
import com.example.signtalk.domain.translate.TranslationToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * "Text/Speech to Sign" -- the reverse direction of [com.example.signtalk.ui.recognition.RecognitionFragment].
 * A hearing person types or speaks a word/phrase here (English or Filipino);
 * it's matched against the same 50-sign dictionary and shown back as a
 * sequence of real sign videos, for a deaf/mute person to read. Together
 * with the camera recognizer, this is what makes the app's tagline on the
 * Home screen ("Two-way Filipino Sign Language communication") actually
 * true rather than just a description of the sign-to-text half.
 */
class TranslateFragment : Fragment() {

    private var _binding: FragmentTranslateBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TranslateViewModel by viewModels {
        viewModelFactory {
            initializer {
                val appContainer = (requireActivity().application as SignTalkApp).container
                TranslateViewModel(appContainer.dictionaryRepository)
            }
        }
    }

    // --- Video playback (TextureView + raw MediaPlayer) -- same approach as
    // DictionaryDetailFragment, and for the same reason: a SurfaceView-based
    // VideoView can render as a black box when hosted in a Fragment. Every
    // clip loops continuously (same as the dictionary screen), so advancing
    // through a multi-word sequence can no longer rely on "clip finished" --
    // instead a dwell timer (see startPlayback/scheduleAdvance) lets each
    // non-final sign repeat a couple of times before moving on; the final
    // sign in the sequence just keeps looping with no timer at all.
    private var mediaPlayer: MediaPlayer? = null
    private var pendingUri: Uri? = null
    private var pendingAutoAdvance: Boolean = true
    private var currentSurface: Surface? = null
    // Identifies which sign is currently bound/playing so a fresh translate()
    // call (a brand-new tokens list, even one that happens to land back on
    // index 0) is always detected as "different sign" -- comparing currentIndex
    // alone isn't enough, since two unrelated single-word results both sit at
    // index 0 and would otherwise look unchanged, leaving the old clip playing
    // under the new sign's label.
    private var playingTokens: List<TranslationToken>? = null
    private var playingTokenIndex: Int = -1
    private var advanceJob: Job? = null

    // Live-as-you-type translation: re-matches a short idle moment after the
    // last keystroke, rather than on every single character (which would
    // otherwise fire a Room query per keystroke) or only on submit.
    private var liveTranslateJob: Job? = null

    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            currentSurface = Surface(surface)
            pendingUri?.let { startPlayback(it, pendingAutoAdvance) }
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

    // --- Speech input ---
    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        binding.micPermissionGroup.visibility = View.GONE
        if (granted) launchSpeechRecognizer() else {
            Toast.makeText(requireContext(), "Microphone access is needed to speak instead of type.", Toast.LENGTH_SHORT).show()
        }
    }

    private val speechRecognizerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
            binding.wordInput.setText(spoken)
            liveTranslateJob?.cancel()
            viewModel.translate(spoken)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTranslateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.signVideoView.surfaceTextureListener = surfaceTextureListener

        binding.translateButton.setOnClickListener { submitTypedText() }
        binding.wordInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                submitTypedText()
                true
            } else {
                false
            }
        }
        // Show the matching sign(s) as soon as the user pauses typing --
        // no need to tap "Show Sign(s)" or press Enter for the common case
        // of just typing a word and watching it appear.
        binding.wordInput.addTextChangedListener { editable ->
            liveTranslateJob?.cancel()
            val text = editable?.toString().orEmpty()
            if (text.isBlank()) {
                viewModel.reset()
                return@addTextChangedListener
            }
            liveTranslateJob = viewLifecycleOwner.lifecycleScope.launch {
                delay(LIVE_TRANSLATE_DEBOUNCE_MS)
                viewModel.translate(text)
            }
        }

        binding.micButton.setOnClickListener {
            val hasPermission = ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                launchSpeechRecognizer()
            } else {
                binding.micPermissionGroup.visibility = View.VISIBLE
            }
        }
        binding.grantMicPermissionButton.setOnClickListener {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }

        binding.replayButton.setOnClickListener { viewModel.replay() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> renderState(state) }
            }
        }
    }

    private fun submitTypedText() {
        liveTranslateJob?.cancel()
        val text = binding.wordInput.text?.toString().orEmpty()
        viewModel.translate(text)
    }

    private fun launchSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            Toast.makeText(requireContext(), "Speech recognition isn't available on this device.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a word or phrase to sign")
        }
        try {
            speechRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e("TranslateFragment", "Failed to launch speech recognizer", e)
            Toast.makeText(requireContext(), "Couldn't open speech recognition.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderState(state: TranslateUiState) {
        when (state) {
            is TranslateUiState.Idle, is TranslateUiState.Translating -> {
                binding.resultGroup.visibility = View.GONE
                binding.noSignsText.visibility = View.GONE
            }
            is TranslateUiState.NoSignsFound -> {
                binding.resultGroup.visibility = View.GONE
                binding.noSignsText.visibility = View.VISIBLE
                stopAndClearVideo()
            }
            is TranslateUiState.Result -> renderResult(state)
        }
    }

    private fun renderResult(state: TranslateUiState.Result) {
        binding.resultGroup.visibility = View.VISIBLE
        binding.noSignsText.visibility = View.GONE

        val matchedIndices = state.tokens.indices.filter { state.tokens[it] is TranslationToken.Matched }
        val positionInSequence = matchedIndices.indexOf(state.currentIndex) + 1
        val currentToken = state.tokens[state.currentIndex] as TranslationToken.Matched

        binding.currentSignLabel.text = currentToken.entry.displayName
        binding.progressLabel.text = "Sign $positionInSequence of ${matchedIndices.size}"

        binding.sequenceText.text = "Sequence: " + state.tokens.joinToString("  ") { token ->
            when (token) {
                is TranslationToken.Matched -> token.entry.displayName
                is TranslationToken.Unmatched -> "[${token.word}]"
            }
        }

        val unmatchedWords = state.tokens.filterIsInstance<TranslationToken.Unmatched>().map { it.word }
        if (unmatchedWords.isNotEmpty()) {
            binding.unmatchedText.visibility = View.VISIBLE
            binding.unmatchedText.text = "No sign found for: " + unmatchedWords.joinToString(", ")
        } else {
            binding.unmatchedText.visibility = View.GONE
        }

        val isLastSign = positionInSequence == matchedIndices.size
        binding.replayButton.visibility =
            if (isLastSign && matchedIndices.size > 1) View.VISIBLE else View.GONE

        if (playingTokens !== state.tokens || playingTokenIndex != state.currentIndex) {
            playingTokens = state.tokens
            playingTokenIndex = state.currentIndex
            bindVideo(currentToken.entry, isLastSign)
        }
    }

    /**
     * Shows this sign's bundled reference clip if one exists (same
     * `sign_videos/<label>.mp4` convention as the dictionary detail
     * screen), else whatever video the user attached to a custom entry,
     * else just its emoji as a fallback so the screen isn't blank.
     */
    private fun bindVideo(entry: DictionaryEntry, isLastSign: Boolean) {
        advanceJob?.cancel()
        viewLifecycleOwner.lifecycleScope.launch {
            val assetPath = "sign_videos/${entry.label}.mp4"
            when {
                hasAsset(assetPath) -> showVideo(copyAssetToCache(assetPath), isLastSign)
                !entry.videoUri.isNullOrBlank() -> showVideo(Uri.parse(entry.videoUri), isLastSign)
                else -> showEmojiFallback(entry.emoji, isLastSign)
            }
        }
    }

    private fun hasAsset(path: String): Boolean =
        try {
            requireContext().assets.openFd(path).close()
            true
        } catch (e: IOException) {
            false
        }

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

    /** No video to loop for this entry -- still advances a plain sequence along on a timer. */
    private fun showEmojiFallback(emoji: String, isLastSign: Boolean) {
        stopVideoOnly()
        binding.signEmojiFallback.text = emoji
        binding.signEmojiFallback.visibility = View.VISIBLE
        if (!isLastSign) {
            advanceJob = viewLifecycleOwner.lifecycleScope.launch {
                delay(EMOJI_DWELL_MS)
                viewModel.advance()
            }
        }
    }

    private fun showVideo(uri: Uri, isLastSign: Boolean) {
        binding.signEmojiFallback.visibility = View.GONE
        binding.signVideoView.visibility = View.VISIBLE
        pendingUri = uri
        pendingAutoAdvance = !isLastSign
        if (currentSurface != null) {
            startPlayback(uri, pendingAutoAdvance)
        }
        // Otherwise onSurfaceTextureAvailable will start it once the
        // TextureView's surface is ready (first-open path).
    }

    /**
     * Plays [uri] on a continuous loop -- same as the dictionary detail
     * screen's clips. When [autoAdvance] is true (this isn't the last sign
     * in the current sequence), it also schedules [viewModel.advance] after
     * the clip has played through twice, so the viewer gets to see the sign
     * repeat at least once before the sequence moves on; the last sign in a
     * sequence has [autoAdvance] false and just loops forever.
     */
    private fun startPlayback(uri: Uri, autoAdvance: Boolean) {
        val surface = currentSurface ?: return
        releasePlayer()
        try {
            mediaPlayer = MediaPlayer().apply {
                setSurface(surface)
                setDataSource(requireContext(), uri)
                isLooping = true
                setOnPreparedListener { mp ->
                    mp.start()
                    if (autoAdvance) {
                        val clipDurationMs = if (mp.duration > 0) mp.duration.toLong() else FALLBACK_CLIP_DURATION_MS
                        advanceJob = viewLifecycleOwner.lifecycleScope.launch {
                            delay(clipDurationMs * REPEATS_BEFORE_ADVANCING)
                            viewModel.advance()
                        }
                    }
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("TranslateFragment", "Video playback error: what=$what extra=$extra uri=$uri")
                    true
                }
                prepareAsync()
            }
        } catch (e: IOException) {
            Log.e("TranslateFragment", "Failed to set video data source: $uri", e)
        }
    }

    /** Tears down just the MediaPlayer/surface state, without touching the emoji fallback view. */
    private fun stopVideoOnly() {
        releasePlayer()
        pendingUri = null
        binding.signVideoView.visibility = View.GONE
    }

    private fun stopAndClearVideo() {
        stopVideoOnly()
        playingTokens = null
        playingTokenIndex = -1
        binding.signEmojiFallback.visibility = View.GONE
    }

    private fun releasePlayer() {
        advanceJob?.cancel()
        mediaPlayer?.apply {
            setOnPreparedListener(null)
            setOnErrorListener(null)
            try {
                reset()
            } catch (e: IllegalStateException) {
                // already in an error/released state -- fine, we're tearing it down anyway
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
        pendingUri = null
        _binding = null
    }

    companion object {
        private const val LIVE_TRANSLATE_DEBOUNCE_MS = 300L

        /** How many times a non-final sign's clip repeats before the sequence auto-advances. */
        private const val REPEATS_BEFORE_ADVANCING = 2L

        /** Used only if MediaPlayer can't report a clip's real duration. */
        private const val FALLBACK_CLIP_DURATION_MS = 3000L

        /** Dwell time for a matched entry with no video at all (just its emoji). */
        private const val EMOJI_DWELL_MS = 2500L
    }
}
