package com.example.signtalk

import android.app.Application
import android.util.Log
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraXConfig
import androidx.emoji2.bundled.BundledEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat
import com.example.signtalk.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SignTalkApp : Application(), CameraXConfig.Provider {

    val container by lazy { AppContainer(this) }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // The dictionary uses newer emoji (e.g. the Emoji 12 colored circles for the
        // FSL colors) that older Android versions draw as empty boxes. Use the emoji
        // font bundled in the APK instead, for every emoji, so they look the same on
        // every device. The default downloadable-font setup is disabled in the manifest.
        EmojiCompat.init(BundledEmojiCompatConfig(this).setReplaceAll(true))
        appScope.launch {
            // Guarantees a full, usable offline dictionary first (from the
            // bundled seed), then best-effort upgrades it with whatever the
            // backend has -- see AppContainer.syncWithBackend for why a
            // failure here is swallowed rather than shown to the user.
            container.seedDictionaryIfNeeded()
            container.syncWithBackend()
        }
    }

    override fun getCameraXConfig(): CameraXConfig {
        return CameraXConfig.Builder.fromConfig(Camera2Config.defaultConfig())
            .setAvailableCamerasLimiter(CameraSelector.Builder().build())
            .setMinimumLoggingLevel(Log.ERROR)
            .build()
    }
}
