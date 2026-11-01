package com.logunovinc.workshopmanager.data.remote

import com.logunovinc.workshopmanager.domain.stock.StockFilter

class StockRemoteDataSource(private val calls: AuthenticatedRequestExecutor) {
    suspend fun stock(warehouseId: Long, filter: StockFilter, limit: Int, offset: Long) = calls.request(StockApi::class.java, missingRoute = true) { api, _ -> api.stock(warehouseId, filter.query.takeIf(String::isNotBlank), filter.onlyAvailable, limit, offset) }
    suspend fun position(warehouseId: Long, productId: Long) = calls.request(StockApi::class.java) { api, _ -> api.position(warehouseId, productId) }
    suspend fun products(query: String, limit: Int, offset: Long) = calls.request(StockApi::class.java, missingRoute = true) { api, _ -> api.products(query.takeIf(String::isNotBlank), limit, offset) }
    suspend fun product(productId: Long) = calls.request(StockApi::class.java) { api, _ -> api.product(productId) }
    suspend fun definition(productId: Long) = calls.request(StockApi::class.java) { api, _ -> api.definition(productId) }
}
