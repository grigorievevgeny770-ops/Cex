package com.logunovinc.workshopmanager.data.remote

import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.data.remote.dto.*
import com.logunovinc.workshopmanager.domain.task.TaskFilter
import retrofit2.Response

class WorkshopRemoteDataSource(private val factory: ApiFactory, private val current: CurrentSession, private val errors: ApiErrorMapper) {
    private val calls = AuthenticatedRequestExecutor(factory, current, errors)
    private suspend fun <T> request(missingRoute: Boolean = false, block: suspend (WorkshopApi, SessionSnapshot) -> Response<T>): Outcome<T> =
        calls.request(WorkshopApi::class.java, missingRoute, block)
    suspend fun manufacturingPreview(id: Long, request: ByRecipePreviewDto) = calls.request(ProductionApi::class.java, missingRoute = true) { api, _ -> api.byRecipePreview(id, request) }
    suspend fun manufacturingPreview(id: Long, request: PrototypePreviewDto) = calls.request(ProductionApi::class.java, missingRoute = true) { api, _ -> api.prototypePreview(id, request) }
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
