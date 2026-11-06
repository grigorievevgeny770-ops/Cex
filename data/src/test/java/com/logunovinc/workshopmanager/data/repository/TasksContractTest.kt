package com.logunovinc.workshopmanager.data.repository

import com.logunovinc.workshopmanager.data.local.pending.*
import com.logunovinc.workshopmanager.data.remote.*
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.task.*
import com.logunovinc.workshopmanager.domain.pending.*
import com.logunovinc.workshopmanager.domain.warehouse.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.time.Instant

class TasksContractTest {
    private lateinit var server: MockWebServer
    private lateinit var session: CurrentSession
    private lateinit var repository: RealTaskRepository
    private lateinit var warehouses: RealWarehouseRepository
    private lateinit var journal: MemoryJournal
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mapper = TaskMapper()
    private fun reply(body: String, status: Int = 200) = MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)
    private fun task(id: Long = 101, type: String = "RECEIVE_GOODS", status: String? = null, version: Long = 3,
        flags: Boolean = true, completed: Boolean = false, cancelled: Boolean = false): String = """{"id":$id,"type":"$type","version":$version${if (flags) ",\"is_completed\":$completed,\"is_cancelled\":$cancelled" else ""}${status?.let { ",\"status\":\"$it\"" }.orEmpty()},"warehouse_id":42,"created_by":1,"created_at":"2026-11-01T15:00:00+03:00","due_at":null,"comment":"test","allowed_actions":["execute"],"lines":[{"id":1001,"product_id":301,"product_name":"Материал","role":"receive","base_unit":"шт","planned_qty":"10.0000","actual_qty":"0","remaining_qty":"10.0000","planned_sheets":9,"actual_sheets":0,"remaining_sheets":9,"sheet_length_m":"1.234"}]}"""
    class MemoryJournal : CommandJournal {
        val commands = mutableMapOf<String, PendingCommandEntity>()
        override suspend fun prepare(command: PendingCommandEntity): PrepareResult { commands[command.id] = command; return PrepareResult.Created(command) }
        override suspend fun get(id: String) = commands[id]
        override fun observe(baseUrl: String, actorId: Long) = flowOf(commands.values.filter { it.baseUrl == baseUrl && it.actorId == actorId })
        override suspend fun update(command: PendingCommandEntity, releaseSlot: Boolean) { commands[command.id] = command }
    }
    @Before fun before() {
        server = MockWebServer().apply { start() }
        val snapshot = SessionSnapshot(server.url("/").toString(), 42, 0)
        session = object : CurrentSession {
            override val profiles = flowOf(snapshot)
            override fun snapshot() = snapshot
            override fun matches(snapshot: SessionSnapshot) = snapshot == snapshot()
            override fun invalidate(generation: Long) = Unit
        }
        val factory = ApiFactory(json, PersistentSessionCookieJar(MemoryCookies()))
        val errors = ApiErrorMapper(json)
        val remote = WorkshopRemoteDataSource(factory, session, errors)
        journal = MemoryJournal()
        val dispatcher = CommandDispatcher(journal, OkHttpCommandSender(factory, session), session, json, errors, JournalMapper(json))
        repository = RealTaskRepository(remote, mapper, dispatcher, json, errors)
        warehouses = RealWarehouseRepository(remote)
    }
    @After fun after() { server.shutdown() }
    @Test fun warehouseResolverReadsArrayAndFinds42AfterMain7() = runBlocking {
        server.enqueue(reply("""[{"id":7,"name":"Главный","code":"main","is_active":true},{"id":42,"name":"Цех","code":"workshop","is_active":true}]"""))
        assertEquals(42L, (ResolveWorkshopUseCase(warehouses)() as Outcome.Success).value.id)
        assertEquals("true", server.takeRequest().requestUrl?.queryParameter("include_inactive"))
    }
    @Test fun strictTaskQueryRejectsLegacyActiveOnlyButAcceptsCurrentTypeAndPages() = runBlocking {
        val allowedKeys = setOf("view", "warehouse_id", "type", "limit", "offset")
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val query = request.requestUrl!!
                if (query.encodedPath != "/api/workshop/tasks") return reply("{}", 404)
                if ((query.queryParameterNames - allowedKeys).isNotEmpty()) return reply(
                    """{"code":"VALIDATION_ERROR","message":"Request validation failed","details":{"errors":[{"loc":["query","active_only"],"message":"Extra inputs are not permitted","type":"extra_forbidden"}]}}""", 422)
                val offset = query.queryParameter("offset")!!.toLong()
                val type = query.queryParameter("type") ?: "RECEIVE_GOODS"
                val items = if (offset == 0L) "${task(type = type)},${task(id = 102, type = type)}" else task(id = 103, type = type)
                return reply("""{"items":[$items],"total":61,"limit":50,"offset":$offset}""")
            }
        }
        val legacy = okhttp3.Request.Builder().url(server.url("/api/workshop/tasks?warehouse_id=42&view=active&active_only=true&limit=50&offset=0")).build()
        okhttp3.OkHttpClient().newCall(legacy).execute().use { response ->
            assertEquals(422, response.code)
            assertTrue(response.body!!.string().contains("Request validation failed"))
        }
        server.takeRequest()
        for (type in listOf(null, TaskType.RECEIVE_GOODS, TaskType.DISPATCH_GOODS, TaskType.MANUFACTURE_PRODUCT)) {
            for (offset in listOf(0L, 50L)) {
                val page = repository.tasks(42, TaskFilter(type = type), 50, offset) as Outcome.Success
                if (offset == 0L) assertEquals(listOf(101L, 102L), page.value.items.map(Task::id))
                val query = server.takeRequest().requestUrl!!
                assertEquals(if (type == null) allowedKeys - "type" else allowedKeys, query.queryParameterNames)
                assertEquals("active", query.queryParameter("view"))
                assertEquals("42", query.queryParameter("warehouse_id"))
                assertEquals(type?.name, query.queryParameter("type"))
                assertEquals("50", query.queryParameter("limit"))
                assertEquals(offset.toString(), query.queryParameter("offset"))
            }
        }
    }
    @Test fun unsupportedEnumsAndNullDatesDoNotGuessMutations() = runBlocking {
        server.enqueue(reply(task(type = "FUTURE", status = "future_status", flags = false)))
        val value = (repository.task(101) as Outcome.Success).value
        assertEquals(TaskType.UNKNOWN, value.type)
        assertNull(value.isCompleted)
        assertNull(value.isCancelled)
        assertFalse(value.canExecute)
        assertNull(value.dueAt)
        assertEquals(Instant.parse("2026-11-01T12:00:00Z"), value.createdAt)
        assertEquals("1.234", value.lines.single().sheetLength?.toPlainString())
        assertEquals(1001L, value.lines.single().id)
        assertEquals(301L, value.lines.single().productId)
        assertEquals(WorkshopRequestStage.UNSUPPORTED, value.workshopStage())
    }
    @Test fun legacyProgressIsReadableAndIsNotSentBackAsAStatusChange() = runBlocking {
        server.enqueue(reply(task(type = "MANUFACTURE_PRODUCT", status = "in_progress").replace("\"created_at\":\"2026-11-01T15:00:00+03:00\"", "\"created_at\":\"2026-11-01T15:00:00+03:00\",\"started_at\":\"2026-11-02T08:00:00Z\"")))
        val value = (repository.task(101) as Outcome.Success).value
        val request = server.takeRequest()
        assertEquals("in_progress", value.legacyStatus)
        assertEquals(Instant.parse("2026-11-02T08:00:00Z"), value.startedAt)
        assertEquals(WorkshopRequestStage.IN_PROGRESS, value.workshopStage())
        assertFalse(request.path!!.contains("status="))
        assertEquals("GET", request.method)
    }
    @Test fun malformedDateMapsCompatibilityNotLocalStorage() = runBlocking {
        server.enqueue(reply(task().replace("2026-11-01T15:00:00+03:00", "not-a-date")))
        assertEquals(ErrorKind.INCOMPATIBLE_SERVER, (repository.task(101) as Outcome.Failure).error.kind)
    }
    @Test fun missingTasksRouteIsCompatibilityButMissingTaskIsNotFound() = runBlocking {
        server.enqueue(reply("""{"code":"NOT_FOUND","message":"Not found"}""", 404))
        val unsupported = (repository.tasks(42, TaskFilter(), 50, 0) as Outcome.Failure).error
        assertEquals(ErrorKind.INCOMPATIBLE_SERVER, unsupported.kind)
        assertTrue(unsupported.message.contains("Сервер ещё не поддерживает"))
        server.enqueue(reply("""{"code":"NOT_FOUND","message":"Not found"}""", 404))
        assertEquals(ErrorKind.NOT_FOUND, (repository.task(101) as Outcome.Failure).error.kind)
        server.enqueue(reply("""{"code":"FORBIDDEN","message":"No warehouse permission"}""", 403))
        assertEquals(ErrorKind.FORBIDDEN, (warehouses.warehouses() as Outcome.Failure).error.kind)
    }
    @Test fun directExecutionUsesCurrentVersionAndAcknowledgedTaskVersionWithoutStart() = runBlocking {
        server.enqueue(reply(task(type = "DISPATCH_GOODS")))
        val initial = (repository.task(101) as Outcome.Success).value
        server.takeRequest()
        server.enqueue(reply("""{"result":{"id":101,"version":1,"duplicate":true},"task":${task(type = "DISPATCH_GOODS", version = 9)}}"""))
        val result = ExecuteDispatchUseCase(repository)(initial, DispatchDraft(listOf(ActualLineInput(1001, sheets = "3")))) as CommandOutcome.Confirmed
        val request = server.takeRequest()
        assertEquals("/api/workshop/tasks/101/executions", request.path)
        val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("3", body["expected_version"]!!.jsonPrimitive.content)
        assertFalse(body.containsKey("expected_transfer_version"))
        assertEquals(9L, result.confirmation.taskVersion)
        assertEquals(1, journal.commands.size)
    }
    @Test fun executionConflictAndRightsRevocationDoNotCallAnotherEndpoint() = runBlocking {
        server.enqueue(reply(task(type = "DISPATCH_GOODS")))
        val initial = (repository.task(101) as Outcome.Success).value
        server.takeRequest()
        server.enqueue(reply("""{"code":"VERSION_CONFLICT","message":"Изменено другим сотрудником"}""", 409))
        val result = ExecuteDispatchUseCase(repository)(initial, DispatchDraft(listOf(ActualLineInput(1001, sheets = "3")))) as CommandOutcome.Rejected
        assertEquals(ErrorKind.CONFLICT, result.error.kind)
        assertEquals("VERSION_CONFLICT", result.error.code)
        assertEquals("/api/workshop/tasks/101/executions", server.takeRequest().path)
        server.enqueue(reply("""{"code":"ACCESS_DENIED","message":"Нет разрешения на выполнение"}""", 403))
        val denied = ExecuteDispatchUseCase(repository)(initial, DispatchDraft(listOf(ActualLineInput(1001, sheets = "3")))) as CommandOutcome.Rejected
        assertEquals(ErrorKind.FORBIDDEN, denied.error.kind)
        assertEquals("/api/workshop/tasks/101/executions", server.takeRequest().path)
    }
    @Test fun completionFlagsPreferNewContractAndLegacyFallbackFailsClosedWhenUnknown() = runBlocking {
        for ((legacy, expected) in listOf("new" to (false to false), "in_progress" to (false to false), "partially_completed" to (false to false), "waiting_resolution" to (false to false), "completed" to (true to false), "cancelled" to (false to true), "future" to (null to null))) {
            server.enqueue(reply(task(status = legacy, flags = false)))
            val value = (repository.task(101) as Outcome.Success).value
            server.takeRequest()
            assertEquals(expected, value.isCompleted to value.isCancelled)
            assertEquals(expected == (false to false), value.canExecute)
        }
        server.enqueue(reply(task(status = "future")))
        val preferred = (repository.task(101) as Outcome.Success).value
        server.takeRequest()
        assertTrue(preferred.canExecute)
        server.enqueue(reply(task().replace(",\"is_cancelled\":false", "")))
        val incomplete = (repository.task(101) as Outcome.Success).value
        server.takeRequest()
        assertFalse(incomplete.canExecute)
        server.enqueue(reply(task(flags = false)))
        assertFalse((repository.task(101) as Outcome.Success).value.canExecute)
        server.takeRequest()
        Unit
    }
    @Test fun historyAndExecutionsReadBeyondEmbeddedFiftySeparately() = runBlocking {
        val execution = """{"id":5001,"task_id":101,"executed_by":42,"executed_at":"2026-11-01T12:00:00Z","final":false,"lines":[{"task_line_id":1001,"product_id":301,"role":"receive","actual_qty":"4"}]}"""
        server.enqueue(reply("""{"items":[$execution],"total":51,"limit":50,"offset":50}"""))
        val facts = repository.executions(101, 50, 50) as Outcome.Success
        assertEquals(5001L, facts.value.items.single().id)
        assertEquals("/api/workshop/tasks/101/executions?limit=50&offset=50", server.takeRequest().path)
        server.enqueue(reply("""{"items":[{"id":6001,"event_type":"executed","created_at":"2026-11-01T12:00:00Z","details":{"execution_id":5001,"comment":"Факт","unexpected":{"raw":"hidden"}}}],"total":51,"limit":50,"offset":50}"""))
        val history = repository.history(101, 50, 50) as Outcome.Success
        assertEquals(6001L, history.value.items.single().id)
        assertFalse(history.value.items.single().details.toString().contains("hidden"))
        assertEquals("/api/workshop/tasks/101/history?limit=50&offset=50", server.takeRequest().path)
    }
}
