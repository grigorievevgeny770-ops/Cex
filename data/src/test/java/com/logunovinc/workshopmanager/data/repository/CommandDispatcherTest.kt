package com.logunovinc.workshopmanager.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.logunovinc.workshopmanager.data.local.pending.*
import com.logunovinc.workshopmanager.data.remote.*
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.pending.*
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CommandDispatcherTest {
    private lateinit var database: CommandDatabase
    private lateinit var room: RoomCommandJournal
    private lateinit var session: FakeSession
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mapper = JournalMapper(json)
    private val errors = ApiErrorMapper(json)
    private val summary = CommandSummary("Отправка", listOf("Материал №10: 2.0000 шт"), "Факт шага")
    private val ack = """{"result":{"id":101,"execution_id":5001,"duplicate":false},"task":{"id":101,"version":4,"status":"partially_completed","lines":[{"id":1001,"actual_qty":"2.0000"}]}}"""
    class FakeSession(baseUrl: String = "https://example.com/") : CurrentSession {
        private val state = MutableStateFlow<SessionSnapshot?>(SessionSnapshot(baseUrl, 42, 0))
        override val profiles = state.asStateFlow()
        override fun snapshot() = state.value
        override fun matches(snapshot: SessionSnapshot) = snapshot == state.value
        override fun invalidate(generation: Long) { if (state.value?.generation == generation) state.value = null }
        fun set(value: SessionSnapshot?) { state.value = value }
    }
    @Before fun before() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CommandDatabase::class.java).allowMainThreadQueries().build()
        room = RoomCommandJournal(database)
        session = FakeSession()
    }
    @After fun after() { database.close() }
    private fun dispatcher(sender: RawCommandSender, journal: CommandJournal = room, registry: CommandMutexRegistry = CommandMutexRegistry.process) =
        CommandDispatcher(journal, sender, session, json, errors, mapper, registry)
    private suspend fun submit(dispatcher: CommandDispatcher) = dispatcher.submit(101, "DISPATCH_GOODS", "POST", "api/workshop/tasks/101/executions", summary) { token ->
        """{"type":"DISPATCH_GOODS","expected_version":3,"client_token":"$token","lines":[{"task_line_id":1001,"qty":"2.0000"}],"comment":"Факт шага"}""".toByteArray()
    }
    private fun sender(block: suspend (PendingCommandEntity) -> RawCommandResponse) = object : RawCommandSender {
        override suspend fun send(command: PendingCommandEntity, session: SessionSnapshot) = block(command)
    }
    @Test fun durablePrepareAndAttemptAreBeforeNetwork() = runBlocking {
        val result = submit(dispatcher(sender { command ->
            val stored = room.get(command.id)!!
            assertArrayEquals(command.body, stored.body)
            assertEquals(PendingState.IN_FLIGHT.name, stored.state)
            assertEquals(command.clientToken, stored.clientToken)
            RawCommandResponse(200, "application/json", ack)
        })) as CommandOutcome.Confirmed
        val saved = room.get(result.confirmation.commandId)!!
        assertEquals(4L, result.confirmation.taskVersion)
        assertEquals(ack, saved.ackBody)
        assertEquals(PendingState.ACKNOWLEDGED.name, saved.state)
        assertNull(database.commands().active(saved.baseUrl, saved.actorId, saved.taskId))
    }
    @Test fun prepareFailureSendsNothing() = runBlocking {
        val calls = AtomicInteger()
        val journal = object : CommandJournal by room {
            override suspend fun prepare(command: PendingCommandEntity): PrepareResult = throw IOException("disk full")
        }
        assertTrue(submit(dispatcher(sender { calls.incrementAndGet(); error("must not send") }, journal)) is CommandOutcome.Rejected)
        assertEquals(0, calls.get())
    }
    @Test fun databaseSlotPreventsTwoDifferentDispatcherInstancesSending() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val network = sender { calls.incrementAndGet(); entered.complete(Unit); release.await(); RawCommandResponse(500, "application/json", "{}") }
        val first = async(Dispatchers.IO) { submit(dispatcher(network, registry = CommandMutexRegistry())) }
        entered.await()
        val second = submit(dispatcher(network, registry = CommandMutexRegistry())) as CommandOutcome.Unresolved
        release.complete(Unit)
        val original = first.await() as CommandOutcome.Unresolved
        assertEquals(1, calls.get())
        assertEquals(original.command.id, second.command.id)
        assertEquals(1, room.observe("https://example.com/", 42).first().size)
    }
    @Test fun ackSaveFailureRetainsRequestAndSafeRetry() = runBlocking {
        var failAck = true
        val journal = object : CommandJournal by room {
            override suspend fun update(command: PendingCommandEntity, releaseSlot: Boolean) {
                if (command.state == PendingState.ACKNOWLEDGED.name && failAck) { failAck = false; throw IOException("disk full after server ACK") }
                room.update(command, releaseSlot)
            }
        }
        val bodies = mutableListOf<ByteArray>()
        val dispatcher = dispatcher(sender { bodies += it.body.copyOf(); RawCommandResponse(200, "application/json", ack.replace("partially_completed","completed")) }, journal)
        val unknown = submit(dispatcher) as CommandOutcome.Unresolved
        assertTrue(unknown.error.message.contains("Сервер подтвердил"))
        assertEquals(5001L, unknown.command.knownExecutionId)
        assertNotNull(database.commands().active("https://example.com/", 42, 101))
        assertNull(unknown.command.confirmation)
        val recovered = dispatcher.retry(unknown.command.id) as CommandOutcome.Confirmed
        assertTrue(recovered.confirmation.terminal)
        assertArrayEquals(bodies[0], bodies[1])
    }
    @Test fun initial403ReleasesSlotAndNewExplicitIntentCanSucceedAfterRightsRestore() = runBlocking {
        var count = 0
        val dispatcher = dispatcher(sender {
            count++
            if (count == 1) RawCommandResponse(403, "application/json", """{"code":"FORBIDDEN","message":"Нет прав"}""")
            else RawCommandResponse(200, "application/json", ack)
        })
        val rejected = submit(dispatcher) as CommandOutcome.Rejected
        assertNull(database.commands().active("https://example.com/", 42, 101))
        val accepted = submit(dispatcher) as CommandOutcome.Confirmed
        assertNotEquals(rejected.command!!.id, accepted.confirmation.commandId)
        assertEquals(2, count)
        assertEquals(2, room.observe("https://example.com/", 42).first().size)
    }
    @Test fun unknownThen401Or403NeverErasesEvidenceOrReleasesSlot() = runBlocking {
        var status = 500
        val dispatcher = dispatcher(sender { RawCommandResponse(status, "application/json", """{"message":"failure"}""") })
        val unknown = submit(dispatcher) as CommandOutcome.Unresolved
        val original = room.get(unknown.command.id)!!
        for (code in listOf(401, 403, 409, 422)) {
            status = code
            val result = dispatcher.retry(original.id) as CommandOutcome.Unresolved
            assertEquals(PendingState.NEEDS_REVIEW, result.command.state)
            assertNotNull(database.commands().active(original.baseUrl, 42, 101))
            assertArrayEquals(original.body, room.get(original.id)!!.body)
        }
    }
    @Test fun actorAndServerIsolationPreventRetryAndHideJournal() = runBlocking {
        val count = AtomicInteger()
        val dispatcher = dispatcher(sender { count.incrementAndGet(); RawCommandResponse(500, "application/json", "{}") })
        val unknown = submit(dispatcher) as CommandOutcome.Unresolved
        val repository = RealPendingCommandRepository(room, mapper, session, dispatcher)
        session.set(SessionSnapshot("https://example.com/", 43, 1))
        assertTrue(dispatcher.retry(unknown.command.id) is CommandOutcome.Rejected)
        assertTrue(repository.observe().first().isEmpty())
        session.set(SessionSnapshot("https://other.com/", 42, 2))
        assertTrue(dispatcher.retry(unknown.command.id) is CommandOutcome.Rejected)
        assertTrue(repository.observe().first().isEmpty())
        assertEquals(1, count.get())
        assertNotNull(room.get(unknown.command.id))
    }
    @Test fun cancellationLeavesUnknownAndExactRequest() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val dispatcher = dispatcher(sender { entered.complete(Unit); awaitCancellation() })
        val job = launch(Dispatchers.IO) { submit(dispatcher) }
        entered.await()
        job.cancelAndJoin()
        val command = room.observe("https://example.com/", 42).first().single()
        assertEquals(PendingState.UNKNOWN.name, command.state)
        assertTrue(command.uncertain)
        assertNotNull(database.commands().active(command.baseUrl, 42, 101))
    }
    @Test fun serverAcceptsLostReplyThenDatabaseReopenRepeatsByteIdentically() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "pending-test-${UUID.randomUUID()}.db"
        database.close()
        database = Room.databaseBuilder(context, CommandDatabase::class.java, name).allowMainThreadQueries().build()
        room = RoomCommandJournal(database)
        val server = MockWebServer().apply { start() }
        try {
            session = FakeSession(server.url("/").toString())
            val jar = PersistentSessionCookieJar(MemoryCookies())
            val rawSender = OkHttpCommandSender(ApiFactory(json, jar), session)
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            val unknown = submit(dispatcher(rawSender)) as CommandOutcome.Unresolved
            val sent = server.takeRequest()
            val persisted = room.get(unknown.command.id)!!
            database.close()
            database = Room.databaseBuilder(context, CommandDatabase::class.java, name).allowMainThreadQueries().build()
            room = RoomCommandJournal(database)
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(ack.replace("false", "true")))
            val confirmed = dispatcher(rawSender).retry(unknown.command.id) as CommandOutcome.Confirmed
            val repeated = server.takeRequest()
            assertArrayEquals(sent.body.readByteArray(), repeated.body.readByteArray())
            assertArrayEquals(persisted.body, room.get(unknown.command.id)!!.body)
            assertEquals(persisted.clientToken, room.get(unknown.command.id)!!.clientToken)
            assertTrue(confirmed.confirmation.duplicate)
            assertEquals(1, room.observe(session.snapshot()!!.baseUrl, 42).first().size)
        } finally { server.shutdown(); database.close(); context.deleteDatabase(name) }
    }

    @Test fun malformedSuccessfulResponseRemainsUnknownWithCompatibilityError() = runBlocking {
        val dispatcher = dispatcher(sender { RawCommandResponse(200, "text/html", "<html>wrong server</html>") })
        val result = submit(dispatcher) as CommandOutcome.Unresolved
        assertEquals(ErrorKind.INCOMPATIBLE_SERVER, result.error.kind)
        assertEquals(PendingState.UNKNOWN, result.command.state)
        assertNotNull(database.commands().active("https://example.com/", 42, 101))
    }
    @Test fun duplicateIntentAfterAckReturnsStoredReceiptWithoutNewNetwork() = runBlocking {
        val calls = AtomicInteger()
        val dispatcher = dispatcher(sender { calls.incrementAndGet(); RawCommandResponse(200, "application/json", ack) })
        val first = submit(dispatcher) as CommandOutcome.Confirmed
        val second = submit(dispatcher) as CommandOutcome.Confirmed
        assertEquals(first.confirmation.commandId, second.confirmation.commandId)
        assertEquals(1, calls.get())
    }
    @Test fun inFlightLoadedAfterCrashIsUnknownWithoutAutomaticSend() = runBlocking {
        val prepared = PendingCommandEntity("crash", "https://example.com/", 42, 101, "start", "token", "POST", "api/workshop/tasks/101/start", "{}".toByteArray(), "fingerprint", mapper.summary(summary), 1L, PendingState.IN_FLIGHT.name)
        room.prepare(prepared)
        val dispatcher = dispatcher(sender { error("no automatic network") })
        val repository = RealPendingCommandRepository(room, mapper, session, dispatcher)
        assertEquals(PendingState.UNKNOWN, repository.observe().first().single().state)
    }
    @Test fun newStartIsRejectedButAnExistingHistoricalStartRetriesItsExactBody() = runBlocking {
        val bodies = mutableListOf<ByteArray>()
        val dispatcher = dispatcher(sender { command ->
            assertEquals("api/workshop/tasks/101/start", command.path)
            bodies += command.body.copyOf()
            RawCommandResponse(200, "application/json", ack)
        })
        val rejected = dispatcher.submit(101, "start", "POST", "api/workshop/tasks/101/start", summary) { token ->
            """{"expected_version":3,"client_token":"$token"}""".toByteArray()
        }
        assertTrue(rejected is CommandOutcome.Rejected)
        assertTrue(bodies.isEmpty())
        val exact = """{"expected_version":3,"client_token":"historical-token","comment":""}""".toByteArray()
        val saved = PendingCommandEntity("historical-start", "https://example.com/", 42, 101, "start", "historical-token", "POST",
            "api/workshop/tasks/101/start", exact, "historical-fingerprint", mapper.summary(summary), 1, PendingState.UNKNOWN.name, uncertain = true)
        room.prepare(saved)
        assertTrue(dispatcher.retry(saved.id) is CommandOutcome.Confirmed)
        assertArrayEquals(exact, bodies.single())
        assertArrayEquals(exact, room.get(saved.id)!!.body)
    }

    @Test fun closeIncompleteUnknownSurvivesRoomReopenAndPermissionsErrorWithoutChangingReason() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val name="close-test-${UUID.randomUUID()}.db"
        database.close()
        database=Room.databaseBuilder(context,CommandDatabase::class.java,name).allowMainThreadQueries().build()
        room=RoomCommandJournal(database)
        val server=MockWebServer().apply { start() }
        try {
            session=FakeSession(server.url("/").toString())
            val network=OkHttpCommandSender(ApiFactory(json,PersistentSessionCookieJar(MemoryCookies())),session)
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            val unknown=dispatcher(network).submit(101,"CLOSE_INCOMPLETE","POST","api/workshop/tasks/101/close-incomplete",summary) { token ->
                """{"expected_version":3,"client_token":"$token","reason":"Исходная причина 😀"}""".toByteArray(Charsets.UTF_8)
            } as CommandOutcome.Unresolved
            val first=server.takeRequest().body.readByteArray()
            val token=room.get(unknown.command.id)!!.clientToken
            database.close()
            database=Room.databaseBuilder(context,CommandDatabase::class.java,name).allowMainThreadQueries().build()
            room=RoomCommandJournal(database)
            for(status in listOf(401,403,409,422)) {
                server.enqueue(MockResponse().setResponseCode(status).setHeader("Content-Type","application/json").setBody("""{"code":"IDEMPOTENCY_CONFLICT","message":"Нужна сверка"}"""))
                assertTrue(dispatcher(network).retry(unknown.command.id) is CommandOutcome.Unresolved)
                assertArrayEquals(first,checkNotNull(server.takeRequest(5,java.util.concurrent.TimeUnit.SECONDS)).body.readByteArray())
                assertEquals(token,room.get(unknown.command.id)!!.clientToken)
                assertNotNull(database.commands().active(server.url("/").toString(),42,101))
                if (session.snapshot() == null) session.set(SessionSnapshot(server.url("/").toString(),42,0))
            }
            server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody(ack.replace("false","true")))
            assertTrue(dispatcher(network).retry(unknown.command.id) is CommandOutcome.Confirmed)
            assertArrayEquals(first,checkNotNull(server.takeRequest(5,java.util.concurrent.TimeUnit.SECONDS)).body.readByteArray())
        } finally { server.shutdown();database.close();context.deleteDatabase(name) }
    }

    @Test fun initialIdempotencyConflictRetainsOriginalCloseAndBlocksAnyCompetingTaskCommand() = runBlocking {
        var sends=0
        val network=sender { sends++;RawCommandResponse(409,"application/json","""{"code":"IDEMPOTENCY_CONFLICT","message":"Нужна сверка"}""") }
        val dispatcher=dispatcher(network)
        val result=dispatcher.submit(101,"CLOSE_INCOMPLETE","POST","api/workshop/tasks/101/close-incomplete",summary) { token ->
            """{"expected_version":3,"client_token":"$token","reason":"Первоначальная причина"}""".toByteArray()
        } as CommandOutcome.Unresolved
        val original=room.get(result.command.id)!!
        assertEquals(PendingState.NEEDS_REVIEW,result.command.state)
        val competing=submit(dispatcher) as CommandOutcome.Unresolved
        val changed=dispatcher.submit(101,"CLOSE_INCOMPLETE","POST","api/workshop/tasks/101/close-incomplete",summary) { token ->
            """{"expected_version":9,"client_token":"$token","reason":"Другая причина"}""".toByteArray()
        } as CommandOutcome.Unresolved
        assertEquals(result.command.id,competing.command.id);assertEquals(result.command.id,changed.command.id);assertEquals(1,sends)
        assertArrayEquals(original.body,room.get(original.id)!!.body);assertEquals("https://example.com/",original.baseUrl);assertEquals(42L,original.actorId)
    }

}
