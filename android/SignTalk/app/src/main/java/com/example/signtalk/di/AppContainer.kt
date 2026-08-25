package com.example.signtalk.di

import android.content.Context
import com.example.signtalk.data.auth.MockAuthRepository
import com.example.signtalk.data.auth.SessionDataStore
import com.example.signtalk.data.dictionary.RoomDictionaryRepository
import com.example.signtalk.data.dictionary.SignTalkDatabase
import com.example.signtalk.data.settings.DataStoreSettingsRepository
import com.example.signtalk.domain.repository.AuthRepository
import com.example.signtalk.domain.repository.DictionaryRepository
import com.example.signtalk.domain.repository.SettingsRepository

/**
 * Small hand-rolled dependency container (no Hilt/Koin) so the scaffold has
 * no extra DI-framework setup to go wrong. Swapping [authRepository] for a
 * real Firebase-backed implementation is the one line to change when the
 * backend/Firebase project are ready -- see docs/MOBILE_APP_NOTES.md.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val sessionDataStore by lazy { SessionDataStore(appContext) }
    private val database by lazy { SignTalkDatabase.getInstance(appContext) }
    private val roomDictionaryRepository by lazy { RoomDictionaryRepository(database.dictionaryDao()) }

    val authRepository: AuthRepository by lazy { MockAuthRepository(sessionDataStore) }
    val dictionaryRepository: DictionaryRepository by lazy { roomDictionaryRepository }
    val settingsRepository: SettingsRepository by lazy { DataStoreSettingsRepository(appContext) }

    suspend fun seedDictionaryIfNeeded() {
        roomDictionaryRepository.seedIfEmpty()
    }
}
