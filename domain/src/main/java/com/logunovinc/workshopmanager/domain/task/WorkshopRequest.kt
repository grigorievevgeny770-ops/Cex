package com.logunovinc.workshopmanager.domain.task

import java.math.BigDecimal

/**
 * Read-only view of a server task. This is not a status machine the phone can advance:
 * the contract has no request transitions and no separate manufactured/handed-over flags.
 */
enum class WorkshopRequestStage {
    NEW,
    IN_PROGRESS,
    CANCELLED,
    SERVER_COMPLETED,
    SERVER_CLOSED_INCOMPLETE,
    UNSUPPORTED,
}

fun Task.workshopStage(): WorkshopRequestStage {
    if (isCancelled == true) return WorkshopRequestStage.CANCELLED
    if (isCompleted == null || isCancelled == null) return WorkshopRequestStage.UNSUPPORTED
    if (isClosedIncomplete) return WorkshopRequestStage.SERVER_CLOSED_INCOMPLETE
    if (isCompleted == true) return WorkshopRequestStage.SERVER_COMPLETED
    val progressed = startedAt != null || legacyStatus in PROGRESS_STATUSES || lines.any { it.hasRecordedFact() }
    return if (progressed) WorkshopRequestStage.IN_PROGRESS else WorkshopRequestStage.NEW
}

/** Products the main warehouse asked to make. Input, receipt and dispatch lines are not a request. */
fun Task.productRequestLines(): List<TaskLine> =
    if (type == TaskType.MANUFACTURE_PRODUCT) lines.filter { it.role == "output" } else emptyList()

private fun TaskLine.hasRecordedFact(): Boolean = actual.compareTo(BigDecimal.ZERO) != 0 || (actualSheets ?: 0) > 0

private val PROGRESS_STATUSES = setOf("in_progress", "partially_completed", "waiting_resolution")
