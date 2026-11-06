package com.logunovinc.workshopmanager.testing

import android.util.Base64
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.logunovinc.workshopmanager.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import java.io.File

@RealBackendOnly
@HiltAndroidTest
class RealCloseIncompleteConnectedTest {
    @get:Rule(order=0) val ownership=object:ExternalResource() { override fun before() { ConnectedBackendConfig.attest() } }
    @get:Rule(order=1) val hilt=HiltAndroidRule(this)
    @get:Rule(order=2) val compose=createAndroidComposeRule<MainActivity>()
    private val scenario by lazy {
        Json.parseToJsonElement(String(Base64.decode(requireNotNull(InstrumentationRegistry.getArguments().getString("realBackendScenario")),Base64.DEFAULT))).jsonObject
    }
    private val proofs=linkedMapOf<String,JsonElement>()
    private val directory get()=File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"real-backend-evidence").apply { mkdirs() }
    @Before fun before() { hilt.inject() }
    @After fun evidence() {
        File(directory,"close-accounting.json").writeText(JsonObject(proofs).toString())
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap -> File(directory,"close-final.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) } }
    }
    private fun waitText(text:String) { compose.waitUntil(20_000) { compose.onAllNodesWithText(text,substring=true).fetchSemanticsNodes().isNotEmpty() } }
    private fun scroll(list:String,matcher:SemanticsMatcher) {
        compose.waitUntil(20_000) { try { compose.onNodeWithTag(list).performScrollToNode(matcher);true } catch (_:AssertionError) { false } }
    }
    private fun click(list:String,matcher:SemanticsMatcher) { scroll(list,matcher);compose.onNode(matcher).performClick() }
    private fun tag(list:String,tag:String)=click(list,hasTestTag(tag))
    private fun task(key:String)=scenario.getValue(key).jsonObject
    private fun open(key:String,type:String) {
        click("task-list",hasTestTag("task-card-${task(key).getValue("id").jsonPrimitive.long}"))
    }
    private fun home() { compose.onNodeWithTag("nav-stock").performClick();waitText("Склад цеха");compose.onNodeWithTag("nav-tasks").performClick() }
    private fun accounting():JsonElement {
        val connection=java.net.URL(ConnectedBackendConfig.baseUrl+"_tseh_test_fixture/accounting").openConnection().apply { connectTimeout=5_000;readTimeout=5_000 }
        return connection.getInputStream().bufferedReader().use { Json.parseToJsonElement(it.readText()) }
    }
    private fun close(key:String) {
        val before=accounting()
        tag("task-detail-list","task-close-incomplete");waitText("Завершить без полного выполнения?")
        compose.onNodeWithTag("close-incomplete-reason").performScrollTo().performTextInput("Закрытие UI $key")
        compose.onNodeWithTag("close-incomplete-confirm").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("task-list").fetchSemanticsNodes().isNotEmpty() && compose.onAllNodesWithTag("task-card-${task(key).getValue("id").jsonPrimitive.long}").fetchSemanticsNodes().isEmpty() }
        val after=accounting();assertEquals("Closing $key must not create physical effects",before,after)
        proofs[key]=buildJsonObject { put("before",before);put("after",after) }
        compose.onNodeWithTag("task-card-${task(key).getValue("id").jsonPrimitive.long}").assertDoesNotExist()
    }
    private fun produce(key:String,prototype:Boolean) {
        open(key,"Изготовление");tag("task-detail-list","task-manufacturing")
        tag("manufacturing-list","manufacturing-output-qty");compose.onNodeWithTag("manufacturing-output-qty").performTextReplacement(if(prototype) "2" else "3")
        if(prototype) {
            click("manufacturing-list",hasText("Найти материалы"));val id=scenario.getValue("materialId").jsonPrimitive.long
            compose.waitUntil(20_000) { compose.onAllNodesWithTag("prototype-add-$id").fetchSemanticsNodes().isNotEmpty() }
            tag("manufacturing-list","prototype-add-$id");tag("manufacturing-list","prototype-material-qty-1")
            compose.onNodeWithTag("prototype-material-qty-1").performTextInput("4")
        }
        tag("manufacturing-list","manufacturing-preview");tag("manufacturing-list","manufacturing-review")
        waitText(if(prototype) "Подтвердить первый прототип?" else "Подтвердить выпуск?")
        compose.onNodeWithTag("manufacturing-confirm").performClick()
        scroll("manufacturing-list",hasText("Готово",substring=true))
        compose.onNodeWithText("Назад",substring=false).performClick();close(key)
    }
    @Test fun actualClosePartialZeroFixedAndBothPrototypePhasesPreserveAccountingAndHistory() {
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("auth-submit").fetchSemanticsNodes().firstOrNull()?.config?.contains(SemanticsProperties.Disabled)==false }
        compose.onNodeWithTag("auth-login").performTextReplacement("ui-worker-1");compose.onNodeWithTag("auth-password").performTextReplacement("isolated-worker-password")
        compose.onNodeWithTag("auth-submit").performScrollTo().performClick()
        open("receive","Приёмка");scroll("task-detail-list",hasTestTag("task-receive"));compose.onNodeWithTag("task-close-incomplete").assertDoesNotExist();home()
        open("dispatch","Отправка");click("task-detail-list",hasText("Отправить товары"))
        click("dispatch-list",hasText("Отправить сейчас, шт"));compose.onNodeWithText("Отправить сейчас, шт").performTextInput("10")
        click("dispatch-list",hasText("Отправить"));waitText("Подтвердить отправку?");compose.onNodeWithText("Подтвердить",substring=false).performClick()
        scroll("dispatch-list",hasText("Готово",substring=true));compose.onNodeWithText("Назад",substring=false).performClick();close("dispatch")
        open("dispatchZero","Отправка");close("dispatchZero")
        produce("fixed",false)
        open("prototypeZero","Изготовление");close("prototypeZero")
        produce("prototype",true)
        compose.onNodeWithText("История заданий").performClick()
        waitText("Закрыта без полного выполнения")
        for(key in listOf("dispatch","dispatchZero","fixed","prototypeZero","prototype")) {
            val type=if(key.startsWith("dispatch")) "Отправка" else "Изготовление"
                click("task-list",hasTestTag("task-card-${task(key).getValue("id").jsonPrimitive.long}"))
            scroll("task-detail-list",hasText("Причина: Закрытие UI $key"))
            compose.onNodeWithTag("task-close-incomplete").assertDoesNotExist()
            compose.onNodeWithText("Назад",substring=false).performClick()
        }
    }
}
