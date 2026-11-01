package com.logunovinc.workshopmanager.domain.auth

import kotlinx.coroutines.flow.StateFlow
import com.logunovinc.workshopmanager.domain.Outcome

/** User ID is provided by /auth/me, never inferred from login or cookie. */
data class Actor(val id: Long, val login: String, val role: String)
data class SessionState(val restoring: Boolean = true, val actor: Actor? = null, val message: String? = null)

interface AuthRepository {
    val session: StateFlow<SessionState>
    val environment: StateFlow<String>
    suspend fun restore(): Outcome<Actor>
    suspend fun login(login: String, password: String): Outcome<Actor>
    suspend fun logout(): Outcome<Boolean>
    suspend fun changeEnvironment(baseUrl: String): Outcome<Unit>
}
