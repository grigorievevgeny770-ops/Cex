@file:kotlinx.serialization.UseSerializers(DecimalSerializer::class)
package com.logunovinc.workshopmanager.data.remote.dto

import java.math.BigDecimal
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class WorkshopOrderDto(
    val id: Long, val version: Long, val status: String,
    @SerialName("due_at") val dueAt: String? = null, val comment: String = "",
    val lines: List<WorkshopOrderLineDto> = emptyList(),
    @SerialName("allowed_actions") val allowedActions: List<String> = emptyList(),
)
@Serializable data class WorkshopOrderLineDto(
    val id: Long,
    @SerialName("catalog_product_id") val productId: Long,
    @SerialName("product_name") val productName: String,
    val size: String? = null, val quantity: BigDecimal, val unit: String = "",
    @SerialName("norm_known") val normKnown: Boolean? = null,
)
@Serializable data class OrderCommandDto(
    @SerialName("expected_version") val expectedVersion: Long,
    @SerialName("client_token") val clientToken: String,
)
@Serializable data class SheetUseDto(
    @SerialName("product_id") val productId: Long, val sheets: Int,
    @SerialName("sheet_length_m") val length: BigDecimal,
)
@Serializable data class ManufactureCommandDto(
    @SerialName("expected_version") val expectedVersion: Long,
    @SerialName("client_token") val clientToken: String,
    val materials: List<SheetUseDto>,
)
@Serializable data class ReverseCommandDto(
    @SerialName("expected_version") val expectedVersion: Long,
    @SerialName("client_token") val clientToken: String,
    val target: String,
)
@Serializable data class StockSummaryLineDto(
    @SerialName("product_id") val productId: Long,
    @SerialName("product_name") val productName: String,
    val size: String? = null, val quantity: BigDecimal, val unit: String = "", val sheets: Int? = null,
)
@Serializable data class StockSummaryDto(
    @SerialName("flat_sheet") val flatSheet: List<StockSummaryLineDto> = emptyList(),
    @SerialName("awaiting_transfer") val awaitingTransfer: List<StockSummaryLineDto> = emptyList(),
    @SerialName("transferred_on") val transferredOn: List<StockSummaryLineDto> = emptyList(),
)
@Serializable data class SheetRequestDto(
    val id: Long, val version: Long,
    @SerialName("product_id") val productId: Long,
    @SerialName("product_name") val productName: String,
    @SerialName("sheet_length_m") val length: BigDecimal, val sheets: Int, val status: String,
    @SerialName("allowed_actions") val allowedActions: List<String> = emptyList(),
)
@Serializable data class SheetRequestCreateDto(
    @SerialName("warehouse_id") val warehouseId: Long,
    @SerialName("product_id") val productId: Long,
    val sheets: Int,
    @SerialName("sheet_length_m") val length: BigDecimal,
    @SerialName("client_token") val clientToken: String,
)
