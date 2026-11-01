package com.logunovinc.workshopmanager.data.remote

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class ApiFactory(val json: Json, val cookieJar: PersistentSessionCookieJar) {
    fun retrofit(baseUrl: String, generation: Long, unauthorized: (Long) -> Unit, allowStale: Boolean = false): Retrofit {
        val client = OkHttpClient.Builder().cookieJar(cookieJar)
            .addInterceptor(SessionGenerationInterceptor(cookieJar, generation, unauthorized, allowStale))
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder().baseUrl(baseUrl).client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
    }
}
