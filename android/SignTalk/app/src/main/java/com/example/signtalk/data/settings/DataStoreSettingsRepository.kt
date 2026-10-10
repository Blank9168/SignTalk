package com.example.signtalk.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.signtalk.domain.repository.AppSettings
import com.example.signtalk.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "signtalk_settings")

class DataStoreSettingsRepository(private val context: Context) : SettingsRepository {

    private object Keys {
        val AUTO_SPEAK = booleanPreferencesKey("auto_speak")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val CONFIDENCE_THRESHOLD = floatPreferencesKey("confidence_threshold")
        val USE_FRONT_CAMERA = booleanPreferencesKey("use_front_camera")
        val USE_MOCK_RECOGNITION = booleanPreferencesKey("use_mock_recognition")
        val BACKEND_MODEL_INFO = stringPreferencesKey("backend_model_info")
        val VIBRATION = booleanPreferencesKey("vibration_enabled")
        val SOUND_EFFECTS = booleanPreferencesKey("sound_effects_enabled")
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val PERFORMANCE_LOGGING = booleanPreferencesKey("performance_logging")
    }

    override val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            autoSpeak = prefs[Keys.AUTO_SPEAK] ?: true,
            speechRate = prefs[Keys.SPEECH_RATE] ?: 1.0f,
            confidenceThreshold = prefs[Keys.CONFIDENCE_THRESHOLD] ?: 0.6f,
            useFrontCamera = prefs[Keys.USE_FRONT_CAMERA] ?: true,
            useMockRecognition = prefs[Keys.USE_MOCK_RECOGNITION] ?: false,
            backendModelInfo = prefs[Keys.BACKEND_MODEL_INFO],
            vibrationEnabled = prefs[Keys.VIBRATION] ?: true,
            soundEffectsEnabled = prefs[Keys.SOUND_EFFECTS] ?: false,
            highContrast = prefs[Keys.HIGH_CONTRAST] ?: false,
            onboardingDone = prefs[Keys.ONBOARDING_DONE] ?: false,
            performanceLogging = prefs[Keys.PERFORMANCE_LOGGING] ?: false
        )
    }

    override suspend fun setAutoSpeak(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.AUTO_SPEAK] = enabled }
    }

    override suspend fun setSpeechRate(rate: Float) {
        context.settingsDataStore.edit { it[Keys.SPEECH_RATE] = rate }
    }

    override suspend fun setConfidenceThreshold(threshold: Float) {
        context.settingsDataStore.edit { it[Keys.CONFIDENCE_THRESHOLD] = threshold }
    }

    override suspend fun setUseFrontCamera(useFront: Boolean) {
        context.settingsDataStore.edit { it[Keys.USE_FRONT_CAMERA] = useFront }
    }

    override suspend fun setUseMockRecognition(useMock: Boolean) {
        context.settingsDataStore.edit { it[Keys.USE_MOCK_RECOGNITION] = useMock }
    }

    override suspend fun setBackendModelInfo(info: String) {
        context.settingsDataStore.edit { it[Keys.BACKEND_MODEL_INFO] = info }
    }

    override suspend fun setVibrationEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.VIBRATION] = enabled }
    }

    override suspend fun setSoundEffectsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SOUND_EFFECTS] = enabled }
    }

    override suspend fun setHighContrast(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.HIGH_CONTRAST] = enabled }
    }

    override suspend fun setOnboardingDone(done: Boolean) {
        context.settingsDataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    override suspend fun setPerformanceLogging(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.PERFORMANCE_LOGGING] = enabled }
    }
}
