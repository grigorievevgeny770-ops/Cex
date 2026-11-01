package com.logunovinc.workshopmanager.data.remote

import com.logunovinc.workshopmanager.data.remote.dto.LoginRequestDto

class AuthRemoteDataSource(private val api: AuthApi) {
    suspend fun login(login: String, password: String) = api.login(LoginRequestDto(login, password))
    suspend fun me() = api.me()
    suspend fun logout(cookie: String) = api.logout(cookie)
}
