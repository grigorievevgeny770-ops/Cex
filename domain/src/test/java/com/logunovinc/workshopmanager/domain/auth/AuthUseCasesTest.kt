package com.logunovinc.workshopmanager.domain.auth

import com.logunovinc.workshopmanager.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AuthUseCasesTest {
    private class Repository : AuthRepository {
        override val session = MutableStateFlow(SessionState())
        override val environment = MutableStateFlow("https://example.com/")
        var calls = 0
        override suspend fun login(login: String, password: String): Outcome<Actor> { calls++; return Outcome.Success(Actor(42, login, "worker")) }
        override suspend fun restore() = Outcome.Success(Actor(42, "worker", "worker"))
        override suspend fun logout() = Outcome.Success(false)
        override suspend fun changeEnvironment(baseUrl: String): Outcome<Unit> { environment.value = baseUrl; return Outcome.Success(Unit) }
    }
    @Test fun loginValidatesBeforeRepository() = runTest {
        val repository = Repository()
        assertTrue(LoginUseCase(repository)(" ", "") is Outcome.Failure)
        assertEquals(0, repository.calls)
        assertEquals("worker", (LoginUseCase(repository)(" worker ", "secret") as Outcome.Success).value.login)
    }
    @Test fun canonicalEnvironmentExcludesCredentialsAndNormalizesPort() = runTest {
        val repository = Repository()
        assertTrue(ChangeEnvironmentUseCase(repository)("https://password@example.com/") is Outcome.Failure)
        assertTrue(ChangeEnvironmentUseCase(repository)("https://example.com/?key=value") is Outcome.Failure)
        assertTrue(ChangeEnvironmentUseCase(repository)("https://EXAMPLE.com:443/api") is Outcome.Success)
        assertEquals("https://example.com/api/", repository.environment.value)
        assertNull(CanonicalBaseUrl.parse("file:///tmp/"))
        assertNull(CanonicalBaseUrl.parse("http://localhost:0/"))
    }
    @Test fun restoreAndLogoutPreserveTypedResult() = runTest {
        val repository = Repository()
        assertEquals(42L, (RestoreSessionUseCase(repository)() as Outcome.Success).value.id)
        assertFalse((LogoutUseCase(repository)() as Outcome.Success).value)
    }
}
