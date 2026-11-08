package com.logunovinc.workshopmanager.data.repository

import com.logunovinc.workshopmanager.data.local.pending.JournalMapper
import com.logunovinc.workshopmanager.data.remote.*
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.order.*
import com.logunovinc.workshopmanager.domain.pending.*
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*

class WorkshopOrderContractTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: RealWorkshopOrderRepository
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private fun reply(body: String, status: Int = 200) = MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)
    private val order = """{"id":7,"version":3,"status":"in_progress","due_at":"2026-11-20T00:00:00Z","comment":"кромка","allowed_actions":["manufacture"],"lines":[{"id":1,"catalog_product_id":501,"product_name":"Конёк простой 2000","size":"2000","quantity":"2","unit":"шт","norm_known":true},{"id":2,"catalog_product_id":502,"product_name":"Отлив П100х1250","size":"1250","quantity":"1","unit":"шт","norm_known":false}]}"""
    private fun ack(status: String, version: Int = 4) = """{"result":{"id":7,"duplicate":false},"request":{"id":7,"version":$version,"status":"$status"}}"""
    @Before fun before() {
        server = MockWebServer().apply { start() }
        val snapshot = SessionSnapshot(server.url("/").toString(), 42, 0)
        val current = object : CurrentSession {
            override val profiles = flowOf(snapshot)
            override fun snapshot() = snapshot
            override fun matches(snapshot: SessionSnapshot) = snapshot == snapshot()
            override fun invalidate(generation: Long) = Unit
        }
        val factory = ApiFactory(json, PersistentSessionCookieJar(MemoryCookies()))
        val errors = ApiErrorMapper(json)
        val dispatcher = CommandDispatcher(TasksContractTest.MemoryJournal(), OkHttpCommandSender(factory, current), current, json, errors, JournalMapper(json))
        repository = RealWorkshopOrderRepository(WorkshopRemoteDataSource(factory, current, errors), dispatcher, json, errors)
    }
    @After fun after() { server.shutdown() }
    @Test fun absentContractDoesNotCallOrderRoutes() = runBlocking {
        server.enqueue(reply("""{"paths":{}}"""))
        val contracts = repository.contracts() as Outcome.Success
        assertFalse(contracts.value.orders)
        assertFalse(contracts.value.stockSummary)
        assertFalse(contracts.value.sheetRequests)
        assertEquals("/openapi.json", server.takeRequest().path)
        assertNull(server.takeRequest(50, java.util.concurrent.TimeUnit.MILLISECONDS))
    }
    @Test fun manufactureWithoutNormSendsActualSheetAndKnownNormSendsNoMaterials() = runBlocking {
        server.enqueue(reply("""{"paths":{"/api/workshop/requests":{"get":{}},"/api/workshop/stock-summary":{"get":{}},"/api/workshop/sheet-requests":{"get":{}}}}"""))
        assertTrue((repository.contracts() as Outcome.Success).value.orders)
        server.takeRequest()
        server.enqueue(reply("""{"items":[$order],"total":1,"limit":50,"offset":0}"""))
        val listed = (repository.orders(42, OrderView.ACTIVE) as Outcome.Success).value.items.single()
        val query = server.takeRequest().requestUrl!!
        assertEquals(setOf("warehouse_id", "view", "limit", "offset"), query.queryParameterNames)
        assertEquals("active", query.queryParameter("view"))
        assertEquals(false, listed.needsActualSheet.not() && listed.lines.any { it.normKnown == false })
        assertTrue(listed.needsActualSheet)
        server.enqueue(reply(ack("manufactured")))
        val made = WorkshopOrders(repository).manufacture(listed, listOf(SheetUse(900, 2, BigDecimal("1.250")))) as CommandOutcome.Confirmed
        val body = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals(setOf("expected_version", "client_token", "materials"), body.keys)
        assertEquals(3, body["expected_version"]!!.jsonPrimitive.int)
        val material = body["materials"]!!.jsonArray.single().jsonObject
        assertEquals(900, material["product_id"]!!.jsonPrimitive.int)
        assertEquals(2, material["sheets"]!!.jsonPrimitive.int)
        assertEquals("1.250", material["sheet_length_m"]!!.jsonPrimitive.content)
        assertFalse(made.confirmation.terminal)
        val known = listed.copy(lines = listed.lines.map { it.copy(normKnown = true) })
        server.enqueue(reply(ack("manufactured", 5)))
        assertTrue(WorkshopOrders(repository).manufacture(known, emptyList()) is CommandOutcome.Confirmed)
        val bare = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals(setOf("expected_version", "client_token"), bare.keys)
    }
    @Test fun handOverIsTerminalAndReverseNamesTheStep() = runBlocking {
        val handed = WorkshopOrder(7, 5, OrderStatus.HANDED_TO_WAREHOUSE, "handed_to_warehouse", null, "", emptyList(), setOf("reverse_hand_over"))
        server.enqueue(reply("""{"result":{"id":7,"duplicate":false},"request":{"id":7,"version":6,"status":"manufactured"}}"""))
        val reversed = repository.reverse(handed, ReverseTarget.HAND_OVER) as CommandOutcome.Confirmed
        val request = server.takeRequest()
        assertEquals("/api/workshop/requests/7/reverse", request.path)
        val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("hand_over", body["target"]!!.jsonPrimitive.content)
        assertEquals(setOf("expected_version", "client_token", "target"), body.keys)
        assertFalse(reversed.confirmation.terminal)
        server.enqueue(reply("""{"result":{"id":7,"duplicate":false},"request":{"id":7,"version":7,"status":"handed_to_warehouse"}}"""))
        val again = repository.handOver(handed.copy(version = 6, status = OrderStatus.MANUFACTURED, allowedActions = setOf("hand_over"))) as CommandOutcome.Confirmed
        assertTrue(again.confirmation.terminal)
        assertEquals("/api/workshop/requests/7/hand-over", server.takeRequest().path)
    }
    @Test fun summaryUsesServerDayAndDoesNotInventSections() = runBlocking {
        server.enqueue(reply("""{"flat_sheet":[{"product_id":900,"product_name":"Лист","size":"1.25","quantity":"3","unit":"м²","sheets":4}],"awaiting_transfer":[],"transferred_on":[{"product_id":501,"product_name":"Конёк простой 2000","size":"2000","quantity":"2","unit":"шт"}]}"""))
        val summary = repository.stockSummary(42, LocalDate.parse("2026-11-06")) as Outcome.Success
        val query = server.takeRequest().requestUrl!!
        assertEquals("/api/workshop/stock-summary", query.encodedPath)
        assertEquals("2026-11-06", query.queryParameter("on"))
        assertEquals(1, summary.value.flatSheet.size)
        assertTrue(summary.value.awaitingTransfer.isEmpty())
        assertEquals("Конёк простой 2000", summary.value.transferredOn.single().productName)
    }
    private val profile = """{"request_id":7,"line_id":1,"version":2,"schema_version":1,"blank_width_mm":250,"drawing":{"schema_version":1,"skewed":true,"length_mm":2000,"segments":[{"start_mm":150,"end_mm":130},{"start_mm":80,"end_mm":100}],"angles_deg":["90.125"],"turns":["left"],"edge1":{"type":"hem","direction":"in"},"edge2":{"type":"seam","direction":"out"}}}"""
    @Test fun drawingPreservesExactDimensionsAndAngles() = runBlocking {
        server.enqueue(reply(profile))
        val drawing=(repository.drawing(7,1) as Outcome.Success).value
        assertEquals("/api/workshop/v2/requests/7/lines/1/drawing",server.takeRequest().path)
        assertEquals(250L,drawing.blankWidthMm)
        assertEquals(150L,drawing.segments[0].startMm)
        assertEquals(130L,drawing.segments[0].endMm)
        assertEquals(BigDecimal("90.125"),drawing.angles.single())
        assertTrue(drawing.skewed)
        assertEquals("out",drawing.edge2.direction)
    }
    @Test fun invalidOrUnknownDrawingCannotBeDisplayedAsValid() = runBlocking {
        for(body in listOf(profile.replace("\"schema_version\":1","\"schema_version\":2"),
            profile.replace("\"request_id\":7","\"request_id\":8"),
            profile.replace("\"start_mm\":150","\"start_mm\":0"),
            profile.replace("\"hem\"","\"unknown\""),profile.replace("90.125","180"))) {
            server.enqueue(reply(body))
            assertTrue(repository.drawing(7,1) is Outcome.Failure)
            server.takeRequest()
        }
    }
    @Test fun areaManufactureSendsOnlyVersionAndDurableToken() = runBlocking {
        val request=WorkshopOrder(7,3,OrderStatus.IN_PROGRESS,"in_progress",null,"",emptyList(),setOf("manufacture_area"))
        server.enqueue(reply(ack("manufactured")))
        assertTrue(repository.manufactureArea(request) is CommandOutcome.Confirmed)
        val sent=server.takeRequest()
        assertEquals("/api/workshop/v2/requests/7/manufacture-area",sent.path)
        val body=json.parseToJsonElement(sent.body.readUtf8()).jsonObject
        assertEquals(setOf("expected_version","client_token"),body.keys)
        assertEquals(3,body["expected_version"]!!.jsonPrimitive.int)
        assertTrue(body["client_token"]!!.jsonPrimitive.content.isNotBlank())
    }
    private val area = """{"request_id":7,"request_version":3,"preview_hash":"${"a".repeat(64)}","formula_version":"profile-area-v1","total_area_m2":"0.2200","lines":[{"line_id":1,"drawing_version":2,"product_name":"Уголок","blank_width_mm":110,"length_mm":2000,"piece_quantity":"1","area_m2":"0.2200"}],"materials":[{"product_id":900,"product_name":"Лист","area_m2":"0.2200"}]}"""
    @Test fun previewIsExactAndManufactureBindsTheShownCalculation()=runBlocking {
        server.enqueue(reply(area))
        val preview=(repository.areaPreview(7) as Outcome.Success).value
        assertEquals("/api/workshop/v2/requests/7/area-preview",server.takeRequest().path)
        assertEquals(BigDecimal("0.2200"),preview.totalAreaM2)
        val request=WorkshopOrder(7,3,OrderStatus.IN_PROGRESS,"in_progress",null,"",listOf(OrderLine(1,501,"Уголок","2000",BigDecimal.ONE,"шт",false,2)),setOf("manufacture_area"))
        server.enqueue(reply(ack("manufactured")))
        assertTrue(WorkshopOrders(repository).manufactureArea(request,preview) is CommandOutcome.Confirmed)
        val body=json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals(setOf("expected_version","client_token","preview_hash"),body.keys)
        assertEquals(preview.hash,body["preview_hash"]!!.jsonPrimitive.content)
    }
    @Test fun unknownOrInconsistentPreviewIsRejectedWithoutSendingManufacture()=runBlocking {
        for(body in listOf(area.replace("profile-area-v1","unknown-formula"),area.replace("\"request_id\":7","\"request_id\":8"),area.replace("\"total_area_m2\":\"0.2200\"","\"total_area_m2\":\"9.0000\""))) {
            server.enqueue(reply(body));assertTrue(repository.areaPreview(7) is Outcome.Failure);server.takeRequest()
        }
        assertNull(server.takeRequest(50,java.util.concurrent.TimeUnit.MILLISECONDS))
    }
}
