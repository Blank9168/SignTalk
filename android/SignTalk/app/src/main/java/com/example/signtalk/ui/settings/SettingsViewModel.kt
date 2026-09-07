package com.example.signtalk.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.signtalk.domain.repository.AppSettings
import com.example.signtalk.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings()
    )

    fun setAutoSpeak(enabled: Boolean) = viewModelScope.launch { settingsRepository.setAutoSpeak(enabled) }
    fun setSpeechRate(rate: Float) = viewModelScope.launch { settingsRepository.setSpeechRate(rate) }
    fun setConfidenceThreshold(threshold: Float) = viewModelScope.launch { settingsRepository.setConfidenceThreshold(threshold) }
    fun setUseFrontCamera(useFront: Boolean) = viewModelScope.launch { settingsRepository.setUseFrontCamera(useFront) }
    fun setUseMockRecognition(useMock: Boolean) = viewModelScope.launch { settingsRepository.setUseMockRecognition(useMock) }
}
