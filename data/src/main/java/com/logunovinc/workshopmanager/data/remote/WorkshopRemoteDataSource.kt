package com.logunovinc.workshopmanager.data.remote

import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.task.TaskFilter
import retrofit2.Response

class WorkshopRemoteDataSource(private val factory: ApiFactory, private val current: CurrentSession, private val errors: ApiErrorMapper) {
    private suspend fun <T> request(missingRoute: Boolean = false, block: suspend (WorkshopApi, SessionSnapshot) -> Response<T>): Outcome<T> {
        val profile = current.snapshot() ?: return Outcome.Failure(AppError(ErrorKind.AUTHENTICATION, "Требуется вход"))
        return try {
            val api = factory.retrofit(profile.baseUrl, profile.generation, current::invalidate).create(WorkshopApi::class.java)
            val response = block(api, profile)
            if (!response.isSuccessful) Outcome.Failure(errors.response(response, missingRoute))
            else if (!current.matches(profile)) Outcome.Failure(AppError(ErrorKind.AUTHENTICATION, "Сессия или сервер изменились"))
            else response.body()?.let { Outcome.Success(it) } ?: Outcome.Failure(AppError(ErrorKind.INCOMPATIBLE_SERVER, "Сервер вернул пустой ответ"))
        } catch (error: Exception) { Outcome.Failure(errors.throwable(error)) }
    }
    suspend fun warehouses() = request(missingRoute = true) { api, _ -> api.warehouses() }
    suspend fun tasks(warehouseId: Long, filter: TaskFilter, limit: Int, offset: Long) = request(missingRoute = true) { api, profile ->
        api.tasks(warehouseId, !filter.completed, filter.type?.name, if (filter.completed) "completed" else null,
            if (filter.assignedToMe) profile.actorId else null, limit, offset)
    }
    suspend fun task(id: Long) = request { api, _ -> api.task(id) }
    suspend fun executions(id: Long, limit: Int, offset: Long) = request { api, _ -> api.executions(id, limit, offset) }
    suspend fun execution(id: Long, executionId: Long) = request { api, _ -> api.execution(id, executionId) }
    suspend fun history(id: Long, limit: Int, offset: Long) = request { api, _ -> api.history(id, limit, offset) }
}
