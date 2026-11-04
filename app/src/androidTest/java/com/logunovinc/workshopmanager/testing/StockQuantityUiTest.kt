package com.logunovinc.workshopmanager.testing

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.logunovinc.workshopmanager.domain.stock.*
import com.logunovinc.workshopmanager.feature.stock.*
import org.junit.*
import java.math.BigDecimal

class StockQuantityUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun equalScaledAvailabilityIsNotDuplicatedButNegativeAndUnknownDimensionsRemainVisible() {
        val base=StockPosition(42,301,"Материал",null,"шт",BigDecimal("4.0"),BigDecimal.ZERO,BigDecimal("4.0000"),BigDecimal("4"),BigDecimal.ZERO,emptyList())
        var position by mutableStateOf(base)
        compose.setContent { StockListScreen(StockListUiState(loading=false,items=listOf(position)),{}, {}) }
        compose.onNodeWithText("На складе: 4 шт").assertIsDisplayed()
        compose.onAllNodesWithText("Доступно:",substring=true).assertCountEquals(0)
        compose.runOnIdle { position=base.copy(free=BigDecimal("-1"),dimensions=listOf(StockDimension(BigDecimal("2"),BigDecimal("4"),9,BigDecimal.ZERO,0,null,null))) }
        compose.onNodeWithText("Доступно: -1 шт").assertIsDisplayed()
        compose.onNodeWithText("2 м · 9 листов · свободно неизвестно").assertIsDisplayed()
    }
}
