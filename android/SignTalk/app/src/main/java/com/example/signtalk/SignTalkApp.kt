package com.example.signtalk

import android.app.Application
import com.example.signtalk.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SignTalkApp : Application() {

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
}
