package com.logunovinc.workshopmanager.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class CommandAckDto(val result: CommandResultDto, val task: CommandTaskSnapshotDto)
@Serializable data class CommandResultDto(
    val id: Long,
    @SerialName("execution_id") val executionId: Long? = null,
    val duplicate: Boolean,
)
@Serializable data class CommandTaskSnapshotDto(val id: Long, val version: Long)
