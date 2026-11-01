package com.logunovinc.workshopmanager.core.ui

import java.math.BigDecimal
import org.junit.Test
import org.junit.Assert.*

class QuantityFormatterTest {
    @Test fun displayRoundsWithoutChangingExactValueOrScale() {
        val value = BigDecimal("2.3450")
        assertEquals("2,35", QuantityFormatter.format(value))
        assertEquals("2.3450", value.toPlainString())
        assertEquals("2", QuantityFormatter.format(BigDecimal("2.0000")))
        assertEquals("0", QuantityFormatter.format(BigDecimal("-0.0000")))
        assertEquals("-2,35", QuantityFormatter.format(BigDecimal("-2.345")))
    }
}
