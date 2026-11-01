package com.logunovinc.workshopmanager.domain.warehouse

import com.logunovinc.workshopmanager.domain.*

data class Warehouse(val id: Long, val name: String, val code: String?, val active: Boolean)
interface WarehouseRepository { suspend fun warehouses(): Outcome<List<Warehouse>> }
/** Resolves the permitted active workshop by code; never substitutes the first or main warehouse. */
class ResolveWorkshopUseCase(private val repository: WarehouseRepository) {
    suspend operator fun invoke(): Outcome<Warehouse> = when (val result = repository.warehouses()) {
        is Outcome.Failure -> result
        is Outcome.Success -> {
            val workshop = result.value.filter { it.code == "workshop" }
            when {
                workshop.isEmpty() -> Outcome.Failure(AppError(ErrorKind.NOT_FOUND, "Склад цеха отсутствует или недоступен", "WORKSHOP_MISSING"))
                workshop.size > 1 -> Outcome.Failure(AppError(ErrorKind.CONFLICT, "Сервер вернул несколько складов цеха", "WORKSHOP_AMBIGUOUS"))
                !workshop.single().active -> Outcome.Failure(AppError(ErrorKind.FORBIDDEN, "Склад цеха неактивен", "WORKSHOP_INACTIVE"))
                else -> Outcome.Success(workshop.single())
            }
        }
    }
}
