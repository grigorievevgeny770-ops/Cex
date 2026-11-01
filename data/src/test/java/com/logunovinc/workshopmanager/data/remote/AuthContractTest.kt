package com.logunovinc.workshopmanager.data.remote

import com.logunovinc.workshopmanager.data.local.EnvironmentSettings
import com.logunovinc.workshopmanager.data.repository.RealAuthRepository
import com.logunovinc.workshopmanager.data.remote.dto.DecimalSerializer
import com.logunovinc.workshopmanager.domain.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

class AuthContractTest {
    private lateinit var server: MockWebServer
    private lateinit var storage: MemoryCookies
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var jar: PersistentSessionCookieJar
    private lateinit var repository: RealAuthRepository
    private fun reply(body: String, code: Int = 200) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)
    @Before fun setup() {
        server = MockWebServer().apply { start() }
        storage = MemoryCookies()
        jar = PersistentSessionCookieJar(storage)
        val settings = object : EnvironmentSettings {
            var url = server.url("/").toString()
            override suspend fun read() = url
            override suspend fun write(baseUrl: String) { url = baseUrl }
        }
        repository = RealAuthRepository(settings, ApiFactory(json, jar), ApiErrorMapper(json), true, server.url("/").toString())
    }
    @After fun close() { server.shutdown() }
    @Test fun loginUsesCookieThenMeWithoutPersistingPassword() = runBlocking {
        server.enqueue(reply("""{"login":"worker","role":"worker"}""").addHeader("Set-Cookie", "session=synthetic; Path=/; HttpOnly"))
        server.enqueue(reply("""{"id":9223372036854775806,"login":"worker","role":"worker","future":true}"""))
        val result = repository.login("worker", "not-persisted") as Outcome.Success
        assertEquals(9223372036854775806L, result.value.id)
        assertEquals("/auth/login", server.takeRequest().path)
        val me = server.takeRequest()
        assertEquals("/auth/me", me.path)
        assertEquals("session=synthetic", me.getHeader("Cookie"))
        assertFalse(storage.value.orEmpty().contains("not-persisted"))
        assertEquals(result.value, repository.session.value.actor)
    }
    @Test fun logoutUsesCookiePathForItsActualEndpoint() = runBlocking {
        server.enqueue(reply("""{"login":"worker","role":"worker"}""").addHeader("Set-Cookie", "session=path-cookie; Path=/auth; HttpOnly"))
        server.enqueue(reply("""{"id":42,"login":"worker","role":"worker"}"""))
        assertTrue(repository.login("worker", "synthetic") is Outcome.Success)
        server.takeRequest(); server.takeRequest()
        server.enqueue(reply("""{"ok":true}"""))
        repository.logout()
        assertEquals("session=path-cookie", server.takeRequest().getHeader("Cookie"))
    }
    @Test fun legacyErrorAndUnauthorizedRestore() = runBlocking {
        server.enqueue(reply("""{"detail":"Неверный логин или пароль"}""", 401))
        val result = repository.login("worker", "bad") as Outcome.Failure
        assertEquals(ErrorKind.AUTHENTICATION, result.error.kind)
        assertEquals("Неверный логин или пароль", result.error.message)
        assertNull(repository.session.value.actor)
        server.enqueue(reply("""{"detail":"Требуется вход"}""", 401))
        assertTrue(repository.restore() is Outcome.Failure)
        assertTrue(jar.loadForRequest(server.url("/")).isEmpty())
    }
    @Test fun htmlAndMissingRouteAreIncompatible() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setHeader("Content-Type", "text/html").setBody("<html>login</html>"))
        assertEquals(ErrorKind.INCOMPATIBLE_SERVER, (repository.login("x", "x") as Outcome.Failure).error.kind)
        server.enqueue(reply("""{"detail":"Not Found"}""", 404))
        assertEquals(ErrorKind.INCOMPATIBLE_SERVER, (repository.login("x", "x") as Outcome.Failure).error.kind)
    }
    @Test fun lateLoginAfterLogoutCannotRestoreCookieOrActor() = runBlocking {
        server.enqueue(reply("""{"login":"worker","role":"worker"}""").addHeader("Set-Cookie", "session=late; Path=/").setBodyDelay(300, TimeUnit.MILLISECONDS).setHeadersDelay(300, TimeUnit.MILLISECONDS))
        server.enqueue(reply("""{"ok":true}"""))
        val login = async(Dispatchers.IO) { repository.login("worker", "synthetic") }
        server.takeRequest(3, TimeUnit.SECONDS) ?: error("login not sent")
        repository.logout()
        assertTrue(login.await() is Outcome.Failure)
        assertNull(repository.session.value.actor)
        assertTrue(jar.loadForRequest(server.url("/")).isEmpty())
    }
    @Test fun logoutClearsLocallyEvenWhenServerFails() = runBlocking {
        server.enqueue(reply("""{"ok":false}""", 500))
        assertEquals(false, (repository.logout() as Outcome.Success).value)
        assertNull(repository.session.value.actor)
        assertTrue(repository.session.value.message.orEmpty().contains("не подтверждён"))
    }
    @Test fun environmentSwitchClearsCookiesAndRejectsUnsafeTransport() = runBlocking {
        jar.saveFromResponse(server.url("/"), listOf(okhttp3.Cookie.parse(server.url("/"), "session=test; Path=/")!!))
        assertTrue(repository.changeEnvironment("https://other.example/") is Outcome.Success)
        assertTrue(jar.loadForRequest(server.url("/")).isEmpty())
        assertTrue(repository.changeEnvironment("http://other.example/") is Outcome.Failure)
    }

    @Test fun lateLogoutResponseCannotClearNewActor() = runBlocking {
        server.enqueue(reply("""{"ok":true}""").setHeadersDelay(400, TimeUnit.MILLISECONDS))
        val logout = async(Dispatchers.IO) { repository.logout() }
        assertEquals("/auth/logout", server.takeRequest(3, TimeUnit.SECONDS)?.path)
        server.enqueue(reply("""{"login":"new","role":"worker"}""").addHeader("Set-Cookie", "session=new; Path=/"))
        server.enqueue(reply("""{"id":43,"login":"new","role":"worker"}"""))
        assertTrue(repository.login("new", "synthetic") is Outcome.Success)
        logout.await()
        assertEquals(43L, repository.session.value.actor?.id)
    }
    @Test fun lateRestoreFailureCannotClearNewActor() = runBlocking {
        server.enqueue(reply("""{"detail":"Unavailable"}""", 500).setHeadersDelay(400, TimeUnit.MILLISECONDS))
        val restore = async(Dispatchers.IO) { repository.restore() }
        assertEquals("/auth/me", server.takeRequest(3, TimeUnit.SECONDS)?.path)
        server.enqueue(reply("""{"login":"new","role":"worker"}"""))
        server.enqueue(reply("""{"id":43,"login":"new","role":"worker"}"""))
        assertTrue(repository.login("new", "synthetic") is Outcome.Success)
        restore.await()
        assertEquals(43L, repository.session.value.actor?.id)
    }
    @Test fun environmentSwitchDuringLoginDoesNotSendMeOrRestoreOldSession() = runBlocking {
        server.enqueue(reply("""{"login":"old","role":"worker"}""").addHeader("Set-Cookie", "session=old; Path=/").setHeadersDelay(200, TimeUnit.MILLISECONDS))
        val login = async(Dispatchers.IO) { repository.login("old", "synthetic") }
        assertEquals("/auth/login", server.takeRequest(3, TimeUnit.SECONDS)?.path)
        assertTrue(repository.changeEnvironment("https://new.example/") is Outcome.Success)
        assertTrue(login.await() is Outcome.Failure)
        assertNull(repository.session.value.actor)
        assertEquals("https://new.example/", repository.environment.value)
        assertTrue(jar.loadForRequest(server.url("/")).isEmpty())
        assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
    }
    @Serializable data class QuantityDto(@Serializable(with = DecimalSerializer::class) val qty: BigDecimal, val id: Long)
    @Test fun exactDecimalScaleAndLongWireValues() {
        val wire = json.encodeToString(QuantityDto(BigDecimal("2.0000"), Long.MAX_VALUE))
        assertTrue(wire.contains("\"qty\":\"2.0000\""))
        assertEquals(BigDecimal("2.0000"), json.decodeFromString<QuantityDto>(wire).qty)
        assertEquals(Long.MAX_VALUE, json.decodeFromString<QuantityDto>(wire).id)
        assertTrue(runCatching { json.decodeFromString<QuantityDto>("""{"qty":2.0,"id":1}""") }.isFailure)
    }
    @Test fun validationArrayBecomesSafeFieldMessages() {
        val response = retrofit2.Response.error<Unit>(422, """{"detail":[{"loc":["body","qty"],"msg":"must be positive","input":"secret"}]}""".toResponseBody("application/json".toMediaType()))
        val error = ApiErrorMapper(json).response(response)
        assertEquals(listOf("qty: must be positive"), error.fieldIssues)
        assertFalse(error.message.contains("secret"))
    }
}
