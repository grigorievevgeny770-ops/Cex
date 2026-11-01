package com.logunovinc.workshopmanager.data.remote

import com.logunovinc.workshopmanager.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface StockApi {
    @GET("api/warehouses/{warehouse}/stock") suspend fun stock(@Path("warehouse") warehouseId: Long,
        @Query("q") query: String?, @Query("only_available") onlyAvailable: Boolean, @Query("limit") limit: Int, @Query("offset") offset: Long): Response<PageDto<StockPositionDto>>
    @GET("api/warehouses/{warehouse}/stock/{product}") suspend fun position(@Path("warehouse") warehouseId: Long, @Path("product") productId: Long): Response<StockPositionDto>
    @GET("api/products") suspend fun products(@Query("q") query: String?, @Query("limit") limit: Int, @Query("offset") offset: Long): Response<PageDto<ProductDto>>
    @GET("api/products/{id}") suspend fun product(@Path("id") productId: Long): Response<ProductDto>
    @GET("api/products/{id}/stock-definition") suspend fun definition(@Path("id") productId: Long): Response<StockDefinitionDto>
}
