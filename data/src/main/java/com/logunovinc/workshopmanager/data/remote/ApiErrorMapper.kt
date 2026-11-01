package com.logunovinc.workshopmanager.data.remote

import com.logunovinc.workshopmanager.domain.*
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import retrofit2.Response

class ApiErrorMapper(private val json: Json) {
    fun response(response: Response<*>, missingRoute: Boolean = false): AppError {
        val raw = response.errorBody()?.string().orEmpty()
        val contentType = response.headers()["Content-Type"].orEmpty()
        if (contentType.contains("text/html") || raw.trimStart().startsWith("<"))
            return AppError(ErrorKind.INCOMPATIBLE_SERVER, "Сервер ещё не поддерживает приложение", httpStatus = response.code())
        val error = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
        val code = (error?.get("code") as? JsonPrimitive)?.contentOrNull
        val message = (error?.get("message") as? JsonPrimitive)?.contentOrNull
            ?: (error?.get("detail") as? JsonPrimitive)?.contentOrNull
        val kind = when (response.code()) {
            401 -> ErrorKind.AUTHENTICATION
            403 -> ErrorKind.FORBIDDEN
            404 -> if (missingRoute) ErrorKind.INCOMPATIBLE_SERVER else ErrorKind.NOT_FOUND
            409 -> ErrorKind.CONFLICT
            422 -> ErrorKind.VALIDATION
            else -> ErrorKind.SERVER
        }
        val fallback = when (kind) {
            ErrorKind.AUTHENTICATION -> "Требуется вход"
            ErrorKind.FORBIDDEN -> "Недостаточно прав для действия"
            ErrorKind.INCOMPATIBLE_SERVER -> "Сервер ещё не поддерживает приложение"
            ErrorKind.NOT_FOUND -> "Ресурс недоступен или сервер не поддерживает приложение"
            ErrorKind.CONFLICT -> "Состояние изменилось. Обновите данные"
            ErrorKind.VALIDATION -> "Проверьте введённые данные"
            else -> "Сервер не смог обработать запрос"
        }
        val issues = fieldIssues(error?.get("details") ?: error?.get("detail"))
        return AppError(kind, message ?: issues.joinToString("; ").ifBlank { fallback }, code, response.code(), issues)
    }
    private fun fieldIssues(value: JsonElement?): List<String> = when (value) {
        is JsonArray -> value.flatMap(::fieldIssues)
        is JsonObject -> {
            val message = (value["msg"] as? JsonPrimitive)?.contentOrNull ?: (value["message"] as? JsonPrimitive)?.contentOrNull
            if (message != null) {
                val location = (value["loc"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.filter { it != "body" }?.joinToString(".")
                listOf(if (location.isNullOrBlank()) message else "$location: $message")
            } else value.values.flatMap(::fieldIssues)
        }
        else -> emptyList()
    }
    fun throwable(error: Exception): AppError = when (error) {
        is CancellationException -> throw error
        is SerializationException, is IllegalArgumentException -> AppError(ErrorKind.INCOMPATIBLE_SERVER, "Ответ сервера несовместим с приложением")
        is IOException -> AppError(ErrorKind.NETWORK, "Не удалось связаться с сервером. Проверьте сеть и адрес")
        else -> AppError(ErrorKind.LOCAL_STORAGE, "Не удалось прочитать или сохранить локальные данные")
    }
}
