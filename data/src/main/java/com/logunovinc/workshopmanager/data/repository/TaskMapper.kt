package com.logunovinc.workshopmanager.data.repository

import com.logunovinc.workshopmanager.data.remote.dto.*
import com.logunovinc.workshopmanager.domain.task.*
import java.time.OffsetDateTime
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class TaskMapper {
    private fun instant(value: String) = OffsetDateTime.parse(value).toInstant()
    fun task(value: TaskDto): Task {
        val (completed, cancelled) = if (value.isCompleted != null || value.isCancelled != null) value.isCompleted to value.isCancelled else when (value.status) {
            "new", "in_progress", "partially_completed", "waiting_resolution" -> false to false
            "completed" -> true to false
            "cancelled" -> false to true
            else -> null to null
        }
        return Task(id = value.id, type = TaskType.entries.find { it.name == value.type } ?: TaskType.UNKNOWN, rawType = value.type,
            isCompleted = completed, isCancelled = cancelled, version = value.version, warehouseId = value.warehouseId,
            createdBy = value.createdBy, createdAt = instant(value.createdAt), completedAt = value.completedAt?.let(::instant),
            dueAt = value.dueAt?.let(::instant), comment = value.comment, sourceWarehouseId = value.sourceWarehouseId,
            targetWarehouseId = value.targetWarehouseId, transferId = value.transferId, transferVersion = value.transferVersion,
            manufacturingOrderId = value.manufacturingOrderId, outcome = value.outcome, lines = value.lines.map(::line),
            isClosedIncomplete = value.isClosedIncomplete, closedIncompleteReason = value.closedIncompleteReason,
            closedIncompleteBy = value.closedIncompleteBy, closedIncompleteAt = value.closedIncompleteAt?.let(::instant),
            allowedActions = value.allowedActions.toSet(), production = value.production?.let { ProductionMapper().descriptor(it) },
            executions = value.executions.map(::execution), history = value.history.map(::history),
            discrepancies = value.discrepancies.map { Discrepancy(it.id, it.status, it.missing, it.unresolved, it.reason) })
    }
    private fun line(value: TaskLineDto) = TaskLine(value.id, value.productId, value.productName, value.sku, value.role,
        value.baseUnit, value.unitName, value.planned, value.actual, value.remaining, value.plannedUnit, value.actualUnit, value.remainingUnit,
        value.plannedSheets, value.actualSheets, value.remainingSheets, value.sheetLength)
    fun execution(value: ExecutionDto) = Execution(value.id, value.taskId, value.actorId, instant(value.at), value.comment, value.final,
        value.lines.map { ExecutionLine(it.taskLineId, it.productId, it.role, it.actual, it.sheets) }, value.receiptId, value.transferId, value.inventoryDocumentId)
    fun history(value: HistoryDto): HistoryEntry {
        val labels = mapOf("comment" to "Комментарий", "reason" to "Причина",
            "assignee_user_id" to "Исполнитель", "execution_id" to "Факт", "transfer_id" to "Перемещение", "receipt_id" to "Приёмка", "outcome" to "Итог")
        val details = value.details.mapNotNull { (key, content) ->
            val label = labels[key] ?: return@mapNotNull null
            (content as? JsonPrimitive)?.contentOrNull?.let { label to it }
        }
        return HistoryEntry(value.id, value.eventType, value.actorId, instant(value.at), details)
    }
    fun <A, B> page(value: PageDto<A>, map: (A) -> B) = Page(value.items.map(map), value.total, value.limit, value.offset)
}
