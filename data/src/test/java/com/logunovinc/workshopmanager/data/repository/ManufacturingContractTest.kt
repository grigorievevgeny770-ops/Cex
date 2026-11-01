package com.logunovinc.workshopmanager.data.repository

import com.logunovinc.workshopmanager.data.local.pending.*
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.task.*
import com.logunovinc.workshopmanager.domain.production.*
import com.logunovinc.workshopmanager.domain.pending.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.math.BigDecimal

class ManufacturingContractTest {
    private lateinit var h: ProductionHarness
    @Before fun before() { h = ProductionHarness() }
    @After fun after() { h.close() }
    @Test fun fixedTwentyOnTenOutputThreeShowsServerSixAndForbidsMaterialsInBothBodies() = runBlocking {
        val task = h.task("fixed_task")
        assertEquals(BigDecimal("20"), task.production!!.materials.single().quantity)
        assertEquals(BigDecimal("10"), task.production!!.basisOutput!!.quantity)
        val command = h.prepare(task)
        val (preview, request) = h.preview(task, command, "fixed_preview_3_6")
        assertEquals(BigDecimal("6"), preview.materials.single().quantity)
        assertEquals("/api/workshop/v2/tasks/101/manufacturing-preview", request.path)
        val previewBody = h.json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals(setOf("mode", "expected_version", "expected_recipe_version", "output"), previewBody.keys)
        val (result, execution) = h.execute(task, command, preview, "fixed_after3")
        assertEquals("/api/workshop/v2/tasks/101/manufacturing-executions", execution.path)
        val body = h.json.parseToJsonElement(execution.body.readUtf8()).jsonObject
        assertEquals(setOf("mode", "expected_version", "expected_recipe_version", "client_token", "output", "comment"), body.keys)
        assertEquals("by_recipe", body["mode"]!!.jsonPrimitive.content)
        assertEquals(3002L, body["output"]!!.jsonObject["task_line_id"]!!.jsonPrimitive.long)
        assertEquals(4L, (result as CommandOutcome.Confirmed).confirmation.taskVersion)
    }
    @Test fun cumulativeRoundingComesFromThreeServerPreviewsNotPhoneArithmetic() = runBlocking {
        val names = listOf("rounding_task", "rounding_after1", "rounding_after2")
        val next = listOf("rounding_after1", "rounding_after2", "rounding_completed")
        val expected = listOf("0.3333", "0.3334", "0.3333")
        for (index in 0..2) {
            val task = h.task(names[index])
            val command = h.prepare(task, "1")
            val (preview, _) = h.preview(task, command, "rounding_preview${index + 1}")
            assertEquals(expected[index], preview.materials.single().quantity.toPlainString())
            val (result, request) = h.execute(task, command, preview, next[index])
            assertTrue(result is CommandOutcome.Confirmed)
            assertFalse(request.body.readUtf8().contains("materials"))
        }
        assertEquals("0", h.task("rounding_completed").lines.single().remaining.toPlainString())
    }
    @Test fun prototypeTwoUsesFourOnceAndNextThreeUsesRecipeWithoutMaterialEntry() = runBlocking {
        val task = h.task("prototype_task")
        val command = h.prepare(task, "2", listOf(PrototypeMaterialInput(301, "4", unitName = "шт", unitQuantity = "4")))
        val (preview, previewRequest) = h.preview(task, command, "prototype_preview_2_4")
        assertEquals(RecipeStatus.AWAITING_PROTOTYPE, task.production!!.status)
        assertEquals(RecipeStatus.READY, preview.proposedRecipe!!.status)
        assertEquals(BigDecimal("2"), preview.proposedRecipe!!.basisOutput!!.quantity)
        val rawPreview = h.json.parseToJsonElement(previewRequest.body.readUtf8()).jsonObject
        assertEquals(setOf("mode", "expected_version", "expected_recipe_version", "output", "materials"), rawPreview.keys)
        assertEquals(301L, rawPreview["materials"]!!.jsonArray.single().jsonObject["product_id"]!!.jsonPrimitive.long)
        val (result, execution) = h.execute(task, command, preview, "prototype_ready_task")
        assertTrue(result is CommandOutcome.Confirmed)
        assertEquals("prototype", h.json.parseToJsonElement(execution.body.readUtf8()).jsonObject["mode"]!!.jsonPrimitive.content)
        val ready = h.task("prototype_ready_task")
        assertEquals(BigDecimal("2"), ready.lines.single().actual)
        assertEquals(BigDecimal("8"), ready.lines.single().remaining)
        val next = h.prepare(ready, "3")
        assertTrue(next is ManufacturingCommand.ByRecipe)
        val (nextPreview, _) = h.preview(ready, next, "after_prototype_preview_3_6")
        assertEquals(BigDecimal("6"), nextPreview.materials.single().quantity)
        val (_, request) = h.execute(ready, next, nextPreview, "prototype_after_next3")
        assertFalse(request.body.readUtf8().contains("materials"))
    }
    @Test fun concurrentPrototype409DoesNotLocallySaveRecipeAndRequiresFreshTask() = runBlocking {
        val task = h.task("prototype_task")
        val command = h.prepare(task, "2", listOf(PrototypeMaterialInput(301, "4")))
        val (preview, _) = h.preview(task, command, "prototype_preview_2_4")
        h.server.enqueue(h.reply("""{"code":"PROTOTYPE_ALREADY_RECORDED","message":"Другой работник уже создал норму","details":{}}""", 409))
        val rejected = ExecuteManufacturingUseCase(h.repository)(task, command, preview, "") as CommandOutcome.Rejected
        assertEquals("PROTOTYPE_ALREADY_RECORDED", rejected.error.code)
        assertEquals(RecipeStatus.AWAITING_PROTOTYPE, task.production!!.status)
        assertEquals("/api/workshop/v2/tasks/101/manufacturing-executions", h.server.takeRequest().path)
        val updated = h.task("prototype_ready_task")
        assertTrue(PrepareManufacturingUseCase()(updated, ManufacturingDraft(ActualLineInput(3002, "2"), listOf(PrototypeMaterialInput(301, "4")))) is Outcome.Failure)
    }
    @Test fun deficitUnknownDimensionAndUnsupportedBatchExplainServerIssues() = runBlocking {
        val task = h.task("fixed_task")
        val command = h.prepare(task)
        val (deficit, _) = h.preview(task, command, "deficit_preview")
        assertFalse(deficit.canExecute)
        assertEquals(BigDecimal("4"), deficit.materials.single().missingQuantity)
        assertTrue(ExecuteManufacturingUseCase(h.repository)(task, command, deficit, "") is CommandOutcome.Rejected)
        val sheetTask = h.task("sheet_task")
        val sheetCommand = h.prepare(sheetTask, "", sheets = "3")
        val (unknown, request) = h.preview(sheetTask, sheetCommand, "sheet_unknown_preview")
        assertEquals(DimensionAvailability.UNKNOWN, unknown.materials.single().dimensionAvailability)
        assertNull(unknown.materials.single().freeSheets)
        assertNull(unknown.materials.single().missingSheets)
        val output = h.json.parseToJsonElement(request.body.readUtf8()).jsonObject["output"]!!.jsonObject
        assertEquals(setOf("task_line_id", "sheets"), output.keys)
        h.server.enqueue(h.reply(ProductionFixtures.text("batch_error"), 422))
        val batch = PreviewManufacturingUseCase(h.repository)(sheetTask, sheetCommand) as Outcome.Failure
        assertEquals(422, batch.error.httpStatus)
        assertEquals("RECIPE_BATCH_NOT_SUPPORTED", batch.error.code)
        h.server.takeRequest()
        Unit
    }
    @Test fun legacyMissingOrUnknownDescriptorNeverCallsNewRoutesOrManualV1() = runBlocking {
        for (fixture in listOf("legacy_task", "legacy_descriptor_task", "unknown_mode_task")) {
            val task = h.task(fixture)
            assertFalse(task.canExecute)
            assertTrue(PrepareManufacturingUseCase()(task, ManufacturingDraft(ActualLineInput(3002, "3"))) is Outcome.Failure)
        }
        assertEquals(3, h.server.requestCount)
        val task = h.task("fixed_task")
        val command = h.prepare(task)
        h.server.enqueue(MockResponse().setResponseCode(404).setHeader("Content-Type", "text/html").setBody("<html>route missing</html>"))
        val error = PreviewManufacturingUseCase(h.repository)(task, command) as Outcome.Failure
        assertEquals(ErrorKind.INCOMPATIBLE_SERVER, error.error.kind)
        assertEquals("/api/workshop/v2/tasks/101/manufacturing-preview", h.server.takeRequest().path)
        assertEquals(5, h.server.requestCount)
    }
    @Test fun productionErrorCodesKeepActualHttpMappingAndNeverGuessStatus() = runBlocking {
        val task = h.task("fixed_task")
        val command = h.prepare(task)
        for ((code, status) in listOf("PROTOTYPE_REQUIRED" to 409, "PROTOTYPE_ALREADY_RECORDED" to 409, "RECIPE_VERSION_CONFLICT" to 409, "PRODUCTION_CONTRACT_UPGRADE_REQUIRED" to 409, "RECIPE_BATCH_NOT_SUPPORTED" to 422)) {
            h.server.enqueue(h.reply("""{"code":"$code","message":"Synthetic contract error","details":{}}""", status))
            val error = PreviewManufacturingUseCase(h.repository)(task, command) as Outcome.Failure
            assertEquals(code, error.error.code)
            assertEquals(status, error.error.httpStatus)
            h.server.takeRequest()
        }
    }
    @Test fun failedManufactureRemainsUnknownWithoutAnyLocalInputOutputOrRecipeChanges() = runBlocking {
        val task = h.task("prototype_task")
        val command = h.prepare(task, "2", listOf(PrototypeMaterialInput(301, "4")))
        val (preview, _) = h.preview(task, command, "prototype_preview_2_4")
        h.server.enqueue(h.reply("""{"message":"uncertain"}""", 500))
        val result = ExecuteManufacturingUseCase(h.repository)(task, command, preview, "") as CommandOutcome.Unresolved
        assertEquals(PendingState.UNKNOWN, result.command.state)
        assertEquals("prototype", result.command.action)
        assertEquals(BigDecimal.ZERO, task.lines.single().actual)
        assertEquals(RecipeStatus.AWAITING_PROTOTYPE, task.production!!.status)
        assertEquals("/api/workshop/v2/tasks/101/manufacturing-executions", h.server.takeRequest().path)
    }
}
