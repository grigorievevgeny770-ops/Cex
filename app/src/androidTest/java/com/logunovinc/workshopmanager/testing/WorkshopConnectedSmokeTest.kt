package com.logunovinc.workshopmanager.testing

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.logunovinc.workshopmanager.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.*
import org.junit.Assert.*
import java.io.File
import kotlinx.serialization.json.*

@HiltAndroidTest
class WorkshopConnectedSmokeTest {
    @get:Rule(order = 0) val backend = BackendRule()
    @get:Rule(order = 1) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 2) val compose = createAndroidComposeRule<MainActivity>()
    @Before fun before() { hilt.inject() }
    private fun waitText(text: String, substring: Boolean = false) {
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun login() {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("auth-submit").fetchSemanticsNodes().firstOrNull()?.config?.contains(SemanticsProperties.Disabled) == false
        }
        screenshot("login-safe-insets")
        compose.onNodeWithTag("auth-login").performTextInput("fixture-worker")
        compose.onNodeWithTag("auth-password").performTextInput("synthetic-only")
        compose.onNodeWithTag("auth-submit").performScrollTo().performClick()
        waitText("№101", substring = true)
    }
    private fun clickInList(list: String, tag: String) {
        compose.onNodeWithTag(list).performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
    }
    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "connected-screenshots").apply { mkdirs() }
        compose.waitForIdle()
        android.os.SystemClock.sleep(350) // Let platform Dialog/IME window animations reach their final frame.
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun loginReceiveConfirmationHistoryAndServerStockRefreshSurviveRecreate() {
        login()
        compose.onNodeWithTag("nav-stock").performClick()
        waitText("Физически: 0 шт")
        compose.onNodeWithTag("nav-tasks").performClick()
        waitText("№101 · Приёмка")
        compose.onNodeWithText("№101 · Приёмка").performScrollTo().performClick()
        clickInList("task-detail-list", "task-receive")
        compose.onNodeWithTag("receive-list").performScrollToNode(hasTestTag("receive-qty-1001"))
        compose.onNodeWithTag("receive-qty-1001").performTextInput("4")
        assertEquals(0, backend.backend.executions.get())
        clickInList("receive-list", "receive-review")
        waitText("Принять эту часть?")
        assertEquals(0, backend.backend.executions.get())
        screenshot("receive-confirmation")
        compose.onNodeWithTag("receive-confirm").performClick()
        compose.waitUntil(15_000) { backend.backend.executions.get() == 1 }
        compose.onNodeWithTag("receive-list").performScrollToIndex(0)
        waitText("Действие подтверждено сервером", substring = true)
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        assertEquals(1, backend.backend.executions.get())
        assertEquals(0, backend.backend.starts.get())
        compose.onNodeWithTag("receive-list").performScrollToIndex(0)
        waitText("Карточка задания")
        compose.onNodeWithText("Карточка задания").performClick()
        compose.onNodeWithTag("task-detail-list").performScrollToNode(hasText("Факт №5001", substring = true))
        waitText("Материал: 4")
        screenshot("received-history")
        compose.onNodeWithTag("nav-stock").performClick()
        waitText("Физически: 4 шт")
        compose.onNodeWithText("Физически: 4 шт").assertIsDisplayed()
        assertTrue(backend.backend.stockReads.get() >= 2)
        assertEquals(1, backend.backend.executions.get())
        screenshot("stock-after-receive")
    }
    @Test fun prototypeIsEnteredOnceThenUiUsesOnlyOutputByRecipe() {
        backend.backend.prototypeScenario = true
        login()
        compose.onNodeWithText("№101 · Изготовление").performScrollTo().performClick()
        clickInList("task-detail-list", "task-manufacturing")
        compose.onNodeWithTag("manufacturing-list").performScrollToNode(hasTestTag("manufacturing-output-qty"))
        compose.onNodeWithTag("manufacturing-output-qty").performTextInput("2")
        compose.onNodeWithTag("manufacturing-list").performScrollToNode(hasText("Найти материалы"))
        compose.onNodeWithText("Найти материалы").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("prototype-add-301").fetchSemanticsNodes().isNotEmpty() }
        clickInList("manufacturing-list", "prototype-add-301")
        compose.onNodeWithTag("manufacturing-list").performScrollToNode(hasTestTag("prototype-material-qty-1"))
        compose.onNodeWithTag("prototype-material-qty-1").performTextInput("4")
        clickInList("manufacturing-list", "manufacturing-preview")
        compose.waitUntil(15_000) { backend.backend.previews.get() == 1 }
        compose.onNodeWithTag("manufacturing-list").performScrollToNode(hasText("Предлагаемая норма", substring = true))
        assertEquals(0, backend.backend.manufacturingExecutions.get())
        clickInList("manufacturing-list", "manufacturing-review")
        waitText("Подтвердить первый прототип?")
        screenshot("prototype-confirmation")
        compose.onNodeWithTag("manufacturing-confirm").performClick()
        compose.waitUntil(15_000) { backend.backend.manufacturingExecutions.get() == 1 }
        compose.onNodeWithTag("manufacturing-list").performScrollToIndex(0)
        waitText("Введите только выпуск. Расход рассчитает сервер по норме")
        waitText("изготовлено 2 · осталось 8", substring = true)
        compose.onNodeWithTag("manufacturing-list").performScrollToNode(hasTestTag("manufacturing-output-qty"))
        compose.onNodeWithTag("manufacturing-output-qty").performTextInput("3")
        clickInList("manufacturing-list", "manufacturing-preview")
        compose.waitUntil(15_000) { backend.backend.previews.get() == 2 }
        val body = Json.parseToJsonElement(backend.backend.lastManufacturingBody).jsonObject
        assertEquals("by_recipe", body["mode"]!!.jsonPrimitive.content)
        assertFalse(body.containsKey("materials"))
        clickInList("manufacturing-list", "manufacturing-review")
        waitText("Подтвердить выпуск?")
        compose.onNodeWithTag("manufacturing-confirm").performClick()
        compose.waitUntil(15_000) { backend.backend.manufacturingExecutions.get() == 2 }
        assertFalse(Json.parseToJsonElement(backend.backend.lastManufacturingBody).jsonObject.containsKey("materials"))
        compose.onNodeWithTag("manufacturing-list").performScrollToIndex(0)
        waitText("изготовлено 5 · осталось 5", substring = true)
        screenshot("manufacturing-by-recipe-success")
    }
}
