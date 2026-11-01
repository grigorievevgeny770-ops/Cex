package com.logunovinc.workshopmanager.domain.stock

import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.task.Page

/** Loads a server-filtered stock page, retaining zero and negative positions by default. */
class LoadStockUseCase(private val repository: StockRepository) {
    suspend operator fun invoke(warehouseId: Long, filter: StockFilter, offset: Long = 0): Outcome<Page<StockPosition>> =
        if (warehouseId <= 0 || offset < 0) Outcome.Failure(AppError(ErrorKind.VALIDATION, "Некорректная страница остатков"))
        else repository.stock(warehouseId, filter.copy(query = filter.query.trim()), 50, offset)
}
/** Reads physical, reserved and free stock without optimistic local updates. */
class LoadStockPositionUseCase(private val repository: StockRepository) {
    suspend operator fun invoke(warehouseId: Long, productId: Long): Outcome<StockPosition> =
        if (warehouseId <= 0 || productId <= 0) Outcome.Failure(AppError(ErrorKind.VALIDATION, "Некорректный склад или товар")) else repository.position(warehouseId, productId)
}
/** Searches the general catalog; a product here is not evidence of stock availability. */
class SearchProductsUseCase(private val repository: StockRepository) {
    suspend operator fun invoke(query: String, offset: Long = 0): Outcome<Page<Product>> =
        if (offset < 0) Outcome.Failure(AppError(ErrorKind.VALIDATION, "Некорректная страница каталога")) else repository.products(query.trim(), 50, offset)
}
/** Reads current units and geometry for a newly selected material, without prices. */
class LoadStockDefinitionUseCase(private val repository: StockRepository) {
    suspend operator fun invoke(productId: Long): Outcome<StockDefinition> =
        if (productId <= 0) Outcome.Failure(AppError(ErrorKind.VALIDATION, "Некорректный товар")) else repository.definition(productId)
}
/** Reads catalog identity independently from warehouse stock. */
class LoadProductUseCase(private val repository: StockRepository) {
    suspend operator fun invoke(productId: Long): Outcome<Product> =
        if (productId <= 0) Outcome.Failure(AppError(ErrorKind.VALIDATION, "Некорректный товар")) else repository.product(productId)
}
