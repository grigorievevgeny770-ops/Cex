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
import org.junit.rules.ExternalResource
import java.net.URI

/** Only the owned launcher supplies this opt-in configuration. No production URL fallback. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RealBackendOnly

object ConnectedBackendConfig {
    @Volatile private var attestedUrl: String? = null
    fun attest() {
        val args = InstrumentationRegistry.getArguments()
        val url = requireNotNull(realUrl) { "Use scripts/check-real-backend-ui.py; missing owned fixture arguments" }
        validate(url)
        val marker = requireNotNull(args.getString("realBackendMarker"))
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(marker.toByteArray()).joinToString("") { "%02x".format(it) }
        val connection = java.net.URL(url + "_tseh_test_fixture").openConnection().apply { connectTimeout=5_000;readTimeout=5_000 }
        val body = connection.getInputStream().bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }
        require(body["kind"]?.jsonPrimitive?.content == "tseh-real-backend-owned-v1") { "Not an owned fixture server" }
        require(body["revision"]?.jsonPrimitive?.content == args.getString("realBackendRevision")) { "Backend revision mismatch" }
        require(body["tokenSha256"]?.jsonPrimitive?.content == hash) { "Fixture marker mismatch" }
        attestedUrl=url
    }
    private fun validate(url: String) {
        val marker = InstrumentationRegistry.getArguments().getString("realBackendMarker").orEmpty()
        val uri = URI(url)
        require(marker.matches(Regex("[a-f0-9]{64}"))) { "Owned fixture marker required" }
        require(uri.scheme == "http" && uri.host == "10.0.2.2" && uri.port in 1024..65535 && uri.port != 8000 && uri.path == "/") { "Only ephemeral emulator fixture allowed" }
    }
    val realUrl: String? get() = InstrumentationRegistry.getArguments().getString("realBackendUrl")
    val baseUrl: String get() = if (realUrl == null) FixtureRuntime.backend.baseUrl else requireNotNull(attestedUrl) { "Fixture must be attested before any HTTP login/write" }

}

@RealBackendOnly
@HiltAndroidTest
class RealBackendConnectedTest {
    @get:Rule(order = 0) val optIn = object : ExternalResource() {
        override fun before() { ConnectedBackendConfig.attest() }
    }
    @get:Rule(order = 1) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 2) val compose = createAndroidComposeRule<MainActivity>()
    private val scenario by lazy {
        val encoded = requireNotNull(InstrumentationRegistry.getArguments().getString("realBackendScenario"))
        Json.parseToJsonElement(String(Base64.decode(encoded, Base64.DEFAULT))).jsonObject
    }
    @Before fun before() { hilt.inject() }
    @After fun evidence() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = java.io.File(context.getExternalFilesDir(null), "real-backend-evidence").apply { mkdirs() }
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            java.io.File(directory, "final-screen.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        }
        runCatching { java.io.File(directory,"semantics.txt").writeText(compose.onRoot().printToString()) }
    }
    private fun waitText(text: String) { compose.waitUntil(20_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(list: String, matcher: SemanticsMatcher) {
        compose.waitUntil(20_000) {
            try { compose.onNodeWithTag(list).performScrollToNode(matcher); true } catch (_: AssertionError) { false }
        }
        compose.onNode(matcher).performClick()
    }
    private fun tagged(list: String, tag: String) = click(list, hasTestTag(tag))
    private fun task(key: String) = scenario.getValue(key).jsonObject
    private fun login(n: Int) {
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("auth-submit").fetchSemanticsNodes().firstOrNull()?.config?.contains(SemanticsProperties.Disabled) == false }
        compose.onNodeWithTag("auth-login").performTextReplacement("ui-worker-$n")
        compose.onNodeWithTag("auth-password").performTextReplacement("isolated-worker-password")
        compose.onNodeWithTag("auth-submit").performScrollTo().performClick()
        waitText("№${task("receive").getValue("id").jsonPrimitive.long} ·")
    }
    private fun open(key: String, type: String) {
        val label = "№${task(key).getValue("id").jsonPrimitive.long} · $type"
        waitText(label); compose.onNodeWithText(label).performScrollTo().performClick()
    }
    private fun home() { compose.onNodeWithTag("nav-stock").performClick(); waitText("Склад цеха"); compose.onNodeWithTag("nav-tasks").performClick() }
    private fun confirmed(list: String) {
        compose.waitUntil(20_000) {
            try { compose.onNodeWithTag(list).performScrollToNode(hasText("Действие подтверждено сервером",substring=true));true } catch (_: AssertionError) { false }
        }
    }
    private fun receive(qty: String, final: Boolean) {
        tagged("task-detail-list", if(final) "task-finish-receive" else "task-receive")
        val line = task("receive").getValue("lines").jsonArray[0].jsonObject.getValue("id").jsonPrimitive.long
        val field = "receive-qty-$line"
        tagged("receive-list",field);compose.onNodeWithTag(field).performTextReplacement(qty)
        if(final) {
            click("receive-list", hasText("Причина недостачи, если она есть"))
            compose.onNodeWithText("Причина недостачи, если она есть").performTextInput("Одна единица не прибыла")
        }
        tagged("receive-list", "receive-review"); waitText(if(final) "Завершить приёмку?" else "Принять эту часть?")
        compose.onNodeWithTag("receive-confirm").performClick();confirmed("receive-list")
    }
    private fun manufacture(key: String, prototype: Boolean) {
        open(key,"Изготовление");tagged("task-detail-list","task-manufacturing")
        fun output(value: String) { tagged("manufacturing-list","manufacturing-output-qty");compose.onNodeWithTag("manufacturing-output-qty").performTextReplacement(value) }
        fun commit() { tagged("manufacturing-list","manufacturing-preview");tagged("manufacturing-list","manufacturing-review");waitText(if(prototype) "Подтвердить" else "Подтвердить выпуск?");compose.onNodeWithTag("manufacturing-confirm").performClick();confirmed("manufacturing-list") }
        output(if(prototype) "2" else "3")
        if(prototype) {
            click("manufacturing-list",hasText("Найти материалы")); val id=scenario.getValue("materialId").jsonPrimitive.long
            compose.waitUntil(20_000) { compose.onAllNodesWithTag("prototype-add-$id").fetchSemanticsNodes().isNotEmpty() };tagged("manufacturing-list","prototype-add-$id")
            tagged("manufacturing-list","prototype-material-qty-1");compose.onNodeWithTag("prototype-material-qty-1").performTextInput("4")
        }
        commit()
        if(prototype) { waitText("Введите только выпуск");output("3");commit();waitText("изготовлено 5 · осталось 5") }
        home()
    }
    @Test fun actualBackendAllThreeFlowsAndDifferentWorkerContinuation() {
        login(1);open("receive","Приёмка");receive("4",false)
        compose.activityRule.scenario.recreate();confirmed("receive-list")
        compose.onNodeWithText("Выйти").performClick();login(2);open("receive","Приёмка");receive("5",true)
        home();manufacture("fixed",false);manufacture("prototype",true)
        open("dispatch","Отправка");click("task-detail-list",hasText("Отправить товары"))
        click("dispatch-list",hasText("Отдано в этом шаге, шт"));compose.onNodeWithText("Отдано в этом шаге, шт").performTextInput("3")
        click("dispatch-list",hasText("Проверить отправку"));waitText("Подтвердить отправку?");compose.onNodeWithText("Подтвердить",substring=false).performClick();confirmed("dispatch-list")
        home();compose.onNodeWithTag("nav-stock").performClick();waitText("Физически: 43 шт")
    }
}
