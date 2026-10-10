package com.example.signtalk.ui.recognition

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.HorizontalScrollView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.signtalk.R
import com.example.signtalk.SignTalkApp
import com.example.signtalk.camera.CameraController
import com.example.signtalk.databinding.FragmentRecognitionBinding
import com.example.signtalk.domain.model.RecognitionHint
import com.example.signtalk.domain.model.RecognitionResult
import com.example.signtalk.domain.model.RecognitionState
import com.example.signtalk.domain.model.TopGuess
import com.example.signtalk.ui.common.SignVideoPlayer
import com.google.android.material.chip.Chip
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class RecognitionFragment : Fragment() {

    private var _binding: FragmentRecognitionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RecognitionViewModel by viewModels {
        viewModelFactory {
            initializer {
                val appContainer = (requireActivity().application as SignTalkApp).container
                RecognitionViewModel(requireActivity().application, appContainer.settingsRepository)
            }
        }
    }

    private lateinit var cameraController: CameraController
    private var examplePlayer: SignVideoPlayer? = null

    /** Last recognized word shown, so the pop animation runs only when it changes. */
    private var lastShownWord: String? = null
    /** Label whose example is on screen (or was hidden by the user for this attempt series). */
    private var exampleLabel: String? = null
    private var exampleDismissedFor: String? = null

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> updatePermissionUi(granted) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecognitionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraController = CameraController(requireContext())
        examplePlayer = SignVideoPlayer(requireContext(), binding.exampleVideo, "RecognitionExample")

        binding.grantPermissionButton.setOnClickListener {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
        binding.clearTranscriptButton.setOnClickListener { viewModel.clearTranscript() }
        binding.speakTranscriptButton.setOnClickListener { viewModel.speakTranscript() }
        binding.tryAgainButton.setOnClickListener {
            hideExample()
            exampleDismissedFor = null
            viewModel.tryAgain()
        }
        binding.replayButton.setOnClickListener { viewModel.replayLastWord() }
        binding.muteButton.setOnClickListener { viewModel.toggleMute() }
        binding.flipCameraButton.setOnClickListener { viewModel.flipCamera() }
        binding.exampleHideButton.setOnClickListener {
            exampleDismissedFor = exampleLabel
            hideExample()
        }

        // Only offer the flip button when the phone really has both cameras.
        val pm = requireContext().packageManager
        val hasBothCameras = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT) &&
            pm.hasSystemFeature(PackageManager.FEATURE_CAMERA)
        binding.flipCameraButton.visibility = if (hasBothCameras) View.VISIBLE else View.GONE

        val hasPermission = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        updatePermissionUi(hasPermission)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settings.map { it.useFrontCamera }.distinctUntilChanged().collect { useFront ->
                    if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        cameraController.bind(binding.previewView, viewLifecycleOwner, useFront) { bitmap, timestampMs ->
                            viewModel.onFrame({ bitmap }, timestampMs)
                        }
                    }
                    binding.flipCameraButton.text = if (useFront) "Rear cam" else "Front cam"
                    binding.flipCameraButton.contentDescription =
                        if (useFront) "Switch to the rear camera" else "Switch to the front camera"
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.recognitionState.combine(viewModel.hint) { state, hint -> state to hint }
                    .collect { (state, hint) -> renderState(state, hint) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.transcript.collect { words -> renderTranscript(words) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settings.collect { s ->
                    binding.muteButton.text = if (s.autoSpeak) "Sound on" else "Muted"
                    binding.muteButton.setIconResource(if (s.autoSpeak) R.drawable.ic_volume_up else R.drawable.ic_volume_off)
                    binding.muteButton.contentDescription =
                        if (s.autoSpeak) "Speech is on. Tap to mute." else "Speech is muted. Tap to turn speech on."
                    binding.performanceOverlay.visibility = if (s.performanceLogging) View.VISIBLE else View.GONE
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.lastWord.collect { word ->
                    binding.replayButton.isEnabled = word != null
                    binding.replayButton.contentDescription =
                        word?.let { "Say \"${it.displayName}\" again" } ?: "Replay speech (nothing recognized yet)"
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.performance.collect { p ->
                    binding.performanceOverlay.text =
                        "%.0f FPS · %.0f ms".format(p.fps, p.inferenceMs)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onScreenVisible(true)
    }

    override fun onStop() {
        viewModel.onScreenVisible(false)
        super.onStop()
    }

    private fun updatePermissionUi(granted: Boolean) {
        binding.permissionGroup.visibility = if (granted) View.GONE else View.VISIBLE
        binding.recognitionGroup.visibility = if (granted) View.VISIBLE else View.GONE
    }

    private fun renderState(state: RecognitionState, hint: RecognitionHint) {
        binding.statusProgress.visibility = View.GONE
        binding.statusProgress.isIndeterminate = false
        binding.statusSubtitle.visibility = View.GONE
        binding.confidenceGroup.visibility = View.GONE
        binding.statusTitle.setTextColor(defaultTextColor())
        // The recognized word is the output people read from across a room: make it huge
        // (auto-shrinks only if a long label would not fit on two lines). Status messages stay small.
        setTitleSizeRange(big = state is RecognitionState.Recognized)
        binding.handGuide.alpha = if (state is RecognitionState.Recognized) 0.35f else 1f
        binding.handGuideLabel.visibility =
            if (state is RecognitionState.WaitingForHands || state is RecognitionState.Initializing) View.VISIBLE else View.GONE

        when (state) {
            is RecognitionState.Initializing -> {
                setPill("Starting", PillTone.INFO, R.drawable.ic_info)
                binding.statusTitle.text = "Getting the camera ready..."
                binding.statusProgress.isIndeterminate = true
                binding.statusProgress.visibility = View.VISIBLE
            }
            is RecognitionState.ModelUnavailable -> {
                setPill("Error", PillTone.ERROR, R.drawable.ic_warning)
                binding.statusTitle.text = "Recognition model unavailable"
                binding.statusTitle.setTextColor(errorColor())
                showSubtitle(state.reason)
            }
            is RecognitionState.Error -> {
                setPill("Error", PillTone.ERROR, R.drawable.ic_warning)
                binding.statusTitle.text = "Something went wrong"
                binding.statusTitle.setTextColor(errorColor())
                showSubtitle(state.message)
            }
            is RecognitionState.WaitingForHands -> {
                lastShownWord = null // the same sign signed again should pop again
                setPill("Ready", PillTone.INFO, R.drawable.ic_sign_hand)
                binding.statusTitle.text = "Show a sign"
                showSubtitle(hintText(hint) ?: "Move your hand into the frame.")
            }
            is RecognitionState.WaitingForBody -> {
                setPill("Ready", PillTone.INFO, R.drawable.ic_sign_hand)
                binding.statusTitle.text = "Step back"
                showSubtitle("Your head and shoulders need to be visible.")
            }
            is RecognitionState.Buffering -> {
                setPill("Detecting", PillTone.INFO, R.drawable.ic_sign_hand)
                binding.statusTitle.text = "Reading your sign..."
                binding.statusProgress.max = state.framesNeeded
                binding.statusProgress.progress = state.framesCollected
                binding.statusProgress.visibility = View.VISIBLE
                hintText(hint)?.let { showSubtitle(it) }
            }
            is RecognitionState.NotSure -> {
                setPill("Not sure", PillTone.WARNING, R.drawable.ic_help)
                binding.statusTitle.text = "Please repeat the sign"
                binding.statusTitle.setTextColor(warningTextColor())
                showSubtitle(hintText(hint) ?: "Hold the sign steady inside the frame.")
                maybeShowExample(state.guess, state.failedAttempts)
            }
            is RecognitionState.Unknown -> {
                setPill("Unknown sign", PillTone.ERROR, R.drawable.ic_warning)
                binding.statusTitle.text = "Sign not recognized"
                binding.statusTitle.setTextColor(errorColor())
                showSubtitle(
                    hintText(hint)
                        ?: if (state.failedAttempts >= EXAMPLE_AFTER_FAILURES) {
                            "This may not be one of the 50 supported signs. Check the Dictionary."
                        } else {
                            "Try the sign again, a little slower."
                        }
                )
            }
            is RecognitionState.Recognized -> renderRecognized(state.result)
        }
        if (state is RecognitionState.Recognized) {
            hideExample()
            exampleDismissedFor = null
        }
    }

    private fun renderRecognized(result: RecognitionResult) {
        setPill("Recognized", PillTone.SUCCESS, R.drawable.ic_check_circle)
        binding.statusTitle.text = result.displayName
        binding.statusTitle.setTextColor(primaryColor())
        val isNew = lastShownWord != result.displayName
        if (isNew) {
            lastShownWord = result.displayName
            popIn(binding.statusTitle)
            // Measure "accepted -> text on screen" after the frame is actually drawn.
            binding.statusTitle.doOnPreDraw {
                viewModel.onResultShown(result, SystemClock.elapsedRealtime())
            }
        }
        val percent = (result.confidence * 100).toInt()
        binding.confidenceLabel.text = "Confidence $percent%"
        binding.confidenceBar.setIndicatorColor(confidenceColor(result.confidence))
        binding.confidenceBar.progress = percent
        binding.confidenceGroup.visibility = View.VISIBLE
    }

    private fun hintText(hint: RecognitionHint): String? = when (hint) {
        RecognitionHint.LOW_LIGHT -> "Too dark. Improve the lighting."
        RecognitionHint.HAND_TOO_FAR -> "Move your hand closer to the camera."
        RecognitionHint.HAND_TOO_CLOSE -> "Move your hand a little farther away."
        RecognitionHint.HAND_AT_EDGE -> "Keep your whole hand inside the frame."
        RecognitionHint.NONE -> null
    }

    private fun showSubtitle(text: String) {
        binding.statusSubtitle.text = text
        binding.statusSubtitle.visibility = View.VISIBLE
    }

    /** After repeated "Not sure" results, show a looping example of the model's top guess. */
    private fun maybeShowExample(guess: TopGuess?, failedAttempts: Int) {
        if (guess == null || failedAttempts < EXAMPLE_AFTER_FAILURES) return
        if (guess.label == exampleLabel || guess.label == exampleDismissedFor) return
        val context = context ?: return
        if (!SignVideoPlayer.hasBundledClip(context, guess.label)) return
        exampleLabel = guess.label
        binding.exampleName.text = guess.displayName
        binding.exampleCard.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            val uri = SignVideoPlayer.bundledClipUri(context, guess.label) ?: return@launch
            if (exampleLabel == guess.label) examplePlayer?.play(uri)
        }
    }

    private fun hideExample() {
        if (exampleLabel == null) return
        exampleLabel = null
        examplePlayer?.stop()
        _binding?.exampleCard?.visibility = View.GONE
    }

    private enum class PillTone { INFO, SUCCESS, WARNING, ERROR }

    private fun setPill(text: String, tone: PillTone, @DrawableRes icon: Int) {
        val (bg, fg) = when (tone) {
            PillTone.INFO -> R.color.status_info_bg to R.color.status_info_fg
            PillTone.SUCCESS -> R.color.status_success_bg to R.color.status_success_fg
            PillTone.WARNING -> R.color.status_warning_bg to R.color.status_warning_fg
            PillTone.ERROR -> R.color.status_error_bg to R.color.status_error_fg
        }
        val pill = binding.statusPill
        pill.text = text
        pill.backgroundTintList = ColorStateList.valueOf(color(bg))
        pill.setTextColor(color(fg))
        val drawable = ContextCompat.getDrawable(requireContext(), icon)?.mutate()
        drawable?.setTint(color(fg))
        drawable?.setBounds(0, 0, dp(20), dp(20))
        pill.setCompoundDrawablesRelative(drawable, null, null, null)
        pill.contentDescription = "Status: $text"
    }

    /** Newly recognized word scales up into place so the change is noticeable at a glance. */
    private fun popIn(view: View) {
        view.animate().cancel()
        view.scaleX = 0.8f
        view.scaleY = 0.8f
        view.alpha = 0.4f
        view.animate().scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(260).setInterpolator(OvershootInterpolator(2f)).start()
    }

    private fun renderTranscript(words: List<String>) {
        binding.transcriptBar.visibility = if (words.isEmpty()) View.GONE else View.VISIBLE
        val group = binding.transcriptChips
        group.removeAllViews()
        words.forEachIndexed { index, word ->
            val isNewest = index == words.lastIndex
            group.addView(Chip(requireContext()).apply {
                text = word
                isClickable = false
                isCheckable = false
                isFocusable = false
                chipStrokeWidth = 0f
                chipBackgroundColor = ColorStateList.valueOf(
                    if (isNewest) primaryColor() else TRANSCRIPT_CHIP_COLOR
                )
                setTextColor(if (isNewest) onPrimaryColor() else android.graphics.Color.WHITE)
            })
        }
        // Keep the newest word in view.
        binding.transcriptScroll.post {
            _binding?.transcriptScroll?.fullScroll(HorizontalScrollView.FOCUS_RIGHT)
        }
    }

    private fun setTitleSizeRange(big: Boolean) {
        val (minSp, maxSp) = if (big) 36 to 72 else 20 to 28
        androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            binding.statusTitle, minSp, maxSp, 2, android.util.TypedValue.COMPLEX_UNIT_SP
        )
    }

    private fun defaultTextColor(): Int = resolveThemeColor(com.google.android.material.R.attr.colorOnSurface)
    private fun primaryColor(): Int = resolveThemeColor(com.google.android.material.R.attr.colorPrimary)
    private fun onPrimaryColor(): Int = resolveThemeColor(com.google.android.material.R.attr.colorOnPrimary)
    private fun errorColor(): Int = resolveThemeColor(com.google.android.material.R.attr.colorError)
    /** Dark amber on the light panel, light amber in dark mode (values-night/colors.xml). */
    private fun warningTextColor(): Int = color(R.color.status_warning_fg)

    private fun confidenceColor(confidence: Float): Int = when {
        confidence >= 0.8f -> color(R.color.confidence_high)
        confidence >= 0.6f -> color(R.color.confidence_medium)
        else -> color(R.color.confidence_low)
    }

    private fun color(@ColorRes id: Int): Int = ContextCompat.getColor(requireContext(), id)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun resolveThemeColor(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        requireContext().theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    private companion object {
        /** Translucent white chips on the dark transcript bar. */
        const val TRANSCRIPT_CHIP_COLOR = 0x33FFFFFF
        /** Show the "Did you mean ...?" example after this many failed attempts in a row. */
        const val EXAMPLE_AFTER_FAILURES = 3
    }

    override fun onDestroyView() {
        super.onDestroyView()
        lastShownWord = null
        exampleLabel = null
        examplePlayer?.release()
        examplePlayer = null
        cameraController.shutdown()
        _binding = null
    }
}
