package com.example.signtalk.ui.recognition

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.HorizontalScrollView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.fragment.app.viewModels
import com.example.signtalk.SignTalkApp
import com.example.signtalk.camera.CameraController
import com.example.signtalk.databinding.FragmentRecognitionBinding
import com.example.signtalk.domain.model.RecognitionState
import com.google.android.material.chip.Chip
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

    /** Last recognized word shown, so the pop animation runs only when it changes. */
    private var lastShownWord: String? = null

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

        binding.grantPermissionButton.setOnClickListener {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
        binding.clearTranscriptButton.setOnClickListener { viewModel.clearTranscript() }
        binding.speakTranscriptButton.setOnClickListener { viewModel.speakTranscript() }

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
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.recognitionState.collect { state -> renderState(state) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.transcript.collect { words -> renderTranscript(words) }
            }
        }
    }

    private fun updatePermissionUi(granted: Boolean) {
        binding.permissionGroup.visibility = if (granted) View.GONE else View.VISIBLE
        binding.recognitionGroup.visibility = if (granted) View.VISIBLE else View.GONE
    }

    private fun renderState(state: RecognitionState) {
        binding.statusProgress.visibility = View.GONE
        binding.statusProgress.isIndeterminate = false
        binding.statusSubtitle.visibility = View.GONE
        binding.confidenceGroup.visibility = View.GONE
        binding.statusTitle.setTextColor(defaultTextColor())
        // The recognized word is the output people read from across a room: make it huge
        // (auto-shrinks only if a long label would not fit on two lines). Status messages stay small.
        setTitleSizeRange(big = state is RecognitionState.Recognized)

        when (state) {
            is RecognitionState.Initializing -> {
                binding.statusTitle.text = "Starting recognition..."
                binding.statusProgress.isIndeterminate = true
                binding.statusProgress.visibility = View.VISIBLE
            }
            is RecognitionState.ModelUnavailable -> {
                binding.statusTitle.text = "Recognition model unavailable"
                binding.statusTitle.setTextColor(errorColor())
                binding.statusSubtitle.text = state.reason
                binding.statusSubtitle.visibility = View.VISIBLE
            }
            is RecognitionState.Error -> {
                binding.statusTitle.text = "Something went wrong"
                binding.statusTitle.setTextColor(errorColor())
                binding.statusSubtitle.text = state.message
                binding.statusSubtitle.visibility = View.VISIBLE
            }
            is RecognitionState.WaitingForHands -> {
                lastShownWord = null // the same sign signed again should pop again
                binding.statusTitle.text = "Show a hand sign to the camera"
            }
            is RecognitionState.WaitingForBody -> {
                binding.statusTitle.text = "Step back so your head and shoulders are visible"
            }
            is RecognitionState.Buffering -> {
                binding.statusTitle.text = "Reading gesture..."
                binding.statusProgress.max = state.framesNeeded
                binding.statusProgress.progress = state.framesCollected
                binding.statusProgress.visibility = View.VISIBLE
            }
            is RecognitionState.NotRecognized -> {
                binding.statusTitle.text = "Gesture not recognized"
                binding.statusTitle.setTextColor(errorColor())
                binding.statusSubtitle.text = "Try holding the sign steadier, or closer to the camera."
                binding.statusSubtitle.visibility = View.VISIBLE
            }
            is RecognitionState.Recognized -> {
                binding.statusTitle.text = state.result.displayName
                binding.statusTitle.setTextColor(primaryColor())
                if (lastShownWord != state.result.displayName) {
                    lastShownWord = state.result.displayName
                    popIn(binding.statusTitle)
                }
                val percent = (state.result.confidence * 100).toInt()
                binding.confidenceLabel.text = "Confidence: $percent%"
                binding.confidenceBar.setIndicatorColor(confidenceColor(state.result.confidence))
                binding.confidenceBar.progress = percent
                binding.confidenceGroup.visibility = View.VISIBLE
            }
        }
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
        val (minSp, maxSp) = if (big) 36 to 72 else 18 to 24
        androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            binding.statusTitle, minSp, maxSp, 2, android.util.TypedValue.COMPLEX_UNIT_SP
        )
    }

    private fun defaultTextColor(): Int = resolveThemeColor(com.google.android.material.R.attr.colorOnSurface)
    private fun primaryColor(): Int = resolveThemeColor(com.google.android.material.R.attr.colorPrimary)
    private fun onPrimaryColor(): Int = resolveThemeColor(com.google.android.material.R.attr.colorOnPrimary)
    private fun errorColor(): Int = resolveThemeColor(com.google.android.material.R.attr.colorError)

    private fun confidenceColor(confidence: Float): Int = when {
        confidence >= 0.8f -> 0xFF2E7D32.toInt() // success_green
        confidence >= 0.6f -> 0xFFF9A825.toInt() // warn_amber
        else -> 0xFFC62828.toInt() // error_red
    }

    private fun resolveThemeColor(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        requireContext().theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    private companion object {
        /** Translucent white chips on the dark transcript bar. */
        const val TRANSCRIPT_CHIP_COLOR = 0x33FFFFFF
    }

    override fun onDestroyView() {
        super.onDestroyView()
        lastShownWord = null
        cameraController.shutdown()
        _binding = null
    }
}
