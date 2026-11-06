package com.logunovinc.workshopmanager.testing

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.logunovinc.workshopmanager.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.*
import org.junit.Assert.*
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.MockResponse

@HiltAndroidTest
class InlinePendingRetryUiTest {
    @get:Rule(order=0) val backend=BackendRule()
    @get:Rule(order=1) val hilt=HiltAndroidRule(this)
    @get:Rule(order=2) val compose=createAndroidComposeRule<MainActivity>()
    @Before fun before() { hilt.inject() }
    private fun waitTag(tag:String) { compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(list:String,tag:String) {
        compose.waitUntil(15_000) { try { compose.onNodeWithTag(list).performScrollToNode(hasTestTag(tag));true } catch(_:AssertionError) { false } }
        compose.onNodeWithTag(tag).performClick()
    }
    @Test fun unknownExecutionIsRetriedInItsTaskWithoutAnyRecoveryScreenOrNewPayload() {
        backend.backend.failReceiveOnce=true
        val requests=java.util.Collections.synchronizedList(mutableListOf<ByteArray>())
        val original=backend.backend.server.dispatcher
        backend.backend.server.dispatcher=object:Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                if(request.method=="POST" && request.requestUrl!!.encodedPath.endsWith("/executions")) requests+=request.body.clone().readByteArray()
                return original.dispatch(request)
            }
        }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("auth-submit").fetchSemanticsNodes().firstOrNull()?.config?.contains(SemanticsProperties.Disabled)==false }
        compose.onNodeWithTag("auth-login").performTextInput("fixture-worker");compose.onNodeWithTag("auth-password").performTextInput("synthetic-only")
        compose.onNodeWithTag("auth-submit").performScrollTo().performClick();waitTag("task-card-101")
        compose.onAllNodesWithText("Проверка действий").assertCountEquals(0)
        compose.onNodeWithTag("task-card-101").performTouchInput { click() }
        click("task-detail-list","task-receive");click("receive-list","receive-qty-1001")
        compose.onNodeWithTag("receive-qty-1001").performTextInput("4")
        click("receive-list","receive-review");waitTag("receive-confirm");compose.onNodeWithTag("receive-confirm").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Исход неизвестен",substring=true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("receive-list").performScrollToIndex(0)
        compose.onNodeWithText("Назад",substring=false).performClick();waitTag("task-retry-pending")
        compose.onAllNodesWithText("Проверка действий").assertCountEquals(0)
        compose.onNodeWithText("Материал: 4 шт",substring=true).assertIsDisplayed()
        compose.onNodeWithTag("task-retry-pending").performTouchInput { doubleClick() }
        compose.waitUntil(15_000) { backend.backend.executions.get()==1 && compose.onAllNodesWithTag("task-retry-pending").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("task-detail-list").assertIsDisplayed()
        assertEquals(2,requests.size);assertArrayEquals(requests[0],requests[1])
        assertEquals(0,backend.backend.starts.get())
    }
}
