package com.logunovinc.workshopmanager.domain.warehouse

import com.logunovinc.workshopmanager.domain.*
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

class ResolveWorkshopUseCaseTest {
    private fun repository(result: Outcome<List<Warehouse>>) = object : WarehouseRepository { override suspend fun warehouses() = result }
    @Test fun resolvesCodeInsteadOfFirstOrLiteralId() = runTest {
        val result = ResolveWorkshopUseCase(repository(Outcome.Success(listOf(Warehouse(7, "Главный", "main", true), Warehouse(42, "Цех", "workshop", true)))))() as Outcome.Success
        assertEquals(42L, result.value.id)
    }
    @Test fun missingInactiveAndForbiddenRemainDistinct() = runTest {
        val missing = ResolveWorkshopUseCase(repository(Outcome.Success(listOf(Warehouse(7, "Главный", "main", true)))))() as Outcome.Failure
        assertEquals("WORKSHOP_MISSING", missing.error.code)
        val inactive = ResolveWorkshopUseCase(repository(Outcome.Success(listOf(Warehouse(42, "Цех", "workshop", false)))))() as Outcome.Failure
        assertEquals("WORKSHOP_INACTIVE", inactive.error.code)
        val denied = ResolveWorkshopUseCase(repository(Outcome.Failure(AppError(ErrorKind.FORBIDDEN, "Нет доступа"))))() as Outcome.Failure
        assertEquals("Нет доступа", denied.error.message)
    }
}
