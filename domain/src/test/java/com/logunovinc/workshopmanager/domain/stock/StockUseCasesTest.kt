package com.logunovinc.workshopmanager.domain.stock

import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.task.Page
import java.math.BigDecimal
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

class StockUseCasesTest {
    private class Repository : StockRepository {
        var calls = 0
        var filter: StockFilter? = null
        val position = StockPosition(42, 301, "Материал", null, "шт", BigDecimal.ZERO, BigDecimal("2"), BigDecimal("-2"), BigDecimal.ZERO, BigDecimal("1"), emptyList())
        override suspend fun stock(warehouseId: Long, filter: StockFilter, limit: Int, offset: Long): Outcome<Page<StockPosition>> { calls++; this.filter = filter; return Outcome.Success(Page(listOf(position), 1, limit, offset)) }
        override suspend fun position(warehouseId: Long, productId: Long): Outcome<StockPosition> { calls++; return Outcome.Success(position) }
        override suspend fun products(query: String, limit: Int, offset: Long): Outcome<Page<Product>> { calls++; return Outcome.Success(Page(listOf(Product(999, "Без остатков", "шт", false, null)), 1, limit, offset)) }
        override suspend fun product(productId: Long): Outcome<Product> { calls++; return Outcome.Success(Product(productId, "Товар", "шт", false, null)) }
        override suspend fun definition(productId: Long): Outcome<StockDefinition> { calls++; return Outcome.Success(StockDefinition(productId, "Товар", null, "шт", false, null, emptyList())) }
    }
    @Test fun invalidIdsAndOffsetsNeverReadRepository() = runTest {
        val repository = Repository()
        assertTrue(LoadStockUseCase(repository)(0, StockFilter()) is Outcome.Failure)
        assertTrue(LoadStockPositionUseCase(repository)(42, 0) is Outcome.Failure)
        assertTrue(SearchProductsUseCase(repository)("", -1) is Outcome.Failure)
        assertTrue(LoadStockDefinitionUseCase(repository)(0) is Outcome.Failure)
        assertTrue(LoadProductUseCase(repository)(0) is Outcome.Failure)
        assertEquals(0, repository.calls)
    }
    @Test fun allReadModelsRetainServerValuesAndCatalogIsIndependent() = runTest {
        val repository = Repository()
        val stock = LoadStockUseCase(repository)(42, StockFilter(" лист ")) as Outcome.Success
        assertEquals(BigDecimal("-2"), stock.value.items.single().free)
        assertEquals(StockFilter("лист", false), repository.filter)
        assertTrue(LoadStockPositionUseCase(repository)(42, 301) is Outcome.Success)
        assertEquals(999L, (SearchProductsUseCase(repository)("new") as Outcome.Success).value.items.single().id)
        assertTrue(LoadStockDefinitionUseCase(repository)(999) is Outcome.Success)
        assertTrue(LoadProductUseCase(repository)(999) is Outcome.Success)
        assertEquals(5, repository.calls)
    }
}
