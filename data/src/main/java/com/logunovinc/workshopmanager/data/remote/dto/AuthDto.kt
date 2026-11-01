package com.logunovinc.workshopmanager.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable data class LoginRequestDto(val login: String, val password: String)
@Serializable data class LoginResponseDto(val login: String, val role: String)
@Serializable data class ActorDto(val id: Long, val login: String, val role: String)
@Serializable data class LogoutResponseDto(val ok: Boolean)
