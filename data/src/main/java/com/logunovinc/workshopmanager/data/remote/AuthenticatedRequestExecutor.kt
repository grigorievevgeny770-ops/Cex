package com.logunovinc.workshopmanager.data.remote

import com.logunovinc.workshopmanager.domain.*
import retrofit2.Response

/** Shared authenticated HTTP read/preview boundary; rejects responses from a superseded profile. */
class AuthenticatedRequestExecutor(private val factory: ApiFactory, private val current: CurrentSession, private val errors: ApiErrorMapper) {
    suspend fun <Api : Any, T> request(apiClass: Class<Api>, missingRoute: Boolean = false, block: suspend (Api, SessionSnapshot) -> Response<T>): Outcome<T> {
        val profile = current.snapshot() ?: return Outcome.Failure(AppError(ErrorKind.AUTHENTICATION, "Требуется вход"))
        return try {
            val api = factory.retrofit(profile.baseUrl, profile.generation, current::invalidate).create(apiClass)
            val response = block(api, profile)
            if (!response.isSuccessful) Outcome.Failure(errors.response(response, missingRoute))
            else if (!current.matches(profile)) Outcome.Failure(AppError(ErrorKind.AUTHENTICATION, "Сессия или сервер изменились"))
            else response.body()?.let { Outcome.Success(it) } ?: Outcome.Failure(AppError(ErrorKind.INCOMPATIBLE_SERVER, "Сервер вернул пустой ответ"))
        } catch (error: Exception) { Outcome.Failure(errors.throwable(error)) }
    }
}
