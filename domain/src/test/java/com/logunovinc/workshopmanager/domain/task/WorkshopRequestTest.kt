package com.logunovinc.workshopmanager.domain.task

import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkshopRequestTest {
    private fun line(role: String, actual: String = "0", sheets: Int? = null) = TaskLine(
        1, 501, "Конёк", null, role, "шт", null, BigDecimal.TEN, BigDecimal(actual), BigDecimal.TEN,
        null, null, null, sheets, if (sheets == null) null else 0, sheets, null,
    )

    @Test fun openManufactureWithoutProgressIsNewAndShowsOnlyOutput() {
        val task = sampleTask(type = TaskType.MANUFACTURE_PRODUCT).copy(lines = listOf(line("input"), line("output")))
        assertEquals(WorkshopRequestStage.NEW, task.workshopStage())
        assertEquals(listOf("output"), task.productRequestLines().map { it.role })
    }

    @Test fun recordedFactOrLegacyProgressIsInProgressWithoutALocalTransition() {
        val started = sampleTask(type = TaskType.MANUFACTURE_PRODUCT).copy(startedAt = Instant.parse("2026-11-06T08:00:00Z"))
        val partial = sampleTask(type = TaskType.MANUFACTURE_PRODUCT).copy(lines = listOf(line("output", actual = "1")))
        val legacy = sampleTask(type = TaskType.MANUFACTURE_PRODUCT, isCompleted = false, isCancelled = false).copy(legacyStatus = "in_progress")
        assertEquals(WorkshopRequestStage.IN_PROGRESS, started.workshopStage())
        assertEquals(WorkshopRequestStage.IN_PROGRESS, partial.workshopStage())
        assertEquals(WorkshopRequestStage.IN_PROGRESS, legacy.workshopStage())
    }

    @Test fun completionAndCancellationStayServerFactsAndDoNotBecomeHandover() {
        assertEquals(WorkshopRequestStage.CANCELLED, sampleTask(isCancelled = true, isCompleted = false).workshopStage())
        assertEquals(WorkshopRequestStage.SERVER_COMPLETED, sampleTask(isCompleted = true).workshopStage())
        assertEquals(WorkshopRequestStage.SERVER_CLOSED_INCOMPLETE, sampleTask().copy(isClosedIncomplete = true).workshopStage())
        assertEquals(WorkshopRequestStage.UNSUPPORTED, sampleTask(isCompleted = null, isCancelled = null).workshopStage())
    }

    @Test fun receiptAndDispatchLinesAreNotFinishedGoodsOnARequest() {
        val receipt = sampleTask(type = TaskType.RECEIVE_GOODS).copy(lines = listOf(line("receive")))
        val dispatch = sampleTask(type = TaskType.DISPATCH_GOODS).copy(lines = listOf(line("dispatch")))
        assertEquals(emptyList<TaskLine>(), receipt.productRequestLines())
        assertEquals(emptyList<TaskLine>(), dispatch.productRequestLines())
    }
}
