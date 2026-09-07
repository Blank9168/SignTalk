package com.example.signtalk.domain.repository

import kotlinx.coroutines.flow.Flow

data class AppSettings(
    val autoSpeak: Boolean = true,
    val speechRate: Float = 1.0f,
    val confidenceThreshold: Float = 0.6f,
    val useFrontCamera: Boolean = true,
    val useMockRecognition: Boolean = false,
    // Short human-readable summary of the active model registered on the
    // backend (e.g. "50 classes - 92.8% accuracy"), set by
    // AppContainer.syncModelVersion() at app startup. Null until the first
    // successful sync -- see SettingsFragment for how this is displayed.
    val backendModelInfo: String? = null
)

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setAutoSpeak(enabled: Boolean)
    suspend fun setSpeechRate(rate: Float)
    suspend fun setConfidenceThreshold(threshold: Float)
    suspend fun setUseFrontCamera(useFront: Boolean)
    suspend fun setUseMockRecognition(useMock: Boolean)
    suspend fun setBackendModelInfo(info: String)
}
