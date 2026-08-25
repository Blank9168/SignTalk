package com.example.signtalk.domain.repository

import kotlinx.coroutines.flow.Flow

data class AppSettings(
    val autoSpeak: Boolean = true,
    val speechRate: Float = 1.0f,
    val confidenceThreshold: Float = 0.6f,
    val useFrontCamera: Boolean = true,
    val useMockRecognition: Boolean = false
)

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setAutoSpeak(enabled: Boolean)
    suspend fun setSpeechRate(rate: Float)
    suspend fun setConfidenceThreshold(threshold: Float)
    suspend fun setUseFrontCamera(useFront: Boolean)
    suspend fun setUseMockRecognition(useMock: Boolean)
}
