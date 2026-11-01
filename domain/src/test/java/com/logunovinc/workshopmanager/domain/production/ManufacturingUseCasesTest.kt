package com.logunovinc.workshopmanager.domain.production

import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.task.*
import com.logunovinc.workshopmanager.domain.pending.*
import java.math.BigDecimal
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

class ManufacturingUseCasesTest {
    private class Repository : TaskRepository {
        var previews = 0
        var writes = 0
        var previewResult: ManufacturingPreview? = null
        override suspend fun manufacturingPreview(task: Task, command: ManufacturingCommand): Outcome<ManufacturingPreview> { previews++; return Outcome.Success(previewResult ?: com.logunovinc.workshopmanager.domain.production.manufacturingPreview(task, command)) }
        override suspend fun manufacture(task: Task, command: ManufacturingCommand, preview: ManufacturingPreview, comment: String): CommandOutcome { writes++; return CommandOutcome.Confirmed(CommandConfirmation("id", task.id, 4, 5001, false)) }
        override suspend fun dispatch(task: Task, command: DispatchCommand): CommandOutcome = error("unused")
        override suspend fun receive(task: Task, command: ReceiveCommand): CommandOutcome = error("unused")
        override suspend fun tasks(warehouseId: Long, filter: TaskFilter, limit: Int, offset: Long): Outcome<Page<Task>> = error("unused")
        override suspend fun task(id: Long): Outcome<Task> = error("unused")
        override suspend fun start(task: Task): CommandOutcome = error("unused")
        override suspend fun executions(taskId: Long, limit: Int, offset: Long): Outcome<Page<Execution>> = error("unused")
        override suspend fun execution(taskId: Long, executionId: Long): Outcome<Execution> = error("unused")
        override suspend fun history(taskId: Long, limit: Int, offset: Long): Outcome<Page<HistoryEntry>> = error("unused")
    }
    @Test fun readyNeedsOnlyOutputAndRejectsManualOverridesLegacyAndAmbiguousOutput() {
        val prepare = PrepareManufacturingUseCase()
        val task = productionTask()
        val command = (prepare(task, ManufacturingDraft(ActualLineInput(3002, "3,0000"))) as Outcome.Success).value as ManufacturingCommand.ByRecipe
        assertEquals("3.0000", command.output.quantity!!.toPlainString())
        assertEquals(1L, command.recipeVersion)
        assertTrue(prepare(task, ManufacturingDraft(ActualLineInput(3002, "3"), listOf(PrototypeMaterialInput(301, "1")))) is Outcome.Failure)
        assertTrue(prepare(task.copy(production = null), ManufacturingDraft(ActualLineInput(3002, "3"))) is Outcome.Failure)
        assertTrue(prepare(task.copy(lines = task.lines + task.lines.single().copy(id = 3003)), ManufacturingDraft(ActualLineInput(3002, "3"))) is Outcome.Failure)
        assertTrue(prepare(task.copy(production = task.production!!.copy(schemaVersion = 1)), ManufacturingDraft(ActualLineInput(3002, "3"))) is Outcome.Failure)
        assertTrue(prepare(task, ManufacturingDraft(ActualLineInput(501, "3"))) is Outcome.Failure)
    }
    @Test fun prototypeBasisIsWholeFirstOutputAndRawIdsAreDifferentContracts() {
        val task = productionTask(prototype = true)
        val command = (PrepareManufacturingUseCase()(task, ManufacturingDraft(ActualLineInput(3002, "2"), listOf(PrototypeMaterialInput(301, "4", unitName = "шт", unitQuantity = "4")))) as Outcome.Success).value as ManufacturingCommand.Prototype
        assertEquals(BigDecimal("2"), command.output.quantity)
        assertEquals(3002L, command.output.taskLineId)
        assertEquals(301L, command.materials.single().productId)
        assertEquals(BigDecimal("4"), command.materials.single().quantity)
        assertEquals(0L, command.recipeVersion)
        assertEquals(RecipeStatus.AWAITING_PROTOTYPE, task.production!!.status)
    }
    @Test fun rawMaterialLimitsUnitsAndSheetGeometryAreValidatedWithoutAreaMath() {
        val task = productionTask(prototype = true)
        fun prepare(input: PrototypeMaterialInput) = PrepareManufacturingUseCase()(task, ManufacturingDraft(ActualLineInput(3002, "2"), listOf(input)))
        for (invalid in listOf(PrototypeMaterialInput(0, "4"), PrototypeMaterialInput(301, "0"), PrototypeMaterialInput(301, "1.00001"), PrototypeMaterialInput(301, "10000000000"), PrototypeMaterialInput(301, "4", sheetLength = "1"), PrototypeMaterialInput(301, sheets = "1"), PrototypeMaterialInput(301, sheets = "1", sheetLength = "1.2345"), PrototypeMaterialInput(301, sheets = "0", sheetLength = "1"), PrototypeMaterialInput(301, "4", unitName = "шт"))) assertTrue(prepare(invalid) is Outcome.Failure)
        val sheets = (prepare(PrototypeMaterialInput(301, sheets = "3", sheetLength = "1,234")) as Outcome.Success).value as ManufacturingCommand.Prototype
        assertNull(sheets.materials.single().quantity)
        assertEquals(BigDecimal("1.234"), sheets.materials.single().sheetLength)
        assertTrue(prepare(PrototypeMaterialInput(301, quantity = "0", sheets = "3", sheetLength = "0.001")) is Outcome.Success)
    }
    @Test fun onlyMatchingServerPreviewCanAuthorizeExecutionAndNoPrototypePhaseFlipOccurs() = runTest {
        val repository = Repository()
        val task = productionTask()
        val command = (PrepareManufacturingUseCase()(task, ManufacturingDraft(ActualLineInput(3002, "3"))) as Outcome.Success).value
        val preview = (PreviewManufacturingUseCase(repository)(task, command) as Outcome.Success).value
        assertEquals(BigDecimal("6"), preview.materials.single().quantity)
        val execute = ExecuteManufacturingUseCase(repository)
        assertTrue(execute(task, command, preview.copy(taskVersion = 99), "") is CommandOutcome.Rejected)
        assertTrue(execute(task, command, preview.copy(canExecute = false), "") is CommandOutcome.Rejected)
        assertEquals(0, repository.writes)
        assertTrue(execute(task, command, preview, "") is CommandOutcome.Confirmed)
        assertEquals(1, repository.writes)
        assertEquals(BigDecimal.ZERO, task.lines.single().actual)
    }
    @Test fun sheetOutputUsesFrozenSheetsEvenWhenBaseQuantityIsZero() {
        val task = productionTask().let { it.copy(lines = it.lines.map { line -> line.copy(planned = BigDecimal.ZERO, remaining = BigDecimal.ZERO, plannedSheets = 9, remainingSheets = 9, actualSheets = 0) },
            production = it.production!!.copy(basisMeasure = BasisMeasure.SHEETS, basisOutput = RecipeOutput(BigDecimal.ZERO, 9, "м²", BigDecimal("0.001")))) }
        val command = (PrepareManufacturingUseCase()(task, ManufacturingDraft(ActualLineInput(3002, sheets = "3"))) as Outcome.Success).value
        assertEquals(3, command.output.sheets)
        assertNull(command.output.quantity)
    }
}
