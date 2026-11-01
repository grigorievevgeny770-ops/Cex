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
    private class Repository : AuthRepository {
        override val session = MutableStateFlow(SessionState())
        override val environment = MutableStateFlow("https://example.com/")
        var calls = 0
        val loginGate = CompletableDeferred<Unit>()
        override suspend fun restore(): Outcome<Actor> { session.value = SessionState(restoring = false); return Outcome.Failure(AppError(ErrorKind.AUTHENTICATION, "Войдите")) }
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
