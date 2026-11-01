package com.logunovinc.workshopmanager.testing

import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.rules.ExternalResource

object FixtureRuntime { lateinit var backend: SyntheticWorkshopBackend }
class BackendRule : ExternalResource() {
    val backend get() = FixtureRuntime.backend
    override fun before() { FixtureRuntime.backend = SyntheticWorkshopBackend() }
    override fun after() { backend.close() }
}
/** Entirely synthetic worker/catalog/warehouse. No request reaches the real backend. */
class SyntheticWorkshopBackend : AutoCloseable {
    val server = MockWebServer()
    val baseUrl: String
    @Volatile var prototypeScenario = false
    @Volatile var closeScenario = false
    @Volatile var closeConflictOnce = false
    @Volatile var closeClosed = false
    val closeAttempts = AtomicInteger()
    val closes = AtomicInteger()
    val closeBodies = java.util.Collections.synchronizedList(mutableListOf<String>())
    private var closeVersion = 3L
    private var closeActual = 10
    private fun closeTask() = """{"id":101,"type":"DISPATCH_GOODS","is_completed":false,"is_cancelled":false,"is_closed_incomplete":$closeClosed,"closed_incomplete_reason":"Причина UI","closed_incomplete_by":42,"closed_incomplete_at":"2026-11-02T12:00:00Z","version":$closeVersion,"warehouse_id":42,"created_by":1,"created_at":"2026-11-01T12:00:00Z","allowed_actions":${if(closeClosed) "[]" else "[\"execute\",\"close_incomplete\"]"},"lines":[{"id":1001,"product_id":301,"product_name":"Материал","role":"input","base_unit":"шт","planned_qty":"20","actual_qty":"$closeActual","remaining_qty":"${20-closeActual}"}]}"""
    val starts = AtomicInteger()
    val executions = AtomicInteger()
    val manufacturingExecutions = AtomicInteger()
    val previews = AtomicInteger()
    val stockReads = AtomicInteger()
    @Volatile var lastManufacturingBody = ""
    @Volatile var receiveFinal = false
    val receiveBodies = java.util.Collections.synchronizedList(mutableListOf<String>())
    private var accepted = 0
    private var version = 2L
    private val receipts = mutableMapOf<String, String>()
    private fun fixture(name: String) = InstrumentationRegistry.getInstrumentation().context.assets.open("production-v2/$name.json").bufferedReader().use { it.readText() }
    private fun response(body: String, status: Int = 200) = MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)
    private fun task(): String = if(closeScenario) closeTask() else if (prototypeScenario) fixture(when (manufacturingExecutions.get()) { 0 -> "prototype_task"; 1 -> "prototype_ready_task"; else -> "prototype_after_next3" }) else """{"id":101,"type":"RECEIVE_GOODS","status":"${if (receiveFinal) "completed" else if (accepted == 0) "new" else "partially_completed"}","version":$version,"warehouse_id":42,"assignee_user_id":${if (accepted == 0) "null" else "42"},"created_by":1,"created_at":"2026-11-01T12:00:00Z","comment":"Synthetic UI task","transfer_version":$version,"outcome":${if(receiveFinal && accepted<10) "\"shortage\"" else "null"},"allowed_actions":["start","execute"],"lines":[{"id":1001,"product_id":301,"product_name":"Материал","role":"receive","base_unit":"шт","planned_qty":"10","actual_qty":"$accepted","remaining_qty":"${10 - accepted}"}]}"""
    private fun page(item: String, total: Int = 1) = """{"items":[$item],"total":$total,"limit":50,"offset":0}"""
    init {
        server.dispatcher = object : Dispatcher() {
            @Synchronized override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                if (path == "/auth/login") return response("""{"login":"fixture-worker","role":"worker"}""").addHeader("Set-Cookie", "session=synthetic-cookie; HttpOnly; Path=/")
                if (path == "/auth/me") return if (request.getHeader("Cookie").orEmpty().contains("session=synthetic-cookie")) response("""{"id":42,"login":"fixture-worker","role":"worker"}""") else response("""{"detail":"Требуется вход"}""", 401)
                if (path == "/auth/logout") return response("""{"ok":true}""")
                if (path == "/openapi.json") return response("""{"paths":{"/api/workshop/tasks/{task_id}/close-incomplete":{"post":{}}}}""")
                if (path == "/api/workshop/tasks/101/close-incomplete" && request.method == "POST") {
                    closeAttempts.incrementAndGet(); closeBodies += request.body.readUtf8()
                    if(closeConflictOnce) {
                        closeConflictOnce=false;closeVersion++;closeActual=12
                        return response("""{"code":"DOCUMENT_STATE_CONFLICT","message":"Другой сотрудник изменил задание"}""",409)
                    }
                    closes.incrementAndGet();closeClosed=true;closeVersion++
                    return response("""{"result":{"id":101,"version":3,"duplicate":false},"task":${task()}}""")
                }
                if (path == "/api/warehouses") return response("""[{"id":7,"name":"Основной","code":"main","is_active":true},{"id":42,"name":"Цех","code":"workshop","is_active":true}]""")
                if (path == "/api/workshop/tasks") return response(page(task()))
                if (path == "/api/workshop/tasks/101") return response(task())
                if (path == "/api/workshop/tasks/101/start") { starts.incrementAndGet(); return response("""{"code":"TEST_ERROR","message":"Smoke must not automatically start"}""", 409) }
                if (path == "/api/workshop/tasks/101/executions" && request.method == "POST") {
                    val rawBody=request.body.readUtf8();receiveBodies += rawBody
                    val body = Json.parseToJsonElement(rawBody).jsonObject
                    val token = body["client_token"]!!.jsonPrimitive.content
                    receipts[token]?.let { return response(it) }
                    executions.incrementAndGet()
                    accepted += body["lines"]!!.jsonArray.sumOf { it.jsonObject["qty"]!!.jsonPrimitive.content.toInt() }
                    receiveFinal=body["final"]?.jsonPrimitive?.boolean ?: false
                    version++
                    val ack = """{"result":{"id":101,"execution_id":5001,"duplicate":false},"task":${task()}}"""
                    receipts[token] = ack
                    return response(ack)
                }
                if (path == "/api/workshop/tasks/101/executions") return response(if (accepted == 0) page("", 0) else page("""{"id":5001,"task_id":101,"executed_by":42,"executed_at":"2026-11-01T12:02:00Z","comment":"Synthetic received","final":false,"lines":[{"task_line_id":1001,"product_id":301,"role":"receive","actual_qty":"4"}]}"""))
                if (path == "/api/workshop/tasks/101/history") return response(if (accepted == 0) page("", 0) else page("""{"id":6001,"event_type":"executed","actor_id":42,"created_at":"2026-11-01T12:02:00Z","details":{"execution_id":5001}}"""))
                if (path == "/api/warehouses/42/stock") { stockReads.incrementAndGet(); return response(page("""{"warehouse_id":42,"product_id":301,"product_name":"Материал","base_unit":"шт","qty":"$accepted","reserved":"0","free":"$accepted","unallocated_stock":"$accepted","unallocated_reserved":"0","dimensions":[]}""")) }
                if (path == "/api/products") return response(page("""{"id":301,"name":"Материал","base_unit":"шт","is_sheet":false}"""))
                if (path == "/api/products/301/stock-definition") return response("""{"id":301,"name":"Материал","base_unit":"шт","is_sheet":false,"units":[{"name":"шт","coefficient":"1"}]}""")
                if (path == "/api/workshop/v2/tasks/101/manufacturing-preview") {
                    previews.incrementAndGet()
                    lastManufacturingBody = request.body.readUtf8()
                    val mode = Json.parseToJsonElement(lastManufacturingBody).jsonObject["mode"]!!.jsonPrimitive.content
                    return response(fixture(if (mode == "prototype") "prototype_preview_2_4" else "after_prototype_preview_3_6"))
                }
                if (path == "/api/workshop/v2/tasks/101/manufacturing-executions") {
                    lastManufacturingBody = request.body.readUtf8()
                    manufacturingExecutions.incrementAndGet()
                    return response("""{"result":{"id":101,"execution_id":5001,"duplicate":false},"task":${task()}}""")
                }
                return response("""{"code":"NOT_FOUND","message":"Synthetic fixture route unavailable"}""", 404)
            }
        }
        server.start()
        baseUrl = "http://127.0.0.1:${server.port}/"
    }
    override fun close() { server.shutdown() }
}
