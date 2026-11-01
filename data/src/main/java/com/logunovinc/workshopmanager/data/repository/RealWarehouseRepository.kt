package com.logunovinc.workshopmanager.data.repository

import com.logunovinc.workshopmanager.data.remote.WorkshopRemoteDataSource
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.warehouse.*

class RealWarehouseRepository(private val remote: WorkshopRemoteDataSource) : WarehouseRepository {
    override suspend fun warehouses(): Outcome<List<Warehouse>> = when (val result = remote.warehouses()) {
        is Outcome.Failure -> result
        is Outcome.Success -> Outcome.Success(result.value.map { Warehouse(it.id, it.name, it.code, it.active) })
    }
}
