package com.example.signtalk.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.signtalk.domain.model.UserSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "signtalk_session")

/**
 * Persists the "logged in as" state on-device so the mock login survives
 * app restarts. Holds no password — [MockAuthRepository] is the only place
 * credentials are checked, and it never writes them here.
 */
class SessionDataStore(private val context: Context) {

    private object Keys {
        val USER_ID = stringPreferencesKey("user_id")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val EMAIL = stringPreferencesKey("email")
    }

    val session: Flow<UserSession?> = context.sessionDataStore.data.map { prefs ->
        val userId = prefs[Keys.USER_ID] ?: return@map null
        val email = prefs[Keys.EMAIL] ?: return@map null
        UserSession(
            userId = userId,
            displayName = prefs[Keys.DISPLAY_NAME] ?: email.substringBefore("@"),
            email = email
        )
    }

    suspend fun save(session: UserSession) {
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.USER_ID] = session.userId
            prefs[Keys.DISPLAY_NAME] = session.displayName
            prefs[Keys.EMAIL] = session.email
        }
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
    }
}
