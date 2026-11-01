package com.logunovinc.workshopmanager.data.remote

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class ApiFactory(val json: Json, val cookieJar: PersistentSessionCookieJar) {
    fun retrofit(baseUrl: String, generation: Long, unauthorized: (Long) -> Unit, allowStale: Boolean = false): Retrofit {
        val client = httpClient(generation, unauthorized, allowStale)
        return Retrofit.Builder().baseUrl(baseUrl).client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
    }
    /** Public compatibility schema only: no cookies, auth/session interceptors or credentials. */
    fun publicRetrofit(baseUrl: String): Retrofit = Retrofit.Builder().baseUrl(baseUrl)
        .client(OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS).build())
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
    fun httpClient(generation: Long, unauthorized: (Long) -> Unit, allowStale: Boolean = false): OkHttpClient =
        OkHttpClient.Builder().cookieJar(cookieJar)
            .addInterceptor(SessionGenerationInterceptor(cookieJar, generation, unauthorized, allowStale))
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS).build()
}
