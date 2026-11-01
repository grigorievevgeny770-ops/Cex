package com.logunovinc.workshopmanager.domain.task

import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.production.*
import com.logunovinc.workshopmanager.domain.pending.*
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

class TaskUseCasesTest {
    private class Repository : TaskRepository {
        override suspend fun closeIncompleteCapability(): Outcome<Boolean> = error("unused")
        override suspend fun closeIncomplete(task: Task, reason: String): com.logunovinc.workshopmanager.domain.pending.CommandOutcome = error("unused")
        var executions = 0
        var reads = 0
        override suspend fun tasks(warehouseId: Long, filter: TaskFilter, limit: Int, offset: Long): Outcome<Page<Task>> { reads++; return Outcome.Success(Page(listOf(sampleTask()), 1, limit, offset)) }
        override suspend fun task(id: Long): Outcome<Task> { reads++; return Outcome.Success(sampleTask(id)) }
        override suspend fun manufacturingPreview(task: Task, command: ManufacturingCommand): Outcome<ManufacturingPreview> = error("production not used")
        override suspend fun manufacture(task: Task, command: ManufacturingCommand, preview: ManufacturingPreview, comment: String): CommandOutcome = error("production not used")
        override suspend fun dispatch(task: Task, command: DispatchCommand): CommandOutcome { executions++; return CommandOutcome.Confirmed(CommandConfirmation("id", task.id, 4, null, false)) }
        override suspend fun receive(task: Task, command: ReceiveCommand): CommandOutcome = error("receive not used")
        override suspend fun executions(taskId: Long, limit: Int, offset: Long): Outcome<Page<Execution>> { reads++; return Outcome.Success(Page(emptyList(), 0, limit, offset)) }
        override suspend fun execution(taskId: Long, executionId: Long): Outcome<Execution> = Outcome.Failure(AppError(ErrorKind.NOT_FOUND, "missing"))
        override suspend fun history(taskId: Long, limit: Int, offset: Long): Outcome<Page<HistoryEntry>> { reads++; return Outcome.Success(Page(emptyList(), 0, limit, offset)) }
    }
    @Test fun directExecutionNeedsServerPermissionAndKnownOpenFlagsWithoutAnyStart() = runTest {
        val repository = Repository()
        val base = sampleTask(type = TaskType.DISPATCH_GOODS).copy(lines = listOf(TaskLine(1001, 301, "Материал", null, "dispatch", "шт", null,
            java.math.BigDecimal.ONE, java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE, null, null, null, null, null, null, null)))
        val draft = DispatchDraft(listOf(ActualLineInput(1001, "1")))
        for (task in listOf(base.copy(type = TaskType.UNKNOWN), base.copy(isCompleted = null), base.copy(isCompleted = true), base.copy(isCancelled = true), base.copy(allowedActions = emptySet()))) {
            assertTrue(ExecuteDispatchUseCase(repository)(task, draft) is CommandOutcome.Rejected)
            assertFalse(task.canExecute)
        }
        assertEquals(0, repository.executions)
        assertTrue(ExecuteDispatchUseCase(repository)(base, draft) is CommandOutcome.Confirmed)
        assertEquals(1, repository.executions)
    }
    @Test fun queryUseCasesValidateAndUseDedicatedHistoryPages() = runTest {
        val repository = Repository()
        assertTrue(LoadTasksUseCase(repository)(0, TaskFilter()) is Outcome.Failure)
        assertTrue(LoadTasksUseCase(repository)(42, TaskFilter(type = TaskType.UNKNOWN)) is Outcome.Failure)
        assertTrue(LoadTaskUseCase(repository)(0) is Outcome.Failure)
        assertTrue(LoadExecutionsUseCase(repository)(101, -1) is Outcome.Failure)
        assertTrue(LoadHistoryUseCase(repository)(101, -1) is Outcome.Failure)
        assertEquals(0, repository.reads)
        assertTrue(LoadTasksUseCase(repository)(42, TaskFilter(), 50) is Outcome.Success)
        assertTrue(LoadTaskUseCase(repository)(101) is Outcome.Success)
        assertTrue(LoadExecutionsUseCase(repository)(101, 50) is Outcome.Success)
        assertTrue(LoadHistoryUseCase(repository)(101, 50) is Outcome.Success)
        assertEquals(4, repository.reads)
    }
}
