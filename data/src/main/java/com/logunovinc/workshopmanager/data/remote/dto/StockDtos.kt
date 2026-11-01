@file:kotlinx.serialization.UseSerializers(DecimalSerializer::class)
package com.logunovinc.workshopmanager.data.remote.dto

import java.math.BigDecimal
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class StockDimensionDto(val length: BigDecimal, val qty: BigDecimal, val sheets: Int, val reserved: BigDecimal,
    @SerialName("reserved_sheets") val reservedSheets: Int, val free: BigDecimal? = null, @SerialName("free_sheets") val freeSheets: Int? = null)
@Serializable data class StockPositionDto(@SerialName("warehouse_id") val warehouseId: Long, @SerialName("product_id") val productId: Long,
    @SerialName("product_name") val productName: String? = null, val sku: String? = null, @SerialName("base_unit") val baseUnit: String,
    val qty: BigDecimal, val reserved: BigDecimal, val free: BigDecimal,
    @SerialName("unallocated_stock") val unallocatedStock: BigDecimal, @SerialName("unallocated_reserved") val unallocatedReserved: BigDecimal,
    val dimensions: List<StockDimensionDto>)
@Serializable data class ProductDto(val id: Long, val name: String, @SerialName("base_unit") val baseUnit: String,
    @SerialName("is_sheet") val sheet: Boolean, val sku: String? = null)
@Serializable data class ProductUnitDto(val name: String, val coefficient: BigDecimal)
@Serializable data class StockDefinitionDto(val id: Long, val name: String, val sku: String? = null,
    @SerialName("base_unit") val baseUnit: String, @SerialName("is_sheet") val sheet: Boolean,
    @SerialName("sheet_full_width_m") val fullWidth: BigDecimal? = null, val units: List<ProductUnitDto>)
