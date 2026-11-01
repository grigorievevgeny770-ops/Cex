package com.logunovinc.workshopmanager.data.repository

import com.logunovinc.workshopmanager.data.remote.*
import com.logunovinc.workshopmanager.data.remote.dto.*
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.stock.*
import com.logunovinc.workshopmanager.domain.task.Page

class RealStockRepository(private val remote: StockRemoteDataSource, private val errors: ApiErrorMapper) : StockRepository {
    private fun <A, B> map(result: Outcome<A>, block: (A) -> B): Outcome<B> = when (result) {
        is Outcome.Failure -> result
        is Outcome.Success -> try { Outcome.Success(block(result.value)) } catch (error: Exception) { Outcome.Failure(errors.throwable(error)) }
    }
    private fun position(value: StockPositionDto) = StockPosition(value.warehouseId, value.productId, value.productName, value.sku, value.baseUnit,
        value.qty, value.reserved, value.free, value.unallocatedStock, value.unallocatedReserved, value.dimensions.map {
            StockDimension(it.length, it.qty, it.sheets, it.reserved, it.reservedSheets, it.free, it.freeSheets)
        })
    private fun product(value: ProductDto) = Product(value.id, value.name, value.baseUnit, value.sheet, value.sku)
    override suspend fun stock(warehouseId: Long, filter: StockFilter, limit: Int, offset: Long) = map(remote.stock(warehouseId, filter, limit, offset)) { Page(it.items.map(::position), it.total, it.limit, it.offset) }
    override suspend fun position(warehouseId: Long, productId: Long) = map(remote.position(warehouseId, productId), ::position)
    override suspend fun products(query: String, limit: Int, offset: Long) = map(remote.products(query, limit, offset)) { Page(it.items.map(::product), it.total, it.limit, it.offset) }
    override suspend fun product(productId: Long) = map(remote.product(productId), ::product)
    override suspend fun definition(productId: Long) = map(remote.definition(productId)) { StockDefinition(it.id, it.name, it.sku, it.baseUnit, it.sheet, it.fullWidth, it.units.map { unit -> ProductUnit(unit.name, unit.coefficient) }) }
}
