package com.example.signtalk.data.auth

import com.example.signtalk.domain.model.AuthResult
import com.example.signtalk.domain.model.UserSession
import com.example.signtalk.domain.repository.AuthRepository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.delay

/**
 * Local, in-memory + on-device "auth" so login/register/dictionary/etc. work
 * end-to-end without the Node.js backend or a Firebase project. Every
 * registered account (email -> password hash) lives only in this process's
 * memory and is lost on process death; only the *session* (who's currently
 * logged in) survives restarts, via [SessionDataStore].
 *
 * To move to real Firebase Authentication: implement [AuthRepository] with
 * FirebaseAuth calls in a new class (e.g. FirebaseAuthRepository) and swap
 * the binding in [com.example.signtalk.di.AppContainer]. No screen or
 * ViewModel needs to change — see docs/MOBILE_APP_NOTES.md.
 */
class MockAuthRepository(
    private val sessionDataStore: SessionDataStore
) : AuthRepository {

    private data class Account(val userId: String, val name: String, val passwordHash: Int)

    // Seed one demo account so the login screen is usable immediately.
    private val accounts = ConcurrentHashMap<String, Account>().apply {
        put("demo@signtalk.app", Account(UUID.randomUUID().toString(), "Demo User", "password123".hashCode()))
    }

    override val currentSession = sessionDataStore.session

    override suspend fun login(email: String, password: String): AuthResult {
        delay(400) // simulate network/auth latency so the loading state is visible
        val normalizedEmail = email.trim().lowercase()
        val account = accounts[normalizedEmail]
            ?: return AuthResult.Failure("No account found for $email. Try registering first.")
        if (account.passwordHash != password.hashCode()) {
            return AuthResult.Failure("Incorrect password.")
        }
        val session = UserSession(account.userId, account.name, normalizedEmail)
        sessionDataStore.save(session)
        return AuthResult.Success(session)
    }

    override suspend fun register(name: String, email: String, password: String): AuthResult {
        delay(400)
        val normalizedEmail = email.trim().lowercase()
        if (normalizedEmail.isBlank() || !normalizedEmail.contains("@")) {
            return AuthResult.Failure("Enter a valid email address.")
        }
        if (password.length < 6) {
            return AuthResult.Failure("Password must be at least 6 characters.")
        }
        if (accounts.containsKey(normalizedEmail)) {
            return AuthResult.Failure("An account with this email already exists.")
        }
        val userId = UUID.randomUUID().toString()
        accounts[normalizedEmail] = Account(userId, name.ifBlank { normalizedEmail.substringBefore("@") }, password.hashCode())
        val session = UserSession(userId, name.ifBlank { normalizedEmail.substringBefore("@") }, normalizedEmail)
        sessionDataStore.save(session)
        return AuthResult.Success(session)
    }

    override suspend fun logout() {
        sessionDataStore.clear()
    }
}
