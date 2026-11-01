package com.logunovinc.workshopmanager.feature.tasks

import androidx.lifecycle.SavedStateHandle
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.task.*
import com.logunovinc.workshopmanager.domain.production.*
import com.logunovinc.workshopmanager.domain.pending.*
import com.logunovinc.workshopmanager.domain.stock.*
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ManufacturingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }
    private class Tasks : TaskRepository {
        override suspend fun closeIncompleteCapability(): Outcome<Boolean> = error("unused")
        override suspend fun closeIncomplete(task: Task, reason: String): com.logunovinc.workshopmanager.domain.pending.CommandOutcome = error("unused")
        var current = productionTask()
        var previewCalls = 0
        var writes = 0
        val submitted = mutableListOf<ManufacturingCommand>()
        var previewing: suspend (Task, ManufacturingCommand) -> Outcome<ManufacturingPreview> = { task, command -> Outcome.Success(com.logunovinc.workshopmanager.feature.tasks.manufacturingPreview(task, command)) }
        var writing: suspend (Task, ManufacturingCommand) -> CommandOutcome = { task, command ->
            if (command is ManufacturingCommand.Prototype) current = productionTask(prototype = true, ready = true, version = 3).let { ready -> ready.copy(
                lines = ready.lines.map { it.copy(actual = BigDecimal("2"), remaining = BigDecimal("8")) },
                production = ready.production!!.copy(basisOutput = RecipeOutput(BigDecimal("2"), null, "шт", null), materials = listOf(RecipeMaterial(301, "Материал", null, BigDecimal("4"), null, null, "шт", null, null)))) }
            CommandOutcome.Confirmed(CommandConfirmation("saved", task.id, 3, 5001, false))
        }
        override suspend fun task(id: Long): Outcome<Task> = Outcome.Success(current)
        override suspend fun manufacturingPreview(task: Task, command: ManufacturingCommand): Outcome<ManufacturingPreview> { previewCalls++; return previewing(task, command) }
        override suspend fun manufacture(task: Task, command: ManufacturingCommand, preview: ManufacturingPreview, comment: String): CommandOutcome { writes++; submitted += command; return writing(task, command) }
        override suspend fun dispatch(task: Task, command: DispatchCommand): CommandOutcome = error("unused")
        override suspend fun receive(task: Task, command: ReceiveCommand): CommandOutcome = error("unused")
        override suspend fun tasks(warehouseId: Long, filter: TaskFilter, limit: Int, offset: Long): Outcome<Page<Task>> = error("unused")
        override suspend fun executions(taskId: Long, limit: Int, offset: Long): Outcome<Page<Execution>> = error("unused")
        override suspend fun execution(taskId: Long, executionId: Long): Outcome<Execution> = error("unused")
        override suspend fun history(taskId: Long, limit: Int, offset: Long): Outcome<Page<HistoryEntry>> = error("unused")
    }
    private class Stock : StockRepository {
        override suspend fun stock(warehouseId: Long, filter: StockFilter, limit: Int, offset: Long): Outcome<Page<StockPosition>> = error("unused")
        override suspend fun position(warehouseId: Long, productId: Long): Outcome<StockPosition> = error("unused")
        override suspend fun products(query: String, limit: Int, offset: Long): Outcome<Page<Product>> = Outcome.Success(Page(listOf(Product(301, "Материал", "шт", false, null)), 1, limit, offset))
        override suspend fun product(productId: Long): Outcome<Product> = error("unused")
        override suspend fun definition(productId: Long): Outcome<StockDefinition> = Outcome.Success(StockDefinition(productId, "Материал", null, "шт", false, null, listOf(ProductUnit("шт", BigDecimal.ONE))))
    }
    private class Pending : PendingCommandRepository {
        val commands = MutableStateFlow(emptyList<PendingCommand>())
        override fun observe() = commands
        override suspend fun retry(commandId: String): CommandOutcome = error("retry only through recovery screen")
    }
    private fun vm(tasks: Tasks, pending: Pending = Pending()) = ManufacturingViewModel(SavedStateHandle(mapOf("taskId" to 101L)), LoadTaskUseCase(tasks), PrepareManufacturingUseCase(), PreviewManufacturingUseCase(tasks), ExecuteManufacturingUseCase(tasks), SearchProductsUseCase(Stock()), LoadStockDefinitionUseCase(Stock()), pending)
    private fun prototypeInput(vm: ManufacturingViewModel) {
        vm.onIntent(ManufacturingIntent.OutputQuantityChanged("2"))
        vm.onIntent(ManufacturingIntent.AddMaterial(Product(301, "Материал", "шт", false, null)))
        val id = vm.uiState.value.materials.single().id
        vm.onIntent(ManufacturingIntent.MaterialChanged(id, PrototypeMaterialField.QUANTITY, "4"))
    }
    @Test fun editsInvalidatePreviewAndLateOlderPreviewCannotReplaceCurrentBinding() = runTest(dispatcher) {
        val tasks = Tasks()
        val old = CompletableDeferred<Unit>()
        tasks.previewing = { task, command ->
            if (command.output.quantity == BigDecimal("3")) withContext(NonCancellable) { old.await(); Outcome.Success(manufacturingPreview(task, command)) }
            else Outcome.Success(manufacturingPreview(task, command))
        }
        val vm = vm(tasks)
        runCurrent()
        vm.onIntent(ManufacturingIntent.OutputQuantityChanged("3")); vm.onIntent(ManufacturingIntent.PreviewClick)
        runCurrent()
        vm.onIntent(ManufacturingIntent.OutputQuantityChanged("4")); vm.onIntent(ManufacturingIntent.PreviewClick)
        runCurrent()
        assertEquals(BigDecimal("4"), vm.uiState.value.binding!!.command.output.quantity)
        old.complete(Unit); runCurrent()
        assertEquals(BigDecimal("4"), vm.uiState.value.binding!!.command.output.quantity)
        vm.onIntent(ManufacturingIntent.CommentChanged("Новый комментарий"))
        assertNull(vm.uiState.value.binding)
        vm.onIntent(ManufacturingIntent.ReviewClick); vm.onIntent(ManufacturingIntent.ConfirmClick)
        runCurrent()
        assertEquals(0, tasks.writes)
    }
    @Test fun prototypePreviewDoesNotFlipPhaseAndOneAckSwitchesToOutputOnlyRecipe() = runTest(dispatcher) {
        val tasks = Tasks().apply { current = productionTask(prototype = true) }
        val gate = CompletableDeferred<Unit>()
        val original = tasks.writing
        tasks.writing = { task, command -> gate.await(); original(task, command) }
        val vm = vm(tasks)
        runCurrent(); prototypeInput(vm); runCurrent()
        assertNotNull(vm.uiState.value.materials.single().definition)
        vm.onIntent(ManufacturingIntent.PreviewClick); runCurrent()
        assertEquals(RecipeStatus.READY, vm.uiState.value.binding!!.preview.proposedRecipe!!.status)
        assertTrue(vm.uiState.value.prototype)
        assertEquals(0, tasks.writes)
        vm.onIntent(ManufacturingIntent.ReviewClick); vm.onIntent(ManufacturingIntent.ConfirmClick); vm.onIntent(ManufacturingIntent.ConfirmClick)
        runCurrent()
        assertEquals(1, tasks.writes)
        assertTrue(vm.uiState.value.prototype)
        gate.complete(Unit); runCurrent()
        assertFalse(vm.uiState.value.prototype)
        assertTrue(vm.uiState.value.materials.isEmpty())
        assertEquals(BigDecimal("2"), vm.uiState.value.task!!.lines.single().actual)
        assertEquals(BigDecimal("8"), vm.uiState.value.task!!.lines.single().remaining)
        vm.onIntent(ManufacturingIntent.OutputQuantityChanged("3")); vm.onIntent(ManufacturingIntent.PreviewClick); runCurrent()
        assertTrue(vm.uiState.value.binding!!.command is ManufacturingCommand.ByRecipe)
        assertEquals(BigDecimal("6"), vm.uiState.value.binding!!.preview.materials.single().quantity)
        assertEquals(1, tasks.writes)
    }
    @Test fun unknownPrototypeStaysBlockedEvenWhenTaskAlreadyShowsReadyUntilMatchingAck() = runTest(dispatcher) {
        val tasks = Tasks().apply { current = productionTask(prototype = true) }
        val pending = Pending()
        val saved = PendingCommand("saved", 101, "prototype", CommandSummary("Прототип", listOf("Выпуск2, материал4")), PendingState.UNKNOWN, Instant.EPOCH, 1)
        tasks.writing = { _, _ -> pending.commands.value = listOf(saved); CommandOutcome.Unresolved(saved, AppError(ErrorKind.NETWORK, "Исход неизвестен")) }
        val vm = vm(tasks, pending)
        runCurrent(); prototypeInput(vm); runCurrent()
        vm.onIntent(ManufacturingIntent.PreviewClick); runCurrent()
        vm.onIntent(ManufacturingIntent.ReviewClick); vm.onIntent(ManufacturingIntent.ConfirmClick); runCurrent()
        assertTrue(vm.uiState.value.result is ExecutionResultState.Unresolved)
        assertEquals(BigDecimal.ZERO, vm.uiState.value.task!!.lines.single().actual)
        tasks.current = productionTask(prototype = true, ready = true)
        vm.onIntent(ManufacturingIntent.Refresh); runCurrent()
        assertFalse(vm.uiState.value.editable)
        vm.onIntent(ManufacturingIntent.OutputQuantityChanged("3")); vm.onIntent(ManufacturingIntent.PreviewClick)
        runCurrent()
        assertEquals(1, tasks.writes)
        assertEquals(1, tasks.previewCalls)
        pending.commands.value = listOf(saved.copy(state = PendingState.ACKNOWLEDGED, confirmation = CommandConfirmation("saved", 101, 3, 5001, true)))
        runCurrent()
        assertTrue(vm.uiState.value.result is ExecutionResultState.Confirmed)
        assertTrue(vm.uiState.value.editable)
        assertFalse(vm.uiState.value.prototype)
    }
    @Test fun refreshedRecipeVersionInvalidatesOldPreviewInsteadOfReplacingVersionsDuringSubmit() = runTest(dispatcher) {
        val tasks = Tasks()
        val vm = vm(tasks)
        runCurrent(); vm.onIntent(ManufacturingIntent.OutputQuantityChanged("3")); vm.onIntent(ManufacturingIntent.PreviewClick); runCurrent()
        assertNotNull(vm.uiState.value.binding)
        tasks.current = tasks.current.copy(version = 4, production = tasks.current.production!!.copy(recipeVersion = 2))
        vm.onIntent(ManufacturingIntent.Refresh); runCurrent()
        assertNull(vm.uiState.value.binding)
        vm.onIntent(ManufacturingIntent.ReviewClick); vm.onIntent(ManufacturingIntent.ConfirmClick); runCurrent()
        assertEquals(0, tasks.writes)
        assertEquals(2L, vm.uiState.value.task!!.production!!.recipeVersion)
    }
    @Test fun deniedPreviewAndUnsupportedDescriptorNeverEnableConfirmationOrFallback() = runTest(dispatcher) {
        val tasks = Tasks()
        tasks.previewing = { task, command -> Outcome.Success(manufacturingPreview(task, command, false).copy(issues = listOf(PreviewIssue("INSUFFICIENT_STOCK", "Не хватает материала", 301, null, 0, emptyList())))) }
        val vm = vm(tasks)
        runCurrent(); vm.onIntent(ManufacturingIntent.OutputQuantityChanged("3")); vm.onIntent(ManufacturingIntent.PreviewClick); runCurrent()
        assertFalse(vm.uiState.value.binding!!.preview.canExecute)
        vm.onIntent(ManufacturingIntent.ReviewClick); vm.onIntent(ManufacturingIntent.ConfirmClick); runCurrent()
        assertFalse(vm.uiState.value.reviewOpen)
        assertEquals(0, tasks.writes)
        tasks.current = tasks.current.copy(production = null)
        vm.onIntent(ManufacturingIntent.Refresh); runCurrent()
        vm.onIntent(ManufacturingIntent.OutputQuantityChanged("3")); vm.onIntent(ManufacturingIntent.PreviewClick); runCurrent()
        assertFalse(vm.uiState.value.editable)
        assertEquals(1, tasks.previewCalls)
    }
}
