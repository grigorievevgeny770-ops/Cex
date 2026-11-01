package com.logunovinc.workshopmanager.domain.stock

import java.math.BigDecimal
import com.logunovinc.workshopmanager.domain.Outcome
import com.logunovinc.workshopmanager.domain.task.Page

data class StockFilter(val query: String = "", val onlyAvailable: Boolean = false)
data class StockDimension(val length: BigDecimal, val quantity: BigDecimal, val sheets: Int,
    val reserved: BigDecimal, val reservedSheets: Int, val free: BigDecimal?, val freeSheets: Int?)
data class StockPosition(val warehouseId: Long, val productId: Long, val productName: String?, val sku: String?, val baseUnit: String,
    val quantity: BigDecimal, val reserved: BigDecimal, val free: BigDecimal,
    val unallocatedStock: BigDecimal, val unallocatedReserved: BigDecimal, val dimensions: List<StockDimension>)
data class Product(val id: Long, val name: String, val baseUnit: String, val sheet: Boolean, val sku: String?)
data class ProductUnit(val name: String, val coefficient: BigDecimal)
/** Current catalog geometry is for new prototype materials, never an existing frozen task. */
data class StockDefinition(val id: Long, val name: String, val sku: String?, val baseUnit: String, val sheet: Boolean,
    val fullWidth: BigDecimal?, val units: List<ProductUnit>)
interface StockRepository {
    suspend fun stock(warehouseId: Long, filter: StockFilter, limit: Int = 50, offset: Long = 0): Outcome<Page<StockPosition>>
    suspend fun position(warehouseId: Long, productId: Long): Outcome<StockPosition>
    suspend fun products(query: String, limit: Int = 50, offset: Long = 0): Outcome<Page<Product>>
    suspend fun product(productId: Long): Outcome<Product>
    suspend fun definition(productId: Long): Outcome<StockDefinition>
}
