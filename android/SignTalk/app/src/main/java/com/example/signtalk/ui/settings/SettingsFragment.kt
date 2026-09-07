package com.example.signtalk.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.signtalk.SignTalkApp
import com.example.signtalk.databinding.FragmentSettingsBinding
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels {
        viewModelFactory {
            initializer {
                val appContainer = (requireActivity().application as SignTalkApp).container
                SettingsViewModel(appContainer.settingsRepository)
            }
        }
    }

    // Guards against the slider/switch listeners re-firing while we're
    // programmatically syncing their values from the observed settings Flow.
    private var updatingFromState = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.autoSpeakSwitch.setOnCheckedChangeListener { _, checked ->
            if (!updatingFromState) viewModel.setAutoSpeak(checked)
        }
        binding.frontCameraSwitch.setOnCheckedChangeListener { _, checked ->
            if (!updatingFromState) viewModel.setUseFrontCamera(checked)
        }
        binding.mockRecognitionSwitch.setOnCheckedChangeListener { _, checked ->
            if (!updatingFromState) viewModel.setUseMockRecognition(checked)
        }
        binding.speechRateSlider.addOnChangeListener { _, value, fromUser ->
            binding.speechRateLabel.text = "Speech rate: ${"%.1f".format(value)}x"
            if (fromUser) viewModel.setSpeechRate(value)
        }
        binding.confidenceSlider.addOnChangeListener { _, value, fromUser ->
            binding.confidenceLabel.text = "Confidence threshold: ${(value * 100).toInt()}%"
            if (fromUser) viewModel.setConfidenceThreshold(value)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settings.collect { settings ->
                    updatingFromState = true
                    binding.autoSpeakSwitch.isChecked = settings.autoSpeak
                    binding.frontCameraSwitch.isChecked = settings.useFrontCamera
                    binding.mockRecognitionSwitch.isChecked = settings.useMockRecognition
                    binding.speechRateSlider.value = settings.speechRate.coerceIn(0.5f, 2.0f)
                    binding.speechRateLabel.text = "Speech rate: ${"%.1f".format(settings.speechRate)}x"
                    binding.confidenceSlider.value = settings.confidenceThreshold.coerceIn(0.3f, 0.95f)
                    binding.confidenceLabel.text = "Confidence threshold: ${(settings.confidenceThreshold * 100).toInt()}%"
                    binding.modelInfoLabel.text = "Backend model: ${settings.backendModelInfo ?: "not yet synced"}"
                    updatingFromState = false
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
