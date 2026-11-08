@file:kotlinx.serialization.UseSerializers(DecimalSerializer::class)
package com.logunovinc.workshopmanager.data.remote.dto

import java.math.BigDecimal
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class ProfileDrawingDto(
    @SerialName("request_id") val requestId: Long,
    @SerialName("line_id") val lineId: Long,
    val version: Long,
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("blank_width_mm") val blankWidthMm: Long,
    val drawing: ProfileDrawingDataDto,
)
@Serializable data class ProfileDrawingDataDto(
    @SerialName("schema_version") val schemaVersion: Int,
    val skewed: Boolean,
    @SerialName("length_mm") val lengthMm: Long,
    val segments: List<ProfileSegmentDto>,
    @SerialName("angles_deg") val angles: List<BigDecimal>,
    val turns: List<String>, val edge1: ProfileEdgeDto, val edge2: ProfileEdgeDto,
)
@Serializable data class ProfileSegmentDto(@SerialName("start_mm") val startMm: Long,@SerialName("end_mm") val endMm: Long? = null)
@Serializable data class ProfileEdgeDto(val type: String,val direction: String)
