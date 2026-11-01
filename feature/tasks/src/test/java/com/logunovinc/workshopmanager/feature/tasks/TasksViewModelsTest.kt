package com.logunovinc.workshopmanager.feature.tasks

import androidx.lifecycle.SavedStateHandle
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.production.*
import com.logunovinc.workshopmanager.domain.task.*
import com.logunovinc.workshopmanager.domain.pending.*
import com.logunovinc.workshopmanager.domain.warehouse.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }
    private class Warehouses : WarehouseRepository {
        var result: Outcome<List<Warehouse>> = Outcome.Success(listOf(Warehouse(7, "Main", "main", true), Warehouse(42, "Workshop", "workshop", true)))
        override suspend fun warehouses() = result
    }
    private open class Tasks : TaskRepository {
        var reads = 0
        var starts = 0
        var taskCalls = 0
        var listing: suspend (TaskFilter, Long) -> Outcome<Page<Task>> = { _, offset -> Outcome.Success(Page(listOf(sampleTask()), 1, 50, offset)) }
        var reading: suspend () -> Outcome<Task> = { Outcome.Success(sampleTask()) }
        var starting: suspend () -> CommandOutcome = { CommandOutcome.Confirmed(CommandConfirmation("id", 101, 4, null, false)) }
        override suspend fun tasks(warehouseId: Long, filter: TaskFilter, limit: Int, offset: Long): Outcome<Page<Task>> { assertEquals(42L, warehouseId); reads++; return listing(filter, offset) }
        override suspend fun task(id: Long): Outcome<Task> { taskCalls++; assertEquals(101L, id); return reading() }
        override suspend fun manufacturingPreview(task: Task, command: ManufacturingCommand): Outcome<ManufacturingPreview> = error("production not used")
        override suspend fun manufacture(task: Task, command: ManufacturingCommand, preview: ManufacturingPreview, comment: String): CommandOutcome = error("production not used")
        override suspend fun dispatch(task: Task, command: DispatchCommand): CommandOutcome = error("dispatch not used")
        override suspend fun receive(task: Task, command: ReceiveCommand): CommandOutcome = error("receive not used")
        override suspend fun start(task: Task): CommandOutcome { starts++; return starting() }
        override suspend fun executions(taskId: Long, limit: Int, offset: Long): Outcome<Page<Execution>> = Outcome.Success(Page((offset until minOf(offset + limit, 55)).map { Execution(1000 + it, taskId, 42, Instant.EPOCH, "", false, emptyList(), null, null, null) }, 55, limit, offset))
        override suspend fun execution(taskId: Long, executionId: Long): Outcome<Execution> = Outcome.Failure(AppError(ErrorKind.NOT_FOUND, "none"))
        override suspend fun history(taskId: Long, limit: Int, offset: Long): Outcome<Page<HistoryEntry>> = Outcome.Success(Page((offset until minOf(offset + limit, 55)).map { HistoryEntry(2000 + it, "executed", 42, Instant.EPOCH, emptyList()) }, 55, limit, offset))
    }
    @Test fun lateResponseOfOldFilterCannotReplaceNewFilter() = runTest(dispatcher) {
        val tasks = Tasks()
        val gate = CompletableDeferred<Unit>()
        tasks.listing = { filter, _ ->
            if (filter.type == null) withContext(NonCancellable) { gate.await(); Outcome.Success(Page(listOf(sampleTask(1)), 1, 50, 0)) }
            else Outcome.Success(Page(listOf(sampleTask(2, type = TaskType.DISPATCH_GOODS)), 1, 50, 0))
        }
        val vm = TaskListViewModel(ResolveWorkshopUseCase(Warehouses()), LoadTasksUseCase(tasks))
        runCurrent()
        vm.onIntent(TaskListIntent.FilterChanged(TaskFilter(type = TaskType.DISPATCH_GOODS)))
        runCurrent()
        assertEquals(listOf(2L), vm.uiState.value.items.map(Task::id))
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf(2L), vm.uiState.value.items.map(Task::id))
        assertEquals(TaskType.DISPATCH_GOODS, vm.uiState.value.filter.type)
    }
    @Test fun serverPaginationLoadsBeyondFiftyAndDoubleTapDoesNotDuplicatePage() = runTest(dispatcher) {
        val tasks = Tasks()
        tasks.listing = { _, offset -> Outcome.Success(Page((offset until minOf(offset + 50, 55)).map { sampleTask(it + 1) }, 55, 50, offset)) }
        val vm = TaskListViewModel(ResolveWorkshopUseCase(Warehouses()), LoadTasksUseCase(tasks))
        runCurrent()
        assertEquals(50, vm.uiState.value.items.size)
        vm.onIntent(TaskListIntent.LoadMore); vm.onIntent(TaskListIntent.LoadMore)
        runCurrent()
        assertEquals(55, vm.uiState.value.items.size)
        assertEquals(2, tasks.reads)
        assertFalse(vm.uiState.value.hasMore)
    }
    @Test fun unavailableWorkshopClearsPreviouslyVisibleTasks() = runTest(dispatcher) {
        val warehouses = Warehouses()
        val vm = TaskListViewModel(ResolveWorkshopUseCase(warehouses), LoadTasksUseCase(Tasks()))
        runCurrent()
        assertFalse(vm.uiState.value.items.isEmpty())
        warehouses.result = Outcome.Success(emptyList())
        vm.onIntent(TaskListIntent.Refresh)
        runCurrent()
        assertTrue(vm.uiState.value.items.isEmpty())
        assertNull(vm.uiState.value.warehouse)
        assertEquals("WORKSHOP_MISSING", vm.uiState.value.error?.code)
    }
    private fun detail(tasks: Tasks) = TaskDetailViewModel(SavedStateHandle(mapOf("taskId" to 101L)), LoadTaskUseCase(tasks), StartTaskUseCase(tasks), LoadExecutionsUseCase(tasks), LoadHistoryUseCase(tasks), object : PendingCommandRepository { override fun observe() = kotlinx.coroutines.flow.flowOf(emptyList<PendingCommand>()); override suspend fun retry(commandId: String): CommandOutcome = error("unused") })
    @Test fun detailDoesNotAutomaticallyStartAndIgnoresDuplicateIntentsInFlight() = runTest(dispatcher) {
        val tasks = Tasks()
        val gate = CompletableDeferred<Unit>()
        tasks.starting = { gate.await(); CommandOutcome.Confirmed(CommandConfirmation("id", 101, 4, null, false)) }
        val vm = detail(tasks)
        runCurrent()
        assertEquals(0, tasks.starts)
        vm.onIntent(TaskDetailIntent.StartClick)
        vm.onIntent(TaskDetailIntent.ConfirmStart)
        vm.onIntent(TaskDetailIntent.StartClick)
        vm.onIntent(TaskDetailIntent.ConfirmStart)
        vm.onIntent(TaskDetailIntent.Refresh)
        runCurrent()
        assertEquals(SubmissionState.Sending, vm.uiState.value.submission)
        assertEquals(1, tasks.starts)
        assertEquals(1, tasks.taskCalls)
        gate.complete(Unit)
        runCurrent()
        assertTrue(vm.uiState.value.submission is SubmissionState.Confirmed)
        assertEquals(2, tasks.taskCalls)
    }
    @Test fun refreshFailureAndConflictDoNotPermitMutationsOnStaleTask() = runTest(dispatcher) {
        val tasks = Tasks()
        val vm = detail(tasks)
        runCurrent()
        tasks.reading = { Outcome.Failure(AppError(ErrorKind.FORBIDDEN, "Права отозваны")) }
        vm.onIntent(TaskDetailIntent.Refresh)
        vm.onIntent(TaskDetailIntent.StartClick)
        assertNotEquals(SubmissionState.ConfirmStart, vm.uiState.value.submission)
        runCurrent()
        assertTrue(vm.uiState.value.staleTask)
        vm.onIntent(TaskDetailIntent.StartClick)
        assertNotEquals(SubmissionState.ConfirmStart, vm.uiState.value.submission)
        assertEquals(0, tasks.starts)
    }
    @Test fun staleVersionReloadsTaskAndKeepsTypedRejection() = runTest(dispatcher) {
        val tasks = Tasks()
        tasks.starting = { tasks.reading = { Outcome.Success(sampleTask(version = 8, actions = emptySet())) }; CommandOutcome.Rejected(null, AppError(ErrorKind.CONFLICT, "Другой работник", "VERSION_CONFLICT")) }
        val vm = detail(tasks)
        runCurrent()
        vm.onIntent(TaskDetailIntent.StartClick); vm.onIntent(TaskDetailIntent.ConfirmStart)
        runCurrent()
        assertEquals(8L, vm.uiState.value.task?.version)
        assertFalse(vm.uiState.value.task!!.canStart)
        assertEquals("VERSION_CONFLICT", (vm.uiState.value.submission as SubmissionState.Rejected).error.code)
    }
    @Test fun dedicatedHistoryAndExecutionPagesKeepAllFiftyFiveDistinctIds() = runTest(dispatcher) {
        val vm = detail(Tasks())
        runCurrent()
        assertEquals(50, vm.uiState.value.executions.size)
        assertEquals(50, vm.uiState.value.history.size)
        vm.onIntent(TaskDetailIntent.LoadMoreExecutions)
        vm.onIntent(TaskDetailIntent.LoadMoreHistory)
        runCurrent()
        assertEquals(55, vm.uiState.value.executions.size)
        assertEquals(55, vm.uiState.value.history.size)
        assertTrue(vm.uiState.value.executions.all { it.id in 1000L..1054L })
        assertTrue(vm.uiState.value.history.all { it.id in 2000L..2054L })
        assertFalse(vm.uiState.value.executionsMore)
        assertFalse(vm.uiState.value.historyMore)
    }
}
