package com.logunovinc.workshopmanager.data.repository

import com.logunovinc.workshopmanager.data.local.pending.*
import com.logunovinc.workshopmanager.data.remote.*
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.task.*
import com.logunovinc.workshopmanager.domain.pending.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*

class DispatchContractTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: RealTaskRepository
    private lateinit var dispatcher: CommandDispatcher
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private fun reply(body: String, status: Int = 200) = MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)
    private fun task(version: Long = 2, actual: String = "0", remaining: String = "10", status: String = "in_progress", sheets: Int? = null): String = """{"id":101,"type":"DISPATCH_GOODS","status":"$status","version":$version,"warehouse_id":42,"created_by":1,"created_at":"2026-11-01T12:00:00Z","transfer_version":99,"allowed_actions":["execute"],"lines":[{"id":2001,"product_id":301,"product_name":"Изделие","role":"dispatch","base_unit":"шт","planned_qty":"10","actual_qty":"$actual","remaining_qty":"$remaining"${sheets?.let { ",\"planned_sheets\":9,\"actual_sheets\":0,\"remaining_sheets\":$it,\"sheet_length_m\":\"1.234\"" }.orEmpty()}}]}"""
    private fun ack(task: String) = """{"result":{"id":101,"execution_id":5001,"duplicate":false},"task":$task}"""
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
        dispatcher = CommandDispatcher(TasksContractTest.MemoryJournal(), OkHttpCommandSender(factory, current), current, json, errors, JournalMapper(json))
        repository = RealTaskRepository(WorkshopRemoteDataSource(factory, current, errors), TaskMapper(), dispatcher, json, errors)
    }
    @After fun after() { server.shutdown() }
    private suspend fun read(body: String): Task { server.enqueue(reply(body)); val task = (repository.task(101) as Outcome.Success).value; server.takeRequest(); return task }
    @Test fun partialThenRemainingPayloadContainsNoReceiveFields() = runBlocking {
        val initial = read(task())
        server.enqueue(reply(ack(task(3, "3", "7"))))
        ExecuteDispatchUseCase(repository)(initial, DispatchDraft(listOf(ActualLineInput(2001, "3")), "Первая часть"))
        val first = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals(setOf("type", "expected_version", "client_token", "comment", "lines"), first.keys)
        assertEquals("DISPATCH_GOODS", first["type"]!!.jsonPrimitive.content)
        assertEquals("3", first["lines"]!!.jsonArray.single().jsonObject["qty"]!!.jsonPrimitive.content)
        val next = read(task(3, "3", "7"))
        server.enqueue(reply(ack(task(4, "10", "0", "completed"))))
        val confirmed = ExecuteDispatchUseCase(repository)(next, DispatchDraft(listOf(ActualLineInput(2001, "7")))) as CommandOutcome.Confirmed
        val request = server.takeRequest()
        assertEquals("/api/workshop/tasks/101/executions", request.path)
        val second = json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals(3L, second["expected_version"]!!.jsonPrimitive.long)
        assertEquals("7", second["lines"]!!.jsonArray.single().jsonObject["qty"]!!.jsonPrimitive.content)
        assertFalse(second.containsKey("expected_transfer_version"))
        assertFalse(second.containsKey("final"))
        assertFalse(second.containsKey("reason"))
        assertEquals(4L, confirmed.confirmation.taskVersion)
    }
    @Test fun sheetsOnlyAndReservedOrInsufficientErrorsStayNative() = runBlocking {
        val initial = read(task(remaining = "0", sheets = 9))
        server.enqueue(reply(ack(task(3, remaining = "0", sheets = 6))))
        ExecuteDispatchUseCase(repository)(initial, DispatchDraft(listOf(ActualLineInput(2001, sheets = "3"))))
        val sheets = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["lines"]!!.jsonArray.single().jsonObject
        assertEquals(setOf("task_line_id", "sheets"), sheets.keys)
        val next = read(task(4))
        for ((status, code) in listOf(409 to "STOCK_RESERVED", 422 to "INSUFFICIENT_STOCK")) {
            server.enqueue(reply("""{"code":"$code","message":"Недостаточно свободного остатка"}""", status))
            val result = ExecuteDispatchUseCase(repository)(next, DispatchDraft(listOf(ActualLineInput(2001, "3")))) as CommandOutcome.Rejected
            assertEquals(code, result.error.code)
            assertEquals(status, result.error.httpStatus)
            assertEquals("Недостаточно свободного остатка", result.error.message)
            assertEquals("/api/workshop/tasks/101/executions", server.takeRequest().path)
        }
    }
    @Test fun unknownRetryRetainsExactScaleVersionsAndToken() = runBlocking {
        val initial = read(task())
        server.enqueue(reply("""{"message":"uncertain"}""", 500))
        val unknown = ExecuteDispatchUseCase(repository)(initial, DispatchDraft(listOf(ActualLineInput(2001, "3,0000")))) as CommandOutcome.Unresolved
        val first = server.takeRequest().body.readUtf8()
        assertTrue(first.contains("3.0000"))
        assertTrue(unknown.command.summary.lines.contains("В пути, получатель ещё не принял"))
        server.enqueue(reply(ack(task(3, "3", "7"))))
        assertTrue(dispatcher.retry(unknown.command.id) is CommandOutcome.Confirmed)
        assertEquals(first, server.takeRequest().body.readUtf8())
    }
}
