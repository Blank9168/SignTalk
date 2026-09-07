package com.example.signtalk.ui.translate

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.signtalk.SignTalkApp
import com.example.signtalk.databinding.FragmentTranslateBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val STEP_DURATION_MS = 2700L

/** Text -> Sign screen: type or speak a word/phrase, see it animated as FSL. */
class TranslateFragment : Fragment() {

    private var _binding: FragmentTranslateBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TranslateViewModel by viewModels {
        viewModelFactory {
            initializer {
                val appContainer = (requireActivity().application as SignTalkApp).container
                TranslateViewModel(requireActivity().application, appContainer.dictionaryRepository)
            }
        }
    }

    private var autoAdvanceJob: Job? = null

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                binding.translateInput.setText(spoken)
                binding.translateInput.setSelection(spoken.length)
                runTranslate()
            }
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

        binding.translateButton.setOnClickListener { runTranslate() }
        binding.micButton.setOnClickListener { launchSpeechRecognizer() }
        binding.avatarSwitchButton.setOnClickListener { binding.signAnimationView.toggleAvatar() }
        binding.prevButton.setOnClickListener { viewModel.previous() }
        binding.nextButton.setOnClickListener { viewModel.next() }
        binding.playPauseButton.setOnClickListener {
            val playing = !viewModel.uiState.value.isPlaying
            viewModel.setPlaying(playing)
            if (playing) startAutoAdvance() else autoAdvanceJob?.cancel()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> renderState(state) }
            }
        }
    }

    private fun runTranslate() {
        viewModel.setInputText(binding.translateInput.text?.toString().orEmpty())
        viewModel.translate()
    }

    private fun launchSpeechRecognizer() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a word or phrase to translate to sign")
        }
        if (intent.resolveActivity(requireContext().packageManager) != null) {
            speechLauncher.launch(intent)
        } else {
            Toast.makeText(requireContext(), "Speech input isn't available on this device.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startAutoAdvance() {
        autoAdvanceJob?.cancel()
        autoAdvanceJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(STEP_DURATION_MS)
                if (!viewModel.uiState.value.isPlaying) break
                if (!viewModel.next()) {
                    viewModel.setPlaying(false)
                    break
                }
            }
        }
    }

    private fun renderState(state: TranslateUiState) {
        if (state.steps.isEmpty()) {
            binding.stepProgressText.text = ""
            binding.currentWordText.text = "Type or speak something to translate."
            binding.unresolvedText.visibility = View.GONE
            binding.playbackControls.visibility = View.GONE
            binding.signAnimationView.setSequence(null)
            return
        }

        binding.playbackControls.visibility = View.VISIBLE
        binding.stepProgressText.text = "Sign ${state.currentIndex + 1} of ${state.steps.size}"
        binding.prevButton.isEnabled = state.currentIndex > 0
        binding.nextButton.isEnabled = state.currentIndex < state.steps.size - 1
        binding.playPauseButton.text = if (state.isPlaying) "Pause" else "Play"

        val step = state.steps.getOrNull(state.currentIndex)
        if (step != null) {
            binding.currentWordText.text = if (step.found) {
                step.displayName
            } else {
                "“${step.text}” — hasn't been learned yet"
            }
            val sequence = if (step.found) viewModel.sequenceForCurrent() else null
            binding.signAnimationView.setSequence(sequence)
            binding.signAnimationView.play()
            if (step.found && sequence == null && !state.animationAvailable) {
                binding.currentWordText.append(" (animation data unavailable)")
            }
        }

        val unresolved = state.steps.filter { !it.found }.joinToString(", ") { "“${it.text}”" }
        binding.unresolvedText.visibility = if (unresolved.isEmpty()) View.GONE else View.VISIBLE
        binding.unresolvedText.text = "Not learned yet: $unresolved"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        autoAdvanceJob?.cancel()
        binding.signAnimationView.stop()
        _binding = null
    }
}
