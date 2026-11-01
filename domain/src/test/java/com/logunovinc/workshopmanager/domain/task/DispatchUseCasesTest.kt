package com.logunovinc.workshopmanager.domain.task

import java.math.BigDecimal
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.production.*
import com.logunovinc.workshopmanager.domain.pending.*
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

class DispatchUseCasesTest {
    private fun task(remaining: String = "10", sheets: Int? = null) = sampleTask(type = TaskType.DISPATCH_GOODS).copy(lines = listOf(TaskLine(2001, 301, "Изделие", null, "dispatch", "шт", null, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal(remaining), null, null, null, sheets, if (sheets != null) 0 else null, sheets, null)))
    private class Repository : TaskRepository {
        override suspend fun closeIncompleteCapability(): Outcome<Boolean> = error("unused")
        override suspend fun closeIncomplete(task: Task, reason: String): com.logunovinc.workshopmanager.domain.pending.CommandOutcome = error("unused")
        var sent: DispatchCommand? = null
        override suspend fun manufacturingPreview(task: Task, command: ManufacturingCommand): Outcome<ManufacturingPreview> = error("production not used")
        override suspend fun manufacture(task: Task, command: ManufacturingCommand, preview: ManufacturingPreview, comment: String): CommandOutcome = error("production not used")
        override suspend fun dispatch(task: Task, command: DispatchCommand): CommandOutcome { sent = command; return CommandOutcome.Confirmed(CommandConfirmation("id", task.id, 4, 5001, false)) }
        override suspend fun receive(task: Task, command: ReceiveCommand): CommandOutcome = error("unused")
        override suspend fun tasks(warehouseId: Long, filter: TaskFilter, limit: Int, offset: Long): Outcome<Page<Task>> = error("unused")
        override suspend fun task(id: Long): Outcome<Task> = error("unused")
        override suspend fun executions(taskId: Long, limit: Int, offset: Long): Outcome<Page<Execution>> = error("unused")
        override suspend fun execution(taskId: Long, executionId: Long): Outcome<Execution> = error("unused")
        override suspend fun history(taskId: Long, limit: Int, offset: Long): Outcome<Page<HistoryEntry>> = error("unused")
    }
    @Test fun partialThreeThenRemainingSevenArePhysicalDeltas() = runTest {
        val repository = Repository()
        val execute = ExecuteDispatchUseCase(repository)
        assertTrue(execute(task(), DispatchDraft(listOf(ActualLineInput(2001, "3")))) is CommandOutcome.Confirmed)
        assertEquals(BigDecimal("3"), repository.sent!!.lines.single().quantity)
        assertTrue(execute(task("7"), DispatchDraft(listOf(ActualLineInput(2001, "7")))) is CommandOutcome.Confirmed)
        assertEquals(BigDecimal("7"), repository.sent!!.lines.single().quantity)
    }
    @Test fun emptyZeroNegativeForeignAndExcessNeverReachRepository() = runTest {
        val repository = Repository()
        val execute = ExecuteDispatchUseCase(repository)
        for (input in listOf(emptyList(), listOf(ActualLineInput(2001, "0")), listOf(ActualLineInput(2001, "-1")), listOf(ActualLineInput(2001, "11")), listOf(ActualLineInput(999, "1")))) {
            assertTrue(execute(task(), DispatchDraft(input)) is CommandOutcome.Rejected)
        }
        assertNull(repository.sent)
    }
    @Test fun frozenSheetsRemainPositiveFactWithBaseZero() {
        val prepare = PrepareDispatchUseCase()
        val result = prepare(task("0", 9), DispatchDraft(listOf(ActualLineInput(2001, sheets = "3")))) as Outcome.Success
        assertEquals(3, result.value.lines.single().sheets)
        assertNull(result.value.lines.single().quantity)
        assertTrue(prepare(task("0", 9), DispatchDraft(listOf(ActualLineInput(2001, sheets = "10")))) is Outcome.Failure)
        assertTrue(prepare(task(), DispatchDraft(listOf(ActualLineInput(2001, "1.00001")))) is Outcome.Failure)
        assertTrue(prepare(task().copy(allowedActions = emptySet()), DispatchDraft(listOf(ActualLineInput(2001, "1")))) is Outcome.Failure)
    }
}
