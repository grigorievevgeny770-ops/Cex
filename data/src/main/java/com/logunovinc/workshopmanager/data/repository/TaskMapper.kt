package com.logunovinc.workshopmanager.data.repository

import com.logunovinc.workshopmanager.data.remote.dto.*
import com.logunovinc.workshopmanager.domain.task.*
import java.time.OffsetDateTime
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class TaskMapper {
    private fun instant(value: String) = OffsetDateTime.parse(value).toInstant()
    fun task(value: TaskDto): Task = Task(value.id,
        TaskType.entries.find { it.name == value.type } ?: TaskType.UNKNOWN, value.type,
        TaskStatus.entries.find { it.name.lowercase() == value.status } ?: TaskStatus.UNKNOWN, value.status,
        value.version, value.warehouseId, value.assigneeId, value.createdBy, instant(value.createdAt),
        value.startedAt?.let(::instant), value.completedAt?.let(::instant), value.dueAt?.let(::instant), value.comment,
        value.sourceWarehouseId, value.targetWarehouseId, value.transferId, value.transferVersion, value.manufacturingOrderId, value.outcome,
        value.lines.map(::line), value.allowedActions.toSet(), value.executions.map(::execution), value.history.map(::history),
        value.discrepancies.map { Discrepancy(it.id, it.status, it.missing, it.unresolved, it.reason) })
    private fun line(value: TaskLineDto) = TaskLine(value.id, value.productId, value.productName, value.sku, value.role,
        value.baseUnit, value.unitName, value.planned, value.actual, value.remaining, value.plannedUnit, value.actualUnit, value.remainingUnit,
        value.plannedSheets, value.actualSheets, value.remainingSheets, value.sheetLength)
    fun execution(value: ExecutionDto) = Execution(value.id, value.taskId, value.actorId, instant(value.at), value.comment, value.final,
        value.lines.map { ExecutionLine(it.taskLineId, it.productId, it.role, it.actual, it.sheets) }, value.receiptId, value.transferId, value.inventoryDocumentId)
    fun history(value: HistoryDto): HistoryEntry {
        val labels = mapOf("comment" to "Комментарий", "reason" to "Причина", "old_status" to "Было", "new_status" to "Стало",
            "assignee_user_id" to "Исполнитель", "execution_id" to "Факт", "transfer_id" to "Перемещение", "receipt_id" to "Приёмка", "outcome" to "Итог")
        val details = value.details.mapNotNull { (key, content) ->
            val label = labels[key] ?: return@mapNotNull null
            (content as? JsonPrimitive)?.contentOrNull?.let { label to it }
        }
        return HistoryEntry(value.id, value.eventType, value.actorId, instant(value.at), details)
    }
    fun <A, B> page(value: PageDto<A>, map: (A) -> B) = Page(value.items.map(map), value.total, value.limit, value.offset)
}
