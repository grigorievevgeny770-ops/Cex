package com.logunovinc.workshopmanager.domain.stock

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlatSheetStockTest {
    private fun position(id: Long) = StockPosition(42, id, "Товар $id", null, "м²", BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, emptyList())

    @Test fun onlyCatalogFlatSheetIsKeptAndOtherGoodsAreNotRelabeled() {
        val sheet = Product(1, "Лист", "м²", true, null)
        val ridge = Product(2, "Конёк простой 2000", "шт", false, null)
        val result = classifyFlatSheet(listOf(position(1), position(2), position(3)), mapOf(1L to sheet, 2L to ridge, 3L to null))
        assertTrue(sheet.isFlatSheet())
        assertFalse(ridge.isFlatSheet())
        assertEquals(listOf(1L), result.sheets.map { it.productId })
        assertEquals(1, result.hidden)
        assertEquals(1, result.unverified)
    }
}
