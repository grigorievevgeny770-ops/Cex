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

class ReceiveContractTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: RealTaskRepository
    private lateinit var dispatcher: CommandDispatcher
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private fun reply(body: String, status: Int = 200) = MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)
    private fun task(version: Long = 2, transfer: Long = 2, actual: String = "0", remaining: String = "10", status: String = "in_progress", outcome: String = "null", sheets: Boolean = false): String = """{"id":101,"type":"RECEIVE_GOODS","status":"$status","version":$version,"warehouse_id":42,"created_by":1,"created_at":"2026-11-01T12:00:00Z","transfer_version":$transfer,"outcome":$outcome,"allowed_actions":["execute"],"lines":[{"id":1001,"product_id":301,"product_name":"Материал","role":"receive","base_unit":"шт","planned_qty":"10","actual_qty":"$actual","remaining_qty":"$remaining"${if (sheets) ",\"planned_sheets\":9,\"actual_sheets\":0,\"remaining_sheets\":9,\"sheet_length_m\":\"1.234\"" else ""}}]}"""
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
        val journal = TasksContractTest.MemoryJournal()
        dispatcher = CommandDispatcher(journal, OkHttpCommandSender(factory, current), current, json, errors, JournalMapper(json))
        repository = RealTaskRepository(WorkshopRemoteDataSource(factory, current, errors), TaskMapper(), dispatcher, json, errors)
    }
    @After fun after() { server.shutdown() }
    private suspend fun read(task: String): Task { server.enqueue(reply(task)); val value = (repository.task(101) as Outcome.Success).value; server.takeRequest(); return value }
    @Test fun tenToFourToFiveFinalSendsDeltaAndFreshVersionsWithoutLossMutation() = runBlocking {
        val firstTask = read(task())
        server.enqueue(reply(ack(task(3, 3, "4", "6"))))
        assertTrue(ExecuteReceiveUseCase(repository)(firstTask, ReceiveDraft(listOf(ActualLineInput(1001, "4")))) is CommandOutcome.Confirmed)
        val first = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("4", first["lines"]!!.jsonArray.single().jsonObject["qty"]!!.jsonPrimitive.content)
        assertFalse(first["final"]!!.jsonPrimitive.boolean)
        val nextTask = read(task(3, 3, "4", "6"))
        server.enqueue(reply(ack(task(4, 4, "9", "1", "completed", "\"shortage\""))))
        val result = ExecuteReceiveUseCase(repository)(nextTask, ReceiveDraft(listOf(ActualLineInput(1001, "5")), true, "Одна единица не прибыла")) as CommandOutcome.Confirmed
        val secondRequest = server.takeRequest()
        assertEquals("/api/workshop/tasks/101/executions", secondRequest.path)
        val second = json.parseToJsonElement(secondRequest.body.readUtf8()).jsonObject
        assertEquals("5", second["lines"]!!.jsonArray.single().jsonObject["qty"]!!.jsonPrimitive.content)
        assertEquals(3L, second["expected_version"]!!.jsonPrimitive.long)
        assertEquals(3L, second["expected_transfer_version"]!!.jsonPrimitive.long)
        assertTrue(second["final"]!!.jsonPrimitive.boolean)
        assertEquals(setOf("type", "expected_version", "expected_transfer_version", "client_token", "comment", "lines", "final", "reason"), second.keys)
        assertEquals(4L, result.confirmation.taskVersion)
        assertTrue(dispatcher.acknowledgedBody(result.confirmation.commandId)!!.contains("shortage"))
    }
    @Test fun zeroFinalSendsEmptyLinesAndSheetFactSendsOnlyFrozenSheets() = runBlocking {
        val initial = read(task())
        server.enqueue(reply(ack(task(3, 3, "0", "10", "completed", "\"shortage\""))))
        ExecuteReceiveUseCase(repository)(initial, ReceiveDraft(emptyList(), true, "Товар не прибыл"))
        val zero = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertTrue(zero["lines"]!!.jsonArray.isEmpty())
        val sheetTask = read(task(version = 5, transfer = 5, remaining = "0", sheets = true))
        server.enqueue(reply(ack(task(6, 6, "0", "0", "completed", sheets = true))))
        assertTrue(ExecuteReceiveUseCase(repository)(sheetTask, ReceiveDraft(listOf(ActualLineInput(1001, sheets = "9")), true)) is CommandOutcome.Confirmed)
        val sheet = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["lines"]!!.jsonArray.single().jsonObject
        assertEquals(setOf("task_line_id", "sheets"), sheet.keys)
        assertEquals(9, sheet["sheets"]!!.jsonPrimitive.int)
    }
    @Test fun unknownReceiveRetryKeepsOriginalExactPayloadScaleTokenAndVersions() = runBlocking {
        val initial = read(task())
        server.enqueue(reply("""{"message":"uncertain"}""", 500))
        val unknown = ExecuteReceiveUseCase(repository)(initial, ReceiveDraft(listOf(ActualLineInput(1001, "4,0000")))) as CommandOutcome.Unresolved
        val first = server.takeRequest().body.readUtf8()
        assertTrue(first.contains("4.0000"))
        server.enqueue(reply(ack(task(3, 3, "4", "6"))))
        assertTrue(dispatcher.retry(unknown.command.id) is CommandOutcome.Confirmed)
        assertEquals(first, server.takeRequest().body.readUtf8())
    }
}
