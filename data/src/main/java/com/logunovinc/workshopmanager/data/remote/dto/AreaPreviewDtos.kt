@file:kotlinx.serialization.UseSerializers(DecimalSerializer::class)
package com.logunovinc.workshopmanager.data.remote.dto

import java.math.BigDecimal
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class AreaPreviewDto(
    @SerialName("request_id") val requestId:Long,
    @SerialName("request_version") val requestVersion:Long,
    @SerialName("preview_hash") val hash:String,
    @SerialName("formula_version") val formulaVersion:String,
    @SerialName("total_area_m2") val totalAreaM2:BigDecimal,
    val lines:List<AreaPreviewLineDto>,val materials:List<AreaPreviewMaterialDto>,
)
@Serializable data class AreaPreviewLineDto(
    @SerialName("line_id") val lineId:Long,@SerialName("drawing_version") val drawingVersion:Long,
    @SerialName("product_name") val productName:String,@SerialName("blank_width_mm") val blankWidthMm:Long,
    @SerialName("length_mm") val lengthMm:Long,@SerialName("piece_quantity") val pieceQuantity:BigDecimal,
    @SerialName("area_m2") val areaM2:BigDecimal,
)
@Serializable data class AreaPreviewMaterialDto(
    @SerialName("product_id") val productId:Long,@SerialName("product_name") val productName:String,
    @SerialName("area_m2") val areaM2:BigDecimal,
)
@Serializable data class AreaManufactureCommandDto(
    @SerialName("expected_version") val expectedVersion:Long,@SerialName("client_token") val clientToken:String,
    @SerialName("preview_hash") val previewHash:String?=null,
)
