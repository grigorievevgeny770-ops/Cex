package com.logunovinc.workshopmanager.data.repository

import com.logunovinc.workshopmanager.data.remote.*
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.stock.*
import com.logunovinc.workshopmanager.domain.warehouse.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.math.BigDecimal

class StockContractTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: RealStockRepository
    private lateinit var warehouses: RealWarehouseRepository
    private val json = Json { ignoreUnknownKeys = true }
    private fun reply(body: String, status: Int = 200) = MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)
    private fun position(id: Long = 301, quantity: String = "0", free: String = "-2") = """{"warehouse_id":42,"product_id":$id,"product_name":"Материал","sku":"TEST","base_unit":"м²","qty":"$quantity","reserved":"2","free":"$free","unallocated_stock":"1.2340","unallocated_reserved":"0.5000","dimensions":[{"length":"2.345","qty":"0","sheets":3,"reserved":"1","reserved_sheets":1,"free":null,"free_sheets":null}]}"""
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
        repository = RealStockRepository(StockRemoteDataSource(AuthenticatedRequestExecutor(factory, current, errors)), errors)
        warehouses = RealWarehouseRepository(WorkshopRemoteDataSource(factory, current, errors))
    }
    @After fun after() { server.shutdown() }
    @Test fun resolvedWorkshopSearchAndPageTwoKeepZeroAndNegativeRowsByDefault() = runBlocking {
        server.enqueue(reply("""[{"id":7,"name":"Main","code":"main","is_active":true},{"id":42,"name":"Workshop","code":"workshop","is_active":true}]"""))
        val warehouse = (ResolveWorkshopUseCase(warehouses)() as Outcome.Success).value
        server.takeRequest()
        server.enqueue(reply("""{"items":[${position()},${position(302, "-3", "-5")}],"total":52,"limit":50,"offset":50}"""))
        val page = LoadStockUseCase(repository)(warehouse.id, StockFilter(" лист "), 50) as Outcome.Success
        assertEquals(BigDecimal.ZERO, page.value.items[0].quantity)
        assertEquals(BigDecimal("-3"), page.value.items[1].quantity)
        assertEquals(BigDecimal("-5"), page.value.items[1].free)
        val request = server.takeRequest().requestUrl!!
        assertEquals("/api/warehouses/42/stock", request.encodedPath)
        assertEquals("лист", request.queryParameter("q"))
        assertEquals("false", request.queryParameter("only_available"))
        assertEquals("50", request.queryParameter("offset"))
        server.enqueue(reply("""{"items":[],"total":0,"limit":50,"offset":0}"""))
        LoadStockUseCase(repository)(42, StockFilter(onlyAvailable = true))
        assertEquals("true", server.takeRequest().requestUrl!!.queryParameter("only_available"))
    }
    @Test fun dimensionUnknownIsNullAndUnallocatedQuantitiesRemainExact() = runBlocking {
        server.enqueue(reply(position()))
        val value = (repository.position(42, 301) as Outcome.Success).value
        assertEquals("/api/warehouses/42/stock/301", server.takeRequest().path)
        assertNull(value.dimensions.single().free)
        assertNull(value.dimensions.single().freeSheets)
        assertEquals(3, value.dimensions.single().sheets)
        assertEquals("1.2340", value.unallocatedStock.toPlainString())
        assertEquals("0.5000", value.unallocatedReserved.toPlainString())
        assertEquals("2.345", value.dimensions.single().length.toPlainString())
    }
    @Test fun catalogSearchAndDefinitionAreReadOnlyAndNotStockProof() = runBlocking {
        server.enqueue(reply("""{"items":[{"id":9223372036854775806,"name":"Лист","base_unit":"м²","is_sheet":true}],"total":51,"limit":50,"offset":50}"""))
        val product = (SearchProductsUseCase(repository)("лист", 50) as Outcome.Success).value.items.single()
        assertEquals(9223372036854775806L, product.id)
        assertEquals("/api/products", server.takeRequest().requestUrl!!.encodedPath)
        server.enqueue(reply("""{"id":301,"name":"Лист","base_unit":"м²","is_sheet":true,"sheet_full_width_m":"1.220","units":[{"name":"упаковка","coefficient":"2.5000"}]}"""))
        val definition = (LoadStockDefinitionUseCase(repository)(301) as Outcome.Success).value
        assertEquals("1.220", definition.fullWidth?.toPlainString())
        assertEquals("2.5000", definition.units.single().coefficient.toPlainString())
        assertEquals("/api/products/301/stock-definition", server.takeRequest().path)
        server.enqueue(reply("""{"id":301,"name":"Лист","base_unit":"м²","is_sheet":true}"""))
        assertTrue(LoadProductUseCase(repository)(301) is Outcome.Success)
        assertEquals("/api/products/301", server.takeRequest().path)
        assertEquals(3, server.requestCount)
    }
    @Test fun forbiddenMissingWarehouseAndMissingStockRouteDoNotBecomeEmptyLists() = runBlocking {
        server.enqueue(reply("""{"code":"FORBIDDEN","message":"Нет доступа к складу"}""", 403))
        assertEquals(ErrorKind.FORBIDDEN, (repository.stock(42, StockFilter()) as Outcome.Failure).error.kind)
        server.enqueue(reply("""{"message":"Not Found"}""", 404))
        assertEquals(ErrorKind.INCOMPATIBLE_SERVER, (repository.stock(42, StockFilter()) as Outcome.Failure).error.kind)
        server.enqueue(reply("[]"))
        assertEquals("WORKSHOP_MISSING", (ResolveWorkshopUseCase(warehouses)() as Outcome.Failure).error.code)
    }
}
