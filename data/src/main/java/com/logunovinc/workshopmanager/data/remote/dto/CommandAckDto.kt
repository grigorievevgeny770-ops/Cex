package com.logunovinc.workshopmanager.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class CommandAckDto(val result: CommandResultDto, val task: CommandTaskSnapshotDto? = null, val request: CommandTaskSnapshotDto? = null) {
    val snapshot: CommandTaskSnapshotDto? get() = task ?: request
}
@Serializable data class CommandResultDto(
    val id: Long,
    @SerialName("execution_id") val executionId: Long? = null,
    val duplicate: Boolean,
)
@Serializable data class CommandTaskSnapshotDto(val id: Long, val version: Long,
    @SerialName("is_completed") val isCompleted: Boolean? = null,
    @SerialName("is_cancelled") val isCancelled: Boolean? = null,
    @SerialName("is_closed_incomplete") val isClosedIncomplete: Boolean = false,
    val status: String? = null,
)
