@file:kotlinx.serialization.UseSerializers(DecimalSerializer::class)
package com.logunovinc.workshopmanager.data.remote.dto

import java.math.BigDecimal
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class ReceiveLineDto(@SerialName("task_line_id") val taskLineId: Long, val qty: BigDecimal? = null, val sheets: Int? = null)
@Serializable data class ReceiveRequestDto(
    val type: String = "RECEIVE_GOODS",
    @SerialName("expected_version") val expectedVersion: Long,
    @SerialName("expected_transfer_version") val expectedTransferVersion: Long,
    @SerialName("client_token") val clientToken: String,
    val comment: String = "", val lines: List<ReceiveLineDto>, val final: Boolean = false, val reason: String = "",
)
