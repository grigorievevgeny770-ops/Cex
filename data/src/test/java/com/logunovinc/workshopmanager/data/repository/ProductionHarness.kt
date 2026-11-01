package com.logunovinc.workshopmanager.data.repository

import com.logunovinc.workshopmanager.data.local.pending.*
import com.logunovinc.workshopmanager.data.remote.*
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.task.*
import com.logunovinc.workshopmanager.domain.production.*
import com.logunovinc.workshopmanager.domain.pending.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.*

/** Synthetic v2 fixtures matched to local backend OpenAPI; these are not live integration tests. */
object ProductionFixtures {
    fun text(name: String): String = requireNotNull(javaClass.classLoader?.getResourceAsStream("production-v2/$name.json")) { "Missing fixture $name" }.bufferedReader().use { it.readText() }
}
class ProductionHarness(journal: CommandJournal = TasksContractTest.MemoryJournal()) : AutoCloseable {
    val server = MockWebServer().apply { start() }
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val snapshot = SessionSnapshot(server.url("/").toString(), 42, 0)
    private val current = object : CurrentSession {
        override val profiles = flowOf(snapshot)
        override fun snapshot() = snapshot
        override fun matches(snapshot: SessionSnapshot) = snapshot == snapshot()
        override fun invalidate(generation: Long) = Unit
    }
    private val factory = ApiFactory(json, PersistentSessionCookieJar(MemoryCookies()))
    private val errors = ApiErrorMapper(json)
    lateinit var dispatcher: CommandDispatcher
    lateinit var repository: RealTaskRepository
    init { rebuild(journal) }
    fun rebuild(journal: CommandJournal) {
        dispatcher = CommandDispatcher(journal, OkHttpCommandSender(factory, current), current, json, errors, JournalMapper(json))
        repository = RealTaskRepository(WorkshopRemoteDataSource(factory, current, errors), TaskMapper(), dispatcher, json, errors)
    }
    fun reply(body: String, status: Int = 200) = MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)
    suspend fun task(name: String): Task {
        server.enqueue(reply(ProductionFixtures.text(name)))
        val task = (repository.task(101) as Outcome.Success).value
        server.takeRequest()
        return task
    }
    fun prepare(task: Task, quantity: String = "3", materials: List<PrototypeMaterialInput> = emptyList(), sheets: String = ""): ManufacturingCommand =
        (PrepareManufacturingUseCase()(task, ManufacturingDraft(ActualLineInput(3002, quantity, sheets), materials)) as Outcome.Success).value
    suspend fun preview(task: Task, command: ManufacturingCommand, name: String): Pair<ManufacturingPreview, RecordedRequest> {
        server.enqueue(reply(ProductionFixtures.text(name)))
        val result = PreviewManufacturingUseCase(repository)(task, command)
        return (result as Outcome.Success).value to server.takeRequest()
    }
    fun ack(name: String, duplicate: Boolean = false) = """{"result":{"id":101,"version":1,"execution_id":5001,"duplicate":$duplicate},"task":${ProductionFixtures.text(name)}}"""
    suspend fun execute(task: Task, command: ManufacturingCommand, preview: ManufacturingPreview, taskFixture: String): Pair<CommandOutcome, RecordedRequest> {
        server.enqueue(reply(ack(taskFixture)))
        val result = ExecuteManufacturingUseCase(repository)(task, command, preview, "Synthetic fixture")
        return result to server.takeRequest()
    }
    override fun close() { server.shutdown() }
}
