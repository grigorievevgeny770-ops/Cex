package com.logunovinc.workshopmanager.domain.order

import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.logunovinc.workshopmanager.domain.Outcome

class WorkshopOrderUseCasesTest {
    private fun order(status: OrderStatus, actions: Set<String>, norms: List<Boolean?>) = WorkshopOrder(
        7, 3, status, status.name.lowercase(), Instant.parse("2026-11-20T00:00:00Z"), "кромка",
        norms.mapIndexed { index, known -> OrderLine(index + 1L, 1000L + index, "Изделие", "2000", BigDecimal.ONE, "шт", known) },
        actions,
    )
    private val sheet = listOf(SheetUse(900, 2, BigDecimal("1.250")))

    @Test fun knownNormRejectsClientQuantitiesAndMissingNormRequiresActualSheet() {
        val known = order(OrderStatus.IN_PROGRESS, setOf("manufacture"), listOf(true, true))
        assertTrue(OrderCommands.manufacture(known, emptyList()) is Outcome.Success)
        assertTrue(OrderCommands.manufacture(known, sheet) is Outcome.Failure)
        val missing = order(OrderStatus.IN_PROGRESS, setOf("manufacture"), listOf(true, false))
        assertTrue(OrderCommands.manufacture(missing, emptyList()) is Outcome.Failure)
        assertTrue(OrderCommands.manufacture(missing, sheet) is Outcome.Success)
        val unknown = order(OrderStatus.IN_PROGRESS, setOf("manufacture"), listOf(null))
        assertTrue(OrderCommands.manufacture(unknown, sheet) is Outcome.Failure)
    }

    @Test fun actionsFollowServerPermissionsRatherThanStatusAlone() {
        val manufactured = order(OrderStatus.MANUFACTURED, setOf("hand_over"), listOf(true))
        assertTrue(manufactured.canHandOver)
        assertEquals(false, manufactured.canReverseManufacture)
        assertEquals(false, manufactured.copy(allowedActions = setOf("reverse_manufacture")).canHandOver)
    }
}
