package com.logunovinc.workshopmanager.data.remote

import com.logunovinc.workshopmanager.data.local.CookieStorage
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class MemoryCookies : CookieStorage {
    var value: String? = null
    override fun read() = value
    override fun write(value: String) { this.value = value }
}

class PersistentSessionCookieJarTest {
    private val origin = "https://example.com/".toHttpUrl()
    @Test fun persistsAndRestoresSessionCookieAndSetCookieUpdates() {
        val storage = MemoryCookies()
        val jar = PersistentSessionCookieJar(storage)
        jar.restore()
        jar.saveFromResponse(origin, listOf(Cookie.parse(origin, "session=first; HttpOnly; Path=/")!!))
        val restarted = PersistentSessionCookieJar(storage).apply { restore() }
        assertEquals("first", restarted.loadForRequest(origin).single().value)
        restarted.saveFromResponse(origin, listOf(Cookie.parse(origin, "session=second; HttpOnly; Path=/")!!))
        assertEquals("second", restarted.loadForRequest(origin).single().value)
        restarted.saveFromResponse(origin, listOf(Cookie.parse(origin, "session=; Max-Age=0; Path=/")!!))
        assertTrue(restarted.loadForRequest(origin).isEmpty())
    }
    @Test fun matchesHostPathSecureAndExpiry() {
        var now = 1000L
        val jar = PersistentSessionCookieJar(MemoryCookies()) { now }
        val cookie = Cookie.Builder().name("session").value("value").hostOnlyDomain("example.com").path("/auth").secure().expiresAt(2000).build()
        jar.saveFromResponse(origin, listOf(cookie))
        assertEquals(1, jar.loadForRequest("https://example.com/auth/me".toHttpUrl()).size)
        assertTrue(jar.loadForRequest("http://example.com/auth/me".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("https://other.com/auth/me".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("https://sub.example.com/auth/me".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("https://example.com/authentic".toHttpUrl()).isEmpty())
        now = 2001
        assertTrue(jar.loadForRequest("https://example.com/auth/me".toHttpUrl()).isEmpty())
    }
    @Test fun lateSetCookieCannotRestoreLogout() {
        val storage = MemoryCookies()
        val jar = PersistentSessionCookieJar(storage)
        val oldGeneration = jar.generation()
        jar.invalidate()
        jar.withGeneration(oldGeneration) { jar.saveFromResponse(origin, listOf(Cookie.parse(origin, "session=late; Path=/")!!)) }
        assertTrue(jar.loadForRequest(origin).isEmpty())
        assertTrue(PersistentSessionCookieJar(storage).apply { restore() }.loadForRequest(origin).isEmpty())
    }
}
