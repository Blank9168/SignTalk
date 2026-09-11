package com.example.signtalk

import android.app.Application
import android.util.Log
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraXConfig
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
