package com.logunovinc.workshopmanager.feature.tasks
import com.logunovinc.workshopmanager.domain.task.*
fun sampleTask(id: Long = 101, type: TaskType = TaskType.RECEIVE_GOODS, status: TaskStatus = TaskStatus.NEW, version: Long = 3, actions: Set<String> = setOf("start", "execute")): Task = Task(
    id = id, type = type, rawType = type.name, status = status, rawStatus = status.name.lowercase(), version = version,
    warehouseId = 42, assigneeId = null, createdBy = 1, createdAt = java.time.Instant.EPOCH,
    startedAt = null, completedAt = null, dueAt = null, comment = "", sourceWarehouseId = 7, targetWarehouseId = 42,
    transferId = 8, transferVersion = 2, manufacturingOrderId = null, outcome = null, lines = emptyList(), allowedActions = actions,
)
