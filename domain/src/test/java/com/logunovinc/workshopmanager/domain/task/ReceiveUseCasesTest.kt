package com.logunovinc.workshopmanager.domain.task

import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.pending.*
import java.math.BigDecimal
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

class ReceiveUseCasesTest {
    private fun line(remaining: String = "10", sheets: Int? = null) = TaskLine(1001, 301, "Материал", null, "receive", "шт", null,
        BigDecimal("10"), BigDecimal.ZERO, BigDecimal(remaining), null, null, null, sheets, if (sheets != null) 0 else null, sheets, BigDecimal("1.234"))
    private fun task(remaining: String = "10", sheets: Int? = null) = sampleTask().copy(lines = listOf(line(remaining, sheets)))
    private class Repository : TaskRepository {
        var received: ReceiveCommand? = null
        override suspend fun receive(task: Task, command: ReceiveCommand): CommandOutcome { received = command; return CommandOutcome.Confirmed(CommandConfirmation("id", task.id, task.version + 1, 5001, false)) }
        override suspend fun tasks(warehouseId: Long, filter: TaskFilter, limit: Int, offset: Long): Outcome<Page<Task>> = error("unused")
        override suspend fun task(id: Long): Outcome<Task> = error("unused")
        override suspend fun start(task: Task): CommandOutcome = error("unused")
        override suspend fun executions(taskId: Long, limit: Int, offset: Long): Outcome<Page<Execution>> = error("unused")
        override suspend fun execution(taskId: Long, executionId: Long): Outcome<Execution> = error("unused")
        override suspend fun history(taskId: Long, limit: Int, offset: Long): Outcome<Page<HistoryEntry>> = error("unused")
    }
    @Test fun partialFourThenFinalFiveIsStepDeltaAndShortageOne() = runTest {
        val repository = Repository()
        val execute = ExecuteReceiveUseCase(repository)
        assertTrue(execute(task(), ReceiveDraft(listOf(ActualLineInput(1001, "4")))) is CommandOutcome.Confirmed)
        assertEquals(BigDecimal("4"), repository.received!!.lines.single().quantity)
        val afterFour = task("6").copy(version = 4, transferVersion = 3, lines = listOf(line("6").copy(actual = BigDecimal("4"))))
        assertTrue(execute(afterFour, ReceiveDraft(listOf(ActualLineInput(1001, "5")), final = true, reason = "Одна единица не прибыла")) is CommandOutcome.Confirmed)
        assertEquals(BigDecimal("5"), repository.received!!.lines.single().quantity)
        assertTrue(repository.received!!.shortage)
        assertTrue(repository.received!!.final)
    }
    @Test fun zeroFinalRequiresReasonWhilePartialRequiresPositiveFact() {
        val prepare = PrepareReceiveUseCase()
        assertTrue(prepare(task(), ReceiveDraft(emptyList())) is Outcome.Failure)
        assertTrue(prepare(task(), ReceiveDraft(emptyList(), final = true)) is Outcome.Failure)
        val zeroFinal = prepare(task(), ReceiveDraft(listOf(ActualLineInput(1001, "0")), final = true, reason = "Весь товар отсутствует")) as Outcome.Success
        assertTrue(zeroFinal.value.lines.isEmpty())
        assertTrue(zeroFinal.value.shortage)
        assertTrue(prepare(task(), ReceiveDraft(listOf(ActualLineInput(1001, "10")), final = true)) is Outcome.Success)
    }
    @Test fun foreignRepeatedExcessAndOverprecisionDoNotReachRepository() = runTest {
        val repository = Repository()
        val execute = ExecuteReceiveUseCase(repository)
        for (inputs in listOf(listOf(ActualLineInput(9999, "1")), listOf(ActualLineInput(1001, "11")), listOf(ActualLineInput(1001, "1.00001")), listOf(ActualLineInput(1001, "-1")), listOf(ActualLineInput(1001, "1"), ActualLineInput(1001, "1")))) {
            assertTrue(execute(task(), ReceiveDraft(inputs)) is CommandOutcome.Rejected)
        }
        assertNull(repository.received)
    }
    @Test fun frozenSheetsWithZeroBaseArePhysicalFactAndBaseOnlyGeometryStaysQty() {
        val prepare = PrepareReceiveUseCase()
        val zeroBase = task("0", 9)
        val sheets = prepare(zeroBase, ReceiveDraft(listOf(ActualLineInput(1001, sheets = "9")), final = true)) as Outcome.Success
        assertEquals(9, sheets.value.lines.single().sheets)
        assertNull(sheets.value.lines.single().quantity)
        assertFalse(sheets.value.shortage)
        assertTrue(prepare(zeroBase, ReceiveDraft(listOf(ActualLineInput(1001, sheets = "10")))) is Outcome.Failure)
        assertTrue(prepare(zeroBase, ReceiveDraft(listOf(ActualLineInput(1001, sheets = "2147483648")))) is Outcome.Failure)
        assertTrue(prepare(task(), ReceiveDraft(listOf(ActualLineInput(1001, "1")))) is Outcome.Success)
        assertTrue(prepare(task(), ReceiveDraft(listOf(ActualLineInput(1001, sheets = "1")))) is Outcome.Failure)
    }
    @Test fun quantityCommaNormalizesExactlyWithoutRoundingOrFormatting() {
        assertEquals("4.0000", QuantityInputParser.quantity(" 4,0000 ")?.toPlainString())
        assertNull(QuantityInputParser.quantity("1,23456"))
        assertNull(QuantityInputParser.quantity("1e2"))
        assertEquals(0, QuantityInputParser.sheets("0"))
        assertEquals(Int.MAX_VALUE, QuantityInputParser.sheets("2147483647"))
        assertNull(QuantityInputParser.sheets("1.0"))
    }
}
