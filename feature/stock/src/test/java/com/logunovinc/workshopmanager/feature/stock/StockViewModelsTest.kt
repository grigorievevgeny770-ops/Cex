package com.logunovinc.workshopmanager.feature.stock

import androidx.lifecycle.SavedStateHandle
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.stock.*
import com.logunovinc.workshopmanager.domain.task.Page
import com.logunovinc.workshopmanager.domain.warehouse.*
import java.math.BigDecimal
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class StockViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }
    private class Warehouses : WarehouseRepository {
        var result: Outcome<List<Warehouse>> = Outcome.Success(listOf(Warehouse(7, "Main", "main", true), Warehouse(42, "Workshop", "workshop", true)))
        override suspend fun warehouses() = result
    }
    private class Stock : StockRepository {
        var calls = 0
        var lastFilter: StockFilter? = null
        var listing: suspend (StockFilter, Long) -> Outcome<Page<StockPosition>> = { _, offset -> Outcome.Success(Page(listOf(position()), 1, 50, offset)) }
        var current = position()
        override suspend fun stock(warehouseId: Long, filter: StockFilter, limit: Int, offset: Long): Outcome<Page<StockPosition>> { assertEquals(42L, warehouseId); calls++; lastFilter = filter; return listing(filter, offset) }
        override suspend fun position(warehouseId: Long, productId: Long): Outcome<StockPosition> { assertEquals(42L, warehouseId); assertEquals(301L, productId); calls++; return Outcome.Success(current) }
        override suspend fun products(query: String, limit: Int, offset: Long): Outcome<Page<Product>> = error("unused")
        var flatSheet = true
        var productResult: Outcome<Product>? = null
        override suspend fun product(productId: Long): Outcome<Product> = productResult ?: Outcome.Success(Product(productId, if (flatSheet) "Лист" else "Конёк", "м²", flatSheet, null))
        override suspend fun definition(productId: Long): Outcome<StockDefinition> = error("unused")
        companion object {
            fun position(id: Long = 301) = StockPosition(42, id, "Материал", null, "шт", BigDecimal.ZERO, BigDecimal("2"), BigDecimal("-2"), BigDecimal.ZERO, BigDecimal.ONE,
                listOf(StockDimension(BigDecimal.ONE, BigDecimal.ZERO, 3, BigDecimal.ONE, 1, null, null)))
        }
    }
    @Test fun debouncedServerSearchCannotBeOverwrittenByOldResponse() = runTest(dispatcher) {
        val repository = Stock()
        val old = CompletableDeferred<Unit>()
        repository.listing = { filter, _ -> if (filter.query.isEmpty()) withContext(NonCancellable) { old.await(); Outcome.Success(Page(listOf(Stock.position(1)), 1, 50, 0)) }
            else Outcome.Success(Page(listOf(Stock.position(2)), 1, 50, 0)) }
        val vm = StockListViewModel(ResolveWorkshopUseCase(Warehouses()), LoadStockUseCase(repository), LoadProductUseCase(repository))
        runCurrent()
        vm.onIntent(StockListIntent.QueryChanged("new"))
        advanceTimeBy(301); runCurrent()
        assertEquals(listOf(2L), vm.uiState.value.items.map(StockPosition::productId))
        old.complete(Unit); runCurrent()
        assertEquals(listOf(2L), vm.uiState.value.items.map(StockPosition::productId))
        assertEquals("new", repository.lastFilter?.query)
    }
    @Test fun pagingBeyondFiftyAlwaysIncludesUnavailableStock() = runTest(dispatcher) {
        val repository = Stock()
        repository.listing = { _, offset -> Outcome.Success(Page((offset until minOf(offset + 50, 55)).map { Stock.position(it + 1) }, 55, 50, offset)) }
        val vm = StockListViewModel(ResolveWorkshopUseCase(Warehouses()), LoadStockUseCase(repository), LoadProductUseCase(repository))
        runCurrent()
        assertFalse(repository.lastFilter!!.onlyAvailable)
        vm.onIntent(StockListIntent.LoadMore); vm.onIntent(StockListIntent.LoadMore)
        runCurrent()
        assertEquals(55, vm.uiState.value.items.size)
        assertEquals(2, repository.calls)
        assertFalse(vm.uiState.value.hasMore)
        assertFalse(repository.lastFilter!!.onlyAvailable)
    }
    @Test fun revokedWorkshopClearsCachedRowsAndShowsPermissionError() = runTest(dispatcher) {
        val warehouse = Warehouses()
        val vm = StockListViewModel(ResolveWorkshopUseCase(warehouse), LoadStockUseCase(Stock()), LoadProductUseCase(Stock()))
        runCurrent()
        assertEquals(1, vm.uiState.value.items.size)
        warehouse.result = Outcome.Failure(AppError(ErrorKind.FORBIDDEN, "Нет доступа"))
        vm.onIntent(StockListIntent.Refresh); runCurrent()
        assertTrue(vm.uiState.value.items.isEmpty())
        assertEquals(ErrorKind.FORBIDDEN, vm.uiState.value.error?.kind)
    }
    @Test fun detailKeepsUnknownDimensionsAndRefreshReadsNewServerTruth() = runTest(dispatcher) {
        val stock = Stock()
        val vm = StockDetailViewModel(SavedStateHandle(mapOf("productId" to 301L)), ResolveWorkshopUseCase(Warehouses()), LoadStockPositionUseCase(stock), LoadProductUseCase(stock))
        runCurrent()
        assertEquals(true, vm.uiState.value.flatSheet)
        assertEquals(BigDecimal("-2"), vm.uiState.value.position!!.free)
        assertNull(vm.uiState.value.position!!.dimensions.single().freeSheets)
        stock.current = stock.current.copy(quantity = BigDecimal("5"), free = BigDecimal("3"))
        vm.onIntent(StockDetailIntent.Refresh); runCurrent()
        assertEquals(BigDecimal("5"), vm.uiState.value.position!!.quantity)
        assertEquals(2, stock.calls)
    }
    @Test fun finishedGoodsAndUnverifiedRowsAreNotShownAsFlatSheet() = runTest(dispatcher) {
        val hidden = Stock()
        hidden.flatSheet = false
        hidden.listing = { _, _ -> Outcome.Success(Page(listOf(Stock.position(2)), 1, 50, 0)) }
        val hiddenList = StockListViewModel(ResolveWorkshopUseCase(Warehouses()), LoadStockUseCase(hidden), LoadProductUseCase(hidden))
        runCurrent()
        assertTrue(hiddenList.uiState.value.items.isEmpty())
        assertEquals(0, hiddenList.uiState.value.unverified)
        val unknown = Stock()
        unknown.productResult = Outcome.Failure(AppError(ErrorKind.NETWORK, "нет сети"))
        val unknownList = StockListViewModel(ResolveWorkshopUseCase(Warehouses()), LoadStockUseCase(unknown), LoadProductUseCase(unknown))
        runCurrent()
        assertTrue(unknownList.uiState.value.items.isEmpty())
        assertEquals(1, unknownList.uiState.value.unverified)
        val detailStock = Stock()
        detailStock.flatSheet = false
        val detail = StockDetailViewModel(SavedStateHandle(mapOf("productId" to 301L)), ResolveWorkshopUseCase(Warehouses()), LoadStockPositionUseCase(detailStock), LoadProductUseCase(detailStock))
        runCurrent()
        assertEquals(false, detail.uiState.value.flatSheet)
    }
}
