@file:kotlinx.serialization.UseSerializers(DecimalSerializer::class)
package com.logunovinc.workshopmanager.data.remote.dto

import java.math.BigDecimal
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class DispatchLineDto(@SerialName("task_line_id") val taskLineId: Long, val qty: BigDecimal? = null, val sheets: Int? = null)
@Serializable data class DispatchRequestDto(
    val type: String = "DISPATCH_GOODS", @SerialName("expected_version") val expectedVersion: Long,
    @SerialName("client_token") val clientToken: String, val comment: String = "", val lines: List<DispatchLineDto>,
)
