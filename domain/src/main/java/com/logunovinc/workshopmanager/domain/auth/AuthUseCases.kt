package com.logunovinc.workshopmanager.domain.auth

import com.logunovinc.workshopmanager.domain.*
import java.net.URI

/** Authenticates and verifies the server-side session without retaining the password. */
class LoginUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(login: String, password: String): Outcome<Actor> =
        if (login.isBlank() || password.isBlank()) Outcome.Failure(AppError(ErrorKind.VALIDATION, "Введите логин и пароль"))
        else repository.login(login.trim(), password)
}

/** Verifies a restored cookie with /auth/me. */
class RestoreSessionUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(): Outcome<Actor> = repository.restore()
}

/** Local logout always succeeds; false means server revocation was not confirmed. */
class LogoutUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(): Outcome<Boolean> = repository.logout()
}

/** Selects an environment and invalidates the previous session before it can be reused. */
class ChangeEnvironmentUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(raw: String): Outcome<Unit> {
        val canonical = CanonicalBaseUrl.parse(raw) ?: return Outcome.Failure(AppError(ErrorKind.VALIDATION, "Нужен адрес HTTP(S) без пароля, query и fragment"))
        return repository.changeEnvironment(canonical)
    }
}

object CanonicalBaseUrl {
    fun parse(raw: String): String? = runCatching {
        val uri = URI(raw.trim())
        require(uri.scheme?.lowercase() in setOf("http", "https"))
        require(!uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null)
        require(uri.port == -1 || uri.port in 1..65535)
        val scheme = uri.scheme.lowercase()
        val host = uri.host.lowercase()
        val port = if ((scheme == "http" && uri.port == 80) || (scheme == "https" && uri.port == 443)) -1 else uri.port
        val path = (uri.path.ifEmpty { "/" }).trimEnd('/') + "/"
        URI(scheme, null, host, port, path, null, null).normalize().toASCIIString()
    }.getOrNull()
}
