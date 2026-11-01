package com.logunovinc.workshopmanager.data.remote

import com.logunovinc.workshopmanager.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface WorkshopApi {
    @GET("api/warehouses") suspend fun warehouses(@Query("include_inactive") includeInactive: Boolean = true): Response<List<WarehouseDto>>
    @GET("api/workshop/tasks?view=active&active_only=true") suspend fun tasks(
        @Query("warehouse_id") warehouseId: Long,
        @Query("type") type: String?,
        @Query("limit") limit: Int, @Query("offset") offset: Long,
    ): Response<PageDto<TaskDto>>
    @GET("api/workshop/tasks/{id}") suspend fun task(@Path("id") id: Long): Response<TaskDto>
    @GET("api/workshop/tasks/{id}/executions") suspend fun executions(@Path("id") id: Long, @Query("limit") limit: Int, @Query("offset") offset: Long): Response<PageDto<ExecutionDto>>
    @GET("api/workshop/tasks/{id}/executions/{execution}") suspend fun execution(@Path("id") id: Long, @Path("execution") executionId: Long): Response<ExecutionDto>
    @GET("api/workshop/tasks/{id}/history") suspend fun history(@Path("id") id: Long, @Query("limit") limit: Int, @Query("offset") offset: Long): Response<PageDto<HistoryDto>>
}
