package com.logunovinc.workshopmanager.domain

sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>
}

data class AppError(
    val kind: ErrorKind,
    val message: String,
    val code: String? = null,
    val httpStatus: Int? = null,
    val fieldIssues: List<String> = emptyList(),
)
enum class ErrorKind { VALIDATION, AUTHENTICATION, FORBIDDEN, NOT_FOUND, CONFLICT, INCOMPATIBLE_SERVER, NETWORK, SERVER, LOCAL_STORAGE }
