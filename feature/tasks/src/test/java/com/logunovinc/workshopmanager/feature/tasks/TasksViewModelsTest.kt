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
        var taskCalls = 0
        var historyReads = 0
        var auditing: suspend (Long) -> Outcome<Page<HistoryEntry>> = { offset -> Outcome.Success(Page((offset until minOf(offset + 50, 55)).map { HistoryEntry(2000 + it, "executed", 42, Instant.EPOCH, emptyList()) }, 55, 50, offset)) }
        var listing: suspend (TaskFilter, Long) -> Outcome<Page<Task>> = { _, offset -> Outcome.Success(Page(listOf(sampleTask()), 1, 50, offset)) }
        var reading: suspend () -> Outcome<Task> = { Outcome.Success(sampleTask()) }
        override suspend fun tasks(warehouseId: Long, filter: TaskFilter, limit: Int, offset: Long): Outcome<Page<Task>> { assertEquals(42L, warehouseId); reads++; return listing(filter, offset) }
        override suspend fun task(id: Long): Outcome<Task> { taskCalls++; assertEquals(101L, id); return reading() }
        override suspend fun manufacturingPreview(task: Task, command: ManufacturingCommand): Outcome<ManufacturingPreview> = error("production not used")
        override suspend fun manufacture(task: Task, command: ManufacturingCommand, preview: ManufacturingPreview, comment: String): CommandOutcome = error("production not used")
        override suspend fun dispatch(task: Task, command: DispatchCommand): CommandOutcome = error("dispatch not used")
        override suspend fun receive(task: Task, command: ReceiveCommand): CommandOutcome = error("receive not used")
        override suspend fun executions(taskId: Long, limit: Int, offset: Long): Outcome<Page<Execution>> = Outcome.Success(Page((offset until minOf(offset + limit, 55)).map { Execution(1000 + it, taskId, 42, Instant.EPOCH, "", false, emptyList(), null, null, null) }, 55, limit, offset))
        override suspend fun execution(taskId: Long, executionId: Long): Outcome<Execution> = Outcome.Failure(AppError(ErrorKind.NOT_FOUND, "none"))
        override suspend fun history(taskId: Long, limit: Int, offset: Long): Outcome<Page<HistoryEntry>> { historyReads++; return auditing(offset) }
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
    private fun detail(tasks: Tasks, debug: Boolean = false, pending: PendingCommandRepository = object : PendingCommandRepository {
        override fun observe() = kotlinx.coroutines.flow.flowOf(emptyList<PendingCommand>())
        override suspend fun retry(commandId: String): CommandOutcome = error("unused")
    }) = TaskDetailViewModel(SavedStateHandle(mapOf("taskId" to 101L)), LoadTaskUseCase(tasks), LoadExecutionsUseCase(tasks), LoadHistoryUseCase(tasks), pending, TaskDebugOptions(showHistoryAvailable = debug))
    @Test fun detailReadsAndAllowsDirectExecutionWithoutAnyLifecycleMutation() = runTest(dispatcher) {
        val tasks = Tasks()
        val vm = detail(tasks)
        runCurrent()
        assertTrue(vm.uiState.value.executionAvailable)
        assertEquals(1, tasks.taskCalls)
        vm.onIntent(TaskDetailIntent.Refresh)
        runCurrent()
        assertTrue(vm.uiState.value.executionAvailable)
        assertEquals(2, tasks.taskCalls)
    }
    @Test fun refreshFailureDoesNotPermitExecutionOnStaleTask() = runTest(dispatcher) {
        val tasks = Tasks()
        val vm = detail(tasks)
        runCurrent()
        tasks.reading = { Outcome.Failure(AppError(ErrorKind.FORBIDDEN, "Права отозваны")) }
        vm.onIntent(TaskDetailIntent.Refresh)
        assertFalse(vm.uiState.value.executionAvailable)
        runCurrent()
        assertTrue(vm.uiState.value.staleTask)
        assertFalse(vm.uiState.value.executionAvailable)
    }
    @Test fun freshServerReadRevokingExecutionRemainsUnavailable() = runTest(dispatcher) {
        val tasks = Tasks()
        val vm = detail(tasks)
        runCurrent()
        tasks.reading = { Outcome.Success(sampleTask(version = 8, actions = emptySet())) }
        vm.onIntent(TaskDetailIntent.Refresh)
        runCurrent()
        assertEquals(8L, vm.uiState.value.task?.version)
        assertFalse(vm.uiState.value.executionAvailable)
    }
    @Test fun savedHistoricalCommandBlocksExecutionUntilItsAckAndFreshRead() = runTest(dispatcher) {
        val tasks = Tasks()
        val saved = PendingCommand("old-start", 101, "start", CommandSummary("Исторический запрос", emptyList()), PendingState.UNKNOWN, Instant.EPOCH, 1)
        val commands = kotlinx.coroutines.flow.MutableStateFlow(listOf(saved))
        val pending = object : PendingCommandRepository {
            override fun observe() = commands
            override suspend fun retry(commandId: String): CommandOutcome = error("only explicit recovery retries")
        }
        val vm = detail(tasks, pending = pending)
        runCurrent()
        assertFalse(vm.uiState.value.executionAvailable)
        tasks.reading = { Outcome.Success(sampleTask(version = 9)) }
        commands.value = listOf(saved.copy(state = PendingState.ACKNOWLEDGED, confirmation = CommandConfirmation(saved.id, 101, 9, null, true)))
        runCurrent()
        assertNull(vm.uiState.value.pending)
        assertEquals(9L, vm.uiState.value.lastConfirmation?.taskVersion)
        assertEquals(9L, vm.uiState.value.task?.version)
        assertTrue(vm.uiState.value.executionAvailable)
    }
    @Test fun dedicatedHistoryAndExecutionPagesKeepAllFiftyFiveDistinctIds() = runTest(dispatcher) {
        val tasks = Tasks()
        val vm = detail(tasks, debug = true)
        runCurrent()
        assertEquals(50, vm.uiState.value.executions.size)
        assertFalse(vm.uiState.value.debugHistoryExpanded)
        assertTrue(vm.uiState.value.history.isEmpty())
        assertEquals(0, tasks.historyReads)
        vm.onIntent(TaskDetailIntent.LoadMoreHistory)
        runCurrent()
        assertEquals(0, tasks.historyReads)
        vm.onIntent(TaskDetailIntent.ToggleDebugHistory)
        runCurrent()
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
    @Test fun auditUnavailableIgnoresDisclosureAndPaginationWhileFactsRemainVisible() = runTest(dispatcher) {
        val tasks = Tasks()
        val vm = detail(tasks)
        runCurrent()
        vm.onIntent(TaskDetailIntent.ToggleDebugHistory)
        vm.onIntent(TaskDetailIntent.LoadMoreHistory)
        vm.onIntent(TaskDetailIntent.Refresh)
        runCurrent()
        assertFalse(vm.uiState.value.debugHistoryAvailable)
        assertFalse(vm.uiState.value.debugHistoryExpanded)
        assertTrue(vm.uiState.value.history.isEmpty())
        assertEquals(0, tasks.historyReads)
        assertEquals(50, vm.uiState.value.executions.size)
    }
    @Test fun collapsingAuditInvalidatesLateReadsAndReopeningFetchesFreshPage() = runTest(dispatcher) {
        val tasks = Tasks()
        val old = CompletableDeferred<Unit>()
        tasks.auditing = { offset -> withContext(NonCancellable) { old.await(); Outcome.Success(Page(listOf(HistoryEntry(2000, "old", 42, Instant.EPOCH, emptyList())), 1, 50, offset)) } }
        val vm = detail(tasks, debug = true)
        runCurrent()
        vm.onIntent(TaskDetailIntent.ToggleDebugHistory)
        runCurrent()
        assertTrue(vm.uiState.value.historyLoading)
        vm.onIntent(TaskDetailIntent.ToggleDebugHistory)
        assertFalse(vm.uiState.value.debugHistoryExpanded)
        assertTrue(vm.uiState.value.history.isEmpty())
        vm.onIntent(TaskDetailIntent.LoadMoreHistory)
        tasks.auditing = { offset -> Outcome.Success(Page(listOf(HistoryEntry(9000, "fresh", null, Instant.EPOCH, emptyList())), 1, 50, offset)) }
        vm.onIntent(TaskDetailIntent.ToggleDebugHistory)
        runCurrent()
        assertEquals(listOf(9000L), vm.uiState.value.history.map(HistoryEntry::id))
        old.complete(Unit)
        runCurrent()
        assertEquals(listOf(9000L), vm.uiState.value.history.map(HistoryEntry::id))
        vm.onIntent(TaskDetailIntent.ToggleDebugHistory)
        vm.onIntent(TaskDetailIntent.Refresh)
        runCurrent()
        assertFalse(vm.uiState.value.debugHistoryExpanded)
        assertTrue(vm.uiState.value.history.isEmpty())
        assertEquals(2, tasks.historyReads)
    }

}
