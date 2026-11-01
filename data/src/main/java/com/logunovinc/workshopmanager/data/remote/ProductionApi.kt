package com.logunovinc.workshopmanager.data.remote

import com.logunovinc.workshopmanager.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

/** Versioned v2 routes: only explicit previews are called, never capability probes or a v1 fallback. */
interface ProductionApi {
    @POST("api/workshop/v2/tasks/{id}/manufacturing-preview") suspend fun byRecipePreview(@Path("id") taskId: Long, @Body request: ByRecipePreviewDto): Response<ManufacturingPreviewDto>
    @POST("api/workshop/v2/tasks/{id}/manufacturing-preview") suspend fun prototypePreview(@Path("id") taskId: Long, @Body request: PrototypePreviewDto): Response<ManufacturingPreviewDto>
}
