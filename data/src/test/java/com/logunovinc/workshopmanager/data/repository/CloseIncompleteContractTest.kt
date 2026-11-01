package com.logunovinc.workshopmanager.data.repository

import com.logunovinc.workshopmanager.data.local.pending.*
import com.logunovinc.workshopmanager.data.remote.*
import com.logunovinc.workshopmanager.data.remote.dto.*
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.task.*
import com.logunovinc.workshopmanager.domain.pending.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.time.Instant
import java.util.concurrent.TimeUnit

class CloseIncompleteContractTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: RealTaskRepository
    private lateinit var dispatcher: CommandDispatcher
    private lateinit var journal: TasksContractTest.MemoryJournal
    private val json = Json { ignoreUnknownKeys=true;encodeDefaults=true;explicitNulls=false }
    private val capability = """{"paths":{"/api/workshop/tasks/{task_id}/close-incomplete":{"post":{}}}}"""
    private fun reply(body: String,status: Int=200) = MockResponse().setResponseCode(status).setHeader("Content-Type","application/json").setBody(body)
    private fun task(closed: Boolean=false,version: Int=3,type: String="DISPATCH_GOODS",reason: String="Материал закончился") = """{"id":101,"type":"$type","is_completed":false,"is_cancelled":false,"version":$version,"warehouse_id":42,"created_by":1,"created_at":"2026-11-01T12:00:00Z","is_closed_incomplete":$closed,"closed_incomplete_reason":"$reason","closed_incomplete_by":42,"closed_incomplete_at":"2026-11-02T15:30:00+03:00","allowed_actions":["execute","close_incomplete"],"lines":[{"id":1001,"product_id":301,"product_name":"Материал","role":"input","base_unit":"шт","planned_qty":"20","actual_qty":"10","remaining_qty":"10"}]}"""
    private fun domain() = TaskMapper().task(json.decodeFromString<TaskDto>(task()))
    @Before fun before() {
        server=MockWebServer().apply { start() }
        val snapshot=SessionSnapshot(server.url("/").toString(),42,0)
        val current=object:CurrentSession { override val profiles=flowOf(snapshot); override fun snapshot()=snapshot; override fun matches(snapshot: SessionSnapshot)=snapshot==snapshot(); override fun invalidate(generation: Long)=Unit }
        val factory=ApiFactory(json,PersistentSessionCookieJar(MemoryCookies()).also { jar -> jar.saveFromResponse(server.url("/"),listOf(okhttp3.Cookie.Builder().name("session").value("worker-fixture-cookie").hostOnlyDomain(server.hostName).path("/").build())) });val errors=ApiErrorMapper(json)
        journal=TasksContractTest.MemoryJournal()
        dispatcher=CommandDispatcher(journal,OkHttpCommandSender(factory,current),current,json,errors,JournalMapper(json))
        repository=RealTaskRepository(WorkshopRemoteDataSource(factory,current,errors),TaskMapper(),dispatcher,json,errors)
    }
    @After fun after() { server.shutdown() }
    @Test fun strictThreeFieldsTrimReasonAndUseCurrentTaskRatherThanOldDuplicateResult() = runBlocking {
        server.enqueue(reply(capability));server.enqueue(reply("""{"result":{"id":101,"version":2,"duplicate":true},"task":${task(true,5)}}"""))
        val result=CloseIncompleteTaskUseCase(repository)(domain(),"  Материал закончился  ") as CommandOutcome.Confirmed
        val schema=server.takeRequest();assertEquals("/openapi.json",schema.path);assertNull(schema.getHeader("Cookie"))
        val request=server.takeRequest();assertEquals("/api/workshop/tasks/101/close-incomplete",request.path);assertEquals("session=worker-fixture-cookie",request.getHeader("Cookie"))
        val payload=json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals(setOf("expected_version","client_token","reason"),payload.keys)
        assertEquals(3,payload["expected_version"]!!.jsonPrimitive.int);assertEquals("Материал закончился",payload["reason"]!!.jsonPrimitive.content)
        assertEquals(5L,result.confirmation.taskVersion)
        val acknowledged=TaskMapper().task(json.decodeFromString<TaskDto>(json.parseToJsonElement(dispatcher.acknowledgedBody(result.confirmation.commandId)!!).jsonObject["task"].toString()))
        assertTrue(acknowledged.isClosedIncomplete);assertFalse(acknowledged.canExecute);assertFalse(acknowledged.canCloseIncomplete)
        assertEquals("10",acknowledged.lines.single().remaining.toPlainString());assertEquals(Instant.parse("2026-11-02T12:30:00Z"),acknowledged.closedIncompleteAt)
    }
    @Test fun missingCapabilityIsRecheckedOnNextAttemptAndCannotPostOrFallback() = runBlocking {
        repeat(2) { server.enqueue(reply("""{"paths":{}}""")) }
        repeat(2) { assertEquals(ErrorKind.INCOMPATIBLE_SERVER,(CloseIncompleteTaskUseCase(repository)(domain(),"нет материала") as CommandOutcome.Rejected).error.kind) }
        repeat(2) { assertEquals("/openapi.json",server.takeRequest().path) }
        assertNull(server.takeRequest(100,TimeUnit.MILLISECONDS));assertTrue(journal.commands.isEmpty())
    }
    @Test fun closeErrorsPreserveNativeCodesAndDoNotFallback() = runBlocking {
        server.enqueue(reply(capability))
        for ((status,kind) in listOf(401 to ErrorKind.AUTHENTICATION,403 to ErrorKind.FORBIDDEN,404 to ErrorKind.NOT_FOUND,409 to ErrorKind.CONFLICT,422 to ErrorKind.VALIDATION)) {
            server.enqueue(reply("""{"code":"CLOSE_ERROR_$status","message":"Причина сервера $status"}""",status))
            val result=repository.closeIncomplete(domain(),"Причина $status") as CommandOutcome.Rejected
            assertEquals(kind,result.error.kind);assertEquals("CLOSE_ERROR_$status",result.error.code)
        }
        assertEquals(6,server.requestCount)
    }
    @Test fun lostCloseResponseKeepsReasonVersionAndTokenForExplicitRetry() = runBlocking {
        server.enqueue(reply(capability));server.enqueue(reply("{}",500))
        val unknown=repository.closeIncomplete(domain(),"Исходная причина") as CommandOutcome.Unresolved
        server.takeRequest();val first=server.takeRequest().body.readByteArray()
        server.enqueue(reply("""{"result":{"id":101,"duplicate":true},"task":${task(true,4)}}"""))
        assertTrue(dispatcher.retry(unknown.command.id) is CommandOutcome.Confirmed)
        assertArrayEquals(first,server.takeRequest().body.readByteArray())
        assertTrue(String(journal.commands.getValue(unknown.command.id).body).contains("Исходная причина"))
    }
    @Test fun activeHistoryAndAllUseServerViewWithoutStatusOrAssignmentQueries() = runBlocking {
        for(view in TaskView.entries) {
            server.enqueue(reply("""{"items":[],"total":0,"limit":50,"offset":50}"""))
            repository.tasks(42,TaskFilter(view=view),50,50)
            val query=server.takeRequest().requestUrl!!
            assertEquals(view.name.lowercase(),query.queryParameter("view"))
            assertEquals(setOf("warehouse_id","view","limit","offset"),query.queryParameterNames)
        }
    }
    @Test fun realConflictCodesRemainDistinctAndOnlyDefiniteStateRejectionAllowsNewConfirmation() = runBlocking {
        server.enqueue(reply(capability));server.enqueue(reply("""{"code":"DOCUMENT_STATE_CONFLICT","message":"Версия изменилась"}""",409))
        val rejected=repository.closeIncomplete(domain(),"Сверить остаток") as CommandOutcome.Rejected
        assertEquals("DOCUMENT_STATE_CONFLICT",rejected.error.code)
        server.takeRequest();val first=json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        server.enqueue(reply("""{"code":"IDEMPOTENCY_CONFLICT","message":"Нужна сверка исходного ключа"}""",409))
        val unresolved=repository.closeIncomplete(domain(),"Сверено, закрыть") as CommandOutcome.Unresolved
        assertEquals("IDEMPOTENCY_CONFLICT",unresolved.error.code);assertEquals(PendingState.NEEDS_REVIEW,unresolved.command.state)
        val next=json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertNotEquals(first["client_token"],next["client_token"])
        assertEquals("Сверить остаток",first["reason"]!!.jsonPrimitive.content)
        assertEquals("Сверено, закрыть",next["reason"]!!.jsonPrimitive.content)
        assertEquals("Сверено, закрыть",unresolved.command.summary.comment)
    }

}
