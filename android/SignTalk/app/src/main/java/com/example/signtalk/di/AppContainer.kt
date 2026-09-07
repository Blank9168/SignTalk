package com.example.signtalk.di

import android.content.Context
import android.util.Log
import com.example.signtalk.data.dictionary.RoomDictionaryRepository
import com.example.signtalk.data.dictionary.SignTalkDatabase
import com.example.signtalk.data.remote.RetrofitClient
import com.example.signtalk.data.settings.DataStoreSettingsRepository
import com.example.signtalk.domain.model.DictionaryEntry
import com.example.signtalk.domain.repository.DictionaryRepository
import com.example.signtalk.domain.repository.SettingsRepository

private const val TAG = "AppContainer"

/**
 * Small hand-rolled dependency container (no Hilt/Koin) so the scaffold has
 * no extra DI-framework setup to go wrong.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val database by lazy { SignTalkDatabase.getInstance(appContext) }
    private val roomDictionaryRepository by lazy { RoomDictionaryRepository(database.dictionaryDao()) }

    val dictionaryRepository: DictionaryRepository by lazy { roomDictionaryRepository }
    val settingsRepository: SettingsRepository by lazy { DataStoreSettingsRepository(appContext) }

    suspend fun seedDictionaryIfNeeded() {
        roomDictionaryRepository.seedIfEmpty()
    }

    /**
     * Best-effort refresh from backend/sign-talk-api, called once at app
     * startup (see SignTalkApp.onCreate). The app is offline-first by
     * design: [seedDictionaryIfNeeded] above already guarantees a full,
     * usable dictionary from the bundled seed with zero network calls, so
     * a failure here (backend not running, no network, wrong
     * NetworkConfig.BASE_URL for the current device) is swallowed and
     * logged rather than surfaced to the user -- the app should work the
     * same on a plane as it does next to a running `npm run dev`.
     */
    suspend fun syncWithBackend() {
        syncDictionaryFromBackend()
        syncModelVersionFromBackend()
    }

    private suspend fun syncDictionaryFromBackend() {
        try {
            val response = RetrofitClient.apiService.getDictionary()
            val remoteEntries = response.entries.map { dto ->
                DictionaryEntry(
                    label = dto.slug,
                    displayName = dto.label,
                    category = dto.category,
                    description = dto.description,
                    emoji = dto.emoji,
                    isUserAdded = false
                )
            }
            roomDictionaryRepository.upsertFromRemote(remoteEntries)
            Log.i(TAG, "Dictionary synced from backend: ${remoteEntries.size} entries")
        } catch (e: Exception) {
            Log.w(TAG, "Dictionary sync skipped (backend unreachable or offline?): ${e.message}")
        }
    }

    private suspend fun syncModelVersionFromBackend() {
        try {
            val model = RetrofitClient.apiService.getActiveModelVersion()
            val accuracyText = model.overallValAccuracy?.let { "%.1f%%".format(it * 100) }
            val info = buildString {
                append("${model.numClasses} classes")
                if (accuracyText != null) append(" • $accuracyText accuracy")
            }
            settingsRepository.setBackendModelInfo(info)
            Log.i(TAG, "Model version synced from backend: $info (${model.version})")
        } catch (e: Exception) {
            Log.w(TAG, "Model version sync skipped: ${e.message}")
        }
    }
}
