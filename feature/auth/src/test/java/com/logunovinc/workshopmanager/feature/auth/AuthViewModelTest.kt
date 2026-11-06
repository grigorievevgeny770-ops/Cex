package com.logunovinc.workshopmanager.feature.auth

import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.auth.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }
    private class Repository(baseUrl: String = "https://example.com/") : AuthRepository {
        override val session = MutableStateFlow(SessionState())
        override val environment = MutableStateFlow(baseUrl)
        var calls = 0
        var environmentWrites=0
        val loginGate = CompletableDeferred<Unit>()
        var restoreError = AppError(ErrorKind.AUTHENTICATION, "Войдите")
        override suspend fun restore(): Outcome<Actor> { session.value = SessionState(restoring = false); return Outcome.Failure(restoreError) }
        override suspend fun login(login: String, password: String): Outcome<Actor> {
            calls++
            loginGate.await()
            val actor = Actor(42, login, "worker")
            session.value = SessionState(restoring = false, actor = actor)
            return Outcome.Success(actor)
        }
        override suspend fun logout(): Outcome<Boolean> { session.value = SessionState(restoring = false); return Outcome.Success(false) }
        override suspend fun changeEnvironment(baseUrl: String): Outcome<Unit> { environmentWrites++;environment.value = baseUrl; return Outcome.Success(Unit) }
    }
    private fun viewModel(repository: Repository, prefill: AuthPrefill) = AuthViewModel(repository, LoginUseCase(repository), RestoreSessionUseCase(repository), LogoutUseCase(repository), ChangeEnvironmentUseCase(repository), prefill)
    @Test fun localStartupPrefillsWithoutAutomaticallyLoggingIn() = runTest(dispatcher) {
        for (host in listOf("10.0.2.2", "127.0.0.1", "localhost")) {
            val repository = Repository("http://$host:8000/")
            val vm = viewModel(repository, AuthPrefill("fixture-local-user", "fixture-local-password"))
            runCurrent()
            assertEquals("fixture-local-user", vm.uiState.value.login)
            assertEquals("fixture-local-password", vm.uiState.value.password)
            assertEquals(0, repository.calls)
        }
    }
    @Test fun offlineAuthorizedStartupPrefillsButOtherOriginsAndPathsStayEmpty() = runTest(dispatcher) {
        for (baseUrl in listOf("http://10.0.2.2:8000/", "https://147-45-154-211.sslip.io/", "https://remote.example/", "https://remote.example:8000/", "http://localhost:9000/", "http://147-45-154-211.sslip.io/", "https://147-45-154-211.sslip.io:8000/", "https://147-45-154-211.sslip.io/other/", "https://147-45-154-211.sslip.io/?q=1", "https://147-45-154-211.sslip.io/#fragment", "https://user@147-45-154-211.sslip.io/", "http://localhost:8000/other/")) {
            val repository = Repository(baseUrl).apply { restoreError = AppError(ErrorKind.NETWORK, "Offline") }
            val vm = viewModel(repository, AuthPrefill("fixture-local-user", "fixture-local-password"))
            runCurrent()
            assertEquals(if (baseUrl in setOf("http://10.0.2.2:8000/", "https://147-45-154-211.sslip.io/")) "fixture-local-password" else "", vm.uiState.value.password)
            assertEquals(0, repository.calls)
        }
    }
    @Test fun appliedRemoteEnvironmentClearsBothPrefilledFields() = runTest(dispatcher) {
        val repository = Repository("http://localhost:8000/")
        val vm = viewModel(repository, AuthPrefill("fixture-local-user", "fixture-local-password"))
        runCurrent()
        vm.onIntent(AuthIntent.EnvironmentChanged("https://remote.example/"))
        vm.onIntent(AuthIntent.ApplyEnvironment)
        runCurrent()
        assertEquals("", vm.uiState.value.login)
        assertEquals("", vm.uiState.value.password)
    }
    @Test fun loginClearsPasswordAndLogoutShowsConfiguredDefaults() = runTest(dispatcher) {
        val repository = Repository("http://localhost:8000/")
        val prefill = AuthPrefill("fixture-local-user", "fixture-local-password")
        val vm = viewModel(repository, prefill)
        runCurrent()
        repository.loginGate.complete(Unit)
        vm.onIntent(AuthIntent.LoginClick)
        runCurrent()
        assertEquals("", vm.uiState.value.password)
        vm.onIntent(AuthIntent.LogoutClick)
        runCurrent()
        assertEquals("fixture-local-password", vm.uiState.value.password)
        assertEquals("fixture-local-user", vm.uiState.value.login)
        assertFalse(prefill.toString().contains("fixture-local-password"))
    }
    @Test fun duplicateLoginTapHasOneRequestAndPasswordClears() = runTest(dispatcher) {
        val repository = Repository()
        val vm = AuthViewModel(repository, LoginUseCase(repository), RestoreSessionUseCase(repository), LogoutUseCase(repository), ChangeEnvironmentUseCase(repository))
        runCurrent()
        vm.onIntent(AuthIntent.LoginChanged("worker"))
        vm.onIntent(AuthIntent.PasswordChanged("transient"))
        vm.onIntent(AuthIntent.LoginClick)
        vm.onIntent(AuthIntent.LoginClick)
        runCurrent()
        assertEquals(1, repository.calls)
        repository.loginGate.complete(Unit)
        runCurrent()
        assertEquals(42L, vm.uiState.value.actor?.id)
        assertEquals("", vm.uiState.value.password)
        vm.onIntent(AuthIntent.LogoutClick)
        runCurrent()
        assertNull(vm.uiState.value.actor)
    }
    @Test fun choosingRemoteAppliesAndPersistsWithoutLoginOrLeakingLocalPrefill() = runTest(dispatcher) {
        val repository=Repository("http://10.0.2.2:8000/")
        val prefill=AuthPrefill("fixture-local-user","fixture-local-password")
        val vm=viewModel(repository,prefill);runCurrent()
        assertEquals(ServerOption.EMULATOR,vm.uiState.value.serverOption)
        vm.onIntent(AuthIntent.ServerSelected(ServerOption.REMOTE));runCurrent()
        assertEquals("https://147-45-154-211.sslip.io/",repository.environment.value)
        assertEquals(ServerOption.REMOTE,vm.uiState.value.serverOption)
        assertEquals("",vm.uiState.value.login);assertEquals("",vm.uiState.value.password)
        assertEquals(0,repository.calls);assertEquals(1,repository.environmentWrites)
        val recreated=viewModel(repository,prefill);runCurrent()
        assertEquals(ServerOption.REMOTE,recreated.uiState.value.serverOption)
        assertEquals("fixture-local-password",recreated.uiState.value.password);assertEquals(0,repository.calls)
    }
    @Test fun customChoiceDoesNotSwitchUntilAppliedAndDraftCannotLoginIntoPreviousServer() = runTest(dispatcher) {
        val repository=Repository(ServerOption.REMOTE.baseUrl!!)
        val vm=viewModel(repository,AuthPrefill());runCurrent()
        vm.onIntent(AuthIntent.ServerSelected(ServerOption.CUSTOM))
        vm.onIntent(AuthIntent.EnvironmentChanged("https://custom.example/warehouse"))
        vm.onIntent(AuthIntent.LoginChanged("worker"));vm.onIntent(AuthIntent.PasswordChanged("transient"))
        vm.onIntent(AuthIntent.LoginClick);runCurrent()
        assertEquals(0,repository.calls);assertEquals(0,repository.environmentWrites)
        assertFalse(vm.uiState.value.environmentApplied)
        assertEquals(ServerOption.REMOTE.baseUrl,repository.environment.value)
        vm.onIntent(AuthIntent.ApplyEnvironment);runCurrent()
        assertEquals("https://custom.example/warehouse/",repository.environment.value)
        assertEquals(ServerOption.CUSTOM,vm.uiState.value.serverOption);assertTrue(vm.uiState.value.environmentApplied)
        val recreated=viewModel(repository,AuthPrefill());runCurrent()
        assertEquals(ServerOption.CUSTOM,recreated.uiState.value.serverOption)
        assertEquals(repository.environment.value,recreated.uiState.value.environmentInput)
        assertEquals("",recreated.uiState.value.password)
    }
    @Test fun presetSelectionOverridesUnappliedDraftAndRemoteDefaultPrefillsWithoutLogin() = runTest(dispatcher) {
        val repository=Repository(ServerOption.REMOTE.baseUrl!!)
        val vm=viewModel(repository,AuthPrefill("fixture-local-user","fixture-local-password"));runCurrent()
        assertEquals(ServerOption.REMOTE,vm.uiState.value.serverOption);assertEquals("fixture-local-user",vm.uiState.value.login);assertEquals("fixture-local-password",vm.uiState.value.password)
        vm.onIntent(AuthIntent.ServerSelected(ServerOption.CUSTOM));vm.onIntent(AuthIntent.EnvironmentChanged("invalid"));vm.onIntent(AuthIntent.ApplyEnvironment);runCurrent()
        assertNotNull(vm.uiState.value.error);assertEquals(0,repository.environmentWrites)
        vm.onIntent(AuthIntent.ServerSelected(ServerOption.REMOTE));runCurrent()
        assertEquals(ServerOption.REMOTE,vm.uiState.value.serverOption);assertEquals(ServerOption.REMOTE.baseUrl,vm.uiState.value.environmentInput)
        vm.onIntent(AuthIntent.ServerSelected(ServerOption.USB));runCurrent()
        assertEquals("http://127.0.0.1:8000/",repository.environment.value);assertEquals(ServerOption.USB,vm.uiState.value.serverOption)
        assertEquals(0,repository.calls)
    }

}
