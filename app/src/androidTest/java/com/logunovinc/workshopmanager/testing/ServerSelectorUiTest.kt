package com.logunovinc.workshopmanager.testing

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.logunovinc.workshopmanager.BuildConfig
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.auth.*
import com.logunovinc.workshopmanager.feature.auth.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*

/** Stateless Compose/MVI host with a local fake repository: no server is contacted. */
class ServerSelectorUiTest {
    @get:Rule val compose=createComposeRule()
    private class Settings : AuthRepository {
        override val environment=MutableStateFlow("https://147-45-154-211.sslip.io/")
        override val session=MutableStateFlow(SessionState(restoring=false))
        var logins=0
        override suspend fun restore():Outcome<Actor> = Outcome.Failure(AppError(ErrorKind.AUTHENTICATION,"Войдите"))
        override suspend fun login(login:String,password:String):Outcome<Actor> { logins++;error("Selection must never log in") }
        override suspend fun logout():Outcome<Boolean> = error("unused")
        override suspend fun changeEnvironment(baseUrl:String):Outcome<Unit> { environment.value=baseUrl;return Outcome.Success(Unit) }
    }
    private fun select(option:ServerOption) {
        compose.onNodeWithTag("auth-server-selector").performScrollTo().performClick()
        compose.onNodeWithTag("auth-server-${option.name.lowercase()}").performClick()
        compose.waitForIdle()
    }
    private fun emptyCredentials() {
        for(tag in listOf("auth-login","auth-password")) assertEquals("",compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
    }
    @Test fun presetsAndCustomApplyKeepActualServerVisibleAndRemoteCredentialsEmpty() {
        assertEquals("https://147-45-154-211.sslip.io/",BuildConfig.DEFAULT_BASE_URL)
        val settings=Settings()
        val vm=AuthViewModel(settings,LoginUseCase(settings),RestoreSessionUseCase(settings),LogoutUseCase(settings),ChangeEnvironmentUseCase(settings),AuthPrefill("fixture-local-user","fixture-local-password"))
        compose.setContent { val state by vm.uiState.collectAsState();AuthScreen(state,vm::onIntent) }
        compose.waitUntil(10_000) { !vm.uiState.value.busy && !vm.uiState.value.restoring }
        compose.onNodeWithText("Удалённый сервер").assertIsDisplayed();emptyCredentials()
        select(ServerOption.EMULATOR)
        compose.waitUntil(10_000) { settings.environment.value=="http://10.0.2.2:8000/" }
        compose.onNodeWithTag("auth-login").performScrollTo().performTextInput("fixture-local-user")
        compose.onNodeWithTag("auth-password").performScrollTo().performTextInput("fixture-local-password")
        select(ServerOption.REMOTE)
        compose.waitUntil(10_000) { vm.uiState.value.serverOption==ServerOption.REMOTE }
        emptyCredentials();assertEquals(0,settings.logins)
        select(ServerOption.CUSTOM)
        compose.onNodeWithTag("auth-server-custom-url").performScrollTo().performTextReplacement("https://custom.example/test")
        compose.onNodeWithTag("auth-submit").performScrollTo().assertIsNotEnabled()
        assertEquals("https://147-45-154-211.sslip.io/",settings.environment.value)
        compose.onNodeWithTag("auth-server-apply").performScrollTo().performClick()
        compose.waitUntil(10_000) { settings.environment.value=="https://custom.example/test/" }
        compose.onNodeWithText("Сервер: https://custom.example/test/").performScrollTo().assertIsDisplayed()
        assertEquals(ServerOption.CUSTOM,vm.uiState.value.serverOption)
        select(ServerOption.USB)
        compose.waitUntil(10_000) { settings.environment.value=="http://127.0.0.1:8000/" }
        assertEquals(0,settings.logins)
    }
}
