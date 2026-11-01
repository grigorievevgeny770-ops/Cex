package com.logunovinc.workshopmanager.feature.tasks
import com.logunovinc.workshopmanager.domain.task.*
fun sampleTask(id: Long = 101, type: TaskType = TaskType.RECEIVE_GOODS, isCompleted: Boolean? = false, isCancelled: Boolean? = false, version: Long = 3, actions: Set<String> = setOf("execute")): Task = Task(
    id = id, type = type, rawType = type.name, isCompleted = isCompleted, isCancelled = isCancelled, version = version,
    warehouseId = 42, createdBy = 1, createdAt = java.time.Instant.EPOCH,
    completedAt = null, dueAt = null, comment = "", sourceWarehouseId = 7, targetWarehouseId = 42,
    transferId = 8, transferVersion = 2, manufacturingOrderId = null, outcome = null, lines = emptyList(), allowedActions = actions,
)
