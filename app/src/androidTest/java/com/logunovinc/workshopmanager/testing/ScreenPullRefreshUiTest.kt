package com.logunovinc.workshopmanager.testing

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import com.logunovinc.workshopmanager.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.atomic.AtomicInteger

@HiltAndroidTest
class ScreenPullRefreshUiTest {
    @get:Rule(order=0) val backend=BackendRule()
    @get:Rule(order=1) val hilt=HiltAndroidRule(this)
    @get:Rule(order=2) val compose=createAndroidComposeRule<MainActivity>()
    private val taskReads=AtomicInteger();private val stockReads=AtomicInteger();private val posts=AtomicInteger()
    @Volatile private var stock="4"
    @Before fun before() {
        hilt.inject();val original=backend.backend.server.dispatcher
        backend.backend.server.dispatcher=object:Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                val path=request.requestUrl!!.encodedPath
                if(request.method=="GET" && path=="/api/workshop/tasks/101") taskReads.incrementAndGet()
                if(request.method=="POST" && path.startsWith("/api/workshop")) posts.incrementAndGet()
                if(path=="/api/warehouses/42/stock/301") {
                    stockReads.incrementAndGet()
                    return MockResponse().setHeader("Content-Type","application/json").setBody("""{"warehouse_id":42,"product_id":301,"product_name":"Материал","base_unit":"шт","qty":"$stock","reserved":"0","free":"$stock","unallocated_stock":"$stock","unallocated_reserved":"0","dimensions":[]}""")
                }
                return original.dispatch(request)
            }
        }
    }
    private fun waitTag(tag:String) { compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(list:String,tag:String) {
        compose.waitUntil(15_000) { try { compose.onNodeWithTag(list).performScrollToNode(hasTestTag(tag));true } catch(_:AssertionError) { false } }
        compose.onNodeWithTag(tag).performClick()
    }
    private fun login() {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("auth-submit").fetchSemanticsNodes().firstOrNull()?.config?.contains(SemanticsProperties.Disabled)==false }
        compose.onNodeWithTag("auth-login").performTextInput("fixture-worker");compose.onNodeWithTag("auth-password").performTextInput("synthetic-only")
        compose.onNodeWithTag("auth-submit").performScrollTo().performClick();waitTag("task-card-101")
    }
    private fun pull(list:String) {
        Espresso.closeSoftKeyboard();compose.onNodeWithTag(list).performScrollToIndex(0)
        compose.onNodeWithTag(list).performTouchInput { swipeDown() }
    }
    @Test fun detailAndFinalReceivePullReadOnceKeepDraftAndBlockWhileSending() {
        login();compose.onNodeWithTag("task-card-101").performTouchInput { click() };waitTag("task-finish-receive")
        val detailReads=taskReads.get();pull("task-detail-list")
        compose.waitUntil(15_000) { taskReads.get()==detailReads+1 }
        compose.waitUntil(15_000) { compose.onNodeWithTag("task-finish-receive").fetchSemanticsNode().config.contains(SemanticsProperties.Disabled).not() }
        click("task-detail-list","task-finish-receive");click("receive-list","receive-qty-1001")
        compose.onNodeWithTag("receive-qty-1001").performTextInput("4")
        compose.onNodeWithTag("receive-list").performScrollToNode(hasText("Причина недостачи, если она есть"))
        compose.onNodeWithText("Причина недостачи, если она есть").performTextInput("Остальное не прибыло")
        val reads=taskReads.get();pull("receive-list")
        compose.waitUntil(15_000) { taskReads.get()==reads+1 }
        compose.onNodeWithTag("receive-list").performScrollToNode(hasTestTag("receive-qty-1001"));compose.onNodeWithTag("receive-qty-1001").assertTextContains("4")
        compose.onNodeWithTag("receive-list").performScrollToNode(hasText("Причина недостачи, если она есть"));compose.onNodeWithText("Остальное не прибыло").assertExists()
        assertEquals(0,posts.get());compose.onAllNodesWithText("Обновить").assertCountEquals(0);compose.onAllNodesWithText("К заданию").assertCountEquals(0)
        click("receive-list","receive-review");backend.backend.ackBodyDelayMillis=4000
        compose.onNodeWithTag("receive-confirm").performClick()
        val during=taskReads.get();pull("receive-list");assertEquals(during,taskReads.get())
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("task-list").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1,posts.get())
    }
    @Test fun stockDetailPhysicalPullLoadsNewQuantity() {
        login();compose.onNodeWithTag("nav-stock").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Материал").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Материал").performTouchInput { click() };waitTag("stock-detail-list")
        compose.waitUntil(15_000) { compose.onAllNodesWithText("На складе: 4 шт").fetchSemanticsNodes().isNotEmpty() }
        val reads=stockReads.get();stock="7";pull("stock-detail-list")
        compose.waitUntil(15_000) { compose.onAllNodesWithText("На складе: 7 шт").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(reads+1,stockReads.get());assertEquals(0,posts.get());compose.onAllNodesWithText("Обновить").assertCountEquals(0)
    }
}
