package com.logunovinc.workshopmanager.domain.task

import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.production.*
import com.logunovinc.workshopmanager.domain.pending.*
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

class TaskUseCasesTest {
    private class Repository : TaskRepository {
        var starts = 0
        var reads = 0
        override suspend fun tasks(warehouseId: Long, filter: TaskFilter, limit: Int, offset: Long): Outcome<Page<Task>> { reads++; return Outcome.Success(Page(listOf(sampleTask()), 1, limit, offset)) }
        override suspend fun task(id: Long): Outcome<Task> { reads++; return Outcome.Success(sampleTask(id)) }
        override suspend fun manufacturingPreview(task: Task, command: ManufacturingCommand): Outcome<ManufacturingPreview> = error("production not used")
        override suspend fun manufacture(task: Task, command: ManufacturingCommand, preview: ManufacturingPreview, comment: String): CommandOutcome = error("production not used")
        override suspend fun dispatch(task: Task, command: DispatchCommand): CommandOutcome = error("dispatch not used")
        override suspend fun receive(task: Task, command: ReceiveCommand): CommandOutcome = error("receive not used")
        override suspend fun start(task: Task): CommandOutcome { starts++; return CommandOutcome.Confirmed(CommandConfirmation("id", task.id, 4, null, false)) }
        override suspend fun executions(taskId: Long, limit: Int, offset: Long): Outcome<Page<Execution>> { reads++; return Outcome.Success(Page(emptyList(), 0, limit, offset)) }
        override suspend fun execution(taskId: Long, executionId: Long): Outcome<Execution> = Outcome.Failure(AppError(ErrorKind.NOT_FOUND, "missing"))
        override suspend fun history(taskId: Long, limit: Int, offset: Long): Outcome<Page<HistoryEntry>> { reads++; return Outcome.Success(Page(emptyList(), 0, limit, offset)) }
    }
    @Test fun unknownEnumsLegacyProductionAndRevokedActionsFailClosed() = runTest {
        val repository = Repository()
        for (task in listOf(sampleTask(type = TaskType.UNKNOWN), sampleTask(status = TaskStatus.UNKNOWN), sampleTask(type = TaskType.MANUFACTURE_PRODUCT), sampleTask(actions = emptySet()), sampleTask(status = TaskStatus.COMPLETED))) {
            assertTrue(StartTaskUseCase(repository)(task) is CommandOutcome.Rejected)
            assertFalse(task.canStart)
        }
        assertEquals(0, repository.starts)
        assertTrue(StartTaskUseCase(repository)(sampleTask()) is CommandOutcome.Confirmed)
        assertEquals(1, repository.starts)
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
