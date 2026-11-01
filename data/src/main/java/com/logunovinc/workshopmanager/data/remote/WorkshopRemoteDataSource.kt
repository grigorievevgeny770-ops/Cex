package com.logunovinc.workshopmanager.data.remote

import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.data.remote.dto.*
import com.logunovinc.workshopmanager.domain.task.TaskFilter
import retrofit2.Response
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WorkshopRemoteDataSource(private val factory: ApiFactory, private val current: CurrentSession, private val errors: ApiErrorMapper) {
    private val capabilityLock = Mutex()
    private val closeCapabilities = mutableMapOf<String, Long>()
    fun sessionSnapshot() = current.snapshot()
    suspend fun closeIncompleteCapability(): Outcome<Boolean> = capabilityLock.withLock {
        val profile = current.snapshot() ?: return@withLock Outcome.Failure(AppError(ErrorKind.AUTHENTICATION, "Требуется вход"))
        closeCapabilities[profile.baseUrl]?.takeIf { System.nanoTime() - it < 300_000_000_000L }?.let { return@withLock Outcome.Success(true) }
        when (val result = calls.request(WorkshopApi::class.java, missingRoute = true, publicRead = true) { api, _ -> api.capabilities() }) {
            is Outcome.Failure -> result
            is Outcome.Success -> {
                val supported = result.value.paths["/api/workshop/tasks/{task_id}/close-incomplete"]?.post != null
                if (!current.matches(profile)) Outcome.Failure(AppError(ErrorKind.AUTHENTICATION, "Сессия или сервер изменились"))
                else { if (supported) closeCapabilities[profile.baseUrl] = System.nanoTime(); Outcome.Success(supported) }
            }
        }
    }
    private val calls = AuthenticatedRequestExecutor(factory, current, errors)
    private suspend fun <T> request(missingRoute: Boolean = false, block: suspend (WorkshopApi, SessionSnapshot) -> Response<T>): Outcome<T> =
        calls.request(WorkshopApi::class.java, missingRoute, block = block)
    suspend fun manufacturingPreview(id: Long, request: ByRecipePreviewDto) = calls.request(ProductionApi::class.java, missingRoute = true) { api, _ -> api.byRecipePreview(id, request) }
    suspend fun manufacturingPreview(id: Long, request: PrototypePreviewDto) = calls.request(ProductionApi::class.java, missingRoute = true) { api, _ -> api.prototypePreview(id, request) }
    suspend fun warehouses() = request(missingRoute = true) { api, _ -> api.warehouses() }
    suspend fun tasks(warehouseId: Long, filter: TaskFilter, limit: Int, offset: Long) = request(missingRoute = true) { api, _ ->
        api.tasks(warehouseId, filter.view.name.lowercase(), filter.type?.name,
            limit, offset)
    }
    suspend fun task(id: Long) = request { api, _ -> api.task(id) }
    suspend fun executions(id: Long, limit: Int, offset: Long) = request { api, _ -> api.executions(id, limit, offset) }
    suspend fun execution(id: Long, executionId: Long) = request { api, _ -> api.execution(id, executionId) }
    suspend fun history(id: Long, limit: Int, offset: Long) = request { api, _ -> api.history(id, limit, offset) }
}
