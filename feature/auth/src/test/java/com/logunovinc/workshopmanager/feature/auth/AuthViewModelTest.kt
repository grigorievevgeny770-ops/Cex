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
        override suspend fun changeEnvironment(baseUrl: String): Outcome<Unit> { environment.value = baseUrl; return Outcome.Success(Unit) }
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
    @Test fun offlineLocalStartupStillPrefillsButRemoteAndOtherPortsStayEmpty() = runTest(dispatcher) {
        for (baseUrl in listOf("http://10.0.2.2:8000/", "https://remote.example/", "https://remote.example:8000/", "http://localhost:9000/")) {
            val repository = Repository(baseUrl).apply { restoreError = AppError(ErrorKind.NETWORK, "Offline") }
            val vm = viewModel(repository, AuthPrefill("fixture-local-user", "fixture-local-password"))
            runCurrent()
            assertEquals(if (baseUrl == "http://10.0.2.2:8000/") "fixture-local-password" else "", vm.uiState.value.password)
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
    @Test fun loginAndLogoutClearPasswordWithoutReapplyingPrefill() = runTest(dispatcher) {
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
        assertEquals("", vm.uiState.value.password)
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
}
