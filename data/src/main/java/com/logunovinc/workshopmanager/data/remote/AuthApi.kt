package com.logunovinc.workshopmanager.data.remote

import com.logunovinc.workshopmanager.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface AuthApi {
    @POST("auth/login") suspend fun login(@Body body: LoginRequestDto): Response<LoginResponseDto>
    @GET("auth/me") suspend fun me(): Response<ActorDto>
    @POST("auth/logout") suspend fun logout(@Header("Cookie") cookie: String): Response<LogoutResponseDto>
}
