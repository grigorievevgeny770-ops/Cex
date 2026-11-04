package com.logunovinc.workshopmanager.testing

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.logunovinc.workshopmanager.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*

/** Fault/input UI coverage stays synthetic; successful accounting is covered by the real backend suite. */
@HiltAndroidTest
class CloseIncompleteConnectedSmokeTest {
    @get:Rule(order=0) val backend=BackendRule()
    @get:Rule(order=1) val hilt=HiltAndroidRule(this)
    @get:Rule(order=2) val compose=createAndroidComposeRule<MainActivity>()
    @Before fun before() { hilt.inject();backend.backend.closeScenario=true }
    private fun waitText(value:String) { compose.waitUntil(15_000) { compose.onAllNodesWithText(value,substring=true).fetchSemanticsNodes().isNotEmpty() } }
    private fun openTask() {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("auth-submit").fetchSemanticsNodes().firstOrNull()?.config?.contains(SemanticsProperties.Disabled)==false }
        compose.onNodeWithTag("auth-login").performTextInput("fixture-worker");compose.onNodeWithTag("auth-password").performTextInput("synthetic-only")
        compose.onNodeWithTag("auth-submit").performScrollTo().performClick();compose.waitUntil(15_000) { compose.onAllNodesWithTag("task-card-101").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("task-card-101").performScrollTo().performClick()
    }
    private fun dialog() {
        compose.waitUntil(15_000) { try { compose.onNodeWithTag("task-detail-list").performScrollToNode(hasTestTag("task-close-incomplete"));true } catch (_:AssertionError) { false } }
        compose.onNodeWithTag("task-close-incomplete").performClick();waitText("Завершить без полного выполнения?")
    }
    private fun reason(value:String) { compose.onNodeWithTag("close-incomplete-reason").performScrollTo().performTextReplacement(value) }
    private fun confirmed() {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("task-list").fetchSemanticsNodes().isNotEmpty() && compose.onAllNodesWithTag("task-card-101").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("task-card-101").assertDoesNotExist()
    }
    @Test fun cancelBlankWhitespaceOversizedAndDoubleTapCannotCreateExtraCommand() {
        openTask();dialog();reason("Не отправлять")
        compose.onNode(hasText("Назад") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(0,backend.backend.closeAttempts.get())
        dialog()
        for(value in listOf("","   ","a".repeat(2001))) {
            reason(value);compose.onNodeWithTag("close-incomplete-confirm").performClick()
            waitText("от 1 до 2000 символов");assertEquals(0,backend.backend.closeAttempts.get())
        }
        reason("Причина UI");compose.onNodeWithTag("close-incomplete-confirm").performTouchInput { doubleClick() }
        confirmed();compose.activityRule.scenario.recreate();confirmed()
        assertEquals(1,backend.backend.closeAttempts.get());assertEquals(1,backend.backend.closes.get())
        assertEquals(0,backend.backend.executions.get());assertEquals(0,backend.backend.starts.get())
        val body=Json.parseToJsonElement(backend.backend.closeBodies.single()).jsonObject
        assertEquals(setOf("expected_version","client_token","reason"),body.keys)
        assertEquals("Причина UI",body["reason"]!!.jsonPrimitive.content)
    }
    @Test fun anotherWorkerConflictRefreshesVersionsAndRequiresNewExplicitConfirmation() {
        backend.backend.closeConflictOnce=true
        openTask();dialog();reason("Причина UI");compose.onNodeWithTag("close-incomplete-confirm").performClick()
        waitText("Задание изменилось. Обновите данные и проверьте количество")
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("task-close-incomplete").fetchSemanticsNodes().firstOrNull()?.config?.contains(SemanticsProperties.Disabled)==false }
        assertEquals(1,backend.backend.closeAttempts.get());assertEquals(0,backend.backend.closes.get())
        dialog();reason("Причина UI");compose.onNodeWithTag("close-incomplete-confirm").performClick();confirmed()
        assertEquals(2,backend.backend.closeAttempts.get());assertEquals(1,backend.backend.closes.get())
        val bodies=backend.backend.closeBodies.map { Json.parseToJsonElement(it).jsonObject }
        assertEquals(3,bodies[0]["expected_version"]!!.jsonPrimitive.int);assertEquals(4,bodies[1]["expected_version"]!!.jsonPrimitive.int)
        assertNotEquals(bodies[0]["client_token"],bodies[1]["client_token"])
    }
    @Test fun terminalAckReceivedInBackgroundReturnsOnceWhenScreenResumes() {
        backend.backend.ackBodyDelayMillis=2000
        openTask();dialog();reason("Причина UI")
        val reads=backend.backend.taskListReads.get()
        compose.onNodeWithTag("close-incomplete-confirm").performClick()
        compose.waitUntil(10_000) { backend.backend.closes.get()==1 }
        compose.onNodeWithTag("task-detail-list").assertIsDisplayed()
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        android.os.SystemClock.sleep(2300)
        assertEquals(reads,backend.backend.taskListReads.get())
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        confirmed();assertEquals(reads+1,backend.backend.taskListReads.get())
    }

}
