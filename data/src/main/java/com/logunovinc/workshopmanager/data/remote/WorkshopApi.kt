package com.logunovinc.workshopmanager.data.remote

import com.logunovinc.workshopmanager.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface WorkshopApi {
    @GET("api/workshop/v2/requests/{request}/lines/{line}/drawing") suspend fun drawing(@Path("request") requestId: Long,@Path("line") lineId: Long): Response<ProfileDrawingDto>
    @GET("api/workshop/request-catalog?sheets=true") suspend fun sheetCatalog(): Response<List<ProductDto>>
    @GET("api/warehouses") suspend fun warehouses(@Query("include_inactive") includeInactive: Boolean = true): Response<List<WarehouseDto>>
    @GET("openapi.json") suspend fun capabilities(): Response<WorkshopCapabilitiesDto>
    @GET("api/workshop/tasks") suspend fun tasks(
        @Query("warehouse_id") warehouseId: Long,
        @Query("view") view: String,
        @Query("type") type: String?,
        @Query("limit") limit: Int, @Query("offset") offset: Long,
    ): Response<PageDto<TaskDto>>
    @GET("api/workshop/tasks/{id}") suspend fun task(@Path("id") id: Long): Response<TaskDto>
    @GET("api/workshop/tasks/{id}/executions") suspend fun executions(@Path("id") id: Long, @Query("limit") limit: Int, @Query("offset") offset: Long): Response<PageDto<ExecutionDto>>
    @GET("api/workshop/tasks/{id}/executions/{execution}") suspend fun execution(@Path("id") id: Long, @Path("execution") executionId: Long): Response<ExecutionDto>
    @GET("api/workshop/tasks/{id}/history") suspend fun history(@Path("id") id: Long, @Query("limit") limit: Int, @Query("offset") offset: Long): Response<PageDto<HistoryDto>>
    @GET("api/workshop/requests") suspend fun orders(
        @Query("warehouse_id") warehouseId: Long, @Query("view") view: String,
        @Query("limit") limit: Int, @Query("offset") offset: Long,
    ): Response<PageDto<WorkshopOrderDto>>
    @GET("api/workshop/requests/{id}") suspend fun order(@Path("id") id: Long): Response<WorkshopOrderDto>
    @GET("api/workshop/stock-summary") suspend fun stockSummary(@Query("warehouse_id") warehouseId: Long, @Query("on") on: String): Response<StockSummaryDto>
    @GET("api/workshop/sheet-requests") suspend fun sheetRequests(@Query("warehouse_id") warehouseId: Long): Response<List<SheetRequestDto>>
}
