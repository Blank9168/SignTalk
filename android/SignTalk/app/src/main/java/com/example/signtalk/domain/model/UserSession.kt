package com.example.signtalk.domain.model

data class UserSession(
    val userId: String,
    val displayName: String,
    val email: String
)

sealed interface AuthResult {
    data class Success(val session: UserSession) : AuthResult
    data class Failure(val message: String) : AuthResult
}
