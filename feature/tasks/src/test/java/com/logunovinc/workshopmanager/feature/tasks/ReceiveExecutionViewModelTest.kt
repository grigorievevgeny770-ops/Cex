package com.logunovinc.workshopmanager.feature.tasks

import androidx.lifecycle.SavedStateHandle
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.task.*
import com.logunovinc.workshopmanager.domain.pending.*
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiveExecutionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }
    private class Tasks : TaskRepository {
        var count = 0
        var reads = 0
        var command: ReceiveCommand? = null
        var current = sampleTask().copy(lines = listOf(TaskLine(1001, 301, "Материал", null, "receive", "шт", null, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, null, null, null, null, null, null, null)))
        var receiveResult: suspend () -> CommandOutcome = { CommandOutcome.Confirmed(CommandConfirmation("command", 101, 4, 5001, false)) }
        override suspend fun receive(task: Task, command: ReceiveCommand): CommandOutcome { count++; this.command = command; return receiveResult() }
        override suspend fun task(id: Long): Outcome<Task> { reads++; return Outcome.Success(current) }
        override suspend fun tasks(warehouseId: Long, filter: TaskFilter, limit: Int, offset: Long): Outcome<Page<Task>> = error("unused")
        override suspend fun start(task: Task): CommandOutcome = error("unused")
        override suspend fun executions(taskId: Long, limit: Int, offset: Long): Outcome<Page<Execution>> = error("unused")
        override suspend fun execution(taskId: Long, executionId: Long): Outcome<Execution> = error("unused")
        override suspend fun history(taskId: Long, limit: Int, offset: Long): Outcome<Page<HistoryEntry>> = error("unused")
    }
    private class Pending : PendingCommandRepository {
        val commands = MutableStateFlow(emptyList<PendingCommand>())
        override fun observe() = commands
        override suspend fun retry(commandId: String): CommandOutcome = error("retry is explicit recovery screen only")
    }
    private fun vm(tasks: Tasks, pending: Pending) = ReceiveExecutionViewModel(SavedStateHandle(mapOf("taskId" to 101L)), LoadTaskUseCase(tasks), PrepareReceiveUseCase(), ExecuteReceiveUseCase(tasks), pending)
    @Test fun inputAndReviewNeverWriteAndDoubleConfirmUsesOneImmutableStep() = runTest(dispatcher) {
        val tasks = Tasks()
        val gate = CompletableDeferred<Unit>()
        tasks.receiveResult = { gate.await(); CommandOutcome.Confirmed(CommandConfirmation("command", 101, 4, 5001, false)) }
        val vm = vm(tasks, Pending())
        runCurrent()
        vm.onIntent(ReceiveExecutionIntent.QuantityChanged(1001, "4,0000"))
        assertEquals(0, tasks.count)
        vm.onIntent(ReceiveExecutionIntent.ReviewClick)
        vm.onIntent(ReceiveExecutionIntent.QuantityChanged(1001, "9"))
        assertEquals("4,0000", vm.uiState.value.inputs.single().quantity)
        assertEquals(0, tasks.count)
        vm.onIntent(ReceiveExecutionIntent.ConfirmClick)
        vm.onIntent(ReceiveExecutionIntent.ConfirmClick)
        vm.onIntent(ReceiveExecutionIntent.Refresh)
        runCurrent()
        assertEquals(1, tasks.count)
        assertEquals("4.0000", tasks.command!!.lines.single().quantity!!.toPlainString())
        assertEquals(1, tasks.reads)
        gate.complete(Unit)
        runCurrent()
        assertTrue(vm.uiState.value.result is ExecutionResultState.Confirmed)
        assertEquals(2, tasks.reads)
        assertEquals("", vm.uiState.value.inputs.single().quantity)
    }
    @Test fun processRecreatedPendingBlocksNewFormAndAcknowledgedRecoveryRefreshesServerTruth() = runTest(dispatcher) {
        val tasks = Tasks()
        val pending = Pending()
        val saved = PendingCommand("saved", 101, "RECEIVE_GOODS", CommandSummary("Приёмка", listOf("Материал: 4.0000 шт")), PendingState.UNKNOWN, Instant.EPOCH, 1)
        pending.commands.value = listOf(saved)
        val vm = vm(tasks, pending)
        runCurrent()
        assertFalse(vm.uiState.value.editable)
        vm.onIntent(ReceiveExecutionIntent.QuantityChanged(1001, "5"))
        vm.onIntent(ReceiveExecutionIntent.ReviewClick)
        assertNull(vm.uiState.value.review)
        assertEquals(0, tasks.count)
        val receipt = CommandConfirmation("saved", 101, 4, 5001, true)
        tasks.current = tasks.current.copy(version = 4, lines = tasks.current.lines.map { it.copy(actual = BigDecimal("4"), remaining = BigDecimal("6")) })
        pending.commands.value = listOf(saved.copy(state = PendingState.ACKNOWLEDGED, confirmation = receipt))
        runCurrent()
        assertTrue(vm.uiState.value.result is ExecutionResultState.Confirmed)
        assertEquals(BigDecimal("4"), vm.uiState.value.task!!.lines.single().actual)
        assertTrue(vm.uiState.value.editable)
    }
    @Test fun unknownResultKeepsSummaryAndDoesNotResubmitOnRefresh() = runTest(dispatcher) {
        val tasks = Tasks()
        val command = PendingCommand("saved", 101, "RECEIVE_GOODS", CommandSummary("Приёмка", listOf("Материал: 4 шт")), PendingState.UNKNOWN, Instant.EPOCH, 1)
        tasks.receiveResult = { CommandOutcome.Unresolved(command, AppError(ErrorKind.NETWORK, "Исход неизвестен")) }
        val vm = vm(tasks, Pending())
        runCurrent()
        vm.onIntent(ReceiveExecutionIntent.QuantityChanged(1001, "4")); vm.onIntent(ReceiveExecutionIntent.ReviewClick); vm.onIntent(ReceiveExecutionIntent.ConfirmClick)
        runCurrent()
        assertTrue(vm.uiState.value.result is ExecutionResultState.Unresolved)
        vm.onIntent(ReceiveExecutionIntent.Refresh)
        runCurrent()
        assertTrue(vm.uiState.value.result is ExecutionResultState.Unresolved)
        assertFalse(vm.uiState.value.editable)
        assertEquals(1, tasks.count)
    }
}
