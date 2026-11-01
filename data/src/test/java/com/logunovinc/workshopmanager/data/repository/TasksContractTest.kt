package com.logunovinc.workshopmanager.data.repository

import com.logunovinc.workshopmanager.data.local.pending.*
import com.logunovinc.workshopmanager.data.remote.*
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.task.*
import com.logunovinc.workshopmanager.domain.pending.*
import com.logunovinc.workshopmanager.domain.warehouse.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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
    private fun task(id: Long = 101, type: String = "RECEIVE_GOODS", status: String = "new", version: Long = 3): String = """{"id":$id,"type":"$type","status":"$status","version":$version,"warehouse_id":42,"assignee_user_id":null,"created_by":1,"created_at":"2026-11-01T15:00:00+03:00","due_at":null,"comment":"test","allowed_actions":["start","execute"],"lines":[{"id":1001,"product_id":301,"product_name":"Материал","role":"receive","base_unit":"шт","planned_qty":"10.0000","actual_qty":"0","remaining_qty":"10.0000","planned_sheets":9,"actual_sheets":0,"remaining_sheets":9,"sheet_length_m":"1.234"}]}"""
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
    @Test fun scopeFiltersAndPaginationAreServerQueries() = runBlocking {
        server.enqueue(reply("""{"items":[${task()}],"total":61,"limit":50,"offset":0}"""))
        val page = repository.tasks(42, TaskFilter(), 50, 0) as Outcome.Success
        assertTrue(page.value.hasMore)
        val default = server.takeRequest().requestUrl!!
        assertEquals("42", default.queryParameter("warehouse_id"))
        assertNull(default.queryParameter("assignee_user_id"))
        assertEquals("true", default.queryParameter("active_only"))
        server.enqueue(reply("""{"items":[],"total":61,"limit":50,"offset":50}"""))
        repository.tasks(42, TaskFilter(TaskType.DISPATCH_GOODS, assignedToMe = true, completed = true), 50, 50)
        val filtered = server.takeRequest().requestUrl!!
        assertEquals("42", filtered.queryParameter("assignee_user_id"))
        assertEquals("DISPATCH_GOODS", filtered.queryParameter("type"))
        assertEquals("completed", filtered.queryParameter("status"))
        assertEquals("false", filtered.queryParameter("active_only"))
        assertEquals("50", filtered.queryParameter("offset"))
    }
    @Test fun unsupportedEnumsAndNullDatesDoNotGuessMutations() = runBlocking {
        server.enqueue(reply(task(type = "FUTURE", status = "future_status")))
        val value = (repository.task(101) as Outcome.Success).value
        assertEquals(TaskType.UNKNOWN, value.type)
        assertEquals(TaskStatus.UNKNOWN, value.status)
        assertFalse(value.canStart)
        assertFalse(value.canExecute)
        assertNull(value.dueAt)
        assertEquals(Instant.parse("2026-11-01T12:00:00Z"), value.createdAt)
        assertEquals("1.234", value.lines.single().sheetLength?.toPlainString())
        assertEquals(1001L, value.lines.single().id)
        assertEquals(301L, value.lines.single().productId)
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
    @Test fun startUsesOnlyVersionAndTokenAndReadsTaskVersionFromAck() = runBlocking {
        server.enqueue(reply(task()))
        val initial = (repository.task(101) as Outcome.Success).value
        server.takeRequest()
        server.enqueue(reply("""{"result":{"id":101,"version":1,"duplicate":true},"task":${task(status = "in_progress", version = 9)}}"""))
        val result = repository.start(initial) as CommandOutcome.Confirmed
        val request = server.takeRequest()
        assertEquals("/api/workshop/tasks/101/start", request.path)
        val body = json.parseToJsonElement(request.body.readUtf8()).toString()
        assertTrue(body.contains("expected_version"))
        assertFalse(body.contains("expected_transfer_version"))
        assertFalse(body.contains("lines"))
        assertEquals(9L, result.confirmation.taskVersion)
        assertEquals(1, journal.commands.size)
        assertTrue(journal.commands.values.single().ackBody.orEmpty().contains("in_progress"))
    }
    @Test fun staleVersionAndRightsConflictAreTypedWithoutFallbackEndpoint() = runBlocking {
        server.enqueue(reply(task()))
        val initial = (repository.task(101) as Outcome.Success).value
        server.takeRequest()
        server.enqueue(reply("""{"code":"VERSION_CONFLICT","message":"Изменено другим работником"}""", 409))
        val result = repository.start(initial) as CommandOutcome.Rejected
        assertEquals(ErrorKind.CONFLICT, result.error.kind)
        assertEquals("VERSION_CONFLICT", result.error.code)
        assertEquals("/api/workshop/tasks/101/start", server.takeRequest().path)
        server.enqueue(reply("""{"code":"FORBIDDEN","message":"Задание назначено другому"}""", 403))
        assertEquals(ErrorKind.FORBIDDEN, (repository.start(initial) as CommandOutcome.Rejected).error.kind)
        assertEquals("/api/workshop/tasks/101/start", server.takeRequest().path)
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
