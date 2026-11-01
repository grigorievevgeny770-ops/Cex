package com.logunovinc.workshopmanager.domain.task

import java.math.BigDecimal
import java.time.Instant
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.production.*
import com.logunovinc.workshopmanager.domain.pending.CommandOutcome

enum class TaskType { RECEIVE_GOODS, DISPATCH_GOODS, MANUFACTURE_PRODUCT, UNKNOWN }
data class Page<T>(val items: List<T>, val total: Long, val limit: Int, val offset: Long) {
    val nextOffset: Long get() = offset + items.size
    val hasMore: Boolean get() = items.isNotEmpty() && nextOffset < total
}
enum class TaskView { ACTIVE, HISTORY, ALL }
data class TaskFilter(val type: TaskType? = null, val view: TaskView = TaskView.ACTIVE)
data class TaskLine(
    val id: Long, val productId: Long, val productName: String, val sku: String?, val role: String,
    val baseUnit: String?, val unitName: String?, val planned: BigDecimal, val actual: BigDecimal, val remaining: BigDecimal,
    val plannedUnit: BigDecimal?, val actualUnit: BigDecimal?, val remainingUnit: BigDecimal?,
    val plannedSheets: Int?, val actualSheets: Int?, val remainingSheets: Int?, val sheetLength: BigDecimal?,
)
data class ExecutionLine(val taskLineId: Long, val productId: Long, val role: String, val actual: BigDecimal, val sheets: Int?)
data class Execution(
    val id: Long, val taskId: Long, val actorId: Long, val at: Instant, val comment: String, val final: Boolean,
    val lines: List<ExecutionLine>, val receiptId: Long?, val transferId: Long?, val inventoryDocumentId: Long?,
)
data class HistoryEntry(val id: Long, val eventType: String, val actorId: Long?, val at: Instant, val details: List<Pair<String, String>>)
data class Discrepancy(val id: Long, val status: String, val missing: BigDecimal, val unresolved: BigDecimal, val reason: String)
data class Task(
    val id: Long, val type: TaskType, val rawType: String, val isCompleted: Boolean?, val isCancelled: Boolean?,
    val version: Long, val warehouseId: Long, val createdBy: Long, val createdAt: Instant,
    val completedAt: Instant?, val dueAt: Instant?, val comment: String,
    val sourceWarehouseId: Long?, val targetWarehouseId: Long?, val transferId: Long?, val transferVersion: Long?,
    val manufacturingOrderId: Long?, val outcome: String?, val lines: List<TaskLine>, val allowedActions: Set<String>,
    val production: ProductionDescriptor? = null,
    val isClosedIncomplete: Boolean = false, val closedIncompleteReason: String? = null,
    val closedIncompleteBy: Long? = null, val closedIncompleteAt: Instant? = null,
    val executions: List<Execution> = emptyList(), val history: List<HistoryEntry> = emptyList(), val discrepancies: List<Discrepancy> = emptyList(),
) {
    val knownWorkflow: Boolean get() = (type in setOf(TaskType.RECEIVE_GOODS, TaskType.DISPATCH_GOODS) || (type == TaskType.MANUFACTURE_PRODUCT && production?.supported == true && lines.count { it.role == "output" } == 1))
    val canExecute: Boolean get() = !isClosedIncomplete && knownWorkflow && isCompleted == false && isCancelled == false && "execute" in allowedActions
    val canCloseIncomplete: Boolean get() = !isClosedIncomplete && isCompleted == false && isCancelled == false &&
        type in setOf(TaskType.DISPATCH_GOODS, TaskType.MANUFACTURE_PRODUCT) && "close_incomplete" in allowedActions
}
interface TaskRepository {
    suspend fun tasks(warehouseId: Long, filter: TaskFilter, limit: Int = 50, offset: Long = 0): Outcome<Page<Task>>
    suspend fun closeIncompleteCapability(): Outcome<Boolean>
    suspend fun closeIncomplete(task: Task, reason: String): CommandOutcome
    suspend fun task(id: Long): Outcome<Task>
    suspend fun manufacturingPreview(task: Task, command: ManufacturingCommand): Outcome<ManufacturingPreview>
    suspend fun manufacture(task: Task, command: ManufacturingCommand, preview: ManufacturingPreview, comment: String): CommandOutcome
    suspend fun dispatch(task: Task, command: DispatchCommand): CommandOutcome
    suspend fun receive(task: Task, command: ReceiveCommand): CommandOutcome
    suspend fun executions(taskId: Long, limit: Int = 50, offset: Long = 0): Outcome<Page<Execution>>
    suspend fun execution(taskId: Long, executionId: Long): Outcome<Execution>
    suspend fun history(taskId: Long, limit: Int = 50, offset: Long = 0): Outcome<Page<HistoryEntry>>
}
