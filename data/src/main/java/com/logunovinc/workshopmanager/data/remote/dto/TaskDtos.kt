@file:kotlinx.serialization.UseSerializers(DecimalSerializer::class)
package com.logunovinc.workshopmanager.data.remote.dto

import java.math.BigDecimal
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable data class PageDto<T>(val items: List<T>, val total: Long, val limit: Int, val offset: Long)
@Serializable data class WarehouseDto(val id: Long, val name: String, val code: String? = null, @SerialName("is_active") val active: Boolean)
@Serializable data class TaskDto(
    val id: Long, val type: String, val status: String, val version: Long,
    @SerialName("warehouse_id") val warehouseId: Long,
    @SerialName("assignee_user_id") val assigneeId: Long? = null,
    @SerialName("created_by") val createdBy: Long,
    @SerialName("created_at") val createdAt: String,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("due_at") val dueAt: String? = null,
    val comment: String = "",
    @SerialName("source_warehouse_id") val sourceWarehouseId: Long? = null,
    @SerialName("target_warehouse_id") val targetWarehouseId: Long? = null,
    @SerialName("transfer_id") val transferId: Long? = null,
    @SerialName("transfer_version") val transferVersion: Long? = null,
    @SerialName("manufacturing_order_id") val manufacturingOrderId: Long? = null,
    val outcome: String? = null, val lines: List<TaskLineDto>,
    @SerialName("allowed_actions") val allowedActions: List<String>,
    val production: ProductionDescriptorDto? = null,
    val executions: List<ExecutionDto> = emptyList(), val history: List<HistoryDto> = emptyList(),
    val discrepancies: List<DiscrepancyDto> = emptyList(),
)
@Serializable data class TaskLineDto(
    val id: Long,
    @SerialName("product_id") val productId: Long,
    @SerialName("product_name") val productName: String,
    val sku: String? = null, val role: String,
    @SerialName("base_unit") val baseUnit: String? = null,
    @SerialName("unit_name") val unitName: String? = null,
    @SerialName("planned_qty") val planned: BigDecimal,
    @SerialName("actual_qty") val actual: BigDecimal,
    @SerialName("remaining_qty") val remaining: BigDecimal,
    @SerialName("planned_unit_qty") val plannedUnit: BigDecimal? = null,
    @SerialName("actual_unit_qty") val actualUnit: BigDecimal? = null,
    @SerialName("remaining_unit_qty") val remainingUnit: BigDecimal? = null,
    @SerialName("planned_sheets") val plannedSheets: Int? = null,
    @SerialName("actual_sheets") val actualSheets: Int? = null,
    @SerialName("remaining_sheets") val remainingSheets: Int? = null,
    @SerialName("sheet_length_m") val sheetLength: BigDecimal? = null,
)
@Serializable data class ExecutionLineDto(
    @SerialName("task_line_id") val taskLineId: Long,
    @SerialName("product_id") val productId: Long, val role: String,
    @SerialName("actual_qty") val actual: BigDecimal,
    @SerialName("actual_sheets") val sheets: Int? = null,
)
@Serializable data class ExecutionDto(
    val id: Long, @SerialName("task_id") val taskId: Long,
    @SerialName("executed_by") val actorId: Long,
    @SerialName("executed_at") val at: String,
    val comment: String = "", val final: Boolean,
    @SerialName("receipt_id") val receiptId: Long? = null,
    @SerialName("transfer_id") val transferId: Long? = null,
    @SerialName("inventory_document_id") val inventoryDocumentId: Long? = null,
    val lines: List<ExecutionLineDto>,
)
@Serializable data class HistoryDto(val id: Long,
    @SerialName("event_type") val eventType: String,
    @SerialName("actor_id") val actorId: Long? = null,
    @SerialName("created_at") val at: String, val details: JsonObject)
@Serializable data class DiscrepancyDto(val id: Long, val status: String,
    @SerialName("missing_qty") val missing: BigDecimal,
    @SerialName("unresolved_qty") val unresolved: BigDecimal, val reason: String)
@Serializable data class StartTaskRequestDto(@SerialName("expected_version") val expectedVersion: Long,
    @SerialName("client_token") val clientToken: String, val comment: String = "")
@Serializable data class TaskResponseDto(val result: CommandResultDto, val task: TaskDto)
