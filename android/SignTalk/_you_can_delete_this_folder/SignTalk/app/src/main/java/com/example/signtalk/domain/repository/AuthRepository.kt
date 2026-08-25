package com.example.signtalk.domain.repository

import com.example.signtalk.domain.model.AuthResult
import com.example.signtalk.domain.model.UserSession
import kotlinx.coroutines.flow.Flow

/**
 * Auth is behind this interface so the mock, local implementation used today
 * ([com.example.signtalk.data.auth.MockAuthRepository]) can be swapped for a
 * real Firebase Authentication implementation later without touching any
 * screen or ViewModel — see docs/MOBILE_APP_NOTES.md.
 */
interface AuthRepository {
    val currentSession: Flow<UserSession?>

    suspend fun login(email: String, password: String): AuthResult
    suspend fun register(name: String, email: String, password: String): AuthResult
    suspend fun logout()
}
